# DhruvAR — Demo + Deployment Master Plan (phased prompts)

Written 2026-11-06. Purpose: one route to follow, in order, with a ready prompt for each phase.
Paste **Section 1 (shared context)** at the start of any new chat, then paste the prompt of the phase you are on.
Work one phase at a time. Do not start a phase until the previous phase's "Done when" is true.

---

## 0. How to use this document

1. Open a new session in the repo folder.
2. Paste Section 1, then one phase prompt (Section 3).
3. When the phase ends, tick its box in Section 5 and commit.
4. If a phase fails its "Done when", stop and fix it. Never skip ahead.

---

## 1. Shared context (paste first, every time)

**Project.** DhruvAR is a field-planning system. An Android app lets an operator choose a region on a map, set an origin
(GNSS or map tap), place assets (tents, trucks, antennas, trenches...) and view them in AR. The plan is sent for approval.
A web dashboard lets reviewers/admins/auditors see plans, approve/reject, and read a tamper-evident audit log.

**Why two Android flavors.** The target devices (Indian Army "SAMBHAV") have no Google Play Services, so ARCore cannot run there.
- `play` flavor = ARCore-backed AR. Needs Google Play Services for AR. **This is the demo build.**
- `foss` flavor = no Google/closed-source code; AR from camera + gyro/compass + GNSS. **This is the deployment target, still maturing.**
Flavor sources: `app/src/play/`, `app/src/foss/`, shared `app/src/main/`. A Gradle task `verifyFossNoGoogleDependencies`
fails the build if ARCore/Play Services/Firebase reach the foss classpath.

**Folders on this machine (`C:\Users\itcell\AndroidStudioProjects\`).**
- `DhruvAR` — my repo, branch `main`, last commit `8879061` (app sign-in + send-for-approval). Node/Express + SQLite server in `server/`, React/Vite dashboard in `web/`.
- `DhruvAR-friend` — colleague's work: 18 commits ahead of `8879061`. Adds the `foss` flavor, sensor-based geo-AR engine (`domain/ar/**`), terrain/DEM (app + `server/src/terrain*.ts`, `dem.ts`), map-first UI, region-drawing changes, calibration. Ends with "anchor lock in progress" (WIP). Has its own `.git`.
- `DhruvAR-django` — git worktree of `DhruvAR`, branch `django-backend`. Contains `server-django/` (**untracked, uncommitted**): Django 5.2 scaffold, models, `rules.py`, `audit.py`, health route, 14 tests. Phase 0 of 6 of its own `PLAN.md`.

**Priorities (from me, the owner).**
1. A strong, stable **demo** (presentation Wednesday) using the **`play`/ARCore** build. foss is "work in progress", not the demo.
2. The **web dashboard** polished and consistent with the app.
3. Backend migration to **Django + PostgreSQL behind Nginx** (load balancers later) — after the demo is safe.
4. Open-source libraries at every step for the deployment path.

**Hard rules.**
- Do not break the working demo. Tag a safe version before risky work; do big merges on a separate branch.
- Do not delete or overwrite anything in `DhruvAR-friend`; read from it, copy from it, never edit it.
- Commit only when I ask. Never push, never force, never rewrite history.
- Before deleting/overwriting a file, look at it first. Report test/build failures with real output.
- Prefer small, verifiable steps. After each step say what changed and how it was verified.
- Keep the API contract (`/api`, JSON field names, status codes, `{ "error": "..." }`) identical between Node and Django.
- Explain jargon in plain words; I am a beginner on Django/Postgres/Nginx/flavors.

**Known facts / traps.**
- Server runs on port 4000 (`api`), dashboard on 5173 (`dashboard`); see `.claude/launch.json`.
- Seed credentials are in `server/data/seed-credentials.txt` (dev data only).
- Django plan lives in `DhruvAR-django/server-django/PLAN.md` (23 routes, behaviours to match, risks).
- Plan content hash depends on JS `JSON.stringify` key order and number format — Django needs a JS-compatible serializer.
- Login throttle is in memory — must move to DB/cache before multi-worker deployment.
- Friend's `docs/Open-OS-Geo-AR-Plan.md` and `docs/DhruvAR-Technical-Report.md` describe the foss design.

---

## 2. Phase map

| # | Phase | Outcome | Est. |
|---|---|---|---|
| P0 | Safety net | Everything committed/tagged; baseline builds recorded | 0.5 d |
| P1 | Demo baseline audit (play) | Written list of what works/breaks in the ARCore + dashboard flow | 0.5 d |
| P2 | Demo fixes — Android play | ARCore flow stable and presentable | 1 d |
| P3 | Demo fixes — Dashboard | Dashboard polished, consistent with app data | 0.5–1 d |
| P4 | Safe merge of friend's work | Branch with friend's work; play proven unchanged; foss compiles | 0.5–1 d |
| P5 | Demo rehearsal + materials | Script, checklist, slides, fallback plan | 0.5 d |
| P6 | Django backend (after demo) | Django passes the same tests as Node | 2–3 d |
| P7 | Postgres + Nginx + Docker | One-command deployable stack, dependency/licence audit | 1 d |
| P8 | Foss hardening (ongoing, with friend) | Field-tested foss build | open |

Do P0→P5 before Wednesday. P6–P8 start afterwards unless time clearly allows.

---

## 3. Phase prompts

### P0 — Safety net
**Goal:** nothing can be lost, and we can always return to a known state.

**Prompt:**
> Read Section 1 context. Phase P0. (1) In `DhruvAR`, show `git status`. Commit the untracked `docs/` files only if I confirm; do not commit anything else. (2) In `DhruvAR-django`, show `git status` and, when I confirm, commit `server-django/` on `django-backend` with a clear message. (3) Create a git tag `demo-safe` on the current `main` commit (tag only, no push). (4) Record the baseline: run `./gradlew assemblePlayDebug` and `./gradlew testPlayDebugUnitTest` (or the nearest equivalent that exists), `npm run build` in `web/`, and the server tests in `server/`. Report each result honestly with failing output. (5) Write results to `docs/prompts/demo-and-deployment/BASELINE.md`.

**Done when:** django work committed, tag exists, BASELINE.md lists pass/fail for each build/test.

### P1 — Demo baseline audit (play flavor)
**Goal:** know exactly what is wrong before fixing.

**Prompt:**
> Phase P1. Using the `main` code (play behaviour): (1) Start `api` and `dashboard` with the preview tools and sign in with a seeded account. Walk through every dashboard page (Overview, plans, new plan, users, account, audit verify) and record bugs/ugly UI with screenshots or text. (2) Read the Android flow in code: Home → SignIn → Planning (map, region, origin, assets) → ARVisualization → Send for approval. List risks: crashes, dead ends, unclear text, missing assets, scale/label issues. (3) Produce `docs/prompts/demo-and-deployment/DEMO-AUDIT.md` with a table: item, severity (blocker/major/minor), where, suggested fix, effort. Do not fix anything yet. Ask me which phone I demo on and whether it has Google Play Services for AR.

**Done when:** DEMO-AUDIT.md exists and I have ranked what to fix.

### P2 — Demo fixes: Android (play)
**Goal:** the ARCore path is stable and looks professional.

**Prompt:**
> Phase P2. Fix only items from DEMO-AUDIT.md marked blocker/major for Android, in order. For each: explain the cause in plain words, make the smallest fix, add or update a unit test when logic is involved, build `assemblePlayDebug`, and report. Cover where relevant: ARCore availability/install flow and clear messages; origin placement and north alignment; asset sizing by region; labels and distance lines; GNSS detect bar; screenshot/capture; sign-in and send-for-approval errors (offline, wrong password, expired session). Do not touch the foss flavor or `domain/ar/**` from the friend. Do not refactor unrelated code. Stop and ask before any change that alters the layout JSON schema.

**Done when:** all blockers/majors fixed, play build installs and the full demo path runs on my phone, `demo-android-ok` tag proposed.

### P3 — Demo fixes: Dashboard
**Goal:** dashboard matches the app data and tells a clear story.

**Prompt:**
> Phase P3. Fix blocker/major dashboard items from DEMO-AUDIT.md. Ensure: a plan sent from the phone appears with correct name, code (TP-YYYY-NNNN), version, status, assets and images; reviewer approve/reject/changes-requested flows show correct messages (reason >= 10 chars for reject/changes); audit log verifies; roles (planner/reviewer/admin/auditor) see only what they should; empty/loading/error states are clear; works at laptop and projector resolution. Use the in-app browser to verify each page and capture proof. Keep the API contract unchanged. Prepare a demo seed (a few realistic plans in different states) via the existing seed script, without touching real data.

**Done when:** every dashboard page verified in the browser with no console errors, demo seed ready.

### P4 — Safe merge of friend's work
**Goal:** bring in the foss work without risking the demo.

**Prompt:**
> Phase P4. (1) Create branch `integrate-foss` from `main`. Never merge into `main` in this phase. (2) Add `DhruvAR-friend` as a git remote (read-only fetch) or fetch his commits; confirm his history contains `8879061`; merge his tip into `integrate-foss`. List every conflict, resolve carefully (his Android/AR/terrain code wins in `app/`; keep my sign-in, assets, approval and dashboard behaviour in `server/` and `web/` — check each file, do not blanket-pick a side). (3) Build `assemblePlayDebug` and `assembleFossDebug`; run unit tests for both; run `verifyFossNoGoogleDependencies`. (4) Compare play behaviour against `demo-safe`: same screens, same flow, ARCore still works; report any difference. (5) Run server and web from the branch and repeat the dashboard walkthrough. (6) Give me a clear verdict: safe to adopt for demo, or keep `main` for demo and adopt after.

**Done when:** verdict written, both flavors compile, dashboard/server verified, `main` untouched.

### P5 — Rehearsal and materials
**Goal:** a calm, repeatable demo.

**Prompt:**
> Phase P5. Create `docs/prompts/demo-and-deployment/DEMO-SCRIPT.md`: audience, 8–10 minute flow (planner on phone → AR → send → reviewer on dashboard → decision → audit), exact clicks and what to say, which accounts to use, a pre-demo checklist (charged phone, ARCore installed, same Wi-Fi/IP, server and dashboard running, map tiles reachable, seed data, screen mirroring), and a fallback plan for each failure (no network, ARCore fails, server down). Add a short honest slide outline: problem, solution, demo, why flavors (play now, foss for SAMBHAV), backend roadmap (Django, PostgreSQL, Nginx then load balancers, open-source only), timeline to next month. Do a full dry run with me and fix issues found.

**Done when:** one full dry run completes without surprises.

### P6 — Django backend (after demo)
**Goal:** Django serves exactly the same API as Node.

**Prompt:**
> Phase P6. Work in `DhruvAR-django/server-django`, rebase `django-backend` onto the current `main` (or `integrate-foss` if adopted). First update PLAN.md's route list for changes since 8879061 (terrain/DEM, layout v2). Then follow PLAN.md phases 1→4 in order, one at a time, each ending with passing tests: 1 auth/users/admin (hashing, own session table, bearer+cookie, throttle moved to DB, CSRF header, headers); 2 layout validation (same messages, JS-compatible content hash) + plans + audit; 3 images (magic-byte check, limits, sanitised names); 4 seed command + make `server/scripts/api-test.ts` run against either server so the same 13 end-to-end tests judge both. Plain Django, no DRF unless I approve. Explain each Django concept as you use it.

**Done when:** the same end-to-end tests pass on Node and Django; dashboard and app work against Django on port 4001.

### P7 — PostgreSQL, Nginx, Docker
**Goal:** a deployable, open-source stack.

**Prompt:**
> Phase P7. (1) Add PostgreSQL support to Django settings (env-driven), migrate, run the full tests against Postgres. (2) Write `docker-compose.yml`: postgres, django (gunicorn), nginx. Nginx: reverse proxy to Django, serve `web/dist` with SPA fallback, upload size limit, security headers, HTTPS-ready, `TRUST_PROXY` and `COOKIE_SECURE` documented. State clearly where load balancers would replace/extend Nginx. (3) Offline/air-gapped notes: map tiles, terrain and fonts hosted locally, no external calls. (4) Produce `LICENSES.md` listing every dependency (Android foss, server, web) with licence and a flag for anything non-open. (5) Write a short ops runbook: start/stop, backup/restore Postgres, rotate secrets, create the first admin.

**Done when:** `docker compose up` brings up the full stack and the dashboard and app work against it.

### P8 — Foss hardening (ongoing, with colleague)
**Goal:** field-tested sensor AR on a non-Google device.

**Prompt:**
> Phase P8. Coordinate with my colleague's work; do not duplicate it. (1) Install `fossDebug` on a device without Google services (or emulator without GMS) and run the acceptance doc `docs/geo-feature-acceptance.md`. (2) Record per-device behaviour in `device_profiles.json` only with measured data. (3) Test heading calibration methods, GNSS filter and ground/terrain at 50–300 m; log with the trace/replay tools; write measured error, not guesses. (4) Track open items: anchor lock, camera preview rotation, heading readiness, DEM availability offline. (5) Never claim accuracy that has not been measured.

**Done when:** a measured accuracy table exists for at least one target device.

---

## 4. Decisions still needed from me
- Which phone is the demo device? Google Play Services for AR installed?
- Exact presentation date/time and audience (technical, senior officers, both?).
- Is Docker allowed on target machines? Server on the same machine as Nginx?
- Must the deployed system run fully offline (tiles, terrain, fonts)?
- Required foss accuracy for deployment.
- Do I keep the sensors AR labelled "beta" in the demo or hide foss entirely?

## 5. Progress checklist
- [ ] P0 Safety net
- [ ] P1 Demo audit
- [ ] P2 Android fixes
- [ ] P3 Dashboard fixes
- [ ] P4 Safe merge
- [ ] P5 Rehearsal
- [ ] P6 Django
- [ ] P7 Postgres/Nginx/Docker
- [ ] P8 Foss hardening

## 6. If something goes wrong
- Build broken after a change → revert that change only; `git tag demo-safe` is the known-good state.
- Merge messy → abandon the `integrate-foss` branch; `main` is unaffected.
- Demo day failure → follow the fallback table in DEMO-SCRIPT.md; keep a recorded video of a good run as last resort.
