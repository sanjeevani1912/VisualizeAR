# Prompt GS-06 — Asset Workspace After Origin

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then `docs/prompts/guided-setup/00-SETUP-SHARED.md`, then this file.
>
> **Prerequisites:** Phases 01–05 complete. Asset footprints, map drag, and `SelectedObjectCard` already exist.
>
> **Do not** rewrite Field Guide or the resume matrix beyond what is needed for Change region/origin. That polish is Phase 07.

## Role

You are a senior Compose UX engineer restoring **planning tools only after setup is complete**, without bringing back the old five-mode clutter.

## Objective

Implement **Guided Setup Phase 06 only**:

`PLACE_ASSETS` is the real planner:

- Region is locked (mask + grid + camera from Phase 04)
- Origin is set
- User can place, select, drag, edit assets
- AR Visualize is available
- Region and origin are **status**, not competing modes

## Interaction design (LOCKED)

### Chrome

| Control | PLACE_ASSETS |
|---------|----------------|
| Search | Hidden |
| Step bar | Hidden |
| Origin | Compact chip. Default collapsed. **Change origin** in the sheet (does not need the old 5-mode Anchor). |
| Region | Compact status (e.g. size label) + **Change region** |
| Mode rail | **Navigate / Place / Edit only** — drop Region and Anchor from the rail |
| Asset picker | Shown (existing FAB) |
| Selection chip/card | Existing `SelectedObjectCard` behavior |
| Map/Grid switcher | Optional; if shown, metric canvas is allowed here only |
| AR | Top-bar icon restored (existing save-before-visualize flow) |

### Change region

1. User taps Change region.
2. If `objects.isNotEmpty()`, confirm dialog: `Changing the region does not delete assets. Placements outside the new box cannot be moved until they are inside.`
3. `setupStep = SET_REGION`, clear camera maxBounds/mask/grid (Phase 04 already clears on that step).
4. Keep origin unless the **new** confirmed box does not contain it → then clear origin and land on SET_ORIGIN (resolver rule from `00-SETUP-SHARED.md`).
5. Confirming a new region returns to SET_ORIGIN if origin missing, else PLACE_ASSETS.

### Change origin

1. `setupStep = SET_ORIGIN` (grid stays; they are still in the box).
2. Stamp/tap inside region as Phase 05.
3. Success returns to PLACE_ASSETS.
4. Warn if objects exist: `Assets stay at the same meter offsets. A new origin moves them on the map.` (This is already how the math works — do not secretly rewrite object x/z.)

### Place / edit

Reuse:

- `placeAssetAtGeographic` (already requires origin; already rejects outside region)
- Map asset drag
- `PLACE_ASSET` / `EDIT` / `NAVIGATE`

Picking an asset from the FAB still switches to Place and snackbars the existing hint.

### What not to bring back

- Five-mode rail with Region + Anchor
- Always-on search icon
- Stacked Geo origin panel + region row + assets + AR FAB on the map bottom

## Functional requirements

### 1. HUD tools

Extend `planningHudToolsFor` with `setupStep` (if not fully done) so PLACE_ASSETS matches the table above.

`allowedModes(PLACE_ASSETS) = { NAVIGATE, PLACE_ASSET, EDIT }`.

### 2. ViewModel

- `requestChangeRegion()` / `requestChangeOrigin()` set the step; do not delete objects.
- After Phase 05, origin-outside-region is already illegal; keep that when changing origin.
- AR button visible only here.

### 3. Empty workspace

Zero assets is valid. Show the picker. Do not auto-open the sheet.

### 4. Metric grid canvas

If the switcher is shown: toggling to `METRIC_GRID` hides map-only mask (canvas has its own grid). Toggling back to MAP restores workspace layers. Do not allow METRIC_GRID during FIND_LOCATION / SET_REGION / SET_ORIGIN (already hidden).

## Out of scope

- Guided-setup Field Guide rewrite (Phase 07)
- Resume of mid-setup saved drafts beyond current `loadLayout` resolver (Phase 07)
- Snap-to-grid
- New asset types / glTF
- Backend

## Tests

1. allowedModes for PLACE_ASSETS excludes SELECT_REGION and SET_ANCHOR
2. requestChangeRegion with objects does not clear objects; step becomes SET_REGION
3. New region that excludes origin clears geo fix and resolves to SET_ORIGIN
4. New region that still contains origin returns to PLACE_ASSETS after confirm (if confirm-and-advance is updated)
5. Existing placement / outside-region tests still pass
6. HUD tools test: asset picker + AR implied by step (if you test predicates)

## Manual QA

1. Complete setup → asset FAB visible, 3-mode rail, AR visible, mask+grid still on
2. Place a tent inside the box; drag it; open the one-line chip then full card
3. Tap outside region → still rejected
4. Change origin → SET_ORIGIN chrome; stamp new point inside; assets shift on the map with the new anchor; return to workspace
5. Change region → drag a new box; if origin still inside, back to assets; if not, origin step
6. AR icon opens existing visualize flow
7. New Plan still starts at search with no assets/AR

## Acceptance criteria

- [ ] PLACE_ASSETS shows assets + 3-mode rail + AR; setup chrome gone
- [ ] Change region / Change origin explicit; no Region/Anchor rail buttons
- [ ] Objects are never auto-deleted by region/origin change
- [ ] Origin still cannot leave the region
- [ ] Compiles; tests pass
- [ ] Did **not** start Phase 07

## Deliverable summary format

1. Files changed
2. Workspace chrome summary
3. Change-region / change-origin behavior
4. Manual QA results
5. “Phase 06 complete; do not start Phase 07 unless asked”
