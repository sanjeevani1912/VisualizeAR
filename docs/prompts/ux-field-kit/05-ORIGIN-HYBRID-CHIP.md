# Prompt UX-05 — Hybrid Origin Chip

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then this file.
>
> **Prerequisites:** UX-01 theme + UX-02 contextual HUD. Prefer UX-03/04 already done so chrome is stable.
>
> **Do not** change AR Visualize briefing collapse (UX-06). Do not redo region gestures.

## Role

You are a senior field-UX engineer simplifying geographic origin setting into a hybrid chip workflow that matches military day-ops use.

## Objective

Implement **UX Phase 05 only**: replace the bulky `GeoOriginPanel` experience with the locked **Hybrid** design:

- **Collapsed chip:** `Origin: Not set` / `Origin: Set` (plus compact readiness cue)
- **Expanded sheet:** 
  - **Primary:** Stamp GPS (one tap: location + heading if available)
  - **Secondary:** Tap on map (enters / highlights `SET_ANCHOR` mode)
  - Heading capture / clear as supporting actions
  - Details (lat/lng/accuracy) behind progressive disclosure — not the first thing the eye hits

## Current behavior to improve

`GeoOriginPanel.kt` today shows:

- Expandable card with monospace lat/lng for both stamped origin and live device
- Buttons: Use location / Heading / Clear
- Permission / GPS-disabled / accuracy warning blocks

Map side: `SET_ANCHOR` mode taps → `setGeographicAnchorFromMap`.

All of this works functionally; it is too dense and fights the HUD.

## Locked UX spec

### Collapsed chip (default)

- Height roughly one toolbar row
- Leading location icon (Moss when set, Rust/Coal when not)
- Title: `Origin`
- Status pill:
  - `Not set` | `Set` 
  - Optional small readiness: `Ready` / `Poor GPS` / `No fix` when expanded or as secondary color on pill
- Chevron to expand
- Lives in the Top HUD zone from UX-02 (not a second full-width banner)

### Expanded sheet

Prefer a compact anchored sheet under the chip (dismiss on outside tap / collapse):

1. **Primary CTA (full width):** `Stamp GPS`  
   - Calls existing `setGeographicAnchorFromDeviceFix(heading)` path  
   - Disabled with reason when permission missing / no fix  
   - On success: collapse chip automatically + snackbar “Origin stamped”

2. **Secondary CTA:** `Tap on map`  
   - Sets `mapInteractionMode = SET_ANCHOR`  
   - Collapses sheet (optional) and shows mode hint from HUD  
   - User taps map → existing `setGeographicAnchorFromMap`  
   - On success: status becomes Set; optional auto-exit to Navigate

3. **Supporting row:** `Capture heading` | `Clear`  
   - Same enablement rules as today

4. **Details (collapsed by default inside sheet):**  
   - “Details” expand shows stamped lat/lng/accuracy/heading and live fix  
   - Monospace OK here — not on the chip face

5. Permission / GPS off / poor accuracy: keep existing recovery actions, but tighter copy and Field Kit `Rust` for warnings

### Copy tone

- Field-ready, short, no jargon walls
- Avoid dumping two full coordinate blocks by default

## Technical requirements

- Refactor `GeoOriginPanel.kt` (or replace with `GeoOriginChip.kt`) — update `PlanningScreen` call site
- Reuse ViewModel APIs:
  - `setGeographicAnchorFromDeviceFix`
  - `setGeographicAnchorFromMap` (via mode)
  - `updateGeographicHeading`
  - `clearGeographicAnchor`
  - permission / `locationState` / `geoAccuracyWarning` / panel expanded flag if present (`setGeoOriginPanelExpanded`)
- When “Tap on map” chosen, ensure mode rail shows Anchor as selected (UX-02)
- Heading: use live compass state already wired in Planning
- Do not weaken accuracy warnings; just present them cleaner
- Field Kit colors only

### Map marker

- Ensure origin marker on map remains visible/updatable when stamped via either path
- If marker color still old brand, align to Field Kit (should already be from UX-01)

## Out of scope

- Changing GNSS controller implementation (unless a tiny bug blocks Stamp)
- Region drag
- Search
- AR screen

## Tests

- Existing geo origin ViewModel tests must still pass
- Add/adjust UI-facing tests only if pure helpers are extracted (e.g. chip label mapping)

## Manual QA

1. Fresh layout → chip shows `Not set` collapsed; map clear
2. Expand → Stamp GPS with permission + fix → origin Set; chip collapses; marker on map
3. Clear → Not set
4. Expand → Tap on map → Anchor mode → tap map → Set
5. Capture heading updates saved heading
6. Deny permission → clear grant/settings path still works
7. Poor accuracy → Rust warning visible without huge panel
8. No overlap with search icon / mode rail (HUD from UX-02 still clean)

## Acceptance criteria

- [ ] Collapsed chip is the default origin control
- [ ] Primary Stamp GPS + secondary Tap on map + heading/clear implemented
- [ ] Lat/lng details are secondary (not the hero UI)
- [ ] Existing geo APIs reused; behavior preserved
- [ ] Auto-collapse on successful stamp
- [ ] Field Kit styling; fits contextual HUD
- [ ] Compiles; tests pass
- [ ] Did **not** implement UX-06 AR briefing work

## Deliverable summary format

1. Files changed
2. Before/after interaction summary (3–5 bullets)
3. Manual QA results
4. Any deferred polish
