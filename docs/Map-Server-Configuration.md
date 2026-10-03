# Map server configuration (DhruvAR)

How the app is pointed at the self-hosted India map server described in `docs/android.md`.
For the current deployment's quirks and the open items with its owner, see
`docs/Map-Server-Handover-Notes.md`.

## The one setting

`app/build.gradle.kts`:

```kotlin
buildConfigField("String", "MAP_SERVER_BASE", "\"http://192.168.1.205:8080\"")
```

No trailing slash. Everything else is derived from it by `MapBasemapConfig`:

| Accessor | Derived value |
|----------|---------------|
| `styleUrl()` | `{BASE}/style.json` |
| `tileJsonUrl()` | `{BASE}/tiles/india.json` |
| `searchUrl()` | `{BASE}/search` |
| `pmtilesArchiveUrl()` | `{BASE}/files/india.pmtiles` |

Leave `MAP_SERVER_BASE` empty to fall back to `MAP_STYLE_URL` (a global OpenFreeMap style). Place
search is then unavailable and `isSearchConfigured()` returns false — callers must degrade calmly
rather than crash.

## Which address to use

| Running on | Base URL |
|------------|----------|
| Physical device on the office network | `http://192.168.1.205:8080` |
| Emulator, server on a **different** machine | `http://192.168.1.205:8080` |
| Emulator, server on the **same** machine as the emulator | `http://10.0.2.2:8080` |
| Production | `https://…` (then delete the cleartext exception below) |

`10.0.2.2` is the emulator's alias for its own host machine, so it only applies when the server runs
on that same machine. The current server does not, so the LAN address is correct in both device and
emulator cases.

Any new host must be added to `app/src/main/res/xml/network_security_config.xml` while it is
HTTP-only, otherwise every request fails silently.

## Why the style is rewritten before use

The server builds absolute URLs from its own `PUBLIC_URL`, and that substitution has not been
applied on the current deployment. `style.json` advertises:

```text
glyphs                      http://localhost:8080/fonts/{fontstack}/{range}.pbf
sources.openmaptiles.url    http://localhost:8080/tiles/india.json
```

and the TileJSON at that second URL advertises `http://localhost:8080/tiles/india/{z}/{x}/{y}.mvt`
in turn. On any machine other than the server, `localhost` resolves to the client itself, so the
style parses and then renders nothing — a blank map with no error.

`MapStyleLoader` therefore fetches both documents, hands them to `MapStyleRewriter`, and gives
MapLibre the corrected style via `Style.Builder().fromJson(...)` instead of a URL.
`MapStyleRewriter`:

1. replaces the placeholder base (and the `127.0.0.1` / `0.0.0.0` variants) with `MAP_SERVER_BASE`;
2. inlines the TileJSON's `tiles` templates into the style source and drops its `url`, so MapLibre
   never fetches the TileJSON whose own `tiles` array repeats the placeholder.

Step 2 is the part that is easy to miss — fixing `style.json` alone still leaves the tile templates
broken.

Only sources already pointing at `MAP_SERVER_BASE` are inlined, so third-party sources in a style
are untouched. The rewrite is idempotent and stays correct once the server-side fix lands, so
nothing here needs reverting then.

If the server is unreachable, `PlanningMapView` falls back to handing the raw `styleUrl()` to
MapLibre and surfaces `Map server unreachable (<base>): <reason>`. The warning deliberately
survives a successful style load, because the fallback style can load fine and still show no tiles.

## Cleartext HTTP exception

The server's 8443 listener is published but has no certificate, so the TLS handshake fails and only
plain HTTP on 8080 works. `app/src/main/res/xml/network_security_config.xml` therefore permits
cleartext **for `192.168.1.205` and `10.0.2.2` only**, with `cleartextTrafficPermitted="false"` as
the base policy.

Do not replace this with `android:usesCleartextTraffic="true"` on `<application>` — that permits
downgraded connections to every destination the app will ever use.

Search queries reveal operational intent and are the sensitive part of this traffic, not the map
data. Remove the `domain-config` block as soon as the server terminates TLS.

## Place search

`PlaceSearchClient.search(query, limit, near)` → `GET {BASE}/search?q=…&limit=…`, no credentials.
`PlaceSearchResponseParser` turns the response into `PlaceSearchHit`s, skipping any hit without
usable coordinates or a name.

Behaviour confirmed against the live server, where it differs from `docs/android.md`:

- the server's default `limit` is **20**, not 10, so the client always sends `limit` explicitly;
- the envelope also carries `processingTimeMs`, `limit`, `offset` and `requestUid`;
- `name_en` is present **only** when `name` is non-Latin. `Delhi` has no `name_en`;
  `Muzaffarabad` has an Urdu `name` plus `name_en`. Use `PlaceSearchHit.displayName`.

Street names are not indexed; the index covers places, amenities, shops, tourism, historic sites,
stations, airports and peaks.

### Search UI

The search icon in the planning HUD expands into a field with a results list. `PlanningViewModel`
owns the state (`PlaceSearchUiState`) and talks to a `PlaceSearchGateway`, which exists so the
debounce and cancellation logic can be tested without a server.

- **Debounce** `PlanningViewModel.SEARCH_DEBOUNCE_MILLIS` (250 ms). Each keystroke cancels the
  previous job, and cancelling the job cancels the HTTP call, so a slow early response cannot
  overwrite a newer query.
- **Nearby bias** when the device has a finite fix, by adding `sort=_geoPoint(lat,lng):asc`.
- **No `filter`.** `filter=kind = place AND rank <= 2` would drop hospitals, stations and airports,
  which are exactly what an operational planner looks up. The server already returns results in
  rank order, so settlements lead anyway — a query for `delhi` returns the state and the city
  before any shop that happens to be named Delhi.
- **Unconfigured base** shows `Map search server not configured` on expanding, and issues no
  request at all. Tiles and the rest of planning are unaffected.
- **Request failure** shows `Search unavailable — check the map server`. The map stays pannable on
  already-cached tiles.
- Choosing a result moves only the camera, to `MapBasemapConfig.SEARCH_RESULT_ZOOM`, via
  `MapCameraBridge`. The layout origin and region bounds are untouched.

Search is hidden on the **metric grid** surface, because a result can only be expressed as a camera
move and the grid has no camera. Switch to Map first.

## Attribution

`MapBasemapConfig.ATTRIBUTION` is rendered on the planning map and is mandatory per
`docs/android.md` §9:

```text
© OpenStreetMap contributors · Boundaries: Survey of India via DataMeet (CC BY 4.0)
```

India's official boundaries are baked into the tiles. Do not add any other administrative border
layer or source on top of them.

## Offline

`pmtilesArchiveUrl()` exposes the full archive (~1.92 GB, `Accept-Ranges: bytes`). Not yet consumed
by the app; see `docs/PMTiles-Configuration.md` for the intended paths. Search is online only.

## Verifying a base URL

```powershell
Invoke-WebRequest 'http://192.168.1.205:8080/search?q=delhi&limit=2' -UseBasicParsing
Invoke-WebRequest 'http://192.168.1.205:8080/style.json' -UseBasicParsing
Invoke-WebRequest 'http://192.168.1.205:8080/tiles/india.json' -UseBasicParsing
```

A 200 from all three means the base is good. If `style.json` still mentions `localhost`, the
server-side fix has not been applied and the client-side rewrite is doing the work.
