# 00 — UX Field Kit Shared Context

> Paste **after** `docs/prompts/00-SHARED-CONTEXT.md` and **before** any `ux-field-kit/0N-*.md` prompt.
>
> This addendum **overrides** outdated styling notes in the base shared context (navy/slate/precision blue). For this series, the Field Kit palette below is LOCKED.

## Series goal

Turn DhruvAR’s planning + AR surfaces into a **professional military day-ops** experience:

1. Always light Field Kit colors (never system dark mode)
2. Clean contextual HUD on Planning (no overlapping chrome)
3. Expandable place search against the map server
4. Drag-to-rectangle region selection (plus viewport fallback)
5. Hybrid origin chip (Stamp GPS + tap map + heading)
6. Collapsible AR geo briefing

## Locked decisions (do not reopen)

| Topic | Locked choice |
|-------|----------------|
| Theme | Force light; ignore `isSystemInDarkTheme()` |
| Palette | cacao, coal, golden hour, rust, mist, moss, meadow (hex below) |
| Region | Corner drag primary; “Use visible area” remains as secondary |
| Origin | Hybrid chip UX |
| Search | Icon → expands (not permanently open bar) |
| Planning chrome | Contextual HUD |
| AR top clutter | Collapsible briefing; hide 3-step progress once origin calibrated |
| Maps provider | Still MapLibre — **not** Google Maps SDK |
| Place search | Use map server `GET {BASE}/search` — **not** Google Places |

## Field Kit palette (LOCKED)

```text
cacao       #603620   deep brown — secondary surfaces / borders
coal        #231f20   near-black — primary text / icons on light
goldenHour  #f3e4bd   warm sand — elevated panels / chips
rust        #b56b37   terracotta — warnings / attention / secondary CTA
mist        #fcf9f2   off-white — app backgrounds
moss        #63703d   olive — primary actions / success / selected
meadow      #b5c37c   sage — secondary accents / region fill / soft highlights
```

### Suggested Material3 mapping (agents must implement consistently)

| Role | Color |
|------|--------|
| `background` | mist |
| `surface` / elevated panels | goldenHour (or mist with subtle cacao border) |
| `onBackground` / `onSurface` | coal |
| `primary` | moss |
| `onPrimary` | mist |
| `secondary` | cacao |
| `tertiary` / success accents | meadow (use carefully for contrast on mist) |
| Warning / accuracy poor | rust |
| Outline / dividers | cacao at reduced alpha, or coal @ ~20% |

Hardcoded `PrecisionBlue`, `Slate*`, dark `Color(0xCC0F172A)` overlays in AR/planning must be migrated to these tokens in the phase that owns that screen. Phase 01 establishes tokens; later phases must not reintroduce blue/slate dark HUD glass.

## Current codebase facts (as of UX series start)

Update mentally if files moved; inspect before editing.

### Theme today
- `ui/theme/Color.kt` — slate + PrecisionBlue + TacticalGreen/Amber
- `ui/theme/Theme.kt` — `darkTheme: Boolean = isSystemInDarkTheme()` chooses Dark/Light schemes
- Call site: `MainActivity` / root composable wrapping `DhruvARTheme`

### Planning chrome today (overlap problem)
- `ui/screens/PlanningScreen.kt` — Box overlay:
  - Top: `GeoOriginPanel` + `PlanningSurfaceSwitcher`
  - Bottom: `MapModeToolbar` (Nav/Region/Anchor/Place/Edit) + AR FAB
  - BottomEnd: `FloatingAssetPickerOverlay`
  - Selection: `SelectedObjectEditorOverlay`
- Region today: **viewport confirm only** via `MapViewportBoundsBridge.queryVisibleBounds()` + `setRegionBoundsFromViewport` — **no drag rectangle yet**
- `SELECT_REGION` map tap handler is intentionally `Unit` (no-op)
- Docs historically allowed drag rectangle (`docs/prompts/04-MAPLIBRE-PLANNING.md`) but it was never shipped

### Origin today
- `ui/components/GeoOriginPanel.kt` — expandable panel with lat/lng monospace dumps, Use location / Heading / Clear
- Map mode `SET_ANCHOR` taps map → `setGeographicAnchorFromMap`
- Device stamp → `setGeographicAnchorFromDeviceFix`

### Map / search today
- `data/map/MapBasemapConfig.kt` — `MAP_STYLE_URL`, optional `PMTILES_URL`
- **No** place-search client in the app yet
- Server contract documented in `docs/android.md` §6: `GET BASE/search?q=&limit=`
- Default style may be OpenFreeMap; search requires a **configured map server base** (BuildConfig) — Phase 03 must add this cleanly

### AR Visualize top clutter today
- `ui/screens/ARVisualizationScreen.kt` — top column stacks compass, status pills, `ArGeoBriefingPanel`, `ArScaleModeRow`, calibration feedback, 3-step progress Card
- Briefing component: `ui/components/ArGeoBriefingPanel.kt`

### Key files agents will touch across the series
- Theme: `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `MainActivity.kt`
- Planning: `PlanningScreen.kt`, `PlanningSurfaceControls.kt`, `GeoOriginPanel.kt`, `FloatingAssetPicker.kt`, `AppTopBar.kt`, `PrimaryButton.kt`
- Map: `ui/map/PlanningMapView.kt`, `MapBasemapConfig.kt`, `PlanningViewModel.kt`
- AR: `ARVisualizationScreen.kt`, `ArGeoBriefingPanel.kt`
- Config: `app/build.gradle.kts` BuildConfig fields
- Docs: `docs/FieldGuide.md`, `docs/PMTiles-Configuration.md` (extend for search base if needed)

## Architecture rules (UX series)

1. **One phase at a time.** Do not implement later UX phases early.
2. Prefer extending existing components over parallel duplicate HUDs.
3. Keep `PlanningViewModel` / `Layout` as source of truth — UX phases are mostly presentation + map gestures + search client.
4. Match Field Kit tokens; no new purple/neon/glow aesthetics.
5. Preserve geo math, schema, ARCore session rules from base shared context.
6. Touch targets ≥ 40dp where practical; field gloves / outdoor glare friendly contrast.
7. Do not commit unless the user asks.
8. When finished: list files changed, tests run, manual QA steps, and anything deferred.

## Non-goals for the entire UX series

- Google Maps SDK / Google Places / paid geocoding
- Dark mode / night ops theme
- Backend auth, multi-user sync, web client
- Redesigning asset 3D models or ARCore tracking algorithms
- Changing meter coordinate conventions
- Rewriting layout JSON schema (unless a phase explicitly requires a tiny additive UI-only preference — prefer none)

## Definition of done (every UX phase)

1. Scope of that phase only — fully implemented
2. App compiles; relevant unit tests pass
3. Manual verification steps in the phase checklist are doable
4. Summary: files changed, how to verify, blocked/deferred items
5. Explicit statement: “Phase N complete; do not start Phase N+1 unless asked”
