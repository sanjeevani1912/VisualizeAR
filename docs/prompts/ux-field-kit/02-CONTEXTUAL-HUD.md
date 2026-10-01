# Prompt UX-02 — Contextual Planning HUD

> Paste `docs/prompts/00-SHARED-CONTEXT.md`, then `docs/prompts/ux-field-kit/00-UX-SHARED.md`, then this file.
>
> **Prerequisites:** UX-01 (Field Kit theme) complete.
>
> **Do not** implement search networking, region corner-drag gestures, origin hybrid copy redesign, or AR briefing collapse. Those are later phases. This phase only **restructures chrome** and wires **visibility by mode**.

## Role

You are a senior Compose UX engineer cleaning a cluttered map planning HUD into a professional, contextual field interface.

## Objective

Implement **UX Phase 02 only**: a **Contextual HUD** on Planning (Map + Grid) so controls do not permanently stack and overlap.

Locked layout intent:

- **Top:** reserved zone for search control (placeholder OK) + origin control (existing panel OK, compacted into chip-sized footprint) + Map/Grid switcher
- **Modes:** compact icon rail (not a wide forever-visible labeled strip fighting assets)
- **Bottom-end:** assets only
- **Mode-aware:** show only tools relevant to the active `MapInteractionMode`

## Problem statement (current)

In `PlanningScreen.kt`, overlays fight each other:

- Top column: full `GeoOriginPanel` + surface switcher (+ map errors)
- Bottom column: full `MapModeToolbar` (5 labeled modes + region sub-row) + AR FAB
- BottomEnd: asset picker FAB (with extra bottom padding when map toolbar present)
- Selection editor overlay elsewhere

Result: unprofessional overlap, hard to use one-handed in the field.

## Functional requirements

### 1. Define HUD zones

Introduce a clear structure (new composable file encouraged, e.g. `ui/components/PlanningHud.kt` or split helpers):

| Zone | Alignment | Contents |
|------|-----------|----------|
| `TopStart` / `Top` | Top | Origin control (compact), optional search slot |
| `TopEnd` | Top end | `PlanningSurfaceSwitcher` (Map/Grid) |
| `ModeRail` | Bottom or Start edge | Compact mode icons |
| `ModeContext` | Near mode rail | **Only** extra tools for active mode |
| `BottomStart` | Bottom start | AR Visualize FAB (or move to top actions if cleaner — pick one and keep consistent) |
| `BottomEnd` | Bottom end | Asset picker only |

Rules:

- No two full-width panels stacked top **and** bottom both eating >30% of the map at once in Navigate mode
- Default **Navigate** mode: map mostly clear — origin chip collapsed, mode rail compact, assets FAB, surface switcher
- When a mode needs extra UI, show a **single** contextual strip/sheet for that mode only

### 2. Compact mode rail

Replace or heavily slim `MapModeToolbar` presentation:

- Modes remain: `NAVIGATE`, `SELECT_REGION`, `SET_ANCHOR`, `PLACE_ASSET`, `EDIT`
- Visual: icon-first, short labels optional (icon + tiny label OK if it fits without horizontal scroll hell)
- Selected mode: Moss highlight (Field Kit)
- On Grid surface: hide map-only modes that do not apply (Region / Anchor map tap), or disable with explanation — **choose one** and document; do not leave dead confusing controls

### 3. Contextual tool visibility matrix

Implement approximately:

| Mode | Extra UI shown | Hidden |
|------|----------------|--------|
| `NAVIGATE` | none (or tiny hint) | region confirm row, place hints |
| `SELECT_REGION` | region helper row: existing “Use area” / Clear (drag comes in UX-04 — keep viewport actions working) | place/edit chrome |
| `SET_ANCHOR` | one-line hint: “Tap map to set origin” (+ keep origin panel accessible) | region row |
| `PLACE_ASSET` | hint if asset type selected / prompt to open picker | region row |
| `EDIT` | selection editor as today when object selected | region row |

Origin panel:

- Prefer **collapsed by default** footprint (chip height)
- Expanding origin must **not** permanently cover the mode rail; use overlay sheet that can dismiss, or expand downward with scrim — avoid permanent double stack with mode context

### 4. Search slot (placeholder only)

Reserve a **top** control for search:

- A Search `IconButton` / FAB that does **nothing functional yet** OR shows snackbar “Search coming in next phase”
- Placement must match where UX-03 will expand the field (TopStart or TopBar action — prefer Top overlay near origin, not buried in overflow menu)

Do **not** add OkHttp search client in this phase.

### 5. Asset picker & selection editor

- Keep `FloatingAssetPickerOverlay` BottomEnd
- Adjust paddings so it never sits under the mode rail
- `SelectedObjectEditorOverlay`: when open, temporarily allow hiding mode context if collision; editor wins for the selected object

### 6. Grid surface

- Contextual HUD still applies
- Map mode rail behavior adapted (see §2)
- Canvas zoom/pan controls (if any on `PlanningCanvas`) must not collide with new HUD — nudge padding if needed

### 7. Field Guide (optional but preferred)

One short note in `docs/FieldGuide.md` or Planning hints: modes are contextual; only active tools show.

## Implementation constraints

- No behavior change to geo math, region bounds API, or anchor setters beyond UI wiring
- Keep `MapViewportBoundsBridge` region confirm working (UX-04 adds drag)
- Use Field Kit theme tokens from UX-01
- Prefer extracting composables over a 800-line `PlanningScreen`
- Do not invent a second ViewModel for layout chrome unless necessary; local `remember` UI state is fine for which sheets are open

## Tests

- Compile + existing unit tests
- Optional: small UI-state helper tests if you extract pure “what to show for mode X” functions — encouraged

## Manual QA

1. Planning Map → Navigate: map mostly visible; origin collapsed; compact modes; assets BottomEnd; no overlapping full-width bars
2. Switch Region → only region helper appears; confirm “Use area” still sets bounds; Clear works
3. Switch Anchor → hint visible; tap map still sets origin
4. Place → picker + tap-to-place still works
5. Edit → select/move still works; editor doesn’t cover critical controls permanently
6. Toggle Map/Grid → HUD remains coherent
7. Rotate device / different phone size if available — no catastrophic overlap

## Acceptance criteria

- [ ] Planning overlays organized into clear HUD zones
- [ ] Mode rail is compact; no endless horizontal labeled clutter by default
- [ ] Extra tools appear only for the active mode
- [ ] Navigate mode leaves most of the map free
- [ ] Region viewport confirm still works
- [ ] Search control placeholder exists in the final top position for UX-03
- [ ] Asset picker BottomEnd without sitting under toolbar
- [ ] Field Kit colors preserved
- [ ] Compiles; tests pass
- [ ] Did **not** implement search API, drag-rectangle, origin hybrid rewrite, or AR collapse

## Deliverable summary format

1. Files changed
2. HUD zone diagram (ascii or bullet)
3. Mode → visible tools table (as implemented)
4. Manual QA results
5. Deferred items for UX-03/04/05
