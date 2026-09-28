# 00 — Shared Context (paste before every phase prompt)

You are implementing features for **DhruvAR**, an Android Kotlin/Jetpack Compose combat/field layout planning and AR visualization app.

## Product goal

Enable users to:
1. Plan layouts on a geographically accurate map (MapLibre + PMTiles)
2. Anchor the plan to real-world GNSS coordinates (lat, lng, heading)
3. Place assets (tents, vehicles, antennas, etc.) in **metric offsets** relative to that geographic anchor
4. Visualize the same plan in **ARCore** at full scale on a physical ground plane

Canonical product spec: `docs/DhruvAR-Project-Proposal.md`

## Current codebase facts (do not invent conflicting conventions)

### Stack
- Language: Kotlin
- UI: Jetpack Compose + Material3
- Navigation: Jetpack Navigation Compose
- AR: Google ARCore (`com.google.ar:core`)
- Persistence: local JSON via `LayoutJsonSerializer` + file repository
- Package root: `com.example.dhruvar`
- Min SDK 24, target/compile SDK 36
- Schema today: `LayoutJsonSerializer.CURRENT_SCHEMA_VERSION = 1`

### Domain model today
- `Layout`: id, name, `Anchor`, `objects: List<LayoutObject>`, timestamps
- `Anchor`: id, label, local `x`/`z` meters (currently always origin), description — **no geo yet**
- `LayoutObject`: assetType, name, `x`, `z` (meters relative to anchor), rotationDegrees, width/length/height meters, scale

### Coordinate convention (CRITICAL — never change)
Right-handed system aligned with ARCore:
- **+X** = east / right (lateral on ground)
- **+Y** = up (vertical). Ground plane Y ≈ 0. Objects do **not** store a Y placement coordinate.
- **+Z** = north / forward on ground in planning convention
- Object positions are **meters relative to Anchor**, never screen pixels
- Distances use Euclidean math on `(x, z)` via `SpatialDistanceCalculator`
- AR projection uses `ARCoordinateTransformer` composing offsets onto ARCore origin pose
- Compass uses magnetic heading; AR origin yaw aligned via `CompassHeadingController` / `setOriginAtCenterHit`

### Key files
- Models: `domain/model/Layout.kt`, `Anchor.kt`, `LayoutObject.kt`
- JSON: `data/model/LayoutJsonSerializer.kt`
- Planning UI: `ui/screens/PlanningScreen.kt`, `ui/components/PlanningCanvas.kt`
- ViewModel: `viewmodel/PlanningViewModel.kt`
- AR UI: `ui/screens/ARVisualizationScreen.kt`
- AR render: `ui/ar/render/ARRenderer.kt`
- Geo/spatial: `domain/spatial/*`
- Sensors: `domain/sensors/CompassHeadingController.kt`
- Proposal: `docs/DhruvAR-Project-Proposal.md`

### Map stack decision (LOCKED)
- Use **MapLibre Native for Android** + **PMTiles** for the planning basemap
- Do **NOT** integrate Google Maps SDK / Google map tiles as the planner basemap
- Device location uses Android **Fused Location Provider** (Play Services Location) or equivalent GNSS APIs — this is not “Google Maps”
- Optional Google APIs (Geocoding/Places) are out of scope unless a later prompt says otherwise

## Architecture rules

1. **Layout JSON is the source of truth.** ARCore session anchors are ephemeral visualization only and must not be persisted into the shared layout document.
2. **Geographic anchor** stores absolute WGS84 position + heading. **Objects** stay as meter offsets `(x, z)` relative to that anchor.
3. Prefer extending existing ViewModel / repository patterns over parallel state systems.
4. Match existing tactical UI language (navy/slate/precision blue, AnchorOrange for origin).
5. Keep changes scoped to the requested phase. Do not implement later phases early.
6. Add/adjust unit tests for every new pure function and serializer path.
7. Update Field Guide copy only when the phase prompt requires it.
8. Do not commit unless the user asks.

## Quality bar

- Professional, production-oriented Kotlin
- Explicit null/permission/error handling
- No placeholder “TODO implement later” left in critical paths unless marked and justified
- Code must compile; new unit tests must pass
- Preserve backward compatibility: old `version: 1` layouts without geo fields must still deserialize

## Definition of done for any phase

1. Implement the requested scope fully
2. Add/update tests
3. Summarize: files changed, schema changes, how to manually verify
4. List anything blocked or intentionally deferred
