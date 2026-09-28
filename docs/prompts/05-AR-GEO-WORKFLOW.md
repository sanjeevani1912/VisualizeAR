# Prompt 05 — AR Visualization Relative to Geolocation

> Paste `00-SHARED-CONTEXT.md` above this prompt.  
> **Prerequisites:** Phases 1–4 complete (geo model, math, GNSS, MapLibre planning).

## Role

You are a senior ARCore engineer connecting DhruvAR’s geographic layouts to on-site AR visualization so 3D assets appear at the planned metric offsets relative to a real-world origin.

## Objective

Implement **Phase 5**: an AR workflow guided by the layout’s geographic anchor, reusing existing ARCore rendering (`ARRenderer`, `ARCoordinateTransformer`, object models) without persisting AR session anchors into JSON.

Also implement a clear path for **full-scale** visualization. Implement **miniature sand-table mode** if feasible in the same phase; if time-box requires split, deliver full-scale completely and stub miniature behind a clean API/flag with documented follow-up — prefer completing miniature if straightforward (uniform scale factor about origin).

## Core principle

```text
Saved layout geographicAnchor (lat, lng, heading)
        + object (x, z) meters
        ↓
User physically goes near the site
        ↓
AR origin placed on detected ground plane, yaw-aligned to heading/compass
        ↓
ARCoordinateTransformer places meshes at meter offsets
```

GPS does **not** continuously drag AR objects. GPS is for **getting the user to the origin** and validating they are near the planned site. Local AR tracking owns centimeter-scale stability during the session.

## Functional requirements

### 1. Pre-AR geo briefing panel
On `ARVisualizationScreen` (before/during calibration):
- Show saved anchor lat/lng/heading (or “Layout has no geographic anchor”)
- If device location available:
  - distance to anchor (haversine)
  - bearing to anchor
  - accuracy
- CTA states:
  - `TOO FAR` if distance > configurable threshold (default 50 m) — warn, allow proceed
  - `NEAR ORIGIN` when within threshold
  - `NO GEO` if layout lacks geographic fix — allow AR using local-only mode (current behavior)

### 2. Origin calibration
Keep existing plane hit + compass-aligned origin placement (`setOriginAtCenterHit`).
Enhancements:
- Prefer using **layout.anchor.headingDegrees** when present for north alignment; fall back to live compass
- After origin set, show confirmation: “Origin calibrated — rendering N objects”
- Reset origin remains available

### 3. Rendering
- Continue using planning `(x, z)` → world pose via `ARCoordinateTransformer`
- Do not convert every frame through GPS
- Selection, distance line, origin marker remain
- If miniature mode implemented: apply uniform scale `s` to horizontal offsets and model scales about origin (e.g. `s = 0.05` for desk); make scale user-selectable presets (Full 1:1, 1:20, 1:50)

### 4. Optional verification readout
For selected object, optionally show derived lat/lng from converter (debug/field verify). Keep UI compact.

### 5. Persistence rule
- Never write ARCore `Anchor` poses into layout JSON
- Only geographic fields + meter offsets remain canonical

## Non-goals
- Cloud anchors / multi-user shared AR sessions
- VPS / Geospatial API mandatory dependency (optional future; do not require Google Geospatial API for this phase unless already trivial — prefer compass + user-placed origin)
- Rewriting the entire renderer

## Tests
- Unit tests for distance/bearing helpers used in briefing panel
- Unit tests for miniature scale math if added
- Extend existing AR transformer tests if scale mode added
- Do not require instrumented ARCore tests to pass CI if environment lacks them; keep JVM tests solid

## Deliverables
1. Geo-aware AR entry/calibration UX
2. Full-scale geo layout visualization working end-to-end
3. Miniature mode (complete or clearly deferred with API hook)
4. Manual field test procedure
5. Summary of GPS vs AR responsibilities (short)

## Manual field test procedure (must include in summary)
1. Create layout on map at a known open area; set GNSS anchor; place objects at 5 m and 10 m north/east
2. Save layout
3. Walk to site; open AR; confirm distance-to-anchor guidance
4. Calibrate origin facing planned heading
5. Verify relative spacing/orientation of assets matches plan
6. Reset origin and recalibrate; confirm repeatability of relative layout

## Acceptance criteria
- [ ] AR screen communicates geo status and distance to anchor
- [ ] Origin calibration uses saved heading when available
- [ ] Objects render at planned meter offsets after calibration
- [ ] Layout JSON still has no AR session anchors
- [ ] Works for layouts without geo (legacy/local mode)
- [ ] Compiles; unit tests pass
