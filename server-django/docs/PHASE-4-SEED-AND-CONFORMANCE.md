# Phase 4 — Seed, conformance and dashboard check (Django backend)

Status: **built and verified (milestone B3).** Written 2026-11-20.
Branch `django-backend`, folder `DhruvAR-django/server-django/`. Nothing in `app/`, `web/` or `server/` was changed,
and the real demo data (`DhruvAR/server/data`) was not touched.

---

## 1. What this phase is, in plain words

Phases 1-3 built every route. This phase answers: **"does Django really behave like Node, with the real dashboard
and the real test file?"** It adds three things:

1. a **`seed` command** that creates the sample users and fictional plans (so there is something to look at);
2. one **end-to-end test file that judges both servers** (the same 13 tests pass against Node and against Django);
3. a **click-through of the real dashboard** running on the Django server.

## 2. The `seed` command

```bash
cd DhruvAR-django/server-django
.venv/Scripts/python manage.py seed            # first time; refuses to run if users already exist
.venv/Scripts/python manage.py seed --reset    # wipes ALL data (users, plans, images, audit log) and seeds again
```
- Creates 8 sample users (`menon`, `sharma` reviewers; `iyer` admin; `nair` auditor; `singh`, `rao`, `bose`, `khan` planners)
  and 12 fictional plans with versions, decisions, comments and a hash-chained audit log (21 entries), exactly like Node's `npm run seed`.
- **Passwords are random** and written once to `DATA_DIR/seed-credentials.txt` (the `data/` folder is git-ignored). They are
  never printed and never committed. Change them after first sign-in.
- The sample data is `api/seed_data/sample_data.json`, exported **once** from the dashboard's `web/src/data/mock.ts`
  (`scripts/export_seed_data.mts`). Its times are shifted when seeding so the newest entries are "just now".
- Like Node's seed, it does not create images (the dashboard's demo photos are inline drawings).
- `--reset` uses Django's `flush` (empties every table and restarts the audit counter) and removes files in `uploads/`.
- `createuser` (make the first real admin) still works; tests were added for it.

**Checked:** I seeded a throwaway Node server (its own `npm run seed`) and a throwaway Django server (`manage.py seed`), then compared
10 views through the API: plans as 4 different users, the audit list as 2 users, the audit chain check, the name list and the
admin user list (times compared as "how long before the newest entry"). **All 10 identical**, including content hashes and layouts.

## 3. One test file for both servers

`scripts/api-test.ts` is a **copy** of `server/scripts/api-test.ts` (I may not edit `server/`). The 13 tests are unchanged.
Only the set-up differs, marked `CHANGED` in the file: it can also test an **already running** server
(`API_BASE`, `SEED_CREDENTIALS`), it finds the app's own export fixture in either folder layout, and the one test that
edits the database directly (to prove tampering is detected) runs a command you supply (`TAMPER_COMMAND`;
helpers `tamper_audit_node.mjs` and `tamper_audit_django.py`). Started with no settings it behaves exactly like the original
(starts its own Node server): **13/13**.

**Upstream change needed** (so this copy can be deleted after the merge): add the same three environment settings to
`server/scripts/api-test.ts`.

Run everything with one command (Git Bash):
```bash
bash DhruvAR-django/server-django/scripts/run_conformance.sh
```
It seeds both servers in temporary folders (ports 4100/4101), compares the seeds, runs the same test file against each, then
stops both and deletes the folders. **Result: seed comparison identical; Node 13/13; Django 13/13.** The tests include the
fixture written by the Android app's own serializer (every current asset type) and the field-image rules.

## 4. The real dashboard on Django

I ran the real dashboard (`DhruvAR/web`, read-only) with a **temporary Vite config kept outside `web/`** that proxies `/api` to Django
on port 4001 (a seeded throwaway database; test passwords set only for this check), and clicked through it in the browser pane:

| Page / action | Result |
|---|---|
| Sign-in as reviewer, admin, auditor, planner (cookie + CSRF header through the proxy), sign-out | works for all four roles |
| Overview (counts, "needs your decision" list) | correct numbers and waiting times |
| Plans list: 12 plans, statuses, planners, region sizes, assets, dates | all correct; a planner sees only their own 3 |
| Plan page: opens a submitted plan and starts the review automatically; site summary, content hash, map, assets table (14 rows) | works |
| Decision: "Request changes" with a reason, confirmation step | recorded; status, "awaiting" badge and audit trail updated |
| Comments (with `✓` and `é`) | posted, text intact |
| Field images: uploaded a real PNG; dashboard shows it | image loads (`image/png`, `nosniff`, `private, max-age=300`) |
| Activity log: the actions above plus sign-ins, "Image added" | correct |
| Auditor: "Verify now" | "Intact · 31 entries" |
| My account: change own password | "Password changed", current session stays signed in |
| Admin: Users page, change `khan`'s role | saved and audited (`khan: role planner -> reviewer`), last-sign-in times shown |

The browser console showed only the expected "not signed in" (401) answers of the page's own check before sign-in.

## 5. Not checked (honestly)

- The **Android app itself** was not run against Django (needs a phone or emulator). Its requests were checked by reading its code and replaying them (section 9).
- The dashboard's **file-picker upload button** was not clicked (the browser tool cannot choose files); the same upload was sent
  to Django with a real PNG request and the dashboard then displayed it.
- Dashboard pages not clicked: password reset and disable-user buttons, creating a user from the form, the "presentation demo" page,
  mobile widths. The routes behind them are covered by tests and the Node comparison.
- **PostgreSQL** (milestone B4), HTTPS/`Secure` cookies and Nginx (phase 5, another session).
- **Speed:** Django's password check (PBKDF2, 1 million rounds) takes about a second per sign-in on this PC; Node's takes a fraction of
  that. Fine for sign-in, but worth knowing (Argon2 or fewer rounds can be chosen later).
- The seeded plans use the dashboard's mock layouts; real plans from the app were tested only through the fixture.

## 6. Files added in this phase

`api/management/commands/seed.py`, `api/seed_data/sample_data.json`, `api/tests/test_seed.py` (10 tests; suite is now **201**),
`scripts/export_seed_data.mts`, `scripts/api-test.ts`, `scripts/compare_seed.py`, `scripts/run_conformance.sh`,
`scripts/tamper_audit_node.mjs`, `scripts/tamper_audit_django.py`.

## 7. Words you may not know
- **Seed:** filling an empty database with starting data.
- **Conformance test:** the same test run against two programs to prove they behave the same.
- **Proxy (Vite):** the dashboard's dev server forwards `/api` requests to the real API, so the browser sees one address.
- **Fixture:** a saved sample input, here the layout file written by the real Android app.

## 8. Next: milestone B4 (not started)
PostgreSQL: settings from environment variables, docker-compose, the whole suite on PostgreSQL, audit-log and plan-code concurrency
under PostgreSQL, and an operations note.

## 9. Follow-up checks (added after B3)

**The Android app's requests.** I read `app/.../data/remote/ServerClient.kt`. The app makes five calls: `POST /api/auth/token`,
`POST /api/auth/logout`, `POST /api/plans`, `POST /api/plans/<id>/versions` and `GET /api/plans`, always with a Bearer token and
`Content-Type: application/json; charset=utf-8`. (It has no image upload; images are added from the dashboard.) It builds the layout with
Android's `org.json`, which writes JSON a little differently from a browser: no spaces, `1.0E-7` for tiny numbers, `\/` for slashes,
`\uXXXX` escapes, and extra fields such as `scale` and the anchor's `label`. Three requests written in exactly that style were added to
`scripts/compare_servers.py` (submit, new version, list). **Node and Django gave identical answers, including identical content hashes.**
The reply fields the app reads (`token`, `user.{id,username,name,rank,role}`, `id`, `code`, `versions[].layout.id`, `status`, `error`) are
the ones both servers send.
To reach Django from a phone on the LAN: start it on all addresses (`manage.py runserver 0.0.0.0:4001`) and add the PC's address to
`ALLOWED_HOSTS` (for example `ALLOWED_HOSTS=192.168.1.214`); the app's server address is then `http://<that address>:4001`.

**Request size limit.** Node refuses a JSON body of more than exactly 2 MB (answer `500`). Django's default was 2.5 MB, so a body between the two
was accepted by Django only. `DATA_UPLOAD_MAX_MEMORY_SIZE` is now exactly 2 MB (it does not apply to the file part of an image
upload). Tested (`BodySizeTests`) and compared: exactly 2 MB accepted, 1 byte more refused, 3 MB refused, on both servers.

Result after these changes: **235 requests identical to Node**, **202 tests pass**, seed comparison identical and 13/13 end-to-end tests on both servers.
