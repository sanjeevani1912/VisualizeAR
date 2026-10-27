# DhruvAR Command Dashboard (web)

React + TypeScript + MapLibre dashboard for senior officials to review, approve or reject tactical plans
submitted from the DhruvAR Android app. See `../docs/Web-Dashboard-Plan.md`.

Two modes in one app:

- **Live dashboard (`/`)**: real sign-in against the API in `../server`, role-based access, data in SQLite.
- **Presentation demo (`/demo`)**: the original fictional-data version, runs entirely in the browser. Kept for presenting.

## Run

```bash
# terminal 1: API (see ../server/README.md)
cd server && npm install && npm run seed && npm run dev

# terminal 2: dashboard
cd web && npm install && npm run dev      # http://localhost:5173  (proxies /api to :4000)
npm run build                              # type-check + production build into dist/
```

Sign-in passwords for the seeded accounts are in `server/data/seed-credentials.txt`. The demo at `/demo/login`
needs no password. Requires Node.js 22.13+.

## What is in it (live)

- Role-based access: planner (own plans only; submit, withdraw, resubmit), reviewer (decide), admin (users), auditor (read-only). Enforced by the server.
- Planner: **Submit a plan** by uploading the layout JSON exported from the app; **field images** (ground photos, not AR captures) attached to a plan.
- Admin: **Users** page (create, change role, disable, reset password). Everyone: change own password.
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
