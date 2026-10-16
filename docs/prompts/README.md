# DhruvAR — Geo Map + AR Feature Prompts

Use these prompts **in order** (01 → 06). Each prompt is self-contained for Cursor or Antigravity.

## How to use

1. Open a **new agent chat** for each prompt (cleaner context).
2. Paste **`00-SHARED-CONTEXT.md`** first, then the phase prompt.
3. Require the agent to run tests / compile before finishing.
4. Commit after each phase succeeds before starting the next.

## Prompt index

| File | Phase | Outcome |
|------|-------|---------|
| `00-SHARED-CONTEXT.md` | Always | Project rules, coords, stack, constraints |
| `01-GEO-DATA-MODEL.md` | 1 | Geographic layout schema + JSON v2 migration |
| `02-GEO-MATH.md` | 2 | Lat/lng ↔ meter offset utilities + tests |
| `03-GNSS-ORIGIN.md` | 3 | Device location + set geographic anchor |
| `04-MAPLIBRE-PLANNING.md` | 4 | MapLibre + PMTiles region + asset placement |
| `05-AR-GEO-WORKFLOW.md` | 5 | AR guided by geo anchor; full-scale viz |
| `06-HARDENING-QA.md` | 6 | Errors, guide, schema freeze, regression |
| `META-ORCHESTRATOR.md` | Optional | Status check / what to run next |
| `COPY-PASTE.md` | Optional | How to feed prompts into Cursor / Antigravity |

## Final desired output (all phases)

A geo-anchored combat layout authoring system on Android:

- MapLibre + PMTiles for geographically accurate 2D planning
- GNSS + compass for geographic origin (lat, lng, heading)
- Assets stored as meters `(x, z)` relative to that origin
- Same layout visualized in ARCore at full scale on real ground
- Versioned layout JSON as the single source of truth

**Not in these prompts:** Google Maps basemap, backend auth/share/approve, web client, live multi-user AR.

---

## Field Kit UX overhaul (after geo 01–06)

Separate series for military day-ops light UI, search, drag-region, origin chip, HUD cleanup, AR briefing:

→ See **[`ux-field-kit/README.md`](ux-field-kit/README.md)**

| Order | File | Outcome |
|-------|------|---------|
| UX-01 | `ux-field-kit/01-THEME-FIELD-KIT.md` | Force light Field Kit palette |
| UX-02 | `ux-field-kit/02-CONTEXTUAL-HUD.md` | Contextual planning HUD |
| UX-03 | `ux-field-kit/03-LOCATION-SEARCH.md` | Expandable place search |
| UX-04 | `ux-field-kit/04-REGION-CORNER-DRAG.md` | Drag rectangle + viewport fallback |
| UX-05 | `ux-field-kit/05-ORIGIN-HYBRID-CHIP.md` | Hybrid origin chip |
| UX-06 | `ux-field-kit/06-AR-COLLAPSIBLE-BRIEFING.md` | Collapsible AR briefing |

---

## Guided setup flow (after Field Kit UX 01–06)

Replaces the all-in-one Planning screen with a gated sequence on one MapLibre map: find location → set region → set origin → place assets.

→ See **[`guided-setup/README.md`](guided-setup/README.md)**

| Order | File | Outcome |
|------|-------|---------|
| GS-01 | `guided-setup/01-SETUP-STEP-MACHINE.md` | `PlanningSetupStep` + HUD gating |
| GS-02 | `guided-setup/02-FIND-LOCATION.md` | Search-first New Plan step |
| GS-03 | `guided-setup/03-SET-REGION.md` | Region-only step; Confirm advances |
| GS-04 | `guided-setup/04-REGION-WORKSPACE-GRID.md` | Camera lock, outside dim, grid overlay |
| GS-05 | `guided-setup/05-SET-ORIGIN.md` | Origin inside region, then unlock assets |
| GS-06 | `guided-setup/06-PLACE-ASSETS.md` | Workspace chrome; change region/origin |
| GS-07 | `guided-setup/07-RESUME-HARDENING.md` | Resume, Field Guide, regression |
