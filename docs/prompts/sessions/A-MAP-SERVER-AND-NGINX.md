# Session A: run the map server on this PC and put Nginx in front

Paste `00-SHARED-CONTEXT.md` first. Working folder for this chat: `C:\Users\itcell\AndroidStudioProjects\DhruvAR`.

## Goal
1. Run the **copied map server** (`E:\map\map-server`) on this PC under Docker, and prove it works.
2. Put **Nginx** in front so a browser or phone uses **one address** for everything:
   ```
   http://<host>:8000/        -> the dashboard (built files from web/dist)
   http://<host>:8000/api/    -> the API (Node today, Django later)
   http://<host>:8000/map/    -> the map server (style, tiles, fonts, search, offline file)
   ```
3. Document it so the owner (a beginner) understands it, and so the same file can later serve the Django + PostgreSQL stack.

## Why (explain this to the owner in plain words)
- The old map server ran on another office PC. Every time the network changed, its IP changed and the app's map went blank.
  Running it on this PC removes that problem.
- Today the dashboard would have to talk to two different addresses (API and map server). Browsers block or complain about that
  (CORS rules, `http` vs `https`, a content-security allow-list). With Nginx there is one address, so those problems do not arise.
- Nginx is also what the owner's senior asked for; later it can be replaced or fronted by load balancers.

## What you are given
- `E:\map\map-server` (about 16 GB, because `work/` holds build leftovers). Needed to run: `docker-compose.yml`, `.env`,
  `deploy/Caddyfile.docker`, `assets/` (style.json, fonts, viewer.html), `dist/india.pmtiles` (2.07 GB), `work/meili` (1.6 GB search
  index), `work/search-key.txt`. Read `README.md` and `docs/README.md` and `docs/commands.md` there first.
- The compose project has services: `tiles` (go-pmtiles), `search` (Meilisearch), `site-init` (runs once, renders `style.json`,
  then exits; **exiting is normal**), `caddy` (front door; host port `HTTP_PORT` from `.env`, currently 8088). A `pipeline` profile
  exists for **building** data. **Never run the pipeline**: it needs 64 GB RAM and 150 GB disk and the data already exists.
- The `.env` says `PUBLIC_URL=http://192.168.1.205:8088` (the old PC). `site-init` substitutes `PUBLIC_URL` for `__BASE__` in
  `assets/style.json`. The tile/glyph/TileJSON URLs inside the style are therefore absolute and must match how clients reach the server.
- Other Docker containers named `backend-*` are old and unrelated: do not touch them.

## Your files and limits
- You may edit: `E:\map\map-server\.env` (back it up first as `.env.bak-before-move`; change only `PUBLIC_URL`, and the port
  only if 8088 is taken) and create new files under a **new worktree**:
  ```
  git -C C:\Users\itcell\AndroidStudioProjects\DhruvAR worktree add ..\DhruvAR-infra -b infra/nginx-map main
  ```
  Put everything new in `DhruvAR-infra\deploy\` and `DhruvAR-infra\docs\MAP-AND-NGINX.md`.
- Do not edit `app/`, `web/src`, `server/`, `server-django/`, or anything in `DhruvAR-friend`.
- Do not print `.env` secrets (`MEILI_MASTER_KEY`, `MEILI_SEARCH_KEY`).
- Ports you own: 8088 (map server), 8000 (Nginx). Port 4000 belongs to whichever chat runs the Node API; do not start it unless needed
  (see step 5).

## Milestone A1: the map server runs here
1. Check Docker works (`docker info`) and ports 8088 and 8000 are free (`netstat -ano | findstr :8088`).
2. Back up `.env`. Set `PUBLIC_URL=http://localhost:8088` for now. Do not change keys.
3. From `E:\map\map-server`: `docker compose up -d` (this starts tiles, search, site-init, caddy only). Check `docker compose ps`
   (site-init "Exited (0)" is correct). Read the logs of each service for errors, with secrets hidden.
4. Prove each endpoint with `curl` (print status and size only, not secrets):
   - `/style.json` (200; confirm its URLs now contain `http://localhost:8088`, no `__BASE__`, no `localhost:8080`)
   - `/tiles/india.json`, `/tiles/india/10/722/437.mvt` (the handover notes verified this tile earlier: ~26 KB)
   - `/fonts/Noto%20Sans%20Regular/0-255.pbf`
   - `/search?q=delhi&limit=2` (should return hits; if the index is empty or errors, report it; do not rebuild it unasked)
   - `HEAD /files/india.pmtiles` (about 2,065,188,239 bytes, `Accept-Ranges: bytes`)
5. Open `http://localhost:8088/viewer.html` in the browser pane (ask the owner to show the Browser pane if it is hidden) and confirm
   the map draws and search works. Take a screenshot as proof.
6. Report what you saw. **Stop here and tell the owner** before continuing.

## Milestone A2: Nginx, one address
Design (check it against what you find; change it if something does not work and explain why):
- A compose file `deploy/docker-compose.nginx.yml` with one `nginx` service (official `nginx` image, stable) publishing port 8000.
- Config `deploy/nginx/default.conf`:
  - `location /map/` -> proxy to the map server's Caddy at `http://host.docker.internal:8088/` (the trailing slash removes the
    `/map` prefix). Pass `Range` and `If-Range` headers; turn buffering off for the big `/files/` download.
  - `location /api/` -> `http://host.docker.internal:4000` (Node today; Django later is only a changed target).
    Pass `Host`, `X-Forwarded-For`, `X-Forwarded-Proto`. Set `client_max_body_size 12m` (the API allows 10 MB images).
  - `location /` -> static dashboard files from a mounted `web/dist` with `try_files $uri /index.html` (single-page app fallback).
    Only if `web/dist` exists; otherwise a clear 404 message.
  - Security headers: `X-Content-Type-Options nosniff`, `X-Frame-Options DENY`, `Referrer-Policy no-referrer`. If you add a
    `Content-Security-Policy`, test the dashboard and map under it; `connect-src 'self'` is enough because the map is same-origin.
  - Do not add any CORS headers: same origin needs none.
- **The style's absolute URLs.** `style.json` and `tiles/india.json` contain absolute URLs built from `PUBLIC_URL`. A phone and a
  laptop reach this PC by different addresses (`localhost`, a LAN IP), so a fixed `PUBLIC_URL` breaks one of them. Preferred solution
  to test: keep a placeholder base in those two JSON responses and let Nginx rewrite it per request with `sub_filter` to
  `$scheme://$http_host/map` (needs `proxy_set_header Accept-Encoding ""` towards the upstream, and
  `sub_filter_once off; sub_filter_types application/json;`). Verify with `curl -H "Host: 192.0.2.7:8000"` that the returned URLs follow
  the Host header. If this works, `PUBLIC_URL` stays a harmless placeholder. If it cannot be made reliable, fall back to a fixed
  `PUBLIC_URL=http://localhost:8000/map` and document the limitation.
- Check through Nginx: every endpoint from A1 under `/map/...`, `/api/health` (only if a Node API is running), and the dashboard
  at `/` (build it first from a **separate copy**, see step 5). Also test an HTTP **Range** request on `/map/files/india.pmtiles`.
- Test a map in the browser: open `http://localhost:8000/map/viewer.html` and confirm it renders. Then ask the owner to open
  `http://<this PC's LAN IP>:8000/map/style.json` on the phone's Chrome to prove the phone can reach it.

5. (Optional, only if the owner agrees) to test `/` and `/api/`: build the dashboard with `npm run build` inside
   `DhruvAR-infra\web` (a worktree copy, so `web/dist` is not created in the main folder), and run the Node API from a copy with a
   temporary `DATA_DIR` on port 4000: `DATA_DIR=<temp folder> npm run seed` then `npm start` inside `DhruvAR-infra\server`.
   Stop it and delete the temp folder afterwards.

## Milestone A3: documentation and handover
Write `DhruvAR-infra\docs\MAP-AND-NGINX.md` for a beginner: the picture of the three services, what each file does, how to start and
stop everything (exact commands), how to check it (the curl list), what to change when the PC's address changes (ideally nothing),
and the list of what is **not verified** (phone over Wi-Fi, HTTPS, load, the app itself). Include a section "what the other
sessions need from this one":
- the value the **dashboard** should use for `VITE_MAP_STYLE_URL` (probably `/map/style.json`, same origin);
- the value the **Android app** should use for `MAP_SERVER_BASE` (for example `http://<host>:8000/map`), and the note that the app's
  main-flavor network config allows cleartext only for listed hosts (`app/src/main/res/xml/network_security_config.xml`) while the
  debug build allows all, so session D must update that list for any new host;
- how a phone on USB can reach it: `adb reverse tcp:8000 tcp:8000` then `http://localhost:8000/map`.
Also record licences: Nginx (BSD-2), go-pmtiles (BSD-3), Meilisearch (MIT), Caddy (Apache-2.0), OSM data (ODbL), borders
(CC BY 4.0). Do not commit until the owner says so.

## Things that commonly go wrong (check these)
- Docker Desktop not started, or the WSL backend stopped.
- Port already in use (an old container, another program).
- `style.json` still containing `__BASE__` or `localhost:8080` (means `site-init` did not re-run: `docker compose up -d --force-recreate site-init caddy`).
- Search returns 401/403 (the search key in `.env` does not match the Meilisearch index; report it, do not regenerate keys unasked).
- Windows path and line-ending issues in mounted config files (use LF in `default.conf`).
- `host.docker.internal` not resolving: it works on Docker Desktop for Windows; if not, use the host's LAN IP or put both
  stacks on one Docker network and say so.
- Sub-filter rewriting not applied because the upstream sent compressed data.

## Definition of done
All endpoints return correct data through `http://localhost:8000/map/...`, the viewer renders a map with search, Range requests work,
the document exists, the owner has been told exactly what the dashboard and app need, and nothing outside your listed files changed.
