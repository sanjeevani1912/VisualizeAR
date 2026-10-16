# 00 — Guided Setup Shared Context

> Paste **after** `docs/prompts/00-SHARED-CONTEXT.md` and `docs/prompts/ux-field-kit/00-UX-SHARED.md`, and **before** any `guided-setup/0N-*.md` prompt.
>
> This addendum **overrides** the all-in-one Planning HUD described in the Field Kit shared context. Field Kit **colors, type, and component names still apply**. Geo math, schema v2, and AR rules from the base shared context still apply.

## Series goal

Replace the cluttered single Planning screen with a **gated setup sequence** on **one MapLibre map**:

1. Find a location (search)
2. Set a region (drag rectangle or use visible area)
3. Work inside that region (camera fitted, outside dimmed, grid overlay)
4. Set geographic origin (stamp GPS or tap inside the region)
5. Place / edit assets (only after origin)

New Plan always starts at step 1. Opening a saved layout resumes at the first incomplete step, or lands on the asset workspace if setup is done.

## Locked product decisions (do not reopen)

| Topic | Locked choice |
|-------|----------------|
| Navigation | **No new NavHost routes.** Keep `Screen.Planning`. One `PlanningMapView` instance. |
| Step model | `PlanningSetupStep`: `FIND_LOCATION` → `SET_REGION` → `SET_ORIGIN` → `PLACE_ASSETS` |
| Map instance | One MapLibre view for the whole sequence. Change HUD + gestures + camera, not screens. |
| Search | Reuse `PlanningSearchField` / `PlaceSearchGateway`. User **must pick a search hit** to leave step 1. |
| Region | Reuse `RegionDraftOverlay` (drag) + “Use visible area”. Confirm commits `GeoBounds`. |
| Grid | **Map overlay inside the region**, not a switch to `PlanningCanvas` during setup. |
| Origin | Reuse hybrid chip: Stamp GPS primary, Tap on map secondary. Tap/stamp **must be inside region**. |
| Assets | Hidden until `PLACE_ASSETS`. Existing `placeAssetAtGeographic` origin/region gates stay. |
| AR | Hidden until `PLACE_ASSETS` (needs origin). |
| Metric grid canvas | Hidden during setup. May reappear only in `PLACE_ASSETS` as an optional precision surface. |
| Back during setup | System back = previous step. From `FIND_LOCATION`, back = Home. From `PLACE_ASSETS`, back = Home. |
| Change after setup | Explicit “Change region” / “Change origin” on the workspace — not extra modes on the rail. |
| Schema | **No JSON version bump.** Do not persist the search hit. Region + origin already live on `Layout`. |
| Maps provider | Still MapLibre. Still map-server `/search`. **Not** Google Maps / Places. |

## Architecture (LOCKED)

```text
HomeScreen
  New Plan  →  createNewLayout()  →  setupStep = FIND_LOCATION  →  PlanningScreen
  Open plan →  loadLayout()       →  resolveSetupStep(layout)   →  PlanningScreen

PlanningScreen (same route, same ViewModel)
  ┌─────────────────────────────────────────┐
  │ PlanningMapView  (always, during setup) │
  │ chrome driven by uiState.setupStep      │
  └─────────────────────────────────────────┘
```

- `PlanningViewModel` remains the single source of UI + layout state.
- Add `setupStep` to `PlanningUiState`.
- Prefer a **pure resolver** (new file under `domain/` or next to `PlanningModes.kt`) so tests do not need Android.
- Do **not** create a second ViewModel, a wizard Nav graph, or a parallel layout draft object.

### Step resolver rules (LOCKED)

`resolvePlanningSetupStep(layout, session)`:

| Layout / session | Step |
|------------------|------|
| New layout (`createNewLayout`) | `FIND_LOCATION` even if leftover chrome exists |
| No `regionBounds`, no geo fix | `FIND_LOCATION` unless session already has a chosen search hit → then `SET_REGION` |
| Has `regionBounds`, no geo fix | `SET_ORIGIN` |
| Has `regionBounds` + geo fix | `PLACE_ASSETS` |
| Geo fix but **no** `regionBounds` (legacy) | `SET_REGION` (keep existing origin; after a new region is confirmed, if origin is outside the new box, clear origin and stay on `SET_ORIGIN`) |

Session-only (not JSON):

- `chosenLocation: ChosenMapLocation?` — lat, lng, display name, set when a search hit is selected
- Optional camera zoom used to fly the map

### HUD visibility matrix (LOCKED)

| Chrome | FIND_LOCATION | SET_REGION | SET_ORIGIN | PLACE_ASSETS |
|--------|---------------|------------|------------|--------------|
| Search | **primary** | hidden (or tiny “change place”) | hidden | hidden (optional later; not this series) |
| Region drag / Use area / Confirm | hidden | **primary** | hidden | hidden |
| Origin chip / sheet | hidden | hidden | **primary** | compact read-only + Change |
| Asset picker | hidden | hidden | hidden | **shown** |
| Mode rail (5 modes) | hidden | hidden | hidden | Navigate / Place / Edit only |
| Map / Grid switcher | hidden | hidden | hidden | optional |
| AR in top bar | hidden | hidden | hidden | shown |
| Step progress + Continue | shown | shown | shown | hidden (workspace) |
| Region grid overlay | no | no | **yes** | **yes** |
| Dim outside region | no | no | **yes** | **yes** |

Top bar titles (suggested copy; keep Field Kit tone, not cute):

- Find location
- Set region
- Set origin
- Planning (existing subtitle: name · saved · N assets)

Progress: `Step 1 of 3` / `2 of 3` / `3 of 3` while in the three setup steps. Workspace has no step bar.

## Current codebase facts (inspect before editing; 2026-10)

Update mentally if files moved.

### Navigation today
- `navigation/Screen.kt` — Home, Planning, ARVisualization, FieldGuide
- `navigation/AppNavigation.kt` — New Plan calls `createNewLayout()` then `navigate(Planning)`
- Shared `PlanningViewModel`

### Planning chrome today (the problem)
- `ui/screens/PlanningScreen.kt` overlays on one map:
  - Top: search **or** origin chip + Map/Grid switcher
  - Bottom: region context + 5-mode rail
  - BottomEnd: `FloatingAssetPickerOverlay`
  - Selection: `SelectedObjectEditorOverlay`
  - AR icon in `AppTopBar`
- `ui/components/PlanningHud.kt` — `planningHudToolsFor(surface, mode, …)` is **mode-aware**, not **setup-step-aware**

### Region today (reuse)
- `ui/map/RegionDraftOverlay.kt` + `domain/spatial/RegionDraftGeometry.kt`
- Confirm → `PlanningViewModel.setRegionBounds`
- Viewport fallback → `MapViewportBoundsBridge` + `setRegionBoundsFromViewport`
- Min side 25 m, max 500 km
- Committed overlay drawn in `PlanningMapView`

### Origin today (reuse)
- `ui/components/GeoOriginPanel.kt` — `GeoOriginChip` + `GeoOriginSheet`
- Stamp: `setGeographicAnchorFromDeviceFix`
- Tap: mode `SET_ANCHOR` → `setGeographicAnchorFromMap`
- Placement already requires `anchor.hasGeographicFix`

### Search today (reuse)
- `PlanningSearchField` / `PlanningSearchButton`
- `onSearchResultSelected` flies camera via `MapCameraBridge` and collapses search
- Does **not** advance any setup step (none exists yet)

### Map today
- `ui/map/PlanningMapView.kt` — tiles, region polygon, origin marker, asset footprints, drag
- `MapCameraBridge` / `MapCameraTarget` — fly to lat/lng/zoom
- **No** outside-region mask, **no** metric grid GeoJSON layer, **no** `maxBounds` lock

### Persistence today
- `Layout`: `regionBounds`, `anchor` geo fields, objects in meters
- `createNewLayout()` / `loadLayout()` reset surface to MAP + NAVIGATE; no setup step

## Architecture rules (this series)

1. **One phase at a time.** Do not implement later phases early (no grid in 01, no origin gating in 02, etc.).
2. Reuse existing search, region-drag, origin chip, and placement APIs. Do not fork them.
3. `Layout` JSON remains source of truth. Setup step is UI + derived from layout + session location.
4. Field Kit tokens only. No new purple/neon chrome. No dark-glass HUD.
5. Preserve geo math, reject-outside-region policy, ARCore session rules.
6. Touch targets ≥ 40 dp. Outdoor contrast.
7. Do not commit unless the user asks.
8. When finished: files changed, tests run, manual QA, deferred items, and “Phase N complete; do not start Phase N+1 unless asked”.

## Non-goals for the entire series

- New Compose Navigation destinations for search / region / origin
- Recreating MapLibre on every step (causes flicker and lost style)
- Google Maps SDK / Google Places
- Backend, share, approve, web
- Production 3D asset models
- Changing `(x, z)` meter conventions or schema version
- Redesigning AR tracking / Geospatial API
- Deleting `PlanningCanvas` (only hide it during setup)
- Rewriting Field Kit theme

## Definition of done (every phase)

1. Scope of that phase only — fully implemented
2. App compiles; relevant unit tests pass (`./gradlew :app:testDebugUnitTest` or the tests named in the phase)
3. Manual verification steps in the phase checklist are doable
4. Summary: files changed, how to verify, blocked/deferred
5. Explicit statement: “Phase N complete; do not start Phase N+1 unless asked”
