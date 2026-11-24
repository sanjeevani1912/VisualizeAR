# Session E: wire the stack (PostgreSQL + Django + Nginx + dashboard + map)

Paste `00-SHARED-CONTEXT.md` first. Working folder for this chat: `C:\Users\itcell\AndroidStudioProjects\DhruvAR`
(you will create your own worktree, below).

## Goal
One command starts a complete, **open-source-only** system on this PC, reachable at **one address, `http://localhost:8000`**:
```
browser / phone ->  Nginx :8000
                     |-- /       the dashboard (built files from web/dist)
                     |-- /api/   Django (gunicorn) -> PostgreSQL 16
                     '-- /map/   the map server (already running separately, see below)
```
Then prove it works end to end, without a phone, and write a beginner-level document.

## What exists (verified 2026-11-20; read these first)
- Branch `integration` (worktree folder `C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration`, pushed as
  `army/sanjeevani/integration`) = `main` + three merged branches, all checks green:
  - `deploy/docker-compose.nginx.yml`, `deploy/mapserver.override.yml`, `deploy/nginx/default.conf`, `docs/MAP-AND-NGINX.md` (session A).
    The Nginx config today sends `/api/` to `http://host.docker.internal:4000` (the old **Node** API), serves `web/dist` at `/`, and
    proxies `/map/` to the map server's Caddy on host port 8088 with a `sub_filter` that rewrites `http://localhost:8088` to the
    address the client used. Read `docs/MAP-AND-NGINX.md` fully; it also lists known limits.
  - `server-django/` (session B): Django 5.2, all 23 routes, 214 tests (7 run only on PostgreSQL), seed command, conformance scripts.
    Read `server-django/docs/PHASE-5-POSTGRESQL.md` and `POSTGRESQL-OPERATIONS.md`, and look at `server-django/compose.postgres.yml`,
    `.env.postgres.example`, `config/settings.py` (database chosen by `DB_ENGINE=postgres` and `DB_NAME`, `DB_USER`, `DB_PASSWORD`
    (required), `DB_HOST`, `DB_PORT`; `TRUST_PROXY`, `COOKIE_SECURE`, `ALLOWED_HOSTS`, `DATA_DIR`, `DJANGO_SECRET_KEY`, session and
    upload settings) and `requirements.txt` (psycopg 3; **gunicorn is not in it yet**).
  - `web/` (session C): dashboard with asset icons, a cleaner plan page and a basemap switch: `VITE_MAP_STYLE_URL` may be a relative URL
    such as `/map/style.json` (read `web/docs/BASEMAP.md` and `web/.env.example`).
- The map server runs separately (Docker Compose project at `E:\map\map-server`, started with
  `docker compose -f docker-compose.yml -f C:\Users\itcell\AndroidStudioProjects\DhruvAR-infra\deploy\mapserver.override.yml up -d`;
  Caddy on 8088; Meilisearch must be pinned to v1.54.2 by that override). **Do not change it.** Leave it running; just proxy to it.
- Containers currently running on this PC: `deploy-nginx-1` (the OLD Nginx from session A, port 8000), the three `map-server-*`
  containers, and `dhruvar-postgres` (session B's development PostgreSQL, host port 5433; do not delete its volume).
  Unrelated old `backend-*` containers exist: ignore them.

## Your files and limits
```
git -C C:\Users\itcell\AndroidStudioProjects\DhruvAR worktree add ..\DhruvAR-stack -b stack/compose integration
```
- Work only inside `DhruvAR-stack` (new files under `deploy/`, a `Dockerfile` for Django under `server-django/`, docs). Do not edit app code
  (`app/`), dashboard source (`web/src`), or Django application code (`server-django/api`, `config`) unless something is broken, and
  then stop and tell the owner first. Never touch `DhruvAR\server\data` (the demo database), `DhruvAR-friend`, or `E:\map\map-server`
  files other than reading them.
- The **old Nginx on port 8000** belongs to session A's setup: before you start your Nginx on 8000, tell the owner and stop only
  `deploy-nginx-1` (do not remove its image or config). Keep a documented way to switch `/api/` back to the Node API as a fallback.
- Secrets (database password, Django secret key, seed passwords) live in a **git-ignored** `deploy/.env`; commit only
  `deploy/.env.example` with placeholders. Generate random values; never print them in chat or commit them.
- Open-source only; record licences (PostgreSQL, gunicorn MIT, Python image, Nginx BSD-2) in your document.
- No commits or pushes unless the owner says so. Ports you may use: 8000 (Nginx), 4001/5433 for tests if needed. Stop anything you start for testing.

## Milestone W1: the stack runs and is proven (do this, then STOP and report)
1. **Dockerfile for Django** (`server-django/Dockerfile`): `python:3.11-slim`, install `requirements.txt` plus `gunicorn` (add it in a
   production requirements file or the Dockerfile, with a pinned range), run as a non-root user, `gunicorn config.wsgi` with a few
   workers (the throttle and audit locks are database-based, so several workers are safe; verify with the concurrency notes in the
   PostgreSQL docs), `DATA_DIR` on a volume (uploads and the generated secret key must survive restarts).
2. **`deploy/docker-compose.yml`** with services `postgres` (postgres:16, named volume, healthcheck, password from `.env`, not published
   to the host unless needed for testing), `django` (depends on healthy postgres; runs `migrate` on start; env: `DB_ENGINE=postgres`,
   `DB_HOST=postgres`, `TRUST_PROXY=true`, `ALLOWED_HOSTS` containing the hosts a browser will use (localhost, 127.0.0.1 and an
   env-configurable LAN address; explain Django's Host check to the owner), `COOKIE_SECURE=false` for plain HTTP with a clear note
   that HTTPS must set it to true) and `nginx` (nginx:1.28-alpine, port 8000, config mounted, `web/dist` mounted read-only,
   `extra_hosts: host.docker.internal:host-gateway` for the map server). Copy and adapt `deploy/nginx/default.conf`: `/api/` now
   `proxy_pass http://django:8000`, keep `/map/` rules unchanged, `client_max_body_size 12m`, pass `Host`, `X-Forwarded-For`
   (`$proxy_add_x_forwarded_for`: Django's `client_ip` takes the **last** entry when `TRUST_PROXY=true`), `X-Forwarded-Proto`.
   Confirm the sign-in lock-out sees real client addresses (test it).
3. **Dashboard build**: a script (`deploy/build-dashboard.ps1` and `.sh`) that runs `npm ci` and
   `VITE_MAP_STYLE_URL=/map/style.json npm run build` in `web/`. Do not commit `web/dist`.
4. **Data**: create the first admin with `manage.py createuser` and sample data with `manage.py seed` inside the Django container
   (see `docs/PHASE-4-SEED-AND-CONFORMANCE.md`); seed passwords go only to a git-ignored file inside the container volume or the
   `deploy/` folder; tell the owner where to read them, do not print them.
5. **Prove it through `http://localhost:8000` only** (not by talking to Django directly):
   - `/api/health`; sign in through the API (token and cookie flows); `GET /api/plans`; submit the app's own export
     `server/scripts/fixtures/app-layout-v2.json` as a planner; decide it as a reviewer; upload a small PNG field image and download it;
     `GET /api/audit/verify` returns ok; a wrong-password lock-out (6th attempt 429) works through Nginx.
   - Run the end-to-end suite from `server-django/scripts/` (see `run_conformance.sh`) against `http://localhost:8000`.
   - Browser check with the browser tools (ask the owner to show the Browser pane if hidden): sign in to the dashboard at
     `http://localhost:8000`, open the submitted plan, confirm the asset **icons**, the **basemap** loading from `/map/style.json`,
     panning/zooming into surrounding areas, the field image, the audit trail, and that nothing in the console is a real error.
   - Map endpoints through port 8000 still work (style, a tile, search, a Range request on `/map/files/india.pmtiles`).
   - Persistence: `docker compose down` then `up` again; users, plans and uploaded images are still there. Backup with
     `server-django/scripts/backup_postgres.sh` (read it first) and say whether restore was tested.
   - Resilience: restart only Django; restart only Postgres; check recovery.
6. **Fallback**: document and test how to point `/api/` back at the Node API on 4000 (for example an alternative Nginx config file).
7. **Document** `docs/STACK.md` for a beginner: the picture, what each service and file is, exact start/stop/reset commands, how to
   build the dashboard, how to change the PC's address, how to reach it from a phone (`adb reverse tcp:8000 tcp:8000`, or the
   PC's LAN address; the phone's Wi-Fi networks were previously on a different subnet from the PC, so say what was and was not tested),
   troubleshooting, licences, and a clear **verified / not verified** list (phone, HTTPS, load, many users, the Android app itself).

## Things that commonly go wrong
- Django answering 400 "DisallowedHost" (Host header not in `ALLOWED_HOSTS`); sessions/cookies marked Secure over plain HTTP.
- Every user appearing with Nginx's container IP (the lock-out then locks everyone): check `TRUST_PROXY` and `X-Forwarded-For`.
- `/map/` JSON rewrite not applied because the upstream sent compressed data (the existing config handles it; do not change it).
- Windows line endings (CRLF) in mounted config files or shell scripts: keep LF.
- Large uploads blocked by `client_max_body_size`; slow first `migrate` before Postgres is healthy.
- Port 8000 still held by the old Nginx; the old Postgres on 5433 colliding with a new publish of 5433.

## Definition of done
`docker compose up -d` (plus the documented dashboard build) gives a working system at `http://localhost:8000` backed by PostgreSQL,
all checks above pass or are listed honestly as failed, the old Node API is a documented fallback, the demo database is untouched,
nothing secret is committed, and the owner has been told in plain language what works, what was not verified, and what session D
(the Android app) needs from this stack (the exact map and server addresses to put in the app).
