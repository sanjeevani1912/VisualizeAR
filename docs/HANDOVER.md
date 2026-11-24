# DhruvAR: handover for the developers (VAPT and deployment)

Written 2026-11-24 by the outgoing intern's tooling session. Plain and specific on purpose. Everything marked **verified** was run on one Windows PC through
`http://localhost:8000` on 2026-11-24; everything marked **not verified** was not. The only phone-dependent parts (app, AR, compass) were **not run by me at all**.

## 1. The system in one picture

```
 Android app (Kotlin, ARCore, MapLibre)         Browser (React dashboard)
      |  USB: adb reverse tcp:8000                    |
      +----------------------+------------------------+
                             v
                  Nginx 1.28   :8000  (the only published port)
                   |-- /      static dashboard (web/dist)
                   |-- /api/  -> Django 5.2 (gunicorn, 3 workers x 2 threads) -> PostgreSQL 16
                   '-- /map/  -> separate map stack on the PC (Caddy :8088 -> go-pmtiles + Meilisearch), data in india.pmtiles (about 2 GB)
```
Roles: `planner` (own plans only), `reviewer` (decides, never own plan), `auditor` (read-only), `admin` (manages ordinary users), `superadmin` (also manages admins).
Plans are versioned and immutable once sent; every action is written to a hash-chained audit log (`GET /api/audit/verify` checks it).

## 2. Folders, branches, remotes

| Place | What it is |
|---|---|
| `...\AndroidStudioProjects\DhruvAR-integration` | Worktree of branch **`integration`**: everything merged (app incl. panorama, `web/`, `server-django/`, `deploy/`, `docs/`). **Build the demo APK and run the stack from here.** |
| `...\DhruvAR` | Main checkout, branch `main`, tag `demo-safe` (the pre-stack demo build). In H9 `main` gets the merge of `integration` and tag `demo-v1`. |
| `...\DhruvAR-app` | Worktree of `app/connect-stack` (app connected to the stack, without panorama). Superseded by `integration`. |
| `...\DhruvAR-heading` | Worktree of **`app/heading-fix`**: the AR heading fix behind a switch, default off. **Not merged.** See `docs/HEADING-FIX-HOWTO.md` on that branch. |
| `...\DhruvAR-pano`, `-stack`, `-django`, `-web`, `-infra` | Older feature worktrees (all merged into `integration`); safe to ignore. |
| `...\DhruvAR-friend` | A colleague's separate clone (foss flavor, sensor-based AR). **Do not edit.** Not part of this build. |
| `E:\map\map-server` | The map stack (separate Compose project, not in Git). Not edited by this work. |
| `server/` | The old Node/Express + SQLite API. Kept as a fallback (`docs/STACK.md` section 7). The terrain routes exist only here (gap 4). |

Remotes: `origin` = `sanjeevani1912/VisualizeAR` (owner's personal repo); `army` = `vide123456789/DhruvAR` (team repo). Pushing to `army` is the owner's decision.

## 3. Run everything from nothing (new PC with Docker Desktop, Node 20+, Git)

```powershell
git clone https://github.com/sanjeevani1912/VisualizeAR.git DhruvAR; cd DhruvAR; git checkout main     # or integration
cd deploy
.\make-env.ps1                    # creates deploy\.env with random secrets (prints none); open it and set LAN_HOST to this PC's address
.\build-dashboard.ps1             # PowerShell only: builds web\dist with the map address baked in (needs internet once for npm)
docker compose up -d --build      # builds the Django image, starts postgres + django + nginx; about 1-3 minutes the first time
docker compose ps                 # postgres must say healthy
curl.exe -s http://localhost:8000/api/health        # {"ok":true}
```
Map server: copy the `E:\map\map-server` folder (needs `india.pmtiles`) and run `docker compose up -d` there; Nginx reaches it on host port 8088. Without it the dashboard still works (plain background).
First user (nobody exists yet): `docker compose exec django python manage.py createuser --username <name> --name "<Full Name>" --rank "Major" --role superadmin` (hidden password prompt).
Do **not** run `manage.py seed` on a real system (it loads fictional demo data and can wipe with `--reset`). Demo data on a dev box: `python server-django\scripts\demo_data.py` (`docs/DEMO-ACCOUNTS.md`).
First time on the phone: `docs/APP-PHONE-TEST.md`. Verified: cold `down` then `up -d` on this PC. **Not verified:** a clone onto a clean PC with no cached images.

## 4. Secrets: where they live and how to rotate

| Secret | Where | Rotate |
|---|---|---|
| Database password, Django secret key | `deploy\.env` (git-ignored) | New values in `.env`. The database password also lives **inside the database volume**: changing `.env` alone gives "password authentication failed" (`docs/STACK.md` section 4 explains the `ALTER USER` step). A new `DJANGO_SECRET_KEY` signs nobody out (sessions are database rows) but test it. |
| `stackadmin` password | `deploy\admin-credentials.txt` (git-ignored) | `docker compose exec django python manage.py resetpassword --username stackadmin`, then delete the file. |
| Demo users' passwords | `deploy\demo-passwords.txt` (git-ignored, plain text, **demo only**) | Delete the file and disable or reset the accounts when real users arrive. |
| Seed passwords | `/data/seed-credentials.txt` in the Django container (plain text) | No longer valid for the 8 demo users. Delete the file on a real system. |
| Phone sign-in token | Encrypted on the phone | Ends after 60 min idle or 12 h; `POST /api/auth/logout`. |

Never commit: `deploy/.env`, `deploy/*credentials*.txt`, `deploy/demo-passwords.txt`, `*.dump`, uploads, `server-django/backups/`. The patterns are in `.gitignore`; check `git status` anyway.

## 5. Known gaps (severity = my judgement for a VAPT scope; High = fix before any real use)

| # | Gap | Sev. | Detail and where |
|---|---|---|---|
| 1 | **HTTP only, no TLS** | High | Passwords and tokens cross the network in clear text off the PC. Put TLS in front (Nginx `listen 443 ssl`, or a proxy) and set `COOKIE_SECURE=true`; `docs/STACK.md` section 12 (HTTPS not verified). Over USB (`adb reverse`) it never leaves the cable. |
| 2 | **`ALLOWED_HOSTS` is not enforced** | Medium | No Django code calls `get_host()`; any `Host` header gets answered (`docs/STACK.md` section 9 item 1). Add `CommonMiddleware` or check in `api/middleware.py`, then test. |
| 3 | **`TRUST_PROXY` assumptions** | Medium | The login lock-out is per username and client address. With `TRUST_PROXY` on, Django trusts `X-Forwarded-For` from Nginx; if anything else can reach Django directly, a client could invent addresses. Django is not published, only Nginx, which overwrites the header (verified with invented headers). Re-check when adding a load balancer. |
| 4 | **Django has no terrain routes** | Medium | The Node server has `terrain*.ts`/`dem.ts`; Django does not. The friend's DEM features would get 404 on the stack. |
| 5 | **AR heading mismatch** | High for the AR demo | AR placement disagrees with the map (rotation uses the saved heading where the live compass is expected; plan probably mirrored). Proven only on paper (`docs/HEADING-REVIEW.md`). Fix is on branch `app/heading-fix`, **off by default and unverified on a device**. |
| 6 | **Magnetic north, not true north** | Medium | No declination correction anywhere in the app (`GeomagneticField` unused). Plans are rotated by the local declination (about a degree near Delhi; more elsewhere). |
| 7 | **Phone over Wi-Fi untested** | Medium | Only USB (`adb reverse`) was planned/tested. Windows Firewall for 8000 not checked; earlier notes say the phone's networks were on other subnets. |
| 8 | **No scheduled backups** | Medium | `deploy/backup-stack.sh` is manual. Backup and restore into a scratch DB were rehearsed (8 tables matched); a **full replace of the live DB was not**. |
| 9 | **Plain-text demo password files** | Medium (demo only) | `deploy/demo-passwords.txt`, `admin-credentials.txt`, seed credentials. Git-ignored but readable by anyone on the PC. Delete before real use. |
| 10 | **Cleartext allowed for localhost in release builds** | Low | Release `network_security_config.xml` allows `http` to `localhost`, `127.0.0.1`, `10.0.2.2`; debug builds allow any host. Remove for production. |
| 11 | **Audit log can be broken by tests** | Low (process) | `server-django/scripts/run_conformance.sh` / `tamper_audit_django.py` deliberately damage entry 3. Never run them on data you keep. |
| 12 | **Demo accounts and data are fictional but present** | Low | Disable demo users when real ones exist (Users page > Disable; never delete: audit lines keep their author). Deleted QA users in this session left audit lines whose actor id no longer resolves (cosmetic). |
| 13 | **Session cookie not `Secure`** over http | Medium | Follows gap 1. |
| 14 | **Dashboard bundle warning** | Low | Single 1 MB JS chunk; works, just heavy. |
| 15 | **Plan dates in demo data are all "today"** | Low | The API always stamps now. |
| 16 | **Backups hold real accounts** | Process | Keep `E:\dhruvar-backups\` on a protected disk. |
| 17 | **Docker build needs internet on first use** | Low | Images and npm/pip packages are downloaded once. No offline mirror prepared. |
| 18 | **No rate limit beyond login** | Medium | Nginx does not limit request rates; upload size is capped (10 MB app, 12 MB Nginx), 30 images per plan. |
| 19 | **Dashboard "Print plan" and several buttons not exercised** | Low | See section 8. |

## 6. Test commands and current numbers (2026-11-24, branch `integration`)

| Suite | Command | Result |
|---|---|---|
| Django API | `cd deploy; docker compose exec django python manage.py test` | **253 tests OK** (about 55 s) |
| Android unit tests | `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest` (repo root) | **361 tests, 0 failures** (verified in `DhruvAR-integration`; `app/heading-fix` has 345 because it lacks the panorama tests) |
| Debug APK | `.\gradlew.bat assembleDebug` | builds; `app\build\outputs\apk\debug\app-debug.apk`, about 176 MB |
| Dashboard | `cd web; npm run typecheck`, and the PowerShell build script | typecheck clean, build OK. **The dashboard has no automated tests.** |
| API checks by role | scripted calls with five temporary users on 2026-11-24 | 20 of 22 passed; the other 2 were wrong expectations (404 on another planner's plan is by design; own-role change is 409) |
| Node API (`server/`) and its conformance script | not run in this session | not verified |

## 7. Licences (open source only; **from my memory of the upstream projects, not re-read from source**)

Server: Django BSD-3; gunicorn MIT; psycopg 3 LGPL-3.0 (unmodified); PostgreSQL (PostgreSQL licence); Nginx BSD-2; Python image (PSF + Debian packages as packaged); Alpine mixed (MIT/GPL).
Dashboard: React MIT; react-dom MIT; react-router-dom MIT; MapLibre GL JS BSD-3; Vite MIT; TypeScript Apache-2.0.
Map: go-pmtiles BSD-3; Meilisearch MIT; Caddy Apache-2.0; OpenStreetMap data ODbL (credit "OpenStreetMap contributors"); borders: Survey of India via DataMeet, CC BY 4.0; Noto fonts SIL OFL 1.1.
Android app: AndroidX/Compose/OkHttp/MapLibre Android SDK are Apache-2.0 / BSD. **`com.google.ar:core` (ARCore) is NOT open source** (Google proprietary SDK; needs Google Play Services for AR) and is a direct dependency of this app build: for the Army's devices without Google services a different AR engine is needed (the friend's `foss` flavor idea; not in this build). I did not run a licence scanner.

## 8. Verified versus not verified

**Verified (PC, through port 8000):** health; migrations to 0005; all test suites above; demo data creation through the API; role rules (planner isolation, reviewer/auditor/admin limits, forced password change, super-admin-only admin creation);
dashboard in the browser pane (plan page icons, basemap, wide panorama viewer, Users page, Activity log, forced-change screen); audit chain valid (158 entries); `down`/`up -d` keeps data; backup and scratch restore.
**Not verified:** anything on a phone (install, sign-in, drawing, sending, AR, compass, panorama capture, forced-change screens in the app); Wi-Fi; HTTPS; Windows Firewall; heavy load; a full live-database replace from a backup; a clean-PC build; the Node API tests; the dashboard decision/comment buttons were exercised through the API, not clicked.

## 9. Read these in order

1. `docs/HANDOVER-FOR-OWNER.md` (short), then this file.  2. `docs/STACK.md` (how the stack runs).  3. `docs/MAP-AND-NGINX.md` (map and routing).
4. `docs/USERS-AND-ROLES-PLAN.md` and `docs/USERS-HOWTO.md` (accounts).  5. `docs/DEMO-ACCOUNTS.md` and `docs/DEMO-SCRIPT.md`.  6. `docs/PANORAMA-FEATURE.md`.
7. `docs/HEADING-REVIEW.md` (and on branch `app/heading-fix`: `docs/HEADING-FIX-HOWTO.md`).  8. `docs/APP-PHONE-TEST.md`.  9. `server-django/docs/*` (the API, phase by phase, and PostgreSQL operations).
10. `docs/layout-schema-v2.md` (the frozen plan file format) and `web/docs/*`.  11. `docs/FINAL-STATUS.md` (status table).

## 10. Creating real users safely
Follow `docs/USERS-HOWTO.md` and `docs/USERS-AND-ROLES-PLAN.md`. In short: confirm `stackadmin` signs in; add each person on the **Users** page (rank dropdown, optional appointment, role, Generate a temporary password);
hand the password over in person, one at a time; the person is forced to choose their own at first sign-in. Disable (never delete) the demo accounts. Real names and ranks never go to Git.
