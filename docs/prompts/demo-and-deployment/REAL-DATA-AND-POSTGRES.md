# DhruvAR — Real users, a real plan, and PostgreSQL (prompt file)

Written 2026-11-10. Goal of this file: turn the sample data into real data for the demo, then move the backend to PostgreSQL
without risking the demo. Paste **Section 1** first in a new chat, then one task prompt from Section 3 or 4.
Do tasks in order. Each task has a "Done when". Do not skip a "Done when".

---

## 0. The decision up front: when to switch to PostgreSQL

**Do not switch the live demo database to PostgreSQL before the demo.**

Why:
- The running server (`server/`, Node + Express) talks to SQLite directly through `node:sqlite` (`server/src/db.ts`, the file `server/data/dhruvar.db`). It has no PostgreSQL support.
- The PostgreSQL route your senior asked for is the **Django backend** (`DhruvAR-django/server-django`, branch `django-backend`, pushed as `sanjeevani/django-backend`). It is at phase 0 of 6. Only `GET /api/health` exists. Login, plans and images are not built.
- A half-finished backend on demo day is the biggest avoidable risk. The demo runs on Node + SQLite. PostgreSQL follows after.

What we CAN do before the demo, safely and separately: run PostgreSQL in Docker and point the Django scaffold at it to prove the connection works (Task D1). That touches nothing the demo uses.

---

## 1. Shared context (paste first, every time)

**Project.** DhruvAR: an Android app plans a field layout (region, origin, assets) and views it in AR; a React dashboard lets reviewers approve it; a Node/Express API with SQLite stores everything with roles and a tamper-evident audit log.

**Current backend facts (verified).**
- API: `server/`, Node 22.13+, Express 5, port 4000, DB `server/data/dhruvar.db`, uploads in `server/data/uploads/`.
- Dashboard: `web/`, Vite on port 5173, proxies `/api` to `localhost:4000`.
- `npm run seed` creates 8 sample users and 12 fictional plans, writes random passwords to `server/data/seed-credentials.txt`. It refuses to run when users exist; `npm run seed -- --reset` wipes EVERYTHING (users, plans, images, audit log) and seeds again.
- Roles: planner, reviewer, admin, auditor. Admin can create users, change roles, deactivate, reset passwords. Admin cannot decide plans. A reviewer cannot decide their own plan. Reject / changes-requested need a reason of at least 10 characters.
- Usernames: 3 to 40 characters, letters, digits, dot, dash, underscore. Passwords: at least 10 characters with upper case, lower case and a digit.
- Rules enforced by the server: you cannot demote or deactivate yourself, and there must always be one active admin.
- The audit log is a hash chain. Each entry hashes the previous one. `GET /api/audit/verify` detects any edit or deletion.
- Plans cannot be deleted through the API.
- Phone sign-in uses `POST /api/auth/token`. The app takes the server address on its sign-in screen. Debug builds accept plain `http`.

**Django facts (verified).** `server-django/` is Django 5.2, plain Django without DRF, own session table, UUID string ids, epoch-millisecond integer timestamps, PBKDF2 password hashing, SQLite by default (`dhruvar-django.db`). `PLAN.md` lists 23 routes to match and phases 0 to 6.

**Hard rules.**
- Never edit `dhruvar.db` by hand or with SQL tools. That breaks the audit chain.
- Never run `npm run seed -- --reset` unless told to, and say what it will delete first.
- Commit only when asked. Never push to `army/main`. Push only to the branch named in the task.
- Report failing builds/tests with real output. Say plainly what was not verified.
- Explain every new concept in plain words; the owner is a beginner with Django, Postgres and Docker.

---

## 2. Order of work

| Task | What | When |
|---|---|---|
| A | Real users | Before the demo |
| B | One real plan created through the app | Before the demo |
| C | Optional: a clean "real data only" database | Before the demo, only if time allows |
| D1 | PostgreSQL connection proof with Django scaffold | Before or after the demo; harmless |
| D2 | Django phases 1 to 4 (auth, plans, images, conformance) | After the demo |
| D3 | PostgreSQL + Nginx + Docker deployment | After the demo |
| D4 | Move existing data from SQLite to PostgreSQL | At cutover only |

---

## 3. Before-the-demo tasks (Node + SQLite stays live)

### Task A — Real users
**Prompt:**
> Read Section 1. Task A. Do NOT reset the database. (1) Show me the current users from the dashboard Users page or `GET /api/admin/users` while signed in as the admin (`iyer`, password from `server/data/seed-credentials.txt`). (2) Ask me for the list of real people: full name, rank, username, role. (3) Create each through the admin Users page, one at a time, respecting the username and password rules. I choose the passwords; never write them into any file in git. (4) Do not delete the sample users (not possible); deactivate them only if I say so, after confirming that at least one real admin is active. (5) Sign in as each new user and confirm the role sees what it should. (6) Run `GET /api/audit/verify` and confirm the chain is valid. Report what changed.

**Done when:** real users exist, each can sign in, audit verification passes, no passwords were committed.

### Task B — One real plan through the app
**Prompt:**
> Read Section 1. Task B. Goal: one genuine plan made on the phone and approved on the dashboard. (1) Check the API is up (`/api/health`) and the phone can reach it (same Wi-Fi, or `adb reverse tcp:4000 tcp:4000` and the address `http://localhost:4000`). (2) With me, sign in on the phone as a real planner, draw a region, set an origin, place a believable set of assets (for example tents, vehicles, an antenna, trenches), give the plan a proper name, and send it for approval. (3) On the dashboard sign in as a real reviewer who is not the planner; confirm the plan appears with the right name, `TP-YYYY-NNNN` code, region, origin, asset count and map. (4) Add a field photo and a comment. (5) Make the decision (approve, or request changes with a reason of at least 10 characters, whichever the demo story needs). (6) Verify the Activity log shows submit, review, comment, decision, and that `audit/verify` passes. If the phone cannot send, use the fallback: export the layout JSON from the app and submit it on the dashboard "New plan" page as a planner. Note every problem you hit in `DEMO-AUDIT.md`.

**Done when:** the plan exists with its decision and audit trail, and the whole path was done without touching the database directly.

### Task C — Optional: database with real data only
The sample data cannot be removed one item at a time. `seed --reset` brings the samples back. If a clean database is wanted:
> Read Section 1. Task C. Propose, then implement only after I approve, a small addition to `server/scripts/seed.ts`: a flag such as `--reset --admin-only` that wipes data and creates one admin account with a random password written to `data/seed-credentials.txt`, with no sample users and no sample plans. Add a test or a manual check. Show me the diff before running it. Do not change any other file. Keep the existing `seed` behaviour identical when the flag is absent. Then, with my confirmation of what will be deleted, run it, create the real users (Task A) and the real plan (Task B).

**Done when:** the dashboard shows only real users and plans, and the old default behaviour of `seed` is unchanged.

---

## 4. PostgreSQL tasks

### Task D1 — Prove Django can talk to PostgreSQL (safe, separate from the demo)
**Prompt:**
> Read Section 1 and `server-django/PLAN.md`. Task D1, on branch `django-backend` in the `DhruvAR-django` worktree. (1) Explain in plain words what PostgreSQL is, what a Docker container is, and what a Django "database engine" setting does. (2) Add `psycopg[binary]` to `requirements.txt`. (3) Make `config/settings.py` choose the database from environment variables, for example `DB_ENGINE=postgres`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_HOST`, `DB_PORT`, defaulting to the current SQLite settings so nothing changes when they are unset. IMPORTANT: the current `OPTIONS` (`transaction_mode: IMMEDIATE`, `timeout`) are SQLite-only and must NOT be passed to PostgreSQL. (4) Add `docker-compose.yml` (or `compose.dev.yml`) with a `postgres:16` service, a named volume, and a password from an env file that is git-ignored. (5) Run `manage.py migrate` against PostgreSQL and the 14 existing tests against it. (6) Report any test that behaves differently from SQLite. Do not touch `server/`, `web/` or `app/`.

**Done when:** `migrate` and the 14 tests pass on PostgreSQL, and unsetting the variables still runs on SQLite.

### Task D2 — Finish the Django API (after the demo)
**Prompt:**
> Read Section 1 and `server-django/PLAN.md`. Task D2. First rebase `django-backend` onto the current `main`, and update the route list for anything added since `8879061`. Then do PLAN phases 1 to 4, one at a time, each ending with passing tests. Rules that must carry over exactly: unseen plans give 404 not 403; decisions name the latest version else 409; reject and changes need a reason of 10+ characters; `TP-YYYY-NNNN` codes numbered per year; sign-in lockout after 5 failures for 15 minutes; sessions 60 min idle and 12 h absolute; every state change and its audit entry in one transaction. PostgreSQL-specific care: (a) audit entries must be written one at a time, in order. SQLite's `BEGIN IMMEDIATE` did that for free; on PostgreSQL lock the latest audit row (`select_for_update`) or use an advisory lock inside the transaction, so two simultaneous writes cannot both chain to the same previous hash. Add a test that fires concurrent writes. (b) Move the in-memory login throttle into the database. (c) Plan content hashes must equal Node's (JS `JSON.stringify` key order and number format). Make `server/scripts/api-test.ts` run against either server so the same end-to-end tests judge both.

**Done when:** the same end-to-end tests pass on Node and on Django, running on PostgreSQL.

### Task D3 — Deployable stack
**Prompt:**
> Read Section 1. Task D3. Write `docker-compose.yml` with `postgres`, `django` (gunicorn) and `nginx`. Nginx: reverse proxy to Django, serve `web/dist` with single-page-app fallback, upload size limit, security headers, HTTPS-ready. Set `TRUST_PROXY` and `COOKIE_SECURE` correctly behind it. Document that load balancers would later replace or sit in front of Nginx, and what changes (sessions and the throttle must not live in one process's memory). Only open-source components. Produce `LICENSES.md` and a short runbook: start, stop, backup and restore PostgreSQL, rotate the Django secret key, create the first admin. Map tiles, terrain and fonts must be hostable offline.

**Done when:** `docker compose up` serves the dashboard and API, and the app signs in against it.

### Task D4 — Move existing data from SQLite to PostgreSQL (only at cutover)
**Prompt:**
> Read Section 1. Task D4. Write a one-off, read-only export from `server/data/dhruvar.db` and an import into Django/PostgreSQL. Keep ids (UUID strings), timestamps (epoch ms) and the audit chain (same seq order, same hashes; the plan says the hash is identical in Python, verify with `audit/verify` on the Django side). Copy `uploads/` files unchanged and keep `stored_name`. Password hashes CANNOT be copied (Node uses scrypt, Django uses PBKDF2): users must get new passwords, so generate them and give them to me in a file that is not committed. Dry-run on a copy first and show row counts before and after. Never modify the original SQLite file.

**Done when:** row counts match, `audit/verify` passes on PostgreSQL, a real plan opens with its images.

---

## 5. Checklists

**Before the demo**
- [ ] Task A and B done on the machine that will run the demo (the database lives on that machine only)
- [ ] `audit/verify` passes
- [ ] No reset after the real plan was created
- [ ] Phone reaches the server (`/api/health` in phone Chrome, or `adb reverse`)
- [ ] Map PC address and network settings handled (see `HOME-GUIDE.md`)

**Before PostgreSQL cutover**
- [ ] The same end-to-end tests pass on Django + PostgreSQL
- [ ] Concurrent-audit test passes
- [ ] Dashboard clicked through in a browser; app tested on a phone
- [ ] Backup of the SQLite file and `uploads/` taken
- [ ] Rollback is possible: Node + SQLite stays untouched until the new stack has run cleanly

## 6. Do not
- Do not switch the demo to PostgreSQL the night before.
- Do not edit the SQLite file by hand.
- Do not commit passwords, `seed-credentials.txt`, `data/`, `.env` files or the Django `secret_key`.
- Do not push to `army/main`.
