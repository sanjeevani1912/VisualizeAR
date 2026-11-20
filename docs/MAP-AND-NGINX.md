# Map server and Nginx: one address for everything

Written 2026-11-20 (session A). For a beginner. Everything here was run on the owner's PC unless it says "not verified".

## 1. The picture

```
 browser / phone
       |
       v
  Nginx  (port 8000)            <- the ONE address people use
   |-- /          -> dashboard files (web/dist)
   |-- /api/      -> the API (Node today, Django later) on port 4000
   '-- /map/      -> map server front door (Caddy) on port 8088
                        |-- tiles  (go-pmtiles reads india.pmtiles)
                        |-- search (Meilisearch)
                        '-- style.json, fonts, the 2 GB offline file
```

Plain words for the new ideas:
- **Docker**: runs a program in a sealed box (a *container*) so it behaves the same on every PC.
- **Docker Compose**: a text file that says which containers to run together, and one command starts them all.
- **Nginx**: a web server used as a *reverse proxy*: it receives every request on one port and forwards it to the right program behind it.
- **Why one address**: the browser treats `address:8000` and `address:4000` as different sites and blocks or complains (CORS, http vs https). One address = no such problems. It also fixes the old trouble where the map server's own address kept changing.

## 2. Files

| File | What it does |
|---|---|
| `E:\map\map-server\docker-compose.yml` | The map server's containers (tiles, search, site-init, caddy). Not ours; not edited. |
| `E:\map\map-server\.env` | Settings. Only `PUBLIC_URL` was changed (now `http://localhost:8088`). Backup: `.env.bak-before-move`. Holds secret keys: never share or commit. |
| `deploy/mapserver.override.yml` | Pins Meilisearch to `v1.54.2`. The compose file says `v1.54`, which now downloads 1.54.3 and refuses the index built with 1.54.2. |
| `deploy/docker-compose.nginx.yml` | Runs Nginx on port 8000, mounts the config and `web/dist`. |
| `deploy/nginx/default.conf` | The routing rules (`/map/`, `/api/`, `/`). Keep LF line endings. |

## 3. Start and stop

Map server (from `E:\map\map-server`). Never run the `pipeline` profile (needs 64 GB RAM, 150 GB disk; the data already exists):
```powershell
docker compose -f docker-compose.yml -f C:\Users\itcell\AndroidStudioProjects\DhruvAR-infra\deploy\mapserver.override.yml up -d
docker compose ps -a        # site-init "Exited (0)" is normal
docker compose down         # stop
```
Nginx (from `DhruvAR-infra`):
```powershell
docker compose -f deploy/docker-compose.nginx.yml up -d
docker compose -f deploy/docker-compose.nginx.yml restart nginx   # after editing default.conf
docker compose -f deploy/docker-compose.nginx.yml down
```
Dashboard files: `cd web; npm ci; npm run build` creates `web/dist`. Node API: `cd server; npm start` (port 4000).
All containers use `restart: unless-stopped`, so they come back when Docker Desktop starts.

## 4. How to check it (curl)

Run on the PC. Expected: 200 unless noted.
```powershell
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8000/map/style.json
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8000/map/tiles/india.json
curl.exe -s -o NUL -w "%{http_code} %{size_download}`n" http://localhost:8000/map/tiles/india/10/722/437.mvt
curl.exe -s -o NUL -w "%{http_code}`n" "http://localhost:8000/map/fonts/Noto%20Sans%20Regular/0-255.pbf"
curl.exe -s "http://localhost:8000/map/search?q=delhi&limit=2"
curl.exe -sI http://localhost:8000/map/files/india.pmtiles          # 2065188239 bytes, Accept-Ranges: bytes
curl.exe -s -D - -o NUL -H "Range: bytes=0-15" http://localhost:8000/map/files/india.pmtiles   # 206
curl.exe -s http://localhost:8000/api/health                        # {"ok":true} only if the API runs; 502 if not
curl.exe -sI http://localhost:8000/                                 # dashboard (404 text if web/dist is empty)
```
Visual check: open `http://localhost:8000/map/viewer.html#10/28.63/77.22`.

## 5. How the changing address problem is solved

`style.json` and `tiles/india.json` contain absolute URLs built from `PUBLIC_URL`. Nginx rewrites `http://localhost:8088` in those two responses to `<scheme>://<address you used>/map` (`sub_filter`). Checked: `Host: localhost:8000`, `Host: 192.0.2.7:8000` and `192.168.1.42:8000` each got URLs with their own address. So **`PUBLIC_URL` stays `http://localhost:8088` and nothing needs changing when the PC's address changes.** Nginx asks the upstream for uncompressed data so the rewrite can read it.

Limits: this assumes the upstream value is exactly `http://localhost:8088`. If `PUBLIC_URL` or `HTTP_PORT` changes, change the `sub_filter` line in `default.conf` too. Behind another proxy that terminates HTTPS, `$scheme` would be `http`; use `X-Forwarded-Proto` then.

## 6. What the other sessions need

- **Dashboard** (`VITE_MAP_STYLE_URL`): `/map/style.json` (same origin, no CORS). Set it when building, then rebuild `web/dist`.
- **Android app** (`MAP_SERVER_BASE`, `app/build.gradle.kts` line ~35, now `http://192.168.1.205:8088`):
  - Over USB: `http://localhost:8000/map`.
  - Over a network: `http://<PC address>:8000/map`.
  - The app's `network_security_config.xml` allows cleartext HTTP only for listed hosts (`192.168.1.205`, `10.0.2.2`). **Session D must add `localhost` (for USB) and any new PC address**, or the main build blocks the map. (The debug build was said to allow all; not checked here.)
  - The app's `MapStyleRewriter` only rewrites `localhost:8080`; our style needs no rewrite.
- **USB to a phone** (no Wi-Fi needed):
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
  ```
  then the phone's `localhost:8000` is this PC. It lasts until the cable is unplugged or adb restarts; run it again then.
- **Django later**: only the `/api/` target in `default.conf` changes (`proxy_pass http://host.docker.internal:<port>`).
- **PC address**: this PC's cable address was `192.168.1.42`. Ask for a fixed ("reserved") address in the router so it does not change.

## 7. What was verified, and what was NOT

Verified on 2026-11-20:
- Every map endpoint above through port 8000 and directly on 8088, including Range (206) and search; the viewer draws India and Delhi and search works, in the PC browser and on a phone over USB (`localhost:8000`).
- Dashboard at `/` (sign-in page shown), SPA fallback, `/api/health`, login and plan list through Nginx (against a throwaway database, since deleted).
- Security headers present (`nosniff`, `DENY`, `no-referrer`).

**Not verified**
- Phone over Wi-Fi: the phone's two networks (`192.168.137.x`, `192.168.0.x`) are not the PC's `192.168.1.x`. Windows Firewall for port 8000 was not checked.
- The Android app itself with the new address; HTTPS; load or many users; image upload near the 12 MB limit; the signed-in dashboard screens; a dashboard map using `/map/style.json`; Content-Security-Policy (none set).
- **Viewer blank on first load (explained, not a fault):** in the test browser pane `viewer.html` stayed blank until the map was nudged. The pane was hidden, so the page had a 0x0 window and almost no animation frames (about 3 in 1.5 s), and MapLibre needs frames to finish loading. Same on port 8088 directly, so not caused by Nginx. After sizing the viewport and nudging, the map drew. A fresh load with a visible window was not re-tested.
- **Firewall (not proven):** the PC's cable network is "Public". Docker Desktop's own "Docker Desktop Backend" rule allows incoming TCP for `com.docker.backend.exe` on Public, so port 8000 should be reachable from the same network, but no second device on `192.168.1.x` was available to prove it. If blocked, add an inbound allow rule for TCP 8000 (needs administrator).
- `viewer.html` loads MapLibre from `unpkg.com`, which breaks the offline rule on a closed network. It is a test page only; the app and dashboard bundle their own copy.
- Tile size was 44,726 bytes (notes said ~26 KB); not investigated.

## 8. Troubleshooting

- 502 on `/api/`: the API is not running on port 4000. 502 on `/map/`: the map server is stopped (`docker compose ps -a`).
- Search container restarting with "database version incompatible": you started without the override file.
- Style contains `__BASE__`: `docker compose up -d --force-recreate site-init caddy`.
- Docker not responding: start Docker Desktop and wait for the engine.
- Port 8000 busy: `netstat -ano | findstr :8000`.

## 9. Licences

Nginx BSD-2-Clause; go-pmtiles BSD-3-Clause; Meilisearch MIT; Caddy Apache-2.0; Alpine (site-init) mainly MIT/GPL parts as packaged; OpenStreetMap data ODbL (credit "OpenStreetMap contributors"); borders Survey of India via DataMeet, CC BY 4.0; Noto Sans fonts SIL OFL 1.1 (taken from the map server's assets; not re-checked here). Licences are from memory of the upstream projects, not re-read from source, except the attributions shown in the viewer.
