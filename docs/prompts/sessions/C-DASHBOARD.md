# Session C: dashboard polish, asset icons and a map that can use the map server

Paste `00-SHARED-CONTEXT.md` first. Working folder: `C:\Users\itcell\AndroidStudioProjects\DhruvAR` (you will work in a worktree, below).

## Goal (the owner's words, tidied)
1. A plan made in the app must appear on the dashboard **exactly as it is**: same region, same origin, every asset in the same
   place with the same size and rotation.
2. The plan page must look **professional and clean**.
3. On the dashboard map, assets must **not be circular dots**. They must be **small, simple icons/symbols** per asset type.
4. The dashboard map should be able to show the **same basemap as the phone** (the map server), so an officer can pan around nearby
   areas. (A static screenshot from the phone is a possible later extra, not part of this job.)

## State today (verified by reading the code; not yet seen on a real phone plan)
- Positions already match **by construction**: `web/src/geo.ts` uses the same flat-earth formulas, earth radius and footprint corner order as the
  app (`GeographicCoordinateConverter.kt`, `MapLayoutProjection.kt`). `offsetToLatLng` and `footprintRing` are the functions to trust and to test.
- `web/src/components/MapView.tsx` (186 lines) draws, on a MapLibre map: a dark mask outside the region, the region fill and outline, a metric grid,
  each asset as a **coloured to-scale footprint polygon**, plus a **circle** ("dots" layer) that fades out between zoom 16 and 18, a highlight outline
  for the selected asset, and an origin ring. Hover shows a popup; click selects. Background is a plain beige `#EDE6D2` unless
  `VITE_MAP_STYLE_URL` is set (then the map server basemap is used and `style.json` must contain correct absolute URLs).
- `web/src/geo.ts` has `ASSET_DEFAULTS` (11 types with label, plural, colour), `summarizeAssets`, `gridFeatures`, formatting helpers.
- `web/src/pages/PlanDetail.tsx` (230 lines): header, decision panel, map card with legend (coloured squares), tabs (assets table, field images,
  versions, comments, audit), site summary card.
- The phone draws each asset as a coloured to-scale rectangle with a **small top-down mark inside** (rotor disc + fuselage for the gunship,
  centre line + nose bar for K-131, cabin block for vehicles, turret ring + barrel for STR-MGS). See `PlanningMapView.kt`
  `drawAssetMark` (around line 855). The dashboard has no equivalent.
- Asset types: `GUNSHIP, K131, K131_TAKEOFF, MILITARY_VEHICLE, OFFROAD_VEHICLE, STR_MGS` (current app) and `TENT, TRUCK, CAR, ANTENNA, TRENCH` (retired but still in sample data).
- The web project has **no test framework** (scripts: dev, build, typecheck). Styles are in one `src/styles.css` (265 lines). Libraries: React 18,
  react-router 6, maplibre-gl 4.7.1. No icon library is used.

## Setup (exact commands)
```
git -C C:\Users\itcell\AndroidStudioProjects\DhruvAR worktree add ..\DhruvAR-web -b web/dashboard-polish main
cd C:\Users\itcell\AndroidStudioProjects\DhruvAR-web\web   && npm install
cd C:\Users\itcell\AndroidStudioProjects\DhruvAR-web\server && npm install
```
Run a **private** API with its own throwaway database (never the demo database):
```
set DATA_DIR=C:\Users\itcell\AppData\Local\Temp\dhruvar-web-dev      (PowerShell: $env:DATA_DIR='...')
cd DhruvAR-web\server  && npm run seed        (writes passwords to %DATA_DIR%\seed-credentials.txt; do not print them in chat)
cd DhruvAR-web\server  && npm run dev         (API on :4000)
cd DhruvAR-web\web     && npm run dev         (dashboard on :5173, proxies /api to :4000)
```
Port 4000 must be free (only one chat runs the Node API at a time; tell the owner if it is busy). Use the browser pane for checks
(ask the owner to show the pane if hidden). Sign in as a reviewer, for example `menon` (password from the temp credentials file).
Delete the temp `DATA_DIR` when you finish.

## Your files and limits
- Edit only inside `DhruvAR-web\web\`. Do not edit `server/`, `app/`, `server-django/`. The API contract is frozen: the dashboard may
  only use fields the API already returns.
- Open-source and offline: draw icons yourself as inline SVG or canvas; no icon CDN, no web fonts from the internet. If you copy a
  shape from an open icon set, record its licence in `web/src/assets/ICONS.md`.

## Milestone C1: a real plan from the app, side by side (find out first, change nothing)
1. Take the app's own export `server/scripts/fixtures/app-layout-v2.json` (schema v2 as the phone writes it). Submit it through the
   dashboard "New plan" page (as a planner) or `POST /api/plans`, on your **private** API.
2. Check the dashboard shows: the same region size, origin, heading, accuracy, every asset (count, type, size), and that each asset's
   position matches what the app computes. Verify numerically: write a small script (Node or the browser console) that takes each object,
   computes `footprintRing` and compares the corner coordinates to an independent computation of the app's formulas (the Kotlin
   functions are in `app/src/main/java/com/example/dhruvar/domain/spatial/`). Report maximum difference in metres.
3. Review the **heading and rotation** handling: the object's forward direction on the map is bearing = heading - rotationDegrees
   (derived from the two conversions; confirm it matches the app's footprint drawing). Report anything that disagrees.
4. Write `web/docs/DASHBOARD-CURRENT-STATE.md` (screens, issues, screenshots by description). **Stop and report** before changing code.

## Milestone C2: asset icons on the map
1. Design **11 simple top-down symbols** (one per asset type), monochrome, readable at about 14 to 28 px, in the same spirit as the
   phone's marks but cleaner: gunship (rotor cross + body), K-131, K-131 take-off, military vehicle, off-road vehicle, STR-MGS (turret + barrel),
   tent, truck, car, antenna (mast with arcs), trench (long slit). Keep them as SVG strings in one file, for example
   `web/src/assetIcons.ts`, each with a `viewBox` and a "forward" direction pointing up.
2. In `MapView.tsx` replace the circle layer with a **symbol layer**: register each icon with `map.addImage` (render SVG to a canvas /
   `ImageData`, pixel ratio aware, with a light halo or the type's colour behind it so it reads on any background); one symbol per asset,
   positioned at the asset's centre; `icon-rotate` = bearing described above, `icon-rotation-alignment: 'map'`, `icon-allow-overlap: true`;
   size interpolated by zoom so it never gets tiny or huge. Keep the to-scale footprint polygon (it is the truth about size); draw the
   icon inside it. When zoomed far out and footprints are sub-pixel, the icon is what the officer sees; make sure icons stay legible
   and do not hide each other more than necessary.
3. Update the hover popup, the selection highlight, the legend (show the icon, not a coloured square) and the assets table (icon next to
   the name). Keep keyboard focus and accessible names (`aria-label`s).
4. Check with the sample plans (several types, 5 to 21 assets) at three zoom levels. Capture before/after screenshots.
5. `npm run typecheck` and `npm run build` must pass. Update `web/docs/DASHBOARD-CURRENT-STATE.md` with what changed. **Stop and report.**

## Milestone C3: professional plan page
Improve, without removing any information or changing behaviour:
- header (name, code, status, planner, submitted, waiting), a clear primary action area (decision panel), spacing and typography consistent
  with the existing "Field Kit" palette (olive/rust/brown/cream: see `styles.css` custom properties), responsive down to a phone width
  and up to a projector (1920 px wide), print-friendly (a reviewer may print a plan: add a simple print stylesheet that shows the map,
  summary and asset table);
- map card: larger map, clear legend with icons, scale bar, north arrow, a "fit to region" button, region size and origin readout;
- asset table: sortable by name/type/distance, grouping or counts by type, selected row stays in sync with the map;
- empty, loading and error states; consistent number formatting (use the existing `dim`, `formatMeters` helpers);
- contrast and keyboard accessibility (AA).
Show before/after screenshots at laptop and phone width. `typecheck` and `build` must pass. **Stop and report.**

## Milestone C4: basemap-ready map (needs session A's output for the final test)
- Make `VITE_MAP_STYLE_URL` accept a **relative** URL such as `/map/style.json` (same origin through Nginx) as well as an absolute one;
  the attribution text must show when a basemap is used (the existing text already covers OSM and Survey of India).
- If the basemap fails to load (server down), fall back to the offline beige background **without crashing**, and show a small notice
  "Basemap unavailable: showing the plan only".
- Document in `web/.env.example` and a short `web/docs/BASEMAP.md`.
- Test it only when session A reports a working map server: until then, test with the fallback. Never hard-code an address.
- The map's region fit, grid and overlays must stay readable on top of the basemap (adjust fill opacities if needed). **Stop and report.**

## Later (do not start): the phone screenshot
An optional extra: the app captures the planning map as an image and sends it with the plan, shown beside the interactive map. It would
need changes in `app/`, `server/` and the Django server, so it is a separate decision.

## Always tell the owner
What changed (files), how you checked it (commands, screenshots), what you did **not** check (real phone plans, real map server, large plans),
and any API field you wished you had.
