# Shared context: paste this first in every session

## The project
**DhruvAR**: a field-planning system for the Indian Army, built by a small team.
- An **Android app** (Kotlin, Jetpack Compose, MapLibre) lets a planner choose a region on a map, set an origin, place assets
  (vehicles, aircraft and so on) and view them in AR, then send the plan for approval.
- A **React dashboard** (Vite, TypeScript, MapLibre GL) lets reviewers, admins and auditors see plans, approve or reject, and
  read a tamper-evident audit log.
- A **Node/Express API with SQLite** is the live backend today.
- A **Django + PostgreSQL** backend is being built to replace it, behind **Nginx**, using **open-source software only**
  (target devices are Army "SAMBHAV" phones without Google Play Services).
- The app has two build flavors (`play` uses ARCore; `foss` uses sensors only). The flavors exist on the colleague's branch
  (`army/feature/new-ar`), not on our `main`. Do not touch flavors unless your job says so.

## The owner and how to work with them
The owner is a beginner with Django, PostgreSQL, Nginx, Docker and Android build flavors.
- Explain every new concept in plain words, in one or two sentences, the first time you use it.
- Prefer small steps. After each step say: what changed, how you checked it, what you did NOT check.
- Never claim something works unless you ran it. Report failures with the real output.
- Write a short document of what you did (path given in your job file).
- When you finish the job's first milestone, STOP and report. Do not start the next milestone until told.

## Folders on this machine (`C:\Users\itcell\AndroidStudioProjects\`)
| Folder | What it is |
|---|---|
| `DhruvAR` | Main repo, branch `main`. Contains `app/` (Android), `web/` (dashboard), `server/` (Node API), `docs/`. Remote `army` = team repo `github.com/vide123456789/DhruvAR`; remote `origin` = owner's own `sanjeevani1912/VisualizeAR`. |
| `DhruvAR-django` | A git worktree of the same repo on branch `django-backend`. Holds `server-django/`. |
| `DhruvAR-friend` | The colleague's work (foss flavor, sensor AR). **Read only. Never edit, never delete.** |
| `E:\map\map-server` | The copied map server (Docker Compose project). Not a git repo of ours. |

Tag `demo-safe` on `main` marks the last known-good demo state.

## Hard rules (every session)
1. **Do not commit, push, merge, rebase, reset or delete branches unless the owner says so.** Never push to `army/main`.
2. **Never touch `DhruvAR/server/data/`** (the demo database, uploads and `seed-credentials.txt`). Use a temporary folder
   via the `DATA_DIR` environment variable for any test database, and delete it afterwards.
3. **Never edit `DhruvAR-friend`**, `app/` (unless you are session D), or files outside your assigned folder.
4. **Do not print or commit secrets**: passwords, `.env` values, `MEILI_*` keys, `seed-credentials.txt`, Django `secret_key`.
5. **The API contract is frozen.** Same URLs under `/api`, same JSON field names, same status codes, errors as
   `{ "error": "message" }`. If you think it must change, stop and tell the owner.
6. **Open-source only** for anything that ships (libraries, fonts, icons, map data). Check the licence of anything you add and record it.
7. Before deleting or overwriting a file, look at it first. Use the least destructive option.
8. Everything must work **offline** on a closed network: no CDN links, no external fonts or tile servers, no analytics.

## Git worktrees (why and how)
A worktree is a second working folder of the same repository on its own branch. It lets two chats work at once
without switching each other's branch. Create one with:
```
git -C C:\Users\itcell\AndroidStudioProjects\DhruvAR worktree add ..\<folder> -b <branch> main
```
The exact commands for your job are in your job file.

## Windows notes
- Shell may be Git Bash or PowerShell. `java` is not on PATH; Android Studio's JDK is at
  `C:\Program Files\Android\Android Studio\jbr` (set `JAVA_HOME` when running Gradle).
- Docker Desktop is installed and was running on 2026-11-20.
- Node 24 is installed (server needs 22.13+ for built-in SQLite). Python 3.11 is installed.

## Verified facts about the current code (as of 2026-11-20)
- **Coordinates:** the app stores the origin and region box as WGS84 latitude/longitude in degrees. Every asset is stored as
  local meters `x`, `z` from that origin (`x` east, `z` north when heading is 0), rotated by the origin's `headingDegrees`
  (clockwise from north). There is no UTM or Web Mercator in our own maths; MapLibre uses Web Mercator internally for tiles.
- **Conversion** (flat-earth, earth radius 6,371,008.7714 m): `north = dLat_rad * R`; `east = dLng_rad * R * cos(anchorLat)`;
  `x = e*cosH - n*sinH`; `z = e*sinH + n*cosH`. The Android app (`GeographicCoordinateConverter.kt`) and the dashboard
  (`web/src/geo.ts`) use the same formulas and the same footprint corner order, so positions agree by construction.
- **Layout JSON (schema v2):** `regionBounds` {north/south Latitude, east/west Longitude}; `anchor` {latitude, longitude,
  headingDegrees, horizontalAccuracyMeters}; `objects[]` {id, assetType, name, x, z, rotationDegrees, widthMeters, lengthMeters,
  heightMeters}. See `docs/layout-schema-v2.md`.
- **Asset types:** the app has 6 (`GUNSHIP, K131, K131_TAKEOFF, MILITARY_VEHICLE, OFFROAD_VEHICLE, STR_MGS`). The server and
  dashboard also accept 5 retired ones (`TENT, TRUCK, CAR, ANTENNA, TRENCH`) because old plans and the sample data use them.
- **Map server** (`E:\map\map-server`): Docker Compose with `tiles` (go-pmtiles serving `dist/india.pmtiles`, 2.07 GB, CORS `*`),
  `search` (Meilisearch, index in `work/meili`), `site-init` (renders `assets/style.json` replacing `__BASE__` with `PUBLIC_URL`),
  `caddy` (front door). Endpoints through Caddy: `/style.json`, `/fonts/...`, `/tiles/india.json`, `/tiles/india/{z}/{x}/{y}.mvt`,
  `/files/india.pmtiles`, `/search?q=`. Its `.env` currently has `PUBLIC_URL=http://192.168.1.205:8088`, `HTTP_PORT=8088`
  (that was the old PC's address).
- The Android app reads the map from `BuildConfig.MAP_SERVER_BASE` (currently `http://192.168.1.205:8088`) and rewrites any
  `http://localhost:8080` placeholder it finds in the style (`MapStyleRewriter.kt`).
- The dashboard map (`web/src/components/MapView.tsx`) uses an offline beige background unless `VITE_MAP_STYLE_URL` is set.
- Known risks seen in code (not tested on a device): the AR screen passes the saved plan heading where a live camera heading is
  expected (`ARVisualizationScreen.kt` ~1036 and `ARCoordinateTransformer.originYawDegreesForMagneticNorth`); the heading is
  magnetic with no declination correction; the AR far plane is 100 m.
- Demo database accounts are fictional sample users (`menon`, `sharma` reviewers; `iyer` admin; `nair` auditor; `singh`, `rao`,
  `bose`, `khan` planners). Passwords are in `DhruvAR/server/data/seed-credentials.txt`; do not copy them anywhere.
