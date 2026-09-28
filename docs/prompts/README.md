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
