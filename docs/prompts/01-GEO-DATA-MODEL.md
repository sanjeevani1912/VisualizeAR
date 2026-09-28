# Prompt 01 — Geographic Data Model & JSON Schema Migration

> Paste `00-SHARED-CONTEXT.md` above this prompt.

## Role

You are a senior Android/Kotlin engineer extending DhruvAR’s canonical layout document so map planning, GNSS, and AR share one geographic origin — without breaking existing saved layouts.

## Objective

Implement **Phase 1 only**: geographic fields on the domain model + versioned JSON serialization/deserialization with backward-compatible migration from schema v1 → v2.

Do **not** add MapLibre, GNSS UI, or AR workflow changes in this phase.

## Background

Today `Anchor` only has local meter fields (`x`, `z`) and label/description. The project proposal requires:

- Map region / bounds for the layout
- Geographic anchor: latitude, longitude, heading
- Object positions remain meters relative to that anchor
- Versioned schema for migration from existing local JSON

Current serializer: `LayoutJsonSerializer` with `CURRENT_SCHEMA_VERSION = 1`.

## Requirements

### 1. Domain types

Add a new immutable data class (suggested name/location):

`com.example.dhruvar.domain.model.GeoBounds`

Fields (Doubles, WGS84):
- `northLatitude`
- `southLatitude`
- `eastLongitude`
- `westLongitude`

Validation helpers (pure functions or `init`/factory):
- north ≥ south
- longitudes finite
- latitudes in `[-90, 90]`
- provide `contains(lat, lng): Boolean` (handle simple non–antimeridian case; document antimeridian as unsupported for v1 of bounds)

Extend `Anchor`:
- Keep existing: `id`, `label`, `x`, `z`, `description`
- Add:
  - `latitude: Double? = null`
  - `longitude: Double? = null`
  - `headingDegrees: Float? = null`  // degrees clockwise from true/magnetic north as documented; for now treat as the planning “+Z / north” alignment heading used by the app’s compass convention
  - `horizontalAccuracyMeters: Float? = null`
- Add convenience: `val hasGeographicFix: Boolean` when lat & lng are non-null and finite

Extend `Layout`:
- Add `regionBounds: GeoBounds? = null`
- Keep all existing fields

**Do not** add `y` placement to `LayoutObject`. Height remains `heightMeters` (size), not position.

### 2. JSON schema v2

Bump `CURRENT_SCHEMA_VERSION` to `2`.

Serialize new fields when present:
```json
{
  "version": 2,
  "id": "...",
  "name": "...",
  "createdAtEpochMs": 0,
  "updatedAtEpochMs": 0,
  "regionBounds": {
    "northLatitude": 0.0,
    "southLatitude": 0.0,
    "eastLongitude": 0.0,
    "westLongitude": 0.0
  },
  "anchor": {
    "id": "...",
    "label": "...",
    "x": 0.0,
    "z": 0.0,
    "description": "...",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "headingDegrees": 12.5,
    "horizontalAccuracyMeters": 4.2
  },
  "objects": [ ... unchanged object shape ... ]
}
```

Deserialization rules:
- `version` 1 or missing geo fields → load successfully with `null` geo fields
- `version` 2 with partial geo → sanitize: invalid numbers become `null` / ignore invalid bounds rather than crash
- Preserve existing defensive sanitization for objects/dimensions/asset types
- Round-trip: serialize(deserialize(v2)) preserves geo fields within normal double/float tolerances

### 3. Repository / ViewModel compatibility

- Ensure `FileLayoutRepository` / `InMemoryLayoutRepository` / `PlanningViewModel` still compile
- If ViewModel creates default layouts, leave geo null until later phases (OK)
- No UI changes required except fixing compile breaks

### 4. Tests (mandatory)

Add/extend unit tests covering:
1. Serialize layout with full geo → deserialize equals (or field-equal)
2. Deserialize classic v1 JSON fixture **without** geo → succeeds, geo null
3. Invalid bounds (south > north) rejected or coerced per your documented rule — be consistent
4. Non-finite lat/lng sanitized to null
5. Existing layout tests still pass

Put fixtures inline in tests (do not depend on device files).

## Constraints

- No MapLibre / location permissions / AR changes
- No Google Maps
- Do not change `(x, z)` meaning for objects
- Match existing code style and package structure
- Prefer small, reviewable diffs

## Deliverables

1. Working code + tests
2. Short summary of schema v2 fields
3. Manual verification steps (e.g. save/load if easy from existing UI — optional)
4. Explicit confirmation that v1 layouts still load

## Acceptance criteria

- [ ] Schema version is 2
- [ ] Geo fields exist on model
- [ ] v1 JSON still deserializes
- [ ] New unit tests pass
- [ ] Project compiles
- [ ] No map/GNSS/AR feature code added
