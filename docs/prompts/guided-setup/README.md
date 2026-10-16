# DhruvAR — Guided Setup Flow Prompts

Use these prompts **in order** (01 → 07). Each prompt is self-contained for Cursor or any other coding agent.

This series turns the current **all-in-one Planning screen** into a **gated setup sequence**, then a clean asset workspace. It does **not** add new Navigation Compose routes. One MapLibre map stays alive; chrome and gestures change by `PlanningSetupStep`.

## Why this series exists

Today, **New Plan** opens `PlanningScreen` with search, origin chip, Map/Grid switcher, region tools, mode rail, asset picker, and AR all at once. That is too much for a first-time field user.

Desired product flow:

```text
Home → New Plan
  1. Find location     (search a place)
  2. Set region        (drag box OR use visible area)
  3. Workspace         (locked region + grid overlay)
  4. Set origin        (stamp GPS or tap inside region)
  5. Place assets      (only after origin)
```

Opening an **existing** layout skips completed steps and lands on the workspace.

## How to use

1. Open a **new agent chat** for each phase (cleaner context).
2. Paste / `@` in this order:
   - `docs/prompts/00-SHARED-CONTEXT.md`
   - `docs/prompts/ux-field-kit/00-UX-SHARED.md` (Field Kit palette still applies)
   - `docs/prompts/guided-setup/00-SETUP-SHARED.md`
   - the phase file (`01-…` … `07-…`)
3. End with: *Stay within this phase. Do not start the next phase.*
4. Run unit tests. Compile. Manually verify the phase checklist.
5. Commit after each phase if you want checkpoints (ask the agent only when you want a commit).

## Prerequisites (do not start this series without them)

| Prerequisite | Evidence |
|--------------|----------|
| Geo phases 01–06 | Schema v2, GNSS, MapLibre, AR geo briefing |
| Field Kit UX 01–03 | Light theme, contextual HUD, expandable search — on `main` |
| Field Kit UX 04–06 | Region corner-drag, origin hybrid chip, collapsible AR briefing — **must be present in the tree** (committed or uncommitted). Files: `RegionDraftOverlay.kt`, `RegionDraftGeometry.kt`, `GeoOriginChip` in `GeoOriginPanel.kt` |

If UX-04/05/06 are missing, **stop**. Finish those prompts first. This series reuses them; it does not re-implement drag-region or the origin chip.

## Prompt index

| File | Phase | Outcome |
|------|-------|---------|
| `00-SETUP-SHARED.md` | Always | Locked flow, HUD matrix, architecture, non-goals |
| `01-SETUP-STEP-MACHINE.md` | 1 | `PlanningSetupStep` + ViewModel transitions + HUD gating |
| `02-FIND-LOCATION.md` | 2 | New Plan = search-first step; Continue after a hit |
| `03-SET-REGION.md` | 3 | Region-only map step; drag or visible area; Confirm advances |
| `04-REGION-WORKSPACE-GRID.md` | 4 | Fit/lock camera to region; dim outside; draw grid overlay |
| `05-SET-ORIGIN.md` | 5 | Origin only inside region; Stamp GPS or tap map; then unlock assets |
| `06-PLACE-ASSETS.md` | 6 | Asset workspace chrome; AR; change-region/origin without clutter |
| `07-RESUME-HARDENING.md` | 7 | Resume incomplete layouts, Field Guide, regression |
| `COPY-PASTE.md` | Optional | How to feed these into an agent |

## Order (do not skip or reorder)

```text
01-SETUP-STEP-MACHINE
→ 02-FIND-LOCATION
→ 03-SET-REGION
→ 04-REGION-WORKSPACE-GRID
→ 05-SET-ORIGIN
→ 06-PLACE-ASSETS
→ 07-RESUME-HARDENING
```

Phase 01 is the foundation. Later phases will look broken if chrome is not gated yet. Phase 04 is visual; 03 must already commit a region. Phase 06 must not run before origin is gated.

## Final desired output (all phases)

A New Plan that feels like three focused jobs, then a workspace:

- **Find location** — search only
- **Set region** — drag box or use visible area
- **Workspace** — selected region, grid, origin, then assets

Same `Layout` JSON v2 source of truth. Same MapLibre instance. Same geo math. Same AR session. No backend, no new schema version, no Google Maps.

## Not in these prompts

- New NavHost routes / four separate map screens
- Google Maps / Google Places
- Backend auth, share, approve, web client
- Production glTF models
- Changing meter coordinate conventions
- Rewriting ARCore tracking
- Dark mode
