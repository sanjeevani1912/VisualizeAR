# Session progress — map server + Field Kit UX

Written so this work survives a chat that loses its earlier turns. Last updated 2026-10-05.

This file is the durable record of the conversation and the code underneath it. The Cursor chat titled **Map server and UI prompts** is the same thread. If that window looks empty, the commits and this file are the source of truth.

## Where the chat went

1. Order of work: map server first, then UX Field Kit 01 → 06.
2. Docker is on the other office PC, not this one. This PC is `192.168.1.206`. The map server is `192.168.1.205`.
3. `site-init` (Alpine) exiting is normal. Leave it off. Caddy, tiles, and search stay running.
4. `style.json` still advertises `http://localhost:8080`. The app rewrites that to the real base. Do not change Docker for it.
5. After that was confirmed, implementation continued in this same chat: map server, UX-01, UX-02, UX-03, then UX-04. The chat UI later stopped showing those turns. The git history did not.

## Server (leave this alone)

| Item | Value |
|------|--------|
| Base | `http://192.168.1.205:8080` |
| This PC | `192.168.1.206` |
| Search | `GET /search?q=delhi` answered 200 from both machines |
| Style / tiles | Answered 200, but URLs inside say `localhost` |
| Ports | 8080 open. 8443 accepts TCP but TLS fails (no certificate). 7700 and 2019 closed (good). |
| App config | `BuildConfig.MAP_SERVER_BASE` |

Handover notes for the server owner: `docs/Map-Server-Handover-Notes.md`.

## Commits already on `main` (2026-10-03)

| Commit | What |
|--------|------|
| `967b7ba` | Map server wiring, localhost rewrite, search client, cleartext only for that host |
| `c5fd025` | UX-01 force light Field Kit theme |
| `9895ace` | UX-02 contextual planning HUD |
| `cb3d509` | UX-03 expandable place search |

Verified on a Pixel 6a for the map server commit: India tiles, Survey of India borders, server fonts.

## UX Field Kit status

| Phase | Status |
|-------|--------|
| 01 Theme | Done, committed |
| 02 HUD | Done, committed |
| 03 Search | Done, committed |
| 04 Region drag | Implemented. Gesture bug fixed 2026-10-05 (finger-up never finished the box because a consumed pointer reported itself as still down). Not committed. |
| 05 Origin chip | Implemented 2026-10-05. Stamp GPS primary, Tap on map secondary, lat/lng behind Details. Not committed. |
| 06 AR briefing | Implemented 2026-10-05. Chip by default after calibration; 3-step bar hidden once the origin is set. Not committed. |

## Phase 04 decisions

- Approach A: a Compose overlay owns the drag so the map cannot pan under the box.
- Pointer-up does not commit. Confirm commits via `setRegionBounds`. Cancel drops the draft.
- Minimum side 25 m, maximum 500 km (`RegionDraftGeometry`).
- “Use visible area” stays as the fallback when no draft is active.
- Region mode does not pan. Pan in Navigate, then come back to Region.

## Phase 05 decisions

- Chip pill is `Not set` or `Set`. Readiness is the pill colour (Moss / Rust / Coal), not a second word.
- Stamp GPS collapses the sheet and shows “Origin stamped”.
- Tap on map selects Anchor mode and closes the sheet. A successful map tap returns to Navigate.

## Phase 06 decisions

- Before calibration the briefing starts expanded and the 3-step bar stays.
- After calibration the briefing collapses to `Geo · Near` / `Geo · Far` / `Geo · Local` / `Geo · No fix`, and the step bar is not drawn.
- Scale stays one row. It hides only while the post-calibration briefing is expanded, so the two do not stack.

## Tests

2026-10-05: `RegionDraftGeometryTest`, `OriginChipStatusTest`, `ArGeoWorkflowTest`, `GeoOriginViewModelTest`, `PlanningHudToolsTest`, and `MapPlanningViewModelTest` passed.

## Asset follow-up (2026-10-05, not committed)

Placed assets were identical map dots, could not be dragged, and the property card opened at full height over the map.

- Map assets are now top-down shapes (tent, truck, car, antenna, trench) drawn to their real ground size.
- Drag an asset on the map in Navigate or Edit. The grid still drags as before.
- Selecting an asset shows a one-line chip (name, distance, “drag to move”). The full position / rotation / size card opens from that chip.
- AR still uses the built-in meshes. There are no photographed or glTF models in the project.

## Not done on a device this pass

The finger-drag, stamp, and AR collapse still need a phone pass. No commit was made; commits happen only when asked.
