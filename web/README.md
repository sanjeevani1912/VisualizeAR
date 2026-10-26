# DhruvAR Command Dashboard (web)

React + TypeScript + MapLibre dashboard for senior officials to review, approve or reject tactical plans
submitted from the DhruvAR Android app. See `../docs/Web-Dashboard-Plan.md`.

**Status: UI prototype on mock data.** There is no backend, no real authentication and no army-unit
(cluster) grouping yet. All people, plans and sites are fictional. State (decisions, comments) is kept in the
browser's localStorage; use "Reset demo data" in the sidebar to start over.

## Run

```bash
cd web
npm install
npm run dev        # http://localhost:5173
npm run build      # type-check + production build into dist/
```

Requires Node.js 20+.

## What is in it

- Demo sign-in as reviewer, admin or auditor (reviewers decide; admin/auditor are read-only; a planner can never decide their own plan).
- Overview: status tiles, "needs your decision" queue, status breakdown, recent activity.
- Plans: search, filter (status, planner, asset type, date range), sortable table; filters live in the URL.
- Plan detail: offline map (region, metric grid, asset footprints, origin), asset table linked to the map, version history, comments, audit trail, decision panel (approve / request changes / reject with mandatory reason and a confirm step; decisions apply to a specific version hash).
- Activity log: append-only record of submissions, decisions and comments.

## Map

By default the map is fully offline (no tiles, no network). To use the map server basemap set
`VITE_MAP_STYLE_URL` (see `.env.example`). The server's `style.json` currently points at `localhost`; fix that
server-side or proxy it before using it from a browser.

## Layout

```
src/types.ts          data types (mirror Android layout schema v2)
src/geo.ts            lat/lng <-> meters maths (same as the Android app), footprints, grid
src/data/mock.ts      fictional users and plans
src/store.tsx         state + decision rules (swap for API calls later)
src/components/       Layout, MapView, DecisionPanel, StatusBadge
src/pages/            Login, Overview, PlanList, PlanDetail, Activity
```

## Next steps

Backend + real auth (plan Phases 2-4), unit/cluster grouping (Phase 6), version compare, PDF export, notifications.
