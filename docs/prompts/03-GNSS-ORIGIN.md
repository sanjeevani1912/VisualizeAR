# Prompt 03 — GNSS Capture & Geographic Origin Setting

> Paste `00-SHARED-CONTEXT.md` above this prompt.  
> **Prerequisites:** Phase 1 (geo model) + Phase 2 (geo math) complete.

## Role

You are a senior Android engineer wiring device location and compass into DhruvAR so a layout’s geographic anchor can be set accurately in the field or during planning preparation.

## Objective

Implement **Phase 3 only**: location permissions, a Fused Location (or equivalent) data source, and UI/ViewModel flows to set/update the layout’s geographic anchor from GNSS + compass heading.

Do **not** integrate MapLibre in this phase. A compact “Geo Origin” panel on Planning (and/or Home create-layout flow) is enough.

## Functional requirements

### 1. Permissions & manifest
- Add `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`
- Runtime permission request using modern Activity Result APIs / Compose-friendly helpers
- Graceful UI when permission denied (explanation + retry; no crash)

### 2. Location layer
Create a small repository/controller, e.g.:
`com.example.dhruvar.domain.location.DeviceLocationController`

Expose a `StateFlow` / callback of:
- latitude, longitude
- horizontal accuracy meters
- altitude optional
- timestamp
- availability / error state (GPS off, no fix, permission missing)

Use Google Play Services **Fused Location Provider** if practical for this project; keep the interface mockable for tests.

Prefer:
- high accuracy while user is actively setting origin
- remove updates on lifecycle stop to save battery

### 3. ViewModel integration
Extend `PlanningViewModel` (or a dedicated GeoOriginViewModel owned by Planning) with actions:
- `refreshLocation()`
- `setGeographicAnchorFromDeviceFix(headingDegrees: Float)`
- `setGeographicAnchorManual(lat, lng, heading)` (validated)
- `clearGeographicAnchor()`
- `updateRegionBounds(...)` stub OK if bounds UI is Phase 4 — or omit until Phase 4

When setting from device:
- write `anchor.latitude/longitude/horizontalAccuracyMeters`
- write `headingDegrees` from live compass (`CompassHeadingController` / existing compass state helpers)
- bump `layout.updatedAtEpochMs`
- persist via existing repository save path if Planning already auto-saves; otherwise ensure save includes geo fields

### 4. UI (Planning screen)
Add a professional, compact **Geo Origin** card/panel consistent with tactical theme:
- Show: lat, lng, accuracy, heading
- Buttons: “Use my location”, “Capture heading”, “Clear”
- Status chips: `NO FIX` / `ACCURACY POOR (>15m)` / `READY`
- Do not clutter the first canvas interaction; place as an expandable panel or bottom sheet section

Reuse existing compass widget patterns where possible (`DeviceCompassWidget`, `rememberCompassState`).

### 5. Accuracy UX policy
- If accuracy > 15 m: allow set but warn “Site-level only; relative meter placement remains precise”
- If no fix: disable primary CTA
- Never invent coordinates

## Non-goals
- MapLibre / PMTiles
- Changing AR calibration flow (Phase 5)
- Backend sync
- Google Maps UI

## Tests
- ViewModel unit tests with a fake location source:
  - permission missing → state reflects it
  - fix applied → anchor.hasGeographicFix true
  - clear → geo null
- Keep location Android types behind an interface so JVM tests work

## Deliverables
1. Permission + location controller + ViewModel + Planning UI panel
2. Tests for ViewModel geo actions
3. Manual test checklist (emulator GPX / real device)
4. Notes on Play Services dependency added to Gradle

## Acceptance criteria
- [ ] App requests location permission correctly
- [ ] User can stamp lat/lng/accuracy/heading onto layout anchor
- [ ] Values persist through save/load (schema v2)
- [ ] Denied permission does not crash
- [ ] No MapLibre code in this phase
- [ ] Compiles; relevant tests pass
