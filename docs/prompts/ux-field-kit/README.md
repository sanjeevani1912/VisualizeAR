# DhruvAR — Field Kit UX Overhaul Prompts

Use these prompts **in order** (01 → 06). Each phase is one agent chat. Do **not** start the next phase until the current one compiles, tests pass, and you can manually verify it.

These prompts assume geo Phases 01–06 (`docs/prompts/01-…` through `06-…`) are already in the codebase (MapLibre planning, GNSS origin, AR geo workflow).

## Locked product decisions (from stakeholder)

| Area | Choice |
|------|--------|
| Color | Force Light Field Kit + map overlay contrast pass |
| Region | Corner-drag rectangle + keep “use visible area” fallback |
| Origin | Hybrid chip (Stamp GPS primary + Tap on map + heading) |
| Search | Search icon that expands (not always-visible bar) |
| Planning chrome | Contextual HUD |
| AR Visualize | Collapsible briefing; hide 3-step bar after origin set |

## Why this order

```text
01 Theme          → tokens everything else will use
02 Contextual HUD → layout slots for search / origin / modes (structure only)
03 Search         → fills the top HUD slot; needs structure from 02
04 Region drag    → uses Region-mode contextual tools from 02
05 Origin hybrid  → replaces bulky panel with the chip slot from 02
06 AR briefing    → independent of map chrome; last polish
```

Do **not** reorder: Search before HUD, or Origin redesign before HUD, will recreate overlap.

## How to use

1. Open a **new agent chat** for each phase.
2. Paste / `@` in this order:
   - `docs/prompts/00-SHARED-CONTEXT.md`
   - `docs/prompts/ux-field-kit/00-UX-SHARED.md`
   - the phase file (`01-…` … `06-…`)
3. End with: *Stay within this phase. Do not start the next phase.*
4. Commit after each phase succeeds (if you want versioned checkpoints).

## Prompt index

| File | Phase | Outcome |
|------|-------|---------|
| `00-UX-SHARED.md` | Always with this series | Palette, decisions, current UI facts, non-goals |
| `01-THEME-FIELD-KIT.md` | 1 | Force light theme + Field Kit tokens + overlay contrast |
| `02-CONTEXTUAL-HUD.md` | 2 | Planning HUD zones; mode-aware tool visibility |
| `03-LOCATION-SEARCH.md` | 3 | Expandable search icon → server `/search` → fly camera |
| `04-REGION-CORNER-DRAG.md` | 4 | Drag rectangle region + viewport confirm fallback |
| `05-ORIGIN-HYBRID-CHIP.md` | 5 | Hybrid origin chip UX |
| `06-AR-COLLAPSIBLE-BRIEFING.md` | 6 | Collapsible geo briefing; hide steps after calibrate |

## Final desired output (all UX phases)

A military **day-ops** light app where:

- Dark mode never appears
- Map planning chrome does not overlap itself
- User can search a place, stamp or tap origin, drag a region box, place assets
- AR Visualize keeps the camera clear; briefing collapses after calibration
