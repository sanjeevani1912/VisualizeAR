# Phase 1 — Sign-in and users (Django backend)

Status: **built and tested.** Not committed yet. Written 2026-11-14.
Branch `django-backend`, folder `DhruvAR-django/server-django/`. Nothing in `app/`, `web/` or `server/` was changed.

---

## 1. What this phase is, in plain words

The app and the dashboard both start the same way: a person signs in. The server must check the password,
remember who the person is, and decide what each role may do. Until that works, nothing else (plans, images)
can be built, because every other route asks "who is calling?".

So phase 1 builds, in Django, the **same sign-in and user management that the Node server already has**.
"Same" is exact: same addresses, same JSON field names, same error messages and numbers. That is what lets
the phone app and the dashboard talk to the Django server later without being changed.

Phase 1 covers **9 of the 23 routes** (10 including `/health`, built in phase 0):

| Route | Who | What it does |
|---|---|---|
| `POST /api/auth/login` | anyone | Browser sign-in. Session goes in a protected cookie. |
| `POST /api/auth/token` | anyone | Phone-app sign-in. Session token comes back in the answer. |
| `POST /api/auth/logout` | anyone | Ends the session. |
| `GET /api/auth/me` | signed in | Who am I? |
| `POST /api/auth/change-password` | signed in | Change your own password. |
| `GET /api/users` | signed in | Names, ranks, roles of everybody (no usernames). |
| `GET /api/admin/users` | admin | Full user list. |
| `POST /api/admin/users` | admin | Create a user. |
| `PATCH /api/admin/users/<id>` | admin | Change name, rank, role, or active/inactive. |
| `POST /api/admin/users/<id>/password` | admin | Reset someone's password. |

Not in this phase: plans, comments, decisions, images, the audit list and `/api/audit/verify` (phases 2 and 3).

---

## 2. How a request travels (the picture to keep in your head)

```
phone / browser
      │  POST /api/auth/token  {"username": "...", "password": "..."}
      ▼
 config/urls.py            → everything under /api/ goes to api/urls.py
      ▼
 api/middleware.py         → (1) read the token → find the user     [load_user]
                             (2) CSRF header check                  [csrf_guard]
      ▼
 api/urls.py               → picks the view for that address
      ▼
 api/http.py  @endpoint    → checks method, "signed in?", "right role?"
      ▼
 api/views_auth.py / views_users.py   → the actual work
      ▼
 api/auth.py, api/audit.py, api/models.py   → passwords, sessions, audit log, database
      ▼
 JSON answer  +  "Cache-Control: no-store"
 (any problem becomes  {"error": "message"}  with the right status number)
```

---

## 3. The files (what each one is for)

| File | Purpose |
|---|---|
| `api/http.py` | Small shared tools: the `ApiError` exception, reading JSON bodies, the validators that replace the Node server's `zod` library, and the `@endpoint` decorator. |
| `api/auth.py` | Password hashing, the failed-sign-in counter, sessions (create, find, expire, delete), cookie helpers, the CSRF check. A port of `server/src/auth.ts`. |
| `api/middleware.py` | Runs around every `/api` request (see the picture above). Turns every error into `{"error": ...}`. |
| `api/views_auth.py` | The five `/auth/...` routes. |
| `api/views_users.py` | `/users` and the four `/admin/users...` routes. |
| `api/urls.py` | The address list, plus a catch-all so unknown `/api/...` addresses answer in JSON, not Django's HTML page. |
| `api/models.py` | Added one table: `LoginThrottle` (the failed-sign-in counter). Migration `0002_login_throttle.py`. |
| `api/management/commands/createuser.py` | A command to create the first admin (see section 8). |
| `config/settings.py` | One line added: the middleware is switched on. |
| `api/tests/helpers.py`, `test_auth.py`, `test_users.py` | 70 new tests. |

Already existing from phase 0 and reused unchanged: `api/audit.py` (hash-chained log), `api/rules.py` (`password_error`), `api/models.py` (User, AuthSession, AuditEntry).

---

## 4. Rules copied exactly from the Node server

**Passwords**
- At least 10 characters, with a capital, a small letter and a digit; must not contain the username; at most 200 characters.
- Unknown username and wrong password give the **same message and cost the same time** ("Incorrect username or password"), so nobody can discover which usernames exist. (Django checks the password against a dummy hash when the user is unknown.)
- Username matching ignores upper/lower case.

**Lock-out**
- 5 failed attempts for the same username **and** the same address lock that pair for 15 minutes. The 6th attempt gets `429 Too many failed attempts. Try again in 15 minutes` (`1 minute` in the singular).
- While locked, even the correct password is refused. A successful sign-in clears the count. The browser login and the app login share the same counter.

**Sessions**
- The token is random and only its SHA-256 is stored, so a leaked database cannot be used to sign in.
- A session ends after **60 minutes idle** or **12 hours total**. "Last seen" is updated at most every 30 seconds.
- Every sign-in creates a new token and destroys the old cookie session (stops "session fixation").
- A bearer token (`Authorization: Bearer ...`) wins over the cookie. A bad bearer means "signed out", even if a good cookie is also sent.
- Changing a user's role, deactivating them, or resetting their password ends all their sessions.
- Changing your own password ends your *other* sessions and keeps the current one on a fresh token.

**CSRF protection**
- A browser request that changes data must carry `X-Requested-With: dhruvar`, otherwise `403 Missing request header`. Bearer-token requests and `POST /auth/token` are exempt.

**Admin rules**
- You cannot demote or deactivate yourself (`409`). There must always be an active admin (`409`, but through the API this can only happen in a race, because the caller is always an active admin).
- Usernames: 3–40 characters, letters, digits, dot, dash, underscore.
- Duplicate usernames are `409 That username is already taken`, in any letter case.

**Audit log**
- Every sign-in, user creation, user update, password change and password reset writes a hash-chained audit entry, with the **same wording** as Node (for example `menon signed in`, `singh: role planner -> reviewer`, `Password reset for singh`).

**Errors and headers**
- Every error body is `{"error": "message"}`.
- Unknown `/api/...` address: `401 Not signed in` if you are signed out, else `404 Not found` (as Express does). `/api/admin/...` needs the admin role first.
- Bad JSON: `400 Malformed JSON`. Anything unexpected: `500 Something went wrong on the server` (details go to the server log only).
- Every API answer carries `Cache-Control: no-store`.
- Validation messages for "create user" and "update user" match the Node server's library (zod 4.6), for example `username: Too small: expected string to have >=3 characters`.

---

## 5. Where Django deliberately differs from Node (and why)

| Topic | Node | Django | Why |
|---|---|---|---|
| Password hashing | scrypt | PBKDF2 (Django's built-in, reviewed) | Hashes cannot be converted between the two. Existing users need new passwords at cutover. |
| Failed-sign-in counter | in memory | database table `LoginThrottle` | Works with several server processes or a load balancer. Node's version forgets everything on restart. |
| Clearing old sessions | every 10 min by a timer | while people sign in (at most every 10 min per process) | Django has no built-in background timer. Expired sessions are rejected anyway; this only tidies the table. |
| `Cache-Control: no-store` on "Malformed JSON" | missing (error raised before the API router) | present | Django applies it to every API answer. Stricter, harmless. |
| JSON spacing | `{"ok":true}` | `{"ok": true}` | Only whitespace; every JSON reader treats them the same. |
| Request size limit (2 MB) | enforced | Django's default limit | To be revisited in phase 5 with the Nginx upload limit. |

---

## 6. How it was checked

**1. Automated tests — 84 pass** (14 from phase 0 + 70 new). Run:
```bash
cd DhruvAR-django/server-django
.venv/Scripts/python manage.py test
```
The tests use a fast password hasher so the suite takes about a second; production uses PBKDF2. They cover: sign-in
(cookie flags, audit entry, last-login time), wrong/unknown user look-alike, case-insensitive usernames, inactive users,
all body problems, lock-out (5 failures, right password refused while locked, per-address, shared with the app login,
expiry, singular "1 minute", reset on success, proxy address only when trusted), the app token (no cookie, no CSRF header,
bearer beats cookie, bad bearers), idle and absolute expiry, "last seen" refresh rule, logout, change password
(ends other sessions, keeps this one), unknown routes, the 500 path, the user lists, creating users (14 validation messages,
password rules, trimming, duplicates), updating (sessions ended on role change or deactivation, audit wording, self rules),
password reset, and that the audit chain still verifies.

**2. Side-by-side check against the real Node server.** I started a throwaway Node server (port 4100, its own temporary
database) and a throwaway Django server (port 4101, its own temporary database), gave both the same two users, and sent
the **same 55 requests** to each: every error case above plus the successful ones. Result: **identical status numbers,
JSON bodies and `Cache-Control` headers for every request**, except these explained differences:
- the "Malformed JSON" header noted in section 5;
- three lines that differ only because the two test databases hold different data (Node had 8 seeded users, Django 2,
  so e.g. "create khan" was a 409 on Node because `khan` already exists there);
- `/api/audit/verify`, which Django does not have yet (phase 2).

Both throwaway servers were stopped and used only temporary folders; the real `server/data/dhruvar.db` was not touched.

**Not checked:** the phone app and the browser dashboard have not yet been pointed at the Django server (that happens
after phase 4, when all routes exist). Real multi-process behaviour of the throttle table was not load-tested.

---

## 7. Known limits and notes for later phases

- **PostgreSQL:** no change needed for phase 1. `LoginThrottle` updates use `select_for_update`, which PostgreSQL honours and SQLite ignores.
- **The audit writer** (`api/audit.py`) is still the phase 0 version. Before PostgreSQL goes live it needs a row lock so two simultaneous writes cannot chain to the same previous entry (listed in `docs/prompts/demo-and-deployment/REAL-DATA-AND-POSTGRES.md`, Task D2).
- **Throttle rows** are never deleted (Node's in-memory entries also lived until restart). A small clean-up can be added with the other housekeeping.
- **Case-insensitive username lookups** use `iexact`; the database-level unique rule `Lower(username)` from phase 0 backs it up.
- String lengths: the Node validator (zod 4.6) counts code points, the same as Python, so there is no difference. (Corrected in phase 2; this note used to say UTF-16 units.)

---

## 8. How to try it yourself

```bash
cd DhruvAR-django/server-django
.venv/Scripts/python manage.py migrate                      # creates the tables (adds LoginThrottle)
.venv/Scripts/python manage.py createuser --username iyer --name "S. Iyer" --rank "Lt Col" --role admin
#   it asks for a password twice (nothing is shown while you type)
ALLOWED_HOSTS=localhost,127.0.0.1 .venv/Scripts/python manage.py runserver 4001
```
Then, in a second window (Git Bash), sign in the way the phone app does:
```bash
curl -s -X POST http://localhost:4001/api/auth/token -H "Content-Type: application/json" \
     -d '{"username":"iyer","password":"<your password>"}'
```
You get `{"token": "...", "idleMinutes": 60, "user": {...}}`. Use the token:
```bash
curl -s http://localhost:4001/api/auth/me -H "Authorization: Bearer <token>"
```
Port 4001 keeps it apart from the Node server on 4000. The Django data lives in `server-django/data/dhruvar-django.db`
(a different file from the Node database).

---

## 9. Words you may not know

- **Session:** the server's memory that "this person already signed in". The *token* is the secret ticket that proves it.
- **Cookie vs. bearer token:** two ways to carry the ticket. Browsers keep a cookie automatically; the phone app sends the token itself in a header.
- **CSRF:** a trick where another website makes your browser send a request using your cookie. The required header stops it, because other sites cannot add it.
- **Hash:** a one-way scramble. The server stores the scramble of the password, never the password.
- **Middleware:** code that runs around every request before and after the real work.
- **Migration:** a recipe Django follows to create or change database tables.
- **Throttle / lock-out:** slowing down guessing by refusing after repeated failures.

## 10. Next: phase 2 (not started)

Layout checking (`layout.ts`), then the plan routes (create, new version, withdraw, start review, decision, comments,
list) and the audit list and verify route. This is the core of the application and the biggest phase.
