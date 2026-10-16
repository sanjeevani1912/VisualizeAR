# Prompt GS-04 — Region Workspace: Camera Lock, Mask, Grid

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then `docs/prompts/guided-setup/00-SETUP-SHARED.md`, then this file.
>
> **Prerequisites:** Phases 01–03 complete. A confirmed `layout.regionBounds` exists before SET_ORIGIN.
>
> **Do not** implement origin-inside-region stamping (Phase 05) or asset-picker workspace (Phase 06). You may draw the grid under the origin step and keep it for PLACE_ASSETS, but origin/asset chrome stays as Phase 01 left it.

## Role

You are a senior MapLibre Native for Android engineer turning a confirmed `GeoBounds` into a **workspace**: the map looks like “this field only”, with a grid.

This is the “third screen: selected map region and grid lines” beat — still the same `PlanningMapView`.

## Objective

Implement **Guided Setup Phase 04 only**:

When `setupStep` is `SET_ORIGIN` or `PLACE_ASSETS` **and** `regionBounds != null`:

1. Fit camera to the region (padding)
2. Constrain pan/zoom so the user cannot wander to another city
3. Dim the world **outside** the box (hole-punch)
4. Draw **grid lines inside the box**

When `setupStep` is FIND_LOCATION or SET_REGION, **do not** apply maxBounds/mask/grid (they must pan the world to choose a box).

## Interaction design (LOCKED)

### Camera

On entering SET_ORIGIN (after Phase 03 Confirm):

- Camera flies to the centroid of `regionBounds` with zoom chosen so the box fills ~70–85% of the view (compute from bounds + viewport; do not hardcode a single zoom for all region sizes).
- Set MapLibre `maxBounds` (or equivalent) to `regionBounds` expanded by a **small** padding (~5–10% of span, or ≥ ~20 m).
- Min zoom: user can still see the whole box. Max zoom: enough to place a tent (~5–10 m features), but not infinite.

When stepping **back** to SET_REGION, **clear** maxBounds/mask/grid so they can pick a new box.

### Mask

- World outside the region: dim with a translucent Coal/Cacao overlay (alpha ~0.35–0.5). Field Kit only.
- Interior of the region: full basemap visibility.
- Preferred implementation: a GeoJSON polygon covering a world bbox with a **hole** = region. Line the hole with Moss/Cacao stroke consistent with the existing region outline. Do not draw a second conflicting fill on top of today’s region layer — **reconcile** with the existing `regionBounds` fill in `PlanningMapView`. One fill + one hole-punch, not two muddy greens.

### Grid (LOCKED semantics)

Origin may **not** exist yet on SET_ORIGIN, so the grid **cannot** use `Anchor` meters until a fix exists.

| Step | Grid datum |
|------|------------|
| SET_ORIGIN | Display-only grid from the region **south-west corner**. Axis-aligned to true north / east (lat/lng projected via existing `GeographicCoordinateConverter` or Web Mercator line sampling). |
| PLACE_ASSETS (after origin exists) | **Re-align** grid to the geographic anchor: lines parallel to planning **+X (east of heading)** and **+Z (north of heading)** so the grid matches asset meters. If heading is null, treat heading as 0. |

Spacing (pick programmatically from the **shortest side** of the region):

| Shortest side | Spacing |
|---------------|---------|
| < 100 m | 10 m |
| < 500 m | 25 m |
| < 2 km | 50 m |
| else | 100 m |

Do not draw lines outside the region (clip). Keep line count reasonable (cap ~40 per axis) so MapLibre stays smooth.

Field Kit: grid lines Coal or Cacao at low alpha; maybe a slightly stronger line every 5th cell. No neon.

This grid is **visual only**. It does not snap assets in this phase (snapping is out of series unless already present on the metric canvas).

### Metric canvas

Do **not** auto-switch `PlanningSurface` to `METRIC_GRID`. The user asked for grid **on the map region**.

## Functional requirements

### 1. Pure geometry helpers (test these)

New file e.g. `domain/spatial/RegionWorkspaceGeometry.kt`:

- `gridSpacingMeters(bounds: GeoBounds): Double`
- `fitZoom(...)` or camera padding helper if you can do it without Android
- `gridLines(bounds, spacing, headingDegrees: Float?, originLatLng: Pair<Double,Double>?): List<LineString as lat/lng pairs>`
  - When `originLatLng` is null, SW corner + heading 0
  - When non-null, origin + heading

Keep geographic math consistent with `GeographicCoordinateConverter` (equirectangular field scale; antimeridian already unsupported by `GeoBounds`).

### 2. MapLibre layers

Extend `PlanningMapView.kt` (or a dedicated helper in `ui/map/`):

- Sources/layers added when style is ready and bounds exist and step ≥ SET_ORIGIN
- Removed / hidden on FIND_LOCATION and SET_REGION
- Follow existing GeoJSON overlay patterns used for asset footprints / region polygon
- Use `MapViewportBoundsBridge` / camera bridge style: no leaking Map objects into the ViewModel

### 3. Camera constraint lifecycle

- Apply on SET_ORIGIN enter and whenever `regionBounds` changes while in SET_ORIGIN / PLACE_ASSETS
- Clear on SET_REGION / FIND_LOCATION
- After `loadLayout` of a complete plan, apply workspace camera (fit once; do not fight user pan every recomposition — consume a one-shot like `MapCameraBridge`)

### 4. Performance

Do not rebuild GeoJSON every frame. Rebuild when bounds, spacing, origin, or heading change.

## Out of scope

- Origin stamp UX
- Asset chrome
- Snapping assets to grid
- Changing `GeoBounds` model
- New Nav route
- Web Mercator as a new canonical planner math (only OK as a drawing approximation if converter is insufficient for dense polylines — prefer converter)

## Tests

Add `RegionWorkspaceGeometryTest.kt`:

1. Spacing buckets at 80 m, 200 m, 1 km, 10 km
2. All grid points lie inside bounds (epsilon OK)
3. Line count cap
4. With origin+heading, a line near origin follows heading (assert endpoints roughly)
5. Null origin uses SW corner

Existing region tests still pass.

## Manual QA

1. Confirm a ~200 m box → SET_ORIGIN: map fills with that box, outside is dim, grid visible, cannot fling to another state
2. Back to SET_REGION: dim/grid/lock gone; can pan the city and drag a new box
3. Confirm a large (~2 km) box → coarser grid, still clipped
4. Load a saved layout with region+origin → workspace mask+grid+lock present (PLACE_ASSETS)
5. Style reload / leave and re-enter Planning does not crash; layers return

## Acceptance criteria

- [ ] SET_ORIGIN / PLACE_ASSETS fit and constrain camera to region
- [ ] Outside region is dimmed; interior clear
- [ ] Grid inside region with automatic spacing; visual only
- [ ] Grid datum SW-corner before origin; anchor-aligned after origin exists (even if Phase 05 has not changed stamp UX yet — use current anchor if `hasGeographicFix`)
- [ ] FIND_LOCATION / SET_REGION are unconstrained world maps
- [ ] Compiles; geometry tests pass
- [ ] Did **not** start Phase 05

## Deliverable summary format

1. Files changed
2. Layer / camera approach (and why)
3. Manual QA results
4. Deferred items
5. “Phase 04 complete; do not start Phase 05 unless asked”
