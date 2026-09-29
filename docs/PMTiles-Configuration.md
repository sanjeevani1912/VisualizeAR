# PMTiles configuration (DhruvAR Phase 4)

DhruvAR uses **MapLibre Native Android** (`org.maplibre.gl:android-sdk:11.8.0`) with native `pmtiles://` support (requires MapLibre ≥ 11.7.0).

## Default (demo) basemap

`BuildConfig.MAP_STYLE_URL` points at MapLibre’s public PMTiles imagery style:

```text
https://demotiles.maplibre.org/pmtiles/raster/style-imagery.json
```

That style JSON already references a remote `pmtiles://https://…` raster archive (Central Alps demo). No Google Maps tiles are used.

Configured in `app/build.gradle.kts`:

```kotlin
buildConfigField(
    "String",
    "MAP_STYLE_URL",
    "\"https://demotiles.maplibre.org/pmtiles/raster/style-imagery.json\""
)
buildConfigField("String", "PMTILES_URL", "\"\"")
```

## Plug in a production PMTiles archive

### Option A — Style JSON (recommended)

1. Host a MapLibre style whose sources use fully-qualified PMTiles URLs, e.g.:

```json
"sources": {
  "basemap": {
    "type": "raster",
    "url": "pmtiles://https://your-cdn.example.com/site.pmtiles",
    "tileSize": 256
  }
}
```

2. Set `MAP_STYLE_URL` to that style’s HTTPS URL (or ship the JSON and load via `asset://`).

### Option B — Local offline file

1. Copy `site.pmtiles` into the app files dir (e.g. `context.filesDir`).
2. Add a raster source at runtime:

```text
pmtiles://file:///data/user/0/…/files/site.pmtiles
```

> Note: `pmtiles://asset://` byte-range reads may be limited on some MapLibre builds; prefer `file://` from app storage for offline packs.

### Option C — Direct `PMTILES_URL` BuildConfig

Set `PMTILES_URL` to a non-empty `pmtiles://https://…` or `pmtiles://file://…` value when you extend `MapBasemapConfig` / style builder to inject a programmatic raster style. Today the app loads `MAP_STYLE_URL` directly.

## Network

`INTERNET` + `ACCESS_NETWORK_STATE` are declared for remote styles/archives. Failures surface as a non-blocking banner on the planning map (“Map tiles unavailable…”).

## Dual surface

- **Map** — MapLibre primary planning surface (region / anchor / place / edit).
- **Metric grid** — existing meter canvas for precise relative edits without tiles.

One `PlanningViewModel` / `Layout` remains the source of truth; object positions stay `(x, z)` meters relative to the geographic anchor.
