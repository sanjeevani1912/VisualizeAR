# The DhruvAR stack: PostgreSQL + Django + Nginx + dashboard + map, at one address

Written 2026-11-20 (session E, milestone W1). For a beginner. Everything marked "verified" was run on the owner's PC through
`http://localhost:8000`. Branch `stack/compose` (worktree `DhruvAR-stack`), nothing committed yet.

## 1. The picture

```
 browser / phone
       |
       v
  Nginx  (port 8000, the ONLY port published to the PC)
   |-- /       dashboard files (web/dist, built by deploy/build-dashboard.ps1)
   |-- /api/   Django (gunicorn, 3 workers x 2 threads) --> PostgreSQL 16
   '-- /map/   the map server, which runs separately (Caddy on PC port 8088)
```

Words, once:
- **Container / image**: a container is a sealed running program; an image is the package it starts from.
- **Docker Compose**: one text file (`deploy/docker-compose.yml`) naming the containers that run together; one command starts all.
- **Volume**: a folder Docker keeps outside the containers so data survives stopping and removing them.
- **Nginx (reverse proxy)**: receives every request on port 8000 and forwards it to the right program behind it.
- **gunicorn**: the production web server that runs Django (Django's own `runserver` is for development only).
- **Django "Host check"**: Django can refuse requests whose `Host` header (the address typed in the browser) is not in `ALLOWED_HOSTS`.
  See section 9, item 1: in this code the check is not actually enforced.

## 2. Files

| File | What it is |
|---|---|
| `deploy/docker-compose.yml` | The stack: `postgres`, `django`, `nginx` (project name `dhruvar`). |
| `deploy/.env` | **Secrets** (database password, Django secret key). Git-ignored. Created by `make-env`. Never share or commit. |
| `deploy/.env.example` | Same file with placeholders (committed). |
| `deploy/make-env.ps1` / `.sh` | Creates `deploy/.env` with random secrets (prints none). Does nothing if the file exists. |
| `deploy/nginx/stack.conf` | Nginx routing used by the stack (`/api/` -> `django:8000`). |
| `deploy/nginx/fallback-node.conf` | Same routing, but `/api/` -> the old Node API on the PC at port 4000 (section 7). |
| `deploy/nginx/default.conf`, `docker-compose.nginx.yml` | Session A's original Nginx (Node API only). Left untouched. |
| `deploy/build-dashboard.ps1` / `.sh` | `npm ci` + build of `web/dist` with the basemap address `/map/style.json` baked in. |
| `deploy/backup-stack.sh` | Backs up the database and the uploaded images (section 6). |
| `deploy/admin-credentials.txt` | The password of the admin `stackadmin`. Git-ignored. |
| `server-django/Dockerfile`, `docker-entrypoint.sh`, `requirements-prod.txt`, `.dockerignore` | How the Django image is built (python:3.11-slim, non-root user, runs `migrate` then gunicorn). |

Two volumes: `dhruvar_pgdata` (the database) and `dhruvar_djangodata` (`/data` in Django: `uploads/` field images and
`seed-credentials.txt`). **Both hold your data**; `docker compose down -v` deletes them.

## 3. Commands (PowerShell, from the `deploy` folder of `DhruvAR-stack`)

First time only:
```powershell
cd C:\Users\itcell\AndroidStudioProjects\DhruvAR-stack\deploy
.\make-env.ps1                      # creates .env; then open it and set LAN_HOST to this PC's address (section 8)
.\build-dashboard.ps1               # needs Node; creates ..\web\dist
```
Map server must be running (it is separate; see `docs/MAP-AND-NGINX.md` section 3). Port 8000 must be free: the old Nginx
`deploy-nginx-1` was **stopped** (not removed) for this; start it again later only if you stop this stack.
```powershell
docker compose up -d --build        # start (builds the Django image the first time, ~1 minute)
docker compose ps                   # all three "Up", postgres "healthy"
docker compose logs -f django       # watch Django (Ctrl+C leaves it running)
docker compose restart django       # restart one service
docker compose down                 # stop and remove containers; DATA STAYS
docker compose down -v              # DANGER: also deletes the database and the images
```
First data (inside the Django container; passwords are random and not printed):
```powershell
docker compose exec django python manage.py seed                      # 8 sample users + 12 fictional plans (refuses if users exist)
docker compose exec django python manage.py seed --reset              # wipes ALL data and seeds again
docker compose exec django python manage.py createuser --username iyer --name "S. Iyer" --rank "Lieutenant Colonel" --appointment "CO" --role admin   # asks for a password; rank must be from the list
docker compose exec django python manage.py listusers                 # who exists (no passwords)
docker compose exec django python manage.py resetpassword --username iyer   # new password, asked twice; the person must change it at next sign-in
docker compose exec django python manage.py setrole --username iyer --role superadmin   # promote (the dashboard cannot make a super admin)
docker compose exec django cat /data/seed-credentials.txt             # shows the sample users' passwords (run it only when you need them)
```
`seed` is for demos. For real use, create real users with `createuser`/the dashboard's admin page and do not seed.
`stackadmin` (promoted to **super admin** on 2026-11-24 with `setrole`) was created with `createuser`; its password is in `deploy/admin-credentials.txt`.
Users, roles, forced password change and everything about accounts: `docs/USERS-HOWTO.md` (how to) and `docs/USERS-AND-ROLES-PLAN.md` (why).
`seed --reset` deletes `stackadmin` too; recreate it with `createuser` (password via the `DHRUVAR_PASSWORD` variable).

After editing an Nginx file: `docker compose restart nginx`. After changing Django code: `docker compose up -d --build django`.
All three services use `restart: unless-stopped`, so they return when Docker Desktop starts.

## 4. Settings you may change (`deploy/.env`)

| Name | Meaning |
|---|---|
| `POSTGRES_PASSWORD` | Read by PostgreSQL **only the first time** its volume is created. Changing it later needs `ALTER USER` (see `server-django/docs/POSTGRESQL-OPERATIONS.md` section 6). |
| `DJANGO_SECRET_KEY` | Signs Django internals. Keep it; change it and nothing breaks, but keep it secret. |
| `LAN_HOST` | The PC's address on the network, added to `ALLOWED_HOSTS`. |
| `NGINX_CONF` | `./nginx/stack.conf` (default) or `./nginx/fallback-node.conf`. |

In the compose file: `TRUST_PROXY=true` (Django believes Nginx's `X-Forwarded-For`/`-Proto` headers, because Nginx is the only way in and
appends the real client address last), `COOKIE_SECURE=false` (needed because we use plain http; **when HTTPS is put in front, set it to
"true"**, otherwise cookies are marked Secure and browsers refuse them over http, or, the other way round, travel unprotected), `DATA_DIR=/data`.
`GUNICORN_WORKERS` / `GUNICORN_THREADS` (defaults 3 / 2): keep workers x threads well below PostgreSQL's 100 connections.

## 5. Why the sign-in lock-out still works behind Nginx

Django's lock-out counts wrong passwords per **user name + client address**. Behind a proxy every request arrives from Nginx, so Django must
read the real address from `X-Forwarded-For`. Nginx writes `$proxy_add_x_forwarded_for` (whatever the client sent, then the real address
**last**), and Django (with `TRUST_PROXY=true`) takes the **last** entry. So a client cannot dodge the lock by inventing the header.
Verified: six wrong passwords, each with a different invented `X-Forwarded-For`, gave 401 x5 then 429. Limit: on Docker Desktop every request
from this PC (and from the phone, if it comes through Windows) may appear as one gateway address (`172.21.0.1` in the Nginx log); the lock is
then per user name + that address, which is still correct (a different user is not locked: verified).

## 6. Backup and restore

```powershell
bash .\backup-stack.sh              # needs Git Bash; keeps the newest 14 of each; use "bash .\backup-stack.sh 30" to keep 30
```
Creates `server-django\backups\dhruvar-<time>.dump` (database) and `uploads-<time>.tar.gz` (field images). Copy them to another disk too.
(`server-django/scripts/backup_postgres.sh` was **not** used: it expects the older development container `dhruvar-postgres` and `.env.postgres`.)

Restore to a scratch copy and compare (this was tested):
```powershell
docker compose exec postgres sh -c 'psql -U $POSTGRES_USER -d postgres -c "CREATE DATABASE restore_test" && pg_restore -U $POSTGRES_USER -d restore_test --no-owner /backups/<dump file name>'
```
To replace the live database: `docker compose stop django`, drop and create `dhruvar`, `pg_restore` into it, put the images back into `/data/uploads`
(`docker cp`), `docker compose start django`, run the audit check. **That full replace was not rehearsed here**, only restore into a scratch database
(9 users / 13 plans / 32 audit entries: same counts as the live one).

## 7. Fallback: send `/api/` back to the old Node API

1. Start Node on the PC with its own data folder (do **not** point it at the demo database unless you mean to): from `DhruvAR\server`,
   `$env:DATA_DIR='C:\somewhere\node-data'; $env:PORT='4000'; npm start` (seed it first with `npm run seed` if the folder is new).
2. Switch Nginx: in `deploy/.env` set `NGINX_CONF=./nginx/fallback-node.conf`, then `docker compose up -d nginx`.
3. Switch back: `NGINX_CONF=./nginx/stack.conf`, `docker compose up -d nginx`.

Verified: with Django stopped, `/api/health` and a Node sign-in answered through port 8000 using the fallback file; switching back restored Django.
(The dashboard and phone would see different users and plans, because Node has its own database.)

## 8. Reaching it from other devices

- **This PC**: `http://localhost:8000`.
- **Phone over USB (no Wi-Fi needed)**: `adb reverse tcp:8000 tcp:8000`, then the phone's `http://localhost:8000` is this PC. Repeat after the cable is replugged.
  The adb path on this PC: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000`.
- **Phone over Wi-Fi**: `http://<PC address>:8000`. This PC's cable address is currently `192.168.1.42` (`ipconfig`); put it in `LAN_HOST`.
  Ask the router for a fixed ("reserved") address. The phone must be on the same network; earlier notes say the phone's Wi-Fi networks were on other subnets.
- To change the PC's address: edit `LAN_HOST` in `.env`, `docker compose up -d django`. Nothing else: the map address is rewritten per request by Nginx.

**What session D (the Android app) needs from this stack**
- Server address: `http://localhost:8000` over USB (with adb reverse) or `http://192.168.1.42:8000` over a network. The API base is that plus `/api`.
  Sign-in for the app: `POST /api/auth/token`.
- Map address: `MAP_SERVER_BASE` = `http://localhost:8000/map` (USB) or `http://192.168.1.42:8000/map`.
- Android must allow cleartext http to those hosts (`network_security_config.xml`; see `docs/MAP-AND-NGINX.md` section 6).
- The Windows Firewall for port 8000 was not checked.

## 9. Things found while doing this

1. **`ALLOWED_HOSTS` is not enforced (not fixed, Django code not edited).** A request with an unlisted `Host` (192.0.2.7) got a normal answer;
   Django's host check only runs when code calls `request.get_host()`, and none does (the minimal middleware list has no `CommonMiddleware`).
   Nothing is broken, but the setting gives no protection today and a wrong LAN address will not show as an error. Suggested fix for the Django owner:
   add `django.middleware.common.CommonMiddleware` or check the host in `api/middleware.py`; test again before relying on it.
2. **Duplicate security headers** (`X-Content-Type-Options: nosniff, nosniff`) appeared because Django and Nginx both set them; the conformance test caught it.
   Fixed in the Nginx files with `proxy_hide_header` for `/api/`.
3. The conformance test **breaks the audit chain on purpose** (it edits entry 3 to prove tampering is noticed). Do not run it against data you want to keep;
   run `seed --reset` afterwards (that is how the stack was left clean).
4. In Git Bash, paths such as `/data/...` are rewritten into Windows paths; add `MSYS_NO_PATHCONV=1` or use PowerShell (the backup script does this itself).

## 10. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `bind: port 8000 already allocated` | The old `deploy-nginx-1` (or anything else) holds 8000: `docker stop deploy-nginx-1`; `netstat -ano \| findstr :8000`. |
| `variable is not set ... run deploy/make-env.ps1` | `deploy/.env` is missing. |
| Dashboard shows "Dashboard not built yet" | Run `deploy\build-dashboard.ps1`, reload. |
| Map is beige with "Basemap unavailable" | Map server down (`docker compose ps -a` in `E:\map\map-server`) or the dashboard was built without `VITE_MAP_STYLE_URL` (use the script, in PowerShell). |
| 502 on `/api/` | Django not up yet or crashed: `docker compose logs django`. After restarting PostgreSQL wait a few seconds. |
| `password authentication failed` in Django logs | `.env` password differs from the one the database volume was created with (section 4). |
| 429 when signing in | Account locked for 15 minutes after repeated wrong passwords (for that user and address). |
| Shell script fails with `\r` errors | CRLF line endings; these files must be LF (`.gitattributes` in `deploy/` enforces it). |

## 11. Licences (open source; from the upstream projects, not re-read from source here)

PostgreSQL (PostgreSQL licence, BSD-like); Django (BSD-3); gunicorn 23 (MIT); psycopg 3 (LGPL-3.0, unmodified library);
Python image `python:3.11-slim` (Python licence; Debian parts GPL etc. as packaged); Nginx (BSD-2-Clause); Alpine in `nginx:alpine` (mainly MIT/GPL as packaged).
Map server licences: `docs/MAP-AND-NGINX.md` section 9.

## 12. Verified and NOT verified

**Verified (2026-11-20, through `http://localhost:8000` only unless stated)**
- Stack builds and starts from scratch: PostgreSQL 16 healthy, Django migrated, gunicorn with 3 workers, Nginx.
- `/api/health`; cookie sign-in (HttpOnly, not Secure over http) and token sign-in; plan list; app export `app-layout-v2.json` submitted as planner,
  decided by a reviewer; PNG uploaded and downloaded byte-identical; `/api/audit/verify` ok; lock-out 429 on the 6th wrong password, also with invented `X-Forwarded-For`.
- The 13-test end-to-end suite (`api-test.ts`) against port 8000: **13/13** (after the header fix; the first run was 12/13 because of the duplicate header).
- Concurrency on three gunicorn workers: 40 simultaneous submissions all succeeded with distinct plan codes, audit chain intact (77 entries), 200 simultaneous reads all 200.
- Map through 8000: style, tile (44,726 bytes), tiles JSON, font, search ("delhi"), Range request on `india.pmtiles` (206, 2,065,188,239 bytes); URLs in `style.json` carry the address used.
- Browser (the Browser pane): signed in at `http://localhost:8000`, overview, the submitted plan: all six asset **icons**, the **basemap** (roads, buildings, OpenStreetMap credit) from `/map/style.json`, zoom and pan into surrounding streets, the field image (loaded), the audit trail (5 entries). Console: only React Router future-flag warnings and one 401 (the pre-sign-in "who am I" call). No tile or API errors seen.
- Persistence: `down` then `up`: users, plans and the image still there. Restart of Django only (healthy in 3 s) and PostgreSQL only (sign-in and audit check fine afterwards).
- Backup script works; restore into a scratch database matched the live counts.
- Fallback to the Node API (section 7), including switching back. The test Node server and its temp data were removed.
- Demo database `DhruvAR\server\data` and `E:\map\map-server` untouched.

**NOT verified**
- The Android app, and any phone (USB or Wi-Fi). Windows Firewall for port 8000. A second device on the network.
- HTTPS (and `COOKIE_SECURE=true`). Many users or hundreds of simultaneous writes. Uploads near 12 MB through Nginx (only a 1-pixel PNG; the 12 MB limit is configured, not exercised).
- A full replace of the live database from a backup (only a scratch restore). Scheduled backups (not created). Upgrading PostgreSQL inside this stack.
- Behaviour during the seconds PostgreSQL is down (only recovery afterwards was checked; an unauthenticated probe cannot show it).
- The dashboard buttons beyond viewing (deciding from the UI, user admin) were not clicked in this stack; they were on Django+PostgreSQL earlier (see `server-django/docs/PHASE-5-POSTGRESQL.md`).
- A cold `up` on a PC with no cached images (needs internet once to download the PostgreSQL, Python and Nginx images and to run `npm ci`/`pip install`).
- Printing the plan from the dashboard.
