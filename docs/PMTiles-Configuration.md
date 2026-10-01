# Map / PMTiles configuration (DhruvAR)

DhruvAR uses **MapLibre Native Android** (`org.maplibre.gl:android-sdk:11.8.0`) with native `pmtiles://` support (requires MapLibre ≥ 11.7.0).

## Default basemap (global — India works)

`BuildConfig.MAP_STYLE_URL` points at **OpenFreeMap Liberty** (OpenStreetMap data, free, no API key):

```text
https://tiles.openfreemap.org/styles/liberty
```

This is a **worldwide** vector style: streets, places, and buildings appear when you zoom into India or anywhere else.

Configured in `app/build.gradle.kts`:

```kotlin
buildConfigField(
    "String",
    "MAP_STYLE_URL",
    "\"https://tiles.openfreemap.org/styles/liberty\""
)
buildConfigField("String", "PMTILES_URL", "\"\"")
```

### Why the old demo looked empty over India

The previous MapLibre demo style:

```text
https://demotiles.maplibre.org/pmtiles/raster/style-imagery.json
```

is named **“Central Alps: Sentinel-2 Imagery”**. Its satellite raster PMTiles archive only covers the Alps (Austria / Italy area). Outside that box you only saw sparse place-name labels from a vector overlay — not a bug in zoom gestures.

## Plug in a production / site PMTiles archive

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

Keep OpenStreetMap / OpenFreeMap attribution visible (MapLibre shows it automatically when the style declares it).

## Dual surface

- **Map** — MapLibre primary planning surface (region / anchor / place / edit).
- **Metric grid** — existing meter canvas for precise relative edits without tiles.

One `PlanningViewModel` / `Layout` remains the source of truth; object positions stay `(x, z)` meters relative to the geographic anchor.
