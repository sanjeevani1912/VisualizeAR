# Prompt UX-06 — Collapsible AR Geo Briefing

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then this file.
>
> **Prerequisites:** UX-01 Field Kit theme applied to AR surfaces. Other UX map phases preferred complete but not required for this file.
>
> **Final phase** of the Field Kit UX series — do not start unrelated refactors.

## Role

You are a senior AR UX engineer reclaiming camera real estate on the Visualize screen while keeping geo guidance available on demand.

## Objective

Implement **UX Phase 06 only**:

1. Make the geo briefing **collapsible**
2. When AR origin is calibrated, show a **compact status chip** by default (not the full lat/lng block)
3. **Hide the 3-step progress bar** once origin is set (`isOriginCalibrated == true`)
4. Keep scale controls accessible without dominating the top of the screen

## Current problem

`ARVisualizationScreen.kt` stacks near the top:

- `DeviceCompassWidget`
- Status / tracking pills
- Full `ArGeoBriefingPanel` (always expanded content)
- `ArScaleModeRow`
- Calibration feedback
- 3-step Card: Detect Ground → Set Origin → Visualize Layout

After calibration, most of that is noise. The camera — the point of Visualize — is buried under text.

## Locked UX spec

### Before calibration (`!isOriginCalibrated`)

- Keep a **useful** prep strip, but tighten vertical space:
  - Compact geo chip/briefing allowed expanded **or** single “Walk to origin / distance” line
  - 3-step progress **visible** (Detect → Set Origin → Visualize)
  - Scale row: keep, but single compact row
- Compass can stay TopEnd if it doesn’t collide; move if needed

### After calibration (`isOriginCalibrated`)

- **Hide** 3-step progress Card entirely
- Geo briefing becomes:
  - **Collapsed default:** one chip, e.g. `Geo · Near` / `Geo · Far` / `Geo · Local` using existing `ArGeoProximityStatus` labels — Field Kit Moss/Rust
  - **Tap chip → expand** sheet/panel with current `ArGeoBriefingPanel` details (anchor coords, distance, bearing, accuracy)
  - Tap again / outside / chevron → collapse
- Scale mode: remain available but prefer a small control (chip or bottom/edge) so it doesn’t sit as a permanent full-width bar under an expanded briefing; if full-width, only one row and never stack with expanded briefing without scroll

### Information priority after calibrate

1. Camera / AR content  
2. Selection / measurement UI (existing)  
3. One status chip + compass  
4. Everything else on demand  

## Technical requirements

### Components

- Update `ArGeoBriefingPanel.kt` to support:
  - `expanded: Boolean` / `onExpandedChange`
  - `compact: Boolean` or separate `ArGeoStatusChip` + expandable panel
- Wire state in `ARVisualizationScreen` with `rememberSaveable` or `remember` for expanded flag
- Default `expanded = false` when `isOriginCalibrated`; when not calibrated, default `expanded = true` **or** show a medium compact prep card — document choice

### 3-step bar

- Only compose/show when `!isOriginCalibrated`
- Do not delete the step logic — hide when done

### Theme

- Field Kit light translucent surfaces from UX-01 (no return to navy glass)
- Warning/success use Rust/Moss

### Do not break

- Calibration tap-to-set-origin flow
- Geo guidance computation (`ArGeoGuidance`)
- Location permission prompts for distance
- Capture / preview / asset selection overlays
- Scale mode still changes visualization scale as today

## Tests

- If pure mapping functions are extracted (status → chip label/color), unit test them
- Compile; existing tests pass
- No requirement for screenshot tests

## Manual QA

1. Enter Visualize uncalibrated → steps visible; briefing useful; can still calibrate
2. Calibrate origin → **steps disappear**; briefing collapses to chip; camera mostly clear
3. Tap chip → details (lat/lng/distance) expand; collapse again
4. Far from origin → chip shows warning color; expand shows distance/bearing
5. Layout without geo → sensible local/no-geo chip, not a huge panel by default after calibrate
6. Scale still switchable after calibrate
7. Compass / capture / measure still reachable

## Acceptance criteria

- [ ] 3-step progress hidden after origin calibrated
- [ ] Briefing collapsed by default after calibration (chip only)
- [ ] Expand/collapse reveals full geo details
- [ ] Pre-calibration guidance still adequate to set origin
- [ ] Field Kit styling; no navy glass relapse
- [ ] No regression to calibration or AR rendering
- [ ] Compiles; tests pass
- [ ] Series complete note in summary

## Deliverable summary format

1. Files changed
2. Collapsed vs expanded behavior table (before/after calibrate)
3. Manual QA results
4. Suggested follow-ups **outside** this series (optional only — do not implement)

## Series completion statement

End with:

```text
UX Field Kit series Phase 06 complete.
Recommended next (optional, not in series): Field Guide screenshots update, QA pass on device, commit checkpoints per phase if not already committed.
```
