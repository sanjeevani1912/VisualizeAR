# Android developer guide

How an Android app uses this map server. Replace `BASE` with the server's public URL, for example
`https://maps.example.com`. In the Android emulator a server running on your PC is `http://192.168.1.205:8080`.

## What the server gives you


| What           | URL                                  | Notes                                                      |
| -------------- | ------------------------------------ | ---------------------------------------------------------- |
| Vector tiles   | `BASE/tiles/india/{z}/{x}/{y}.mvt`   | Mapbox Vector Tile, OpenMapTiles schema, z0-14, XYZ scheme |
| TileJSON       | `BASE/tiles/india.json`              | bounds, zoom range, tile URL template                      |
| Whole archive  | `BASE/files/india.pmtiles`           | single file for offline use; supports HTTP `Range`         |
| Map style      | `BASE/style.json`                    | MapLibre style using the tiles and glyphs above            |
| Glyphs (fonts) | `BASE/fonts/{fontstack}/{range}.pbf` | referenced by the style                                    |
| Place search   | `BASE/search?q=...`                  | no key needed                                              |


**No authentication.** Tiles, style, files and search are all open: the app sends no key or login. The server
itself attaches a search-only credential on the way to the search engine, and only `/search` is reachable, so
the app cannot (and need not) touch the admin side. If access control is needed later, put it in front of the
server (for example an API gateway) rather than in the app.

**One server address.** `style.json` and `tiles/india.json` contain absolute URLs built from the server's
`PUBLIC_URL` setting. The app must reach the server at exactly that address. For the emulator the admin sets
`PUBLIC_URL=http://10.0.2.2:8080`; for a real device on the same network use the PC's LAN address or, in production,
the HTTPS domain.

## 1. Show the map (easiest: MapLibre Native)

```kotlin
// build.gradle: implementation("org.maplibre.gl:android-sdk:<latest 11.x>")
MapLibre.getInstance(context)
mapView.getMapAsync { map ->
    map.setStyle(Style.Builder().fromUri("BASE/style.json"))
    map.cameraPosition = CameraPosition.Builder().target(LatLng(28.61, 77.21)).zoom(10.0).build()
}
```

The style shows water, land cover, roads, buildings, places and India's borders. Because the tiles
already contain India's official boundaries, **do not add another border layer or data source**.
Attribution must be visible in the app: `© OpenStreetMap contributors` and
`Boundaries: Survey of India via DataMeet (CC BY 4.0)`. The same text is in the TileJSON `attribution` field.

Cleartext HTTP is blocked on Android 9+ unless allowed. Use HTTPS in production. For local testing add a
network security config that permits your dev host only.

## 2. Fetch tiles yourself

Tile coordinates follow the usual web-map scheme (`z/x/y`, y counted from the top).

```kotlin
val client = OkHttpClient()
fun tile(z: Int, x: Int, y: Int): ByteArray? {
    val req = Request.Builder().url("BASE/tiles/india/$z/$x/$y.mvt").build()
    client.newCall(req).execute().use { r ->
        if (r.code == 204 || r.code == 404) return null      // no data here (open sea, outside India)
        check(r.isSuccessful) { "tile HTTP ${r.code}" }
        return r.body!!.bytes()      // OkHttp asks for gzip and undoes it; the server also sends plain bytes to clients that do not
    }
}
```

- A tile with no data (open sea, outside India) answers `204`. Treat it as "nothing to draw".
- If you read the raw bytes another way and they start with `1f 8b`, they are gzip: decompress before decoding.
- Add an HTTP cache to OkHttp (`Cache(dir, 200 MB)`); tiles change only when the server data is rebuilt.
- Tile size is small (a few KB to ~500 KB at busy z14 city tiles).



### Lon/lat to tile and back

```kotlin
fun lonToTileX(lon: Double, z: Int) = ((lon + 180.0) / 360.0 * (1 shl z)).toInt()
fun latToTileY(lat: Double, z: Int): Int {
    val r = Math.toRadians(lat)
    return ((1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * (1 shl z)).toInt()
}
```

Inside a tile, geometry uses integer coordinates `0..4095` (extent 4096), origin top-left, y down.
Coordinate of a point: `lon = (x + px/4096.0) / 2^z * 360 - 180`; latitude from the inverse Mercator of `(y + py/4096.0) / 2^z`.

## 3. Layers in the tiles (OpenMapTiles schema)


| Layer                                                   | Geometry       | Useful fields                                                                                       |
| ------------------------------------------------------- | -------------- | --------------------------------------------------------------------------------------------------- |
| `water`, `waterway`                                     | polygon / line | `class`                                                                                             |
| `landcover`, `landuse`, `park`                          | polygon        | `class`                                                                                             |
| `transportation`                                        | line           | `class` (motorway, trunk, primary, ..., path, rail), `brunnel` (bridge/tunnel), `oneway`, `surface` |
| `transportation_name`                                   | line           | `name`, `name:en`, `name:hi`, `ref`, `class`                                                        |
| `building`                                              | polygon        | `render_height`, `render_min_height` (metres)                                                       |
| `place`                                                 | point          | `name`, `name:*`, `class` (country, state, city, town, village, ...), `rank`                        |
| `poi`                                                   | point          | `name`, `class`, `subclass`                                                                         |
| `housenumber`, `aeroway`, `mountain_peak`, `water_name` | mixed          | as in OpenMapTiles                                                                                  |
| `boundary`                                              | line           | `admin_level`: `2` country, `4` state/UT; `disputed=0`; `maritime=0`; `claimed_by=IN`               |


Names: `name` is the local name; `name:en`, `name:hi`, `name:bn`, `name:te`, `name:mr`, `name:ta`,
`name:ur`, `name:gu`, `name:kn`, `name:or`, `name:ml`, `name:pa`, `name:as` exist where OpenStreetMap has them.
Use `name:<lang>` with `name` as fallback.

**Borders.** Only India's own borders are in the tiles: the official land border (including PoK,
Gilgit-Baltistan, Aksai Chin, Shaksgam and Arunachal Pradesh) and state/UT lines. Borders between other countries are
not drawn and there is no line along the coast. Draw the `boundary` layer as it is.

## 4. Drawing on a Canvas

MVT is a protobuf file (`vector_tile.proto`, spec 2.1). Decode it with any MVT decoder for the JVM (or generate
classes from the spec's `.proto` with protobuf-lite). Each layer has features with a type
(1 point, 2 line, 3 polygon), tags (attributes) and a command-encoded geometry.

Outline of a draw loop (illustrative sketch, not compiled against a specific library):

```kotlin
// for each visible tile, oldest zoom first: water/landcover -> roads -> buildings -> boundary -> labels
val scale = tileSizePx / 4096f
for (layer in listOf("water", "landcover", "transportation", "building", "boundary")) {
    for (f in decoded.layer(layer).features) {
        val path = Path()
        // MoveTo / LineTo / ClosePath commands -> path.moveTo(x*scale, y*scale) ...
        canvas.drawPath(path, paintFor(layer, f.attributes))
    }
}
```

Paint rules to start from: use the colours and widths in `BASE/style.json`, which is a readable example of a full
style. Keep tiles decoded in an LRU cache and redraw only on camera change.

## 5. ARCore and 3D planning

- Fetch z14 tiles around the user (a 3x3 block is about 7 km across). z14 tiles are about 2.4 km wide at India's latitudes.
- Convert tile coordinates to metres in a local east-north frame centred on the user's GPS position, then place
content relative to the ARCore anchor / Geospatial pose. Do this conversion yourself; the tiles carry only lon/lat.
- Buildings: extrude each `building` polygon from `render_min_height` to `render_height` (metres).
If `render_height` is missing, use a default (for example 8 m).
- Roads and paths: polylines from `transportation` (`class` tells you width and importance).
- Keep vertical scale in metres and the horizontal scale in the same units (metres per degree of latitude is
about 111,320; longitude scales by cos(latitude)).
- Use z14 for AR geometry, lower zooms for overview maps.



## 6. Search

`GET BASE/search?q=<text>&limit=10` (no headers needed).

```kotlin
val req = Request.Builder()
    .url("BASE/search".toHttpUrl().newBuilder()
        .addQueryParameter("q", query)
        .addQueryParameter("limit", "10")
        .build())
    .build()
```

Real response for `q=muzaffarabad` (trimmed; the first hit is the capital of PoK):

```json
{
  "hits": [
    { "id": "n212677583", "name": "مظفر آباد", "name_en": "Muzaffarabad",
      "names": ["Muzafarabad", "Muzaffarábád", "Музафарабад"],
      "kind": "place", "subtype": "city", "rank": 1,
      "_geo": { "lat": 34.373475, "lng": 73.470241 } }
  ],
  "query": "muzaffarabad", "estimatedTotalHits": 11
}
```


| Field             | Meaning                                                                                  |
| ----------------- | ---------------------------------------------------------------------------------------- |
| `id`              | OSM object: `n123` node, `w45` way, `r6` relation                                        |
| `name`            | name in the local script (may be Urdu, Hindi, ...)                                       |
| `name_en`         | English name, present for most places. Show `name_en ?: name` to English users           |
| `names`           | other-language and alternative spellings (searchable, for display only if you need them) |
| `rank`            | 1 city/state ... 9 ordinary POI; results are already ordered with cities first           |
| `kind`, `subtype` | e.g. `place`/`city`, `amenity`/`hospital`, `tourism`/`hotel`                             |
| `_geo`            | `lat`, `lng`                                                                             |


Options (query parameters):

- Typing as you go works: the last word matches as a prefix, and small typos are tolerated.
- Search in English or Latin script for reliable results. Devanagari queries (for example `दिल्ली`) match loosely and
can return unrelated cities, so prefer `name_en`-style queries for now.
- Nearest first: `sort=_geoPoint(28.61,77.21):asc` (your latitude, longitude).
- Only towns and cities: `filter=kind = place AND rank <= 2`. Filterable fields: `kind`, `subtype`, `rank`, `_geo`.
URL-encode the value (`--data-urlencode` in curl, `addQueryParameter` in OkHttp).
- At most 200 results per query. The search covers places, amenities, shops, tourism, historic sites, stations,
airports and peaks. Street names are not indexed.
- Search hits give a point; to show it use `_geo` and zoom the camera to about 15.

Debounce input (about 250 ms) and cancel the previous request when the text changes.

## 7. Offline use

`BASE/files/india.pmtiles` is the complete archive (several GB). Options:

- Download it once (`DownloadManager` handles resume) and open it with a PMTiles reader. Recent MapLibre Native
versions can read `pmtiles://` sources; check your version's release notes.
- Or read it remotely with HTTP `Range` requests, so only the needed tiles are transferred.

Search is online only (server-side).

## 8. Errors and edge cases


| Situation            | What to do                                                                                                      |
| -------------------- | --------------------------------------------------------------------------------------------------------------- |
| Tile `204`           | nothing to draw, do not retry                                                                                   |
| Tile `5xx` / timeout | retry with backoff, keep showing lower-zoom tiles                                                               |
| Search `401/403`     | wrong or expired key                                                                                            |
| Search `5xx`         | show "search unavailable"; the map still works                                                                  |
| Zoom above 14        | request z14 tiles and scale them up (overzoom); the server has no z15+                                          |
| Outside India        | empty tiles (`204`); data covers India only (68-97.5 E, 6.7-37.1 N), although the TileJSON reports world bounds |




## 9. Checklist for release

- HTTPS base URL (nothing secret goes in the APK).
- Attribution visible on every map screen.
- OkHttp cache on, `204` handled.
- Names use the user's language with `name` fallback.
- Do not overlay other administrative border data.

