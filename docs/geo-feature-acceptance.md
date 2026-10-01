# Geo feature acceptance demo script

End-to-end check against the current DhruvAR build (Phases 1–6). Use a device/emulator with Play Services, network (for OpenFreeMap / tiles), and ARCore where available.

## Demo script (15 steps)

1. **Launch** DhruvAR → Home. Optionally open **Field Guide** and skim geo workflow.
2. **Create new layout** → Planning opens on **Map** surface.
3. Confirm **MapLibre** basemap loads (OpenFreeMap Liberty — streets worldwide, including India). If offline, a non-blocking “Map tiles unavailable” banner appears — app does not crash.
4. Expand **GEO ORIGIN** → grant location (or deny once: clear message + retry / Open settings if permanent).
5. With GPS on, wait for **READY** (or stamp with **ACCURACY POOR** and note the site-level warning).
6. **Use my location** + **Heading** → anchor shows lat/lng/heading. Save is marked unsaved until you save.
7. Mode **Region** → pan to site → **Use visible area** → cyan region overlay appears.
8. Mode **Place** (or **+** asset) → tap map three times (tent / vehicle / antenna). Confirm footprints/markers.
9. Intentionally tap **outside** region → placement rejected with snackbar (not clamped).
10. Clear GEO ORIGIN temporarily → try Place → blocked with prompt to set origin; restore origin.
11. **Save** layout. Confirm file/schema v2 (geo + meters). Reopen from Home → same plan.
12. Toggle **Metric grid** → nudge an object → toggle back to **Map** → position matches (one ViewModel).
13. **Visualize in AR** → **GEO BRIEFING** shows distance/bearing (`NEAR ORIGIN` / `TOO FAR` / `NO GEO` as applicable). Far = warn, proceed allowed.
14. Detect ground → **Set Planning Origin** (prefers saved heading). See “Origin calibrated — rendering N objects”. Assets at relative meters. Try **1:20** scale then **1:1**.
15. **Reset** origin, recalibrate, confirm relative layout repeatability. Back to planner → reload layout → plan unchanged. **No AR session poses** in JSON.

## Pass criteria

- No crashes on permission deny, GPS off, tile fail, missing geo, outside region, AR without geo.
- Saved JSON remains the source of truth across Map / Metric / AR.
- Unit test suite green (`./gradlew :app:testDebugUnitTest`).

## Release notes (paste-ready)

> DhruvAR now supports geographically accurate field planning: MapLibre + PMTiles basemap, GNSS/compass origin stamping, optional region bounds, and asset placement stored as metric offsets in schema v2 JSON. On-site AR guides you to the origin with distance/bearing, calibrates using the saved heading, and renders assets at planned relative positions (full-scale or miniature). Legacy layouts without geo continue to work in local mode.

## Known limitations

- Absolute GNSS error vs relative meter precision.
- Basemap needs network unless a local PMTiles archive is configured (`docs/PMTiles-Configuration.md`).
- Antimeridian region boxes unsupported.
- Equirectangular geo math: field scale ≤ ~1–2 km recommended.
- AR requires user-placed plane origin (no VPS / Geospatial API in this release).
