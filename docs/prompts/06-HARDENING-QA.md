# Prompt 06 — Hardening, Field Guide, Schema Freeze & Regression QA

> Paste `00-SHARED-CONTEXT.md` above this prompt.  
> **Prerequisites:** Phases 1–5 implemented.

## Role

You are a staff engineer hardening the geo-map + AR feature for field use and freezing the layout contract so backend/web can later consume the same JSON.

## Objective

Implement **Phase 6**: production-quality error handling, UX copy, documentation, regression tests, and a frozen schema note. No new major features unless required to fix gaps found during QA.

## Workstreams

### 1. Failure modes (must handle gracefully)
Implement or verify polished handling for:
- Location permission denied / permanently denied
- Location services disabled
- GNSS accuracy very poor
- PMTiles missing / network failure / corrupt archive
- Layout missing geographic anchor when user tries map placement
- Layout missing region bounds (define whether placement allowed globally or blocked)
- AR on device without geo fix
- AR when user is far from anchor
- Map mode vs metric grid mode state restoration

All paths: no crashes, clear tactical-style messages, recoverable actions.

### 2. Field Guide updates
Update `FieldGuide` / How-to-use content to explain:
1. Set geographic origin (GNSS + heading)
2. Select region on map
3. Place assets (meters under the hood)
4. Save
5. On-site AR calibration
6. What GPS accuracy means vs relative meter precision

Keep tone consistent with existing guide.

### 3. Schema freeze document
Create `docs/layout-schema-v2.md` describing:
- JSON shape
- field meanings
- coordinate conventions (X east, Z north/heading-aligned, Y up)
- nullability rules
- migration from v1
- explicit statement: AR session anchors are not part of the document
- compatibility notes for future web MapLibre client

### 4. Regression test pack
Add/extend tests so CI covers:
- v1 deserialize
- v2 round-trip with region + geo anchor + objects
- converter round-trip
- ViewModel: cannot place on map without geo (if logic is testable)
- Spatial distance still correct
- Serializer edge cases (NaN, missing fields)

Run unit tests and fix failures.

### 5. Performance & polish pass
- Annotation update thrash on map
- Location updates stopped when leaving Planning
- MapView lifecycle leaks checked
- Remove dead debug logs

### 6. Final acceptance demo script
Write `docs/geo-feature-acceptance.md` with a 10–15 step demo script matching the final desired output.

## Final desired output (verify end-to-end)

A user can:
1. Open DhruvAR Planning
2. See MapLibre basemap from PMTiles
3. Capture/set geographic anchor via GNSS + heading
4. Select a geographic region
5. Place/edit tents/vehicles/antennas on the map
6. Save a versioned layout JSON with geo + meter offsets
7. Open AR, navigate using distance/bearing to anchor, calibrate origin
8. See 3D assets at correct relative real-world positions
9. Reload the layout later and get the same plan

## Non-goals
- Backend API implementation
- Web React client
- Google Maps migration
- Multi-user live AR

## Deliverables
1. Hardening fixes
2. `docs/layout-schema-v2.md`
3. `docs/geo-feature-acceptance.md`
4. Updated Field Guide
5. Green unit test suite
6. Concise release notes paragraph for the feature

## Acceptance criteria
- [ ] Critical failure modes handled
- [ ] Schema doc matches actual serializer output
- [ ] Acceptance script is accurate to the build
- [ ] Unit tests pass
- [ ] No unresolved crash bugs in the geo/map/AR happy path
- [ ] Summary lists known limitations (GPS absolute error, antimeridian, etc.)
