# Layout schema v2 (frozen)

**Status:** Frozen for Android ↔ future backend/web MapLibre consumers.  
**Serializer:** `LayoutJsonSerializer.CURRENT_SCHEMA_VERSION = 2`  
**Package:** `com.example.dhruvar`

This document matches the **actual** serialize/deserialize behaviour of the Android client.

---

## Coordinate conventions (do not change)

Right-handed system aligned with ARCore planning:

| Axis | Meaning |
|------|---------|
| **+X** | East / right (lateral), relative to geographic anchor when heading = 0; rotated with heading |
| **+Y** | Up. Objects do **not** store a Y placement coordinate |
| **+Z** | North / forward of plan when heading = 0; planning “forward” aligns to `anchor.headingDegrees` |

- Object positions are **meters relative to the Anchor**, never screen pixels.
- `headingDegrees`: clockwise from geographic/magnetic north to planning **+Z** (compass convention used in-app).
- `heightMeters` on objects is **size**, not elevation.

---

## JSON shape

```json
{
  "version": 2,
  "id": "uuid",
  "name": "Tactical Field Alpha",
  "createdAtEpochMs": 0,
  "updatedAtEpochMs": 0,
  "regionBounds": {
    "northLatitude": 0.0,
    "southLatitude": 0.0,
    "eastLongitude": 0.0,
    "westLongitude": 0.0
  },
  "anchor": {
    "id": "uuid",
    "label": "Origin Anchor",
    "x": 0.0,
    "z": 0.0,
    "description": "Primary deployment reference datum",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "headingDegrees": 12.5,
    "horizontalAccuracyMeters": 4.2
  },
  "objects": [
    {
      "id": "uuid",
      "assetType": "TENT",
      "name": "Tent 1",
      "x": 5.0,
      "z": -2.0,
      "rotationDegrees": 30.0,
      "widthMeters": 4.0,
      "lengthMeters": 6.0,
      "heightMeters": 3.0,
      "scale": 1.0
    }
  ]
}
```

`regionBounds` and geo keys on `anchor` are omitted when null on serialize.

---

## Field meanings & nullability

| Path | Type | Nullable | Notes |
|------|------|----------|--------|
| `version` | int | no (default treat as 1 if missing) | Writer always emits `2` |
| `id` / `name` | string | regenerated / `"Untitled Plan"` if blank |
| `createdAtEpochMs` / `updatedAtEpochMs` | long | defaults to now if missing |
| `regionBounds` | object | **yes** | Axis-aligned WGS84 box; antimeridian (west > east) unsupported → ignored on load |
| `anchor.x` / `anchor.z` | float | local meters (usually 0) |
| `anchor.latitude` / `longitude` | double | **yes** | WGS84; both required for `hasGeographicFix` |
| `anchor.headingDegrees` | float | **yes** | `[0,360)` preferred |
| `anchor.horizontalAccuracyMeters` | float | **yes** | GNSS accuracy at stamp time |
| `objects[].assetType` | enum name | unknown → `TENT` |
| `objects[].x` / `z` | float | meters vs anchor |
| `objects[].width/length/heightMeters` | float | must be > 0 or fall back to asset defaults |
| `objects[].isSelected` | — | **not persisted** |

### Explicit exclusion

**ARCore session anchors / poses are never part of this document.** They are ephemeral visualization only.

---

## Migration from v1

- v1 JSON (no geo / no `regionBounds`) deserializes successfully with all geo fields `null`.
- Non-finite or out-of-range latitudes → sanitized to `null`.
- Invalid bounds (south > north, non-finite, antimeridian) → `regionBounds = null`.
- Round-trip: serialize(deserialize(v2)) preserves geo within normal float tolerances.

---

## Web / MapLibre client notes

1. Treat `version >= 2` as supporting optional geo; ignore unknown object fields.
2. Place symbols with the same meter→lat/lng math as `GeographicCoordinateConverter` (equirectangular + heading).
3. Do not invent AR poses into shared JSON.
4. `regionBounds` may be used to fit the map camera; absence means no site clip.

---

## Known limitations

- Equirectangular projection: best ≤ ~1–2 km mid-latitudes.
- Antimeridian-crossing `regionBounds` not supported.
- Absolute GNSS error is independent of relative meter precision.
