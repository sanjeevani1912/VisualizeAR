# Moving the DhruvAR backend from Node to Django

**Status: phases 0-4 done (scaffold; sign-in and users; layout checking, plans, decisions, comments, audit; field images; seed, conformance and dashboard check). All 23 routes built. PostgreSQL support is done and tested (`docs/PHASE-5-POSTGRESQL.md`; runbook `docs/POSTGRESQL-OPERATIONS.md`). Next: serving/hardening and cut-over planning. The Node server in `server/` is untouched and remains the live backend.**
Details: `docs/PHASE-1-AUTH-AND-USERS.md`, `docs/PHASE-2-PLANS.md`, `docs/PHASE-3-IMAGES.md`, `docs/PHASE-4-SEED-AND-CONFORMANCE.md`.

## Isolation: how the current system stays safe
- Lives in a **separate git worktree** (`DhruvAR-django/`, a sibling of `DhruvAR/`) on its own branch, `django-backend`. The working folder and `main` are not changed by anything done here.
- All new code is in `server-django/`. Nothing in `app/`, `web/` or `server/` is edited.
- Own database file (`dhruvar-django.db`) and own data folder, so it can never read or write the Node server's `dhruvar.db`.
- To abandon the idea: `git worktree remove ../DhruvAR-django` and `git branch -D django-backend`. Nothing else to undo.

## What must not change (the contract)
The Android app and the web dashboard talk to the API, so the Django server has to behave identically.
Same base URL (`/api`), same JSON field names, same status codes, same `{ "error": "message" }` error body.
Both clients then work unchanged; only the server behind them is replaced.

### The 23 routes to reproduce
| Area | Route | Done |
|---|---|---|
| Health | `GET /api/health` | yes |
| Auth | `POST /auth/login`, `POST /auth/token`, `POST /auth/logout`, `GET /auth/me`, `POST /auth/change-password` | yes (phase 1) |
| Users | `GET /users` (names, any signed-in user) | yes (phase 1) |
| Admin | `GET /admin/users`, `POST /admin/users`, `PATCH /admin/users/:id`, `POST /admin/users/:id/password` | yes (phase 1) |
| Audit | `GET /audit`, `GET /audit/verify` | yes (phase 2) |
| Plans | `GET /plans`, `POST /plans`, `POST /plans/:id/versions`, `/withdraw`, `/start-review`, `/decision`, `/comments` | yes (phase 2) |
| Images | `POST /plans/:id/attachments`, `GET /attachments/:id`, `DELETE /attachments/:id` | yes (phase 3) |

### Behaviours that are easy to get subtly wrong
- A plan the user may not see is reported as **404**, never 403, so ids cannot be probed.
- Reject / changes-requested need a reason of at least 10 characters; approve does not.
- A decision must name the **latest** version, else 409. It is recorded against that version.
- Plan codes are `TP-YYYY-NNNN`, numbered per year.
- Resubmitting keeps the old name when the new layout has the default name `Untitled plan`.
- `start-review` answers `{ok:true, changed:false}` when it does not apply, rather than an error.
- Sign-in: wrong username and wrong password cost the same time and give the same message.
  5 failures per username+IP lock for 15 minutes (429 with the minutes left).
  Every login issues a new token and destroys the old cookie session.
- Sessions: 60 min idle, 12 h absolute, `last_seen` refreshed only if older than 30 s, expired ones purged every 10 min.
- Bearer token (`Authorization: Bearer ...`) wins over the cookie `dhruvar_sid`. Bearer requests skip the CSRF header; `POST /auth/token` is exempt.
  Cookie requests that change data need `X-Requested-With: dhruvar`.
- Admin rules: cannot demote or deactivate yourself; there must always be one active admin; a role change, deactivation or password reset ends that user's sessions.
- Changing your own password ends your other sessions and keeps this one on a new token.
- Images: JPEG/PNG/WebP only, checked by the file's own first bytes (415), max 30 per plan (409), size limit (413 "Image is too large"), names sanitised, stored as `<uuid>.<ext>`, served with `nosniff`, `inline`, `private, max-age=300`.
- Every state change writes its audit entry **in the same transaction**.
- Unknown `/api/...` paths give JSON 404; bad JSON gives 400 "Malformed JSON"; anything unexpected gives 500 "Something went wrong on the server".
- `Cache-Control: no-store` on all API responses.
- Layout checking keeps the same rules and messages (`Invalid layout file: <path> — <message>`), the 500-object cap, defaults for missing sizes, generated ids/names, rotation normalised to 0-360.

## Decisions made (change any of them before phase 1)
| Choice | Why |
|---|---|
| **Plain Django, no Django REST Framework** | The API needs exact JSON shapes and error bodies. DRF's defaults differ (`{"detail": ...}`), so it would need overriding almost everywhere. Fewer dependencies, easier to match. DRF can be added later if wanted. |
| **Own session table, not Django sessions** | Must support bearer tokens, the `dhruvar_sid` cookie, idle + absolute timeouts, and the same "end all sessions of a user" behaviour. |
| **Own CSRF header check, not Django CSRF tokens** | The dashboard and app already use `X-Requested-With`; changing clients is out of scope. |
| **UUID string ids, epoch-millisecond integer timestamps** | Responses stay byte-compatible with what the clients expect. |
| **Django PBKDF2 password hashing** | Built in and reviewed. Node's scrypt hashes cannot be copied across, so accounts are re-seeded (the current database is development data only). Argon2 is a one-line upgrade later. |
| **SQLite now, PostgreSQL by settings change** | Same as today; Django makes the later move small. |
| **Python 3.11 + Django 5.2 LTS** | Python 3.11 is installed here; 5.2 is a long-term-support release. |

## Phases
Each phase ends with its tests passing and nothing outside `server-django/` changed.

0. **Scaffold** (done): project, settings, data models, `rules.py` and `audit.py` ported, health route, 14 tests.
   Audit hashes are verified identical to the Node implementation.
1. **Auth and users** (done 2026-11-14, 84 tests, compared with Node on 55 requests): password hashing, sessions, bearer + cookie, login throttle, CSRF header, security headers/CSP; routes for auth, users, admin.
2. **Layout and plans** (done 2026-11-20, 166 tests, 544 Node test vectors for layout and hash, compared with Node on 138 requests with 0 differences): `layout.ts` ported (same checks and messages, same content hash via a JavaScript-exact serialiser), then the plan routes and `/audit`.
3. **Images** (done 2026-11-20, 191 tests, compared with Node on 229 requests with 0 differences): upload, serve, delete.
4. **Seed and conformance** (done 2026-11-20: `seed` command identical to Node's seed on 10 API views; the same 13 end-to-end tests pass on Node and Django; real dashboard clicked through on Django; 201 tests): a seed command (sample users/plans, exported once from `web/src/data/mock.ts`); make `server/scripts/api-test.ts` able to run against either server, so the **same 13 end-to-end tests** judge both. The fixture produced by the Android app's own serializer must pass too.
5. **Serving and hardening**: serve `web/dist` with the single-page-app fallback, CSP with the map origin, `Secure` cookies behind HTTPS, gunicorn, run notes. (PostgreSQL settings: done, see docs/PHASE-5-POSTGRESQL.md.)
6. **Cutover decision**: only when every test passes against Django, the dashboard works in a browser and the app works on a phone. Until then Node stays live.

## Risks
- (Resolved in phase 2: `api/jsutil.py` prints JavaScript-style numbers/strings; 544 vectors from the real Node code match.) The original risk: the plan content hash uses `JSON.stringify` key order and number formatting (`10` not `10.0`).
- (Resolved in phase 1: the sign-in throttle now lives in the database table LoginThrottle.) The original risk: it was in memory, as in Node.
- zod's error text differs from any Python validator's. Messages the clients display must be matched by hand and covered by tests.
- `seed.ts` imports the dashboard's TypeScript mock data. The Django seed needs a one-off JSON export of it.

## Running it (phase 0)
```bash
cd server-django
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt     # Windows; use .venv/bin/python on Linux/macOS
.venv/Scripts/python manage.py migrate
.venv/Scripts/python manage.py test
ALLOWED_HOSTS=localhost,127.0.0.1 .venv/Scripts/python manage.py runserver 4001
```
Use a port other than 4000 so it never collides with the Node server. For a phone on the LAN add the PC's IP to `ALLOWED_HOSTS`.
Settings use the same environment variables as the Node server (`DATA_DIR`, `SESSION_IDLE_MINUTES`, `LOGIN_MAX_FAILURES`, ...), plus `DJANGO_SECRET_KEY` and `DJANGO_DEBUG`.
