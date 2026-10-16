# Prompt GS-05 — Set Origin Inside the Region

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then `docs/prompts/guided-setup/00-SETUP-SHARED.md`, then this file.
>
> **Prerequisites:** Phases 01–04 complete. Hybrid origin chip (`GeoOriginChip` / `GeoOriginSheet`) already exists (Field Kit UX-05). Workspace mask+grid already on SET_ORIGIN.
>
> **Do not** unhide the asset picker, mode rail, or AR. That is Phase 06. After a successful origin stamp this phase **does** advance to `PLACE_ASSETS` (Phase 06 will dress that step).

## Role

You are a senior GNSS + Compose engineer making origin the **only** remaining setup job, and requiring that origin to sit **inside the confirmed region**.

## Objective

Implement **Guided Setup Phase 05 only**:

On `SET_ORIGIN`:

1. Show the hybrid origin chip/sheet as the primary control
2. **Stamp GPS** if the device fix is inside `regionBounds`; otherwise reject
3. **Tap on map** only accepts taps inside the region
4. Capture heading (existing)
5. Successful stamp + heading-or-default → `advanceSetupStep()` → `PLACE_ASSETS`
6. Grid realigns to the new anchor (Phase 04 already specified this; trigger a rebuild)

## Current behavior (reuse)

- Chip: Not set / Set; Moss/Rust/Coal
- Sheet: Stamp GPS primary, Tap on map secondary, lat/lng behind Details
- `setGeographicAnchorFromDeviceFix(heading)`
- `setGeographicAnchorFromMap` via `SET_ANCHOR` mode
- Placement already rejects outside region **for assets**, but origin tap currently can be **anywhere**

## Interaction design (LOCKED)

### Happy path — Stamp GPS

1. User is looking at the locked region workspace.
2. Expand chip → Stamp GPS (permission flow unchanged).
3. If `locationState` fix is inside region → write anchor, collapse sheet, snackbar `Origin stamped`, advance to PLACE_ASSETS.
4. If fix is **outside** region → do **not** write anchor; snackbar `GPS is outside the selected region — move closer or tap on the map`. Stay on SET_ORIGIN.
5. Poor accuracy (>15 m): keep existing Rust warning; still allow stamp **if inside region** (site-level GNSS error is already documented).

### Happy path — Tap on map

1. Tap on map from the sheet → `SET_ANCHOR` (rail stays hidden; hint on the map: `Tap inside the region to set origin`).
2. Tap inside → `setGeographicAnchorFromMap` with live compass heading → collapse → advance.
3. Tap outside (including dimmed area) → reject, snackbar `Origin must be inside the selected region`. Stay in tap mode.
4. Back / Cancel tap mode returns to NAVIGATE on SET_ORIGIN without clearing a previous origin.

### Heading

- Keep Capture heading.
- If heading is missing, default `0f` as today’s map-tap path does. Do not block Continue solely for missing compass.

### Advance rules

Advance FIND→… is already gated. For this phase:

`advanceSetupStep()` SET_ORIGIN → PLACE_ASSETS requires `anchor.hasGeographicFix`.

**When** to call it:

- **Lock:** auto-advance on first successful stamp/tap of this visit (same as Continue with one tap).
- Also provide a bottom **Continue** if an origin already exists (legacy / stepped back).

Stepping **back** from PLACE_ASSETS is **not** system-back (that goes Home). Phase 06 adds explicit Change origin. In this phase, `goToPreviousSetupStep` from SET_ORIGIN still goes to SET_REGION.

If origin already set and user is on SET_ORIGIN (resume incomplete? or back from a future Change origin): show Continue `Use this origin`.

### Clear

Clear origin stays on SET_ORIGIN. Do not delete region. Grid returns to SW-corner datum (Phase 04).

## Functional requirements

### 1. Inside-region helper

Reuse `MapLayoutProjection.isInsideRegionOrUnbounded`. For origin, region **is** set, so unbounded must not apply. Add a dedicated wrapper if clearer:

`fun isUsableOrigin(region: GeoBounds, lat: Double, lng: Double): Boolean`

Unit-test on-corner (inclusive) vs clearly outside.

### 2. ViewModel

- `setGeographicAnchorFromDeviceFix`: if `setupStep == SET_ORIGIN` (or region is set and step is not PLACE_ASSETS yet), **reject** outside-region fixes. When already PLACE_ASSETS, same rule: moving origin must stay inside region.
- `setGeographicAnchorFromMap`: reject outside region whenever `regionBounds != null`.
- On success during SET_ORIGIN: `advanceSetupStep()`.

Do not weaken permission / accuracy messaging.

### 3. HUD

SET_ORIGIN:

- Origin chip + sheet
- Short footer: `Step 3 of 3 · Set origin`
- Continue if origin already set
- No asset FAB, no AR, no 5-mode rail
- Tap-mode hint instead of the old mode rail Anchor icon

### 4. Marker

Existing origin marker on the map must update on stamp. If it can render outside the box from legacy data, still show it; new stamps cannot be outside.

## Out of scope

- Asset picker / Place / Edit
- AR FAB
- Field Guide (Phase 07)
- GNSS controller rewrite
- Allowing origin outside region “for convenience”

## Tests

1. Map tap inside region sets origin and advances step
2. Map tap outside region does not set origin, no advance
3. Device-fix path: mock inside vs outside (if tests already mock location, extend; else test a extracted predicate + a ViewModel method that takes lat/lng)
4. `advanceSetupStep` without geo fix stays SET_ORIGIN
5. Existing `GeoOriginViewModelTest` / `OriginChipStatusTest` still pass
6. Corner inclusive

## Manual QA

1. New Plan → location → region → origin chip only; grid+mask visible
2. Stamp GPS **inside** the box (walk/simulate) → advances; chip Set
3. Stamp GPS when you are clearly elsewhere → rejected
4. Tap on map inside → origin marker + advance
5. Tap on dimmed outside → rejected
6. Permission deny path still offers settings
7. Back to region, Confirm same box, origin still there → Continue works

## Acceptance criteria

- [ ] Origin can only be stamped/tapped inside `regionBounds`
- [ ] Successful origin on SET_ORIGIN advances to PLACE_ASSETS
- [ ] Hybrid chip UX reused, not replaced with the old full panel
- [ ] Assets/AR/mode rail still hidden on SET_ORIGIN
- [ ] Grid realigns once origin exists (Phase 04 behavior)
- [ ] Compiles; tests pass
- [ ] Did **not** start Phase 06

## Deliverable summary format

1. Files changed
2. Origin-inside-region rules
3. Manual QA results
4. Deferred items
5. “Phase 05 complete; do not start Phase 06 unless asked”
