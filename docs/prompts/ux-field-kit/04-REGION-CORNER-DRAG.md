# Prompt UX-04 — Region Corner-Drag + Viewport Fallback

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then this file.
>
> **Prerequisites:** UX-01 theme, UX-02 contextual HUD. UX-03 search recommended but not strictly required.
>
> **Do not** redesign origin hybrid chip (UX-05) or AR briefing (UX-06).

## Role

You are a senior MapLibre + Compose gesture engineer replacing clumsy “pan then confirm whole viewport” region selection with a proper drag-rectangle tool, while keeping viewport confirm as a fallback.

## Objective

Implement **UX Phase 04 only**:

**Primary:** In `SELECT_REGION` mode — tap to place first corner → drag to opposite corner → live rectangle preview → Confirm / Cancel / Clear.

**Secondary (keep):** Existing “Use visible area” / `setRegionBoundsFromViewport` remains available as quick ops fallback in the Region contextual strip.

## Current behavior (replace as primary)

- `MapModeToolbar` / Region context: “Pan to site, then confirm” + **Use area**
- `PlanningScreen` `SELECT_REGION` tap handler is `Unit`
- Bounds applied via `PlanningViewModel.setRegionBoundsFromViewport` / `setRegionBounds` / `clearRegionBounds`
- `PlanningMapView` draws existing `layout.regionBounds` if set

Domain model `GeoBounds` and validation already exist — **reuse**; do not invent a parallel bounds type.

## Interaction design (LOCKED)

### Happy path — corner drag

1. User selects **Region** mode (contextual HUD shows region tools).
2. Instruction text: “Tap first corner, drag to opposite corner”.
3. **Pointer down** on map (first corner) starts a draft rectangle; map **pan/zoom should not steal** this gesture while drafting (disable map gestures during draft, or use a Compose overlay that owns the drag — pick the approach that is reliable on MapLibre Android).
4. **Drag** updates the opposite corner; live preview rectangle in Field Kit colors (`Meadow` fill @ low alpha, `Moss`/`Cacao` stroke).
5. **Pointer up** completes the draft (not yet committed to `Layout`) OR commits immediately — **prefer:** pointer-up = draft ready, user taps **Confirm** to commit (avoids accidental tiny boxes). Alternative acceptable: pointer-up auto-commits if area ≥ minimum size; still offer Confirm if you used two-step. Pick one; document.
6. **Confirm** → `GeoBounds.createOrNull(...)` → `viewModel.setRegionBounds(bounds)`.
7. **Cancel draft** discards preview without changing layout.
8. **Clear** removes committed `regionBounds` (existing API).

### Constraints & validation

- Reject inverted / zero-area / sub-minimum size boxes (define a sensible minimum, e.g. ~20–50 meters on each side **or** minimum degrees epsilon — document)
- Antimeridian: existing `GeoBounds` rules — if createOrNull fails, snackbar with clear message
- Draft must convert screen/overlay coordinates → lat/lng using MapLibre projection APIs (`map.projection.fromScreenLocation` / equivalent)

### Viewport fallback (LOCKED keep)

In Region contextual strip, keep:

- **Use visible area** → existing `MapViewportBoundsBridge.queryVisibleBounds()` + `setRegionBoundsFromViewport`
- **Clear** when a region exists

Label copy: make primary path obvious, e.g. “Drag box on map” as main hint; “Use visible area” as secondary button.

### Editing an existing region

- Starting a new drag replaces draft; Confirm replaces committed bounds
- Optional: show existing region dimmed while drafting a new one

## Technical approach options (choose one clean approach)

**Preferred A — Compose overlay in Region mode**

- Full-size transparent overlay above `PlanningMapView` only when `SELECT_REGION` and drafting/listening
- Handles drag; converts coords via map projection bridge
- Map gestures disabled while overlay active

**B — MapLibre gesture / annotation drag**

- Use native map touch listeners carefully; higher risk of fighting pan

Document which you chose and why.

### Bridge requirements

Extend map bridge patterns already used (`MapViewportBoundsBridge`):

- Ability to convert screen px → `LatLng`
- Ability to disable/enable map UI gestures (`uiSettings.setAllGesturesEnabled(false)` during draft)

Preview drawing:

- Either Compose `Canvas` rectangle in overlay, or MapLibre `FillLayer`/`Polyline` for draft — Compose overlay is usually simpler for draft UX

Committed region rendering: keep/update existing region overlay in `PlanningMapView` using Field Kit colors.

## ViewModel / state

Add UI-only draft state (ViewModel or screen `remember`):

- `regionDraft: GeoBounds?` or corner lat/lngs
- `isRegionDrafting: Boolean`

Committed bounds stay on `Layout.regionBounds` only after Confirm (or after auto-commit if that path chosen).

Reuse:

- `setRegionBounds`
- `clearRegionBounds`
- `setRegionBoundsFromViewport`

## Contextual HUD integration

Region mode context row should include:

- Short instruction
- Confirm (enabled when draft valid)
- Cancel draft
- Use visible area
- Clear committed

Do not reintroduce a permanently huge bottom panel; stay within UX-02 patterns.

## Tests

- Unit tests for draft → `GeoBounds.createOrNull` helper if extracted
- ViewModel tests: set/clear region still pass (`MapPlanningViewModelTest`)
- Add test for minimum-size rejection if logic is pure

## Manual QA

1. Region mode → drag a clear rectangle over a block → Confirm → region persists on map and in layout (save/reload)
2. Cancel draft → no region change
3. Clear → region removed; placement outside region rules still correct when region set
4. Use visible area still works
5. Tiny accidental drag → rejected with message
6. During draft, map does not pan away unexpectedly
7. After Confirm, switch Navigate → region remains visible; HUD cleans up

## Acceptance criteria

- [ ] Corner-drag rectangle is the primary Region UX
- [ ] Live preview uses Field Kit colors
- [ ] Confirm commits via existing `GeoBounds` / `setRegionBounds`
- [ ] Viewport “Use visible area” fallback still works
- [ ] Clear works for committed bounds
- [ ] Map gesture conflict handled (no stupid pan-while-drag)
- [ ] Invalid/tiny boxes rejected with user feedback
- [ ] Tests updated/added; project compiles
- [ ] Did **not** implement origin hybrid rewrite or AR collapse

## Deliverable summary format

1. Files changed
2. Gesture approach chosen (A or B) + rationale
3. Commit-on-up vs Confirm-step choice
4. Manual QA results
5. Any MapLibre version caveats
