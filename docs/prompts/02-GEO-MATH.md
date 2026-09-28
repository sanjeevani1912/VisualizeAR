# Prompt 02 — Geographic Coordinate Math (Lat/Lng ↔ Meters)

> Paste `00-SHARED-CONTEXT.md` above this prompt.  
> **Prerequisite:** Phase 1 complete (geo fields on `Anchor` / `Layout`, schema v2).

## Role

You are a senior geospatial/Android engineer implementing the pure math layer that connects map taps and GNSS positions to DhruvAR’s meter-based `(x, z)` planning space.

## Objective

Implement **Phase 2 only**: a tested utility that converts between WGS84 lat/lng and local ENU-style meter offsets relative to a geographic anchor.

No MapLibre UI, no GNSS permissions UI, no AR changes in this phase (pure domain math + tests is enough; thin wrappers OK).

## Coordinate contract (must match app)

Given geographic anchor `A = (lat0, lng0, headingDegrees)`:

- Planning **+X** = east relative to anchor when heading is identity-aligned; with heading, rotate the local frame so planning **+Z** aligns with the anchor heading on the ground plane (document the exact convention in KDoc).
- Planning **+Z** = “forward/north of plan” as used by existing planning (north-up canvas today).
- Planning **Y** unused for placement (always ground).

Recommended approach for field scale (≤ ~1–2 km):
- Convert lat/lng delta → local east/north meters using equirectangular or local tangent plane approximation
- Then rotate east/north into planning `(x, z)` using `headingDegrees`

Use WGS84 mean Earth radius constant; document approximation error.

## API to implement

Create something like:

`com.example.dhruvar.domain.spatial.GeographicCoordinateConverter`

Suggested API (names may vary but behavior must be clear):

```kotlin
data class LatLng(val latitude: Double, val longitude: Double)

object GeographicCoordinateConverter {
    fun latLngToMetersOffset(
        anchorLatitude: Double,
        anchorLongitude: Double,
        anchorHeadingDegrees: Float,
        targetLatitude: Double,
        targetLongitude: Double
    ): Pair<Float, Float> // (xMeters, zMeters)

    fun metersOffsetToLatLng(
        anchorLatitude: Double,
        anchorLongitude: Double,
        anchorHeadingDegrees: Float,
        xMeters: Float,
        zMeters: Float
    ): LatLng

    fun distanceMeters(a: LatLng, b: LatLng): Double // haversine OK

    fun bearingDegrees(from: LatLng, to: LatLng): Float // optional but useful for Phase 5
}
```

Also provide Anchor-based overloads that no-op / return null / throw a typed error when `!anchor.hasGeographicFix` — pick one explicit policy and document it.

Heading convention must be documented in KDoc with a worked example:
- Example: anchor heading `0` means planning +Z points to geographic north; positive heading rotates clockwise (match compass/azimuth conventions already used in the app if possible).

## Requirements

1. Pure Kotlin, no Android framework dependencies in the converter (so unit tests run on JVM).
2. Round-trip property: for offsets within 500 m of anchor,  
   `meters → latlng → meters` error ≤ **0.25 m** (prefer ≤ 0.1 m).
3. Known vector tests:
   - ~10 m east of anchor → primarily +X when heading = 0
   - ~25 m north of anchor → primarily +Z when heading = 0
   - Same point with heading = 90° rotates axes appropriately (assert with tolerances)
4. Guard non-finite inputs.
5. Unit tests in `app/src/test/...` covering the above.

## Constraints

- Do not change ARCore transformer behavior yet (Phase 5)
- Do not add map dependencies
- Do not use Google Maps Geometry library (keep pure or small internal math)
- Keep floating tolerances realistic; avoid brittle exact equality on doubles

## Deliverables

1. Converter implementation + KDoc with convention diagram/explanation
2. Unit tests with named scenarios
3. Summary table: input → expected axis behavior
4. Note any limitations (polar regions, antimeridian, multi-km sites)

## Acceptance criteria

- [ ] Bidirectional conversion exists and is documented
- [ ] Round-trip accuracy met for ≤500 m
- [ ] Heading rotation tested
- [ ] JVM unit tests pass
- [ ] No UI/map/AR feature work beyond what’s required to compile
