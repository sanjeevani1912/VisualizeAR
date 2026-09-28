# Prompt 04 — MapLibre + PMTiles Planning Surface

> Paste `00-SHARED-CONTEXT.md` above this prompt.  
> **Prerequisites:** Phases 1–3 complete (geo model, math, GNSS origin).

## Role

You are a senior Android / maps engineer replacing blank-canvas-only planning with a geographically accurate MapLibre map fed by PMTiles, while preserving DhruvAR’s meter-based asset model and existing planning interactions.

## Objective

Implement **Phase 4**: MapLibre Native Android map in the planning flow with:
1. PMTiles basemap
2. Region selection
3. Geographic anchor visible/movable on map
4. Place / select / move / rotate assets on the map, stored as `(x, z)` meters relative to the geographic anchor via `GeographicCoordinateConverter`

## Locked technology choices
- **MapLibre** for map rendering
- **PMTiles** as tile source (bundled sample and/or configurable URL)
- **Not** Google Maps SDK / Google tile ToS workarounds
- Existing Compose Planning UX should remain coherent (tactical theme)

## Functional requirements

### 1. Dependencies & wiring
- Add MapLibre Android SDK via Gradle (version pin documented in summary)
- Ensure Compose interop (`AndroidView` MapView or official Compose bindings if suitable)
- Lifecycle: MapView `onStart/onStop/onDestroy/onLowMemory` correctly forwarded
- Config: e.g. `BuildConfig` or `res/values` for PMTiles path/URL — do not hardcode secrets

### 2. PMTiles source
- Support loading a PMTiles archive from:
  - app assets / files dir (offline sample), and/or
  - HTTPS URL (if plugin/support available)
- If a real production tileset is unavailable, include a **documented** sample approach and a clear `README` snippet under `docs/` or comments on how to plug the real PMTiles URL
- Show a non-blocking error state if tiles fail to load

### 3. Planning modes
Implement an explicit mode enum in ViewModel/UI, e.g.:
- `NAVIGATE` — pan/zoom map
- `SELECT_REGION` — user defines `GeoBounds` (drag rectangle or set from visible viewport + confirm)
- `SET_ANCHOR` — tap map to set/move geographic anchor (keep heading from compass or last heading)
- `PLACE_ASSET` — tap to place selected asset type
- `EDIT` — select/drag existing assets

Mode switcher UI must be clear and thumb-friendly.

### 4. Asset overlays
- Render each `LayoutObject` as a map annotation/symbol/polygon footprint using:
  - `metersOffsetToLatLng(anchor, obj.x, obj.z)`
  - rotationDegrees for icon/footprint orientation
- Selecting an asset updates existing `PlanningViewModel` selection state
- Dragging an asset updates `(x, z)` by converting new lat/lng → meters via converter
- Enforce placement inside `regionBounds` when bounds are set (snap reject or clamp — document choice)
- If `!anchor.hasGeographicFix`, block placement on map and prompt user to set origin (GNSS panel from Phase 3)

### 5. Dual surface strategy (important)
Choose **one** professional approach and implement it cleanly:

**Preferred:** MapLibre becomes the primary planning surface for geo layouts; keep the existing meter `PlanningCanvas` available as a secondary “Metric grid” tab/toggle for precise relative editing without map tiles.

OR:

Map underlay + metric overlay — only if technically clean with MapLibre.

Do **not** leave two divergent state systems. One `PlanningViewModel` / one `Layout` remains source of truth.

### 6. Preserve existing capabilities
Wire through existing behaviors where possible:
- asset picker / types
- rotation
- distance-to-anchor display
- save/load/rename layouts
- selection card

### 7. Performance
- Avoid rebuilding all annotations from scratch on every frame; update diffs when layout objects change
- Debounce high-frequency drag updates if needed

## Non-goals
- AR changes (Phase 5)
- Sharing/backend
- Perfect cadastral survey accuracy
- Google Places search

## Tests
- ViewModel: region set/clear; reject place without geo anchor; drag updates meters using fake converter if needed
- Converter integration tests already exist from Phase 2 — add map-related VM tests
- UI screenshot tests optional (not required)

## Deliverables
1. MapLibre planning integration
2. Region + anchor + asset placement on map
3. Toggle/tab for metric canvas if retained
4. Docs note: how to configure PMTiles
5. Manual QA script (create layout → set origin → select region → place 3 assets → save → reopen)

## Acceptance criteria
- [ ] Map renders from PMTiles (or documented sample)
- [ ] User can set region bounds on map
- [ ] User can set/move geographic anchor on map
- [ ] Assets placed on map persist as meter `(x, z)` + reload on correct geo positions
- [ ] Placement blocked without geographic anchor
- [ ] Existing save/load still works
- [ ] No Google Maps basemap
- [ ] Compiles; tests pass
