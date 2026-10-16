# Prompt GS-03 — Set Region Step

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then `docs/prompts/guided-setup/00-SETUP-SHARED.md`, then this file.
>
> **Prerequisites:** Phases 01–02 complete. `RegionDraftOverlay` + `RegionDraftGeometry` already exist (Field Kit UX-04).
>
> **Do not** implement outside-region dimming, metric grid overlay, or `maxBounds` camera lock. That is Phase 04. **Do not** build origin UX (Phase 05).

## Role

You are a senior MapLibre + Compose engineer making region selection the **only** job on the second setup step.

## Objective

Implement **Guided Setup Phase 03 only**:

On `SET_REGION`, the map is already centered on the chosen place. The user either **drags a box** or taps **Use visible area**, then **Confirm**, which commits `GeoBounds` and advances to `SET_ORIGIN`.

## Current behavior (reuse, then gate)

- `SELECT_REGION` + `RegionDraftOverlay`: tap-drag opposite corner; pointer-up = draft; Confirm commits via `setRegionBounds`
- `Use visible area` via `MapViewportBoundsBridge.queryVisibleBounds()` + `setRegionBoundsFromViewport`
- Min 25 m, max 500 km; antimeridian rejected
- Mode rail currently also exposes Region among five modes — rail is **hidden** on this step (Phase 01). Force `mapInteractionMode = SELECT_REGION` while `setupStep == SET_REGION`.

## Interaction design (LOCKED)

### Happy path — drag

1. User arrives from Continue on Find location. Map is on the chosen place. Gestures: overlay owns drag; map does not pan under the box (existing UX-04 Approach A).
2. Hint: `Drag a box on the map, or use the visible area.`
3. Live preview in Field Kit Meadow/Moss.
4. Pointer-up does **not** commit. Confirm required.
5. Confirm (valid draft) → `setRegionBounds` → `advanceSetupStep()` → `SET_ORIGIN`.
6. Cancel discards draft only.

### Happy path — visible area

1. User pans/zooms (when **not** mid-drag) so the site fills the screen.
2. **Use visible area** commits viewport bounds the same as today, then **auto-advances** to SET_ORIGIN.
3. Keep this as the secondary action, not the primary hint.

### Validation

Reuse `RegionDraftGeometry.rejectionReason`. Snackbar on reject. Do not advance.

### Back

- SET_REGION → FIND_LOCATION (Phase 01). Keep committed region or not?
  - **Lock:** stepping back does **not** clear `regionBounds` automatically. If they Continue from search again to a new place, they must set a **new** region; confirming a new box **replaces** bounds (existing `setRegionBounds` replace).
  - If they step back and forward without changing location, and a region already exists, show Confirm as **Continue with current region** plus ability to drag a replacement.

### Clear

- **Clear** on this step removes committed bounds and stays on SET_REGION. Do not pop Home.

### What must not happen

- Do not show origin chip
- Do not place assets
- Do not draw the Phase 04 grid/mask
- Do not fit/lock camera yet (Phase 04). A light `MapCameraBridge` fly to chosen location on entering SET_REGION **is** in scope if the map was reset.

## Functional requirements

### 1. Entering the step

When `setupStep` becomes `SET_REGION`:

- `setMapInteractionMode(SELECT_REGION)`
- Re-fly to `chosenLocation` if present (guards against user panning away on the search step)
- Mount `RegionDraftOverlay`

### 2. Bottom chrome

Replace the five-mode rail with a **step footer**:

| Control | Role |
|---------|------|
| Hint / draft size label | Existing `RegionDraftGeometry.sizeLabel` |
| Confirm | Enabled when draft bounds valid **or** a committed region already exists |
| Cancel | Visible when draft active |
| Use visible area | Secondary |
| Clear | Visible when committed region exists |

Primary Confirm copy: `Confirm region`. Moss.

### 3. Advance

Extract a single function, e.g. `confirmRegionAndAdvance(bounds)`:

1. `setRegionBounds(bounds)` must succeed
2. `advanceSetupStep()` → SET_ORIGIN
3. Clear local `regionDraft`

Do not advance if `GeoBounds.createOrNull` fails.

### 4. Overlay vs map pan

Keep UX-04 behavior: while SET_REGION, overlay can disable map gestures during draft. After pointer-up, re-enable pan so they can still Use visible area. Do not regress the “finger-up never finished the box” bug (consumed pointer still reported down). Re-read `RegionDraftOverlay.kt` comments before touching it.

## Out of scope

- Hole-punch dimmer, grid lines, maxBounds
- Origin stamp
- Schema
- New Nav route
- Changing min/max region sizes

## Tests

1. Confirm valid bounds → layout.regionBounds set AND setupStep SET_ORIGIN
2. Invalid / tiny box → no advance
3. `setRegionBoundsFromViewport` path also advances when invoked from this step (if you route it through `confirmRegionAndAdvance`)
4. `clearRegionBounds` on SET_REGION stays SET_REGION
5. Existing `RegionDraftGeometryTest` still passes

If Confirm-and-advance is a ViewModel method, unit-test it. If it stays in the Composable, keep bounds commit in the VM and test that.

## Manual QA

1. New Plan → pick place → Continue → only region tools on a map at that place
2. Drag a reasonable box → Confirm → lands on SET_ORIGIN chrome (origin chip, no assets) from Phase 01
3. Back → SET_REGION; previous box still on the map if it was committed
4. Use visible area on a city-scale view → Confirm/advance works
5. Tiny scribble → snackbar, stay on SET_REGION
6. Map does not pan while dragging the box; pan works when not dragging

## Acceptance criteria

- [ ] SET_REGION is region-only; mode forced to SELECT_REGION
- [ ] Drag-confirm and Use visible area both commit GeoBounds and advance to SET_ORIGIN
- [ ] Reuses RegionDraftOverlay; no second gesture system
- [ ] No grid/mask/camera lock
- [ ] Compiles; tests pass
- [ ] Did **not** start Phase 04

## Deliverable summary format

1. Files changed
2. Region-step interaction summary
3. Manual QA results
4. Deferred items
5. “Phase 03 complete; do not start Phase 04 unless asked”
