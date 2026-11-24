# Users, roles and accounts in DhruvAR: how it works today, and the plan

Written 2026-11-24 (session F). **Sections 0-9 were written before anything was built and describe the code as it was then**
(the plan); the owner then decided (section 10) and Phases 1-4 were built the same day: **section 11 says what is now true**, and
`docs/USERS-HOWTO.md` is the practical guide. Everything below about the
code was read from the files named (file:line). What I could not check says "not verified". No password, token or `.env` content
is printed here. Read for a beginner: every new word is explained once.

## 0. Words used in this document

| Word | Plain meaning |
|---|---|
| **Role** | A label on a user (`planner`, `reviewer`, `admin`, `auditor`) that decides what the server lets them do. |
| **Hash** | A one-way scramble of a text. You can check "is this the same text?" but cannot turn it back. |
| **PBKDF2** | The scrambling method Django uses for passwords. It is deliberately slow, so guessing is slow. |
| **Session / token** | After sign-in the server gives the browser or phone a long random "ticket" (token). Showing the ticket proves who you are, until it expires. |
| **SHA-256** | A fast one-way scramble. Used for the ticket: the database keeps only its scramble, so a stolen database cannot be used to sign in. |
| **Migration** | A small Python file that creates or changes database tables. `migrate` runs them. |
| **Superuser** | Django's word for an all-powerful account. Made by `createsuperuser`, used for Django's own `/admin/` website. |
| **Forced password change** | After first sign-in the person *must* choose a new password before doing anything else. |
| **Deactivate** | Switch an account off (`active = false`) without deleting it. |

## 1. How users get made today

There are exactly **three** ways. All end in the table `api_user` with a hashed password.

```
 (a) command line, inside Docker          (b) dashboard "Users" page            (c) demo data
 manage.py createuser                     POST /api/admin/users                 manage.py seed
 api/management/commands/createuser.py    api/views_users.py:49 (_create_user)  api/management/commands/seed.py
        \                                        |                                     /
         '---------------->  password_error() checks  ----> hash_password() ---------'
                                     api/rules.py:81          api/auth.py:43
                                                |
                                         table api_user (+ audit line "User created")
```

**(a) `createuser`** (`createuser.py:23-55`). Needed once to make the *first* admin, because the API route needs an admin already
(its own docstring, line 1-2). It takes `--username --name --rank --role`. The password is asked twice without echo
(`getpass`, lines 41-45) or read from the environment variable `DHRUVAR_PASSWORD` (for scripts). It applies the same password rules
as the API (line 46) and never prints the password. It **refuses an existing username** (line 38), so it cannot be used to reset a password.
In Docker: `docker compose exec django python manage.py createuser --username ... --name "..." --rank "..." --role admin`
(from `docs/STACK.md` section 3). Audit line "User created" is written with *no actor* (`audit(None, ...)`, line 54).

**(b) `POST /api/admin/users`** (`views_users.py:42-84`). Only role `admin` may call it (`roles=("admin",)`, line 42; enforced in
`http.py:225`). The body holds username, name, rank, role and a password the admin chose. The server checks the fields (username 3-40
characters, letters/digits/dot/dash/underscore; name 1-80; rank 1-40; role one of four), checks the password rules, refuses a duplicate
username in any letter case (line 66 and the database rule `uniq_username_ci`, `models.py:63`), then saves the user and an audit line
`User created` with the admin as actor (line 81), in one transaction. The new user gets `active = true`.

**The dashboard Users page** (`web/src/pages/Users.tsx`, shown only to admins: `App.tsx:46` `RoleGate roles={['admin']}`,
menu link `Layout.tsx:43`) is a form over that route. It has a "Generate" button that makes a random password in the browser
(`Users.tsx:20-23`: 12 random bytes, plus `-Aa1` so the rules pass). After creating, the page shows the password **once on screen**
(line 61) for the admin to hand over. The same page can change a role (lines 91-93), disable/enable (98-100) and reset a password (101-111,
a generated one, shown once, line 107). It cannot edit name or rank, although the server route allows it (`views_users.py:92-93`).

**(c) `seed`** (`seed.py`). Creates 8 fictional users and 12 fictional plans. Passwords are random and written to
`/data/seed-credentials.txt` inside the Django container's volume (lines 52-57, 113-116, file mode 0600), not printed. It refuses to run if users
exist; `seed --reset` deletes **everything** (line 34-49) including any real users.

**What happens to the password.** Typed or generated -> sent to the server -> `password_error()` -> `make_password()`
(Django PBKDF2, `auth.py:43-44`, `settings.py:124` allows only that hasher) -> stored as a text starting `pbkdf2_sha256$<iterations>$<salt>$<hash>`
in column `api_user.password`. The plain password is never stored and never logged by the code. Two caveats: the dashboard shows the
one-time password on screen, and today the stack is plain `http`, so the password crosses the network unencrypted (see section 6, HTTPS).

**Who can create users:** only an admin (route) or whoever can run a command inside the Docker container (that is the PC's owner).

## 2. Roles and who can create users after going live

### 2.1 What each role may do (read from `rules.py`, `views_plans.py`, `views_users.py`, `views_attachments.py`)

| Action | planner | reviewer | admin | auditor |
|---|---|---|---|---|
| See plans | **own plans only** (`rules.py:34-35`) | all | all | all |
| Create a plan / submit a new version | yes (`rules.py:38`, `:54`, own plan, only after changes requested or rejected) | no | no | no |
| Withdraw a plan | own, while open (`rules.py:50`) | no | no | no |
| Start review | no | yes, not own plan (`rules.py:46`) | no | no |
| Approve / reject / request changes | no | yes, not own plan, reason >= 10 characters for reject/changes (`rules.py:42`, `:75`) | no | no |
| Comment | own plans (`rules.py:58`) | yes | yes | **no** |
| Upload field images | own plan | yes | no | no (`rules.py:62-68`) |
| Remove an image | the uploader | the uploader | **any** (`rules.py:71`) | the uploader (but cannot upload, so none) |
| See audit log | only lines about own plans (`views_plans.py:308-312`) | all | all | all |
| Check audit chain (`/api/audit/verify`) | no | no | yes | yes (`views_plans.py:323`) |
| List all users with usernames, last sign-in | no | no | yes (`views_users.py:42`) | no |
| Names list (name, rank, role, active; no username) | yes | yes | yes | yes (`views_users.py:22-26`) |
| Create user / change role / disable / reset password | no | no | yes | no |
| Change own password | yes | yes | yes | yes (`views_auth.py:89`) |

Guards inside user management (`views_users.py:100-109`): you cannot demote or disable **yourself**; you cannot demote or disable the **last active admin**.

### 2.2 Is there a "super admin"?

**No.** Verified by reading the code:
- `models.py:15` has exactly four roles, and the database enforces them (`user_role_valid`, `models.py:64`).
- `INSTALLED_APPS` is only `django.contrib.auth`, `contenttypes`, `api` (`settings.py:69-73`): **no `django.contrib.admin`**, no sessions app.
- `config/urls.py` has only `api/`: **no `/admin/` website**.
- No `is_superuser`/`is_staff` field (the `User` class extends `AbstractBaseUser`, not `AbstractUser`, `models.py:43`), and a search for `createsuperuser`
  and `/admin/` in `server-django` finds only the API's own `/api/admin/...` routes and tests.

So today **every admin is equal**: any admin can create more admins, promote anyone to admin, demote or disable other admins (not the last one) and reset any password,
including another admin's. One stolen or careless admin account can therefore take over everything. That is the weakness the "super admin" idea would fix.

### 2.3 "Django stuff", explained

- **Django's admin site** (`/admin/`) is a ready-made web page Django generates to edit any table by hand. **`createsuperuser`** is the command that makes the first account allowed into it.
- **Why we do not use them:** (1) DhruvAR copied the older Node server's behaviour exactly (own roles, own JSON errors, own session cookie), so Django's built-in login/session/CSRF are
  deliberately switched off (`settings.py:75-82`). (2) Django's admin lets a user edit any row directly, including plans, decisions and audit lines, **without** writing the DhruvAR audit entry
  and without the plan state rules. That would break the "everything is audited, tamper-evident" promise. (3) A second, bigger login page is a second thing to attack.
- **What we do instead:** the dashboard Users page for day-to-day work, plus small management commands (`createuser` now; `resetpassword` and `listusers` proposed) for the person who controls the server.
  Those commands go through the same password rules and write audit lines.

### 2.4 Recommendation: add a "super admin"?

**Yes, but as a small rule, not a fifth screen.** Proposal: a new role value `superadmin` (or a flag `is_owner` on `User`; role is simpler because the dashboard, rules and database check already revolve around `role`).

| | admin | super admin |
|---|---|---|
| Create / disable / reset planners, reviewers, auditors | yes | yes |
| Create another admin or super admin, promote to admin, demote/disable/reset an admin | **no** | **yes** |
| Everything an admin may do on plans | same | same |
| How created | dashboard (by super admin) | **only** the command line inside the container |
| How many | any | 1 to 2, named people |

Cost: one migration (role list and the database check rule), `rules.py`, `views_users.py`, `web/src/types.ts`, `Users.tsx`, `rules.ts`, tests. If you want fewer changes, a cheaper variant that keeps four roles:
**only admins who were made with the command line may create admins** — but that cannot be seen later in the database, so the role value is cleaner. This is decision D1.

## 3. Where is everything stored?

Short answer: **everything about users and plans is in PostgreSQL; only the image files and some secrets/config are outside it.**
Checked in the real database with read-only `\dt` and `\d` (no data columns with secrets were printed).

```
 PostgreSQL 16 (container dhruvar-postgres-1, volume dhruvar_pgdata, database "dhruvar")
 |
 |-- api_user          id, username, name, rank, role, active, created_at, last_login_at,
 |                     password = PBKDF2 hash only ("pbkdf2_sha256$...")
 |-- api_authsession   token_hash (SHA-256 of the ticket; the ticket itself is never stored),
 |                     user_id -> api_user, created_at, last_seen      (expiry is computed from these)
 |-- api_loginthrottle key "username|ip", count (wrong tries), locked_until   (lock-out counters)
 |-- api_plan          id, code TP-YYYY-NNNN, name, owner_id -> api_user, status, created_at, updated_at
 |-- api_planversion   plan_id, version, layout_json (the whole plan as text), content_hash, note, submitted_by_id
 |-- api_decision      plan_id, version, reviewer_id, decision, reason, decided_at
 |-- api_comment       plan_id, version, author_id, text, created_at
 |-- api_attachment    plan_id, version, file_name, stored_name, mime, size, caption, uploaded_by_id   (only the *description* of an image)
 |-- api_auditentry    seq, at, actor_id (plain text), action, plan_id, detail, prev_hash, hash   (hash chain)
 |-- auth_group, auth_group_permissions, auth_permission, django_content_type, django_migrations   (Django internals; unused by the app logic)

 Docker volume dhruvar_djangodata  (mounted at /data in the Django container)
 |-- uploads/<uuid>.png|jpg|webp   the image files themselves (NOT in the database; the row in api_attachment points to them by stored_name)
 |-- seed-credentials.txt          plain-text passwords of the DEMO users, written by `seed` (delete once not needed)

 deploy/.env (on the PC, Git-ignored)  database password, Django secret key, LAN_HOST
 deploy/admin-credentials.txt          the stackadmin password (Git-ignored via deploy/*credentials*.txt)
```

| Kind of data | Table.column | Notes |
|---|---|---|
| User record | `api_user`: `username`, `name`, `rank`, `role`, `active` | role limited by a database rule; username unique ignoring case |
| Password | `api_user.password` | PBKDF2 hash only (`auth.py:43`) |
| Session ticket | `api_authsession.token_hash` | only SHA-256 (`auth.py:116-118`) |
| Lock-out counters | `api_loginthrottle.count`, `.locked_until` | in the database so several server processes agree (`models.py:84-93`) |
| Plans and versions | `api_plan`, `api_planversion.layout_json` | plan shape kept as text |
| Comments, decisions | `api_comment`, `api_decision` | each points to its user |
| Audit log | `api_auditentry` | each line stores the hash of the one before (`audit.py:46-61`); `actor_id` is text, not a link, so lines survive any user change |
| Images | files in volume `/data/uploads`; description in `api_attachment` | **Verified:** files are not in the database. A backup must copy both (the stack's `backup-stack.sh` does) |
| Secrets/config | `deploy/.env`, environment variables | not in any database |

Live check on 2026-11-24 (counts only): 9 users (2 admins, 2 reviewers, 4 planners, 1 auditor, all active), 2 session rows. The uploads folder was empty at that moment.
Not verified: row counts of the other tables, and the exact PBKDF2 iteration count (Django 5.2's default; read it from one hash header if you want to confirm, without printing the hash).

## 4. What are rank and name used for?

- **Stored** as free text in `api_user.name` (max 80) and `.rank` (max 40), `models.py:47-48`. The server checks only length (1-80 / 1-40) when creating (`views_users.py:54-55`).
- **Shown**: the dashboard shows "rank name" wherever a person appears (`storeContext.ts:43` `fullName`; `Users.tsx:88`) - plan owner, reviewer, commenter, decision lines.
  The names list `/api/users` sends name, rank, role, active to every signed-in user so the pages can print who wrote or decided something (`views_users.py:22-26`).
  The phone app sends only the sign-in and reads the user object back (`ServerClient.kt:61-65`). Where the app prints the rank: not verified.
- **Not in audit lines or plan files as a stored copy.** The audit line stores the user's **id** and, in some texts, the **username** (`"User created"`, `"Signed in"` lines). The name and rank are looked up
  **today** from `api_user`. So if someone is promoted from Major to Lt Col, old plans and old audit lines will show the new rank. That is a side effect to be aware of.
- **Free text today.** The live data already shows the effect: ranks "Admin", "Captain", "Lieutenant", "Lt Col" are mixed, so "Lieutenant Colonel" could also appear in many spellings.
- **Recommendation:** use a **fixed list** (a dropdown) for rank, stored as text so old rows still work. Reasons: consistent display, no typos, easy sorting by seniority later. Keep **name** free text. Start list (owner to confirm; Army officer ranks):
  Lieutenant, Captain, Major, Lieutenant Colonel, Colonel, Brigadier, Major General, Lieutenant General, General; add JCO/other ranks (Subedar, Naib Subedar...) only if non-officers will use the system.
  Implementation: list in one place on the server (checked in `_create_user` and `admin_user`) and the same list in `Users.tsx`; no database change needed. Also add name/rank editing to the Users page.

## 5. Passwords and accounts, end to end

| Step | What the code does today | File:line |
|---|---|---|
| First sign-in | Dashboard: `POST /api/auth/login` (cookie). Phone: `POST /api/auth/token` (token in the reply). Same user table, same checks, same lock-out. | `views_auth.py:40-69` |
| Forced password change | **Does not exist.** There is no "must change" flag on `User`; an admin-set or generated password works forever. The dashboard text only *asks* people to change it (`Users.tsx:143`). | `models.py:43-58` |
| Changing your own password | Dashboard page "My account" (`Account.tsx`). Needs the current password; new password must pass the rules; **all** the user's sessions are ended and a new one starts. The phone app has no change-password screen (not found in `ServerClient.kt`). | `views_auth.py:89-108` |
| Password rules | At least 10 characters, at most 200, upper case + lower case + digit, must not contain the username (if the username has 3+ letters). No check against common passwords, no history, no expiry. | `rules.py:81-90` |
| Reset by admin | Dashboard "Reset password" -> `POST /api/admin/users/<id>/password`; ends that user's sessions; audit line "Password reset". Does not force a change at next sign-in. | `views_users.py:129-144` |
| Lock-out | 5 wrong tries per **username + IP** locks that pair for 15 minutes (reply 429). Settings `LOGIN_MAX_FAILURES`, `LOGIN_LOCK_MINUTES`. Unknown usernames cost the same time as wrong passwords, and the reply never says which was wrong. | `auth.py:78-106`, `settings.py:38-39` |
| Session expiry | Idle 60 minutes, absolute 12 hours (settings `SESSION_IDLE_MINUTES`, `SESSION_ABSOLUTE_HOURS`). Expired tickets are refused and cleaned up. The cookie is `HttpOnly`, `SameSite=Strict`. | `auth.py:133-173`, `settings.py:34-35` |
| Deactivation | Admin sets `active=false`: sign-in refused (`auth.py:101`), existing tickets refused (`auth.py:166`) and deleted (`views_users.py:124-125`). Audit line "User updated". | `views_users.py:87-126` |
| Deletion | **No route and no command deletes a user.** Also the database forbids it for anyone with work: plans, versions, decisions, comments and images all point to the user with `on_delete=PROTECT` (`models.py:100,115,127,137,151`). | |
| Why deactivate, not delete | Approvals and comments must keep a real author for as long as the record exists, and the audit chain must still read correctly. Deactivation keeps all history and still blocks the person at once. | |
| When someone leaves | Plans stay with their name and rank; decisions and comments stay; audit lines stay. Their open plans stay open until someone acts: **a planner's plan cannot be re-assigned** (owner cannot be changed; no route). Process fix: withdraw or finish their plans before deactivating, or accept that a deactivated owner's rejected plan can only be re-submitted by creating a new plan. | `rules.py:50-55` |
| Same accounts for app and dashboard | One table `api_user`. Two doors: cookie (`/auth/login`) and bearer token (`/auth/token`), both stored in the same `api_authsession` table and expired by the same rules. The phone keeps its token encrypted (`SessionStore.kt:17-20`). The same lock-out counters apply to both. | |

Gotchas found: (1) `change_password` ends **every** session, including a phone's, and gives a cookie only; the phone would need to sign in again. (2) The "last active admin" and "cannot demote yourself" checks
(`views_users.py:106-109`) read then write without a database lock, so two admins demoting each other at the same instant could in theory leave none (not tested; low risk).
(3) `createuser` writes the "User created" audit line with no actor, so the line shows an empty actor in the dashboard.

## 6. Gaps for a real deployment

| Item | State | Evidence / comment | Needed for |
|---|---|---|---|
| Create one user | **exists** | dashboard, API, command | - |
| Bulk creation (CSV) | **missing** | no import command | before real use (a unit of dozens of officers) |
| One-command create/reset inside Docker | **half**: create exists; **reset missing** (`createuser` refuses existing names) | `createuser.py:38` | demo (nice), real use (needed) |
| Forced change of temporary password | **missing** | no flag in `models.py` | before real use |
| Password expiry (e.g. every 90 days) | **missing** | none | later (check the unit's policy) |
| Common-password / reuse check | **missing** | `rules.py:81-90` only length/class | later |
| 2-factor sign-in | **missing** | none | later (not needed for the demo) |
| Who sees whose plans (unit/formation) | **partly**: planners see **only their own** plans and own audit lines; reviewers, admins and auditors see **every** plan | `rules.py:34-35`, `views_plans.py:86,308-312` | single-unit demo: fine. Several units: **missing** (no `unit` field anywhere) |
| Recover if the only admin forgets the password | **workaround only**: create a second admin with `createuser`, then reset the first in the dashboard; or `seed --reset` (destroys data - never). No reset command. | `createuser.py`, `docs/STACK.md` section 3 | before real use |
| Audit of user changes | **exists**: "User created", "User updated" (role change, activated/deactivated), "Password reset", "Password changed", "Signed in" | `views_users.py:81,122,142`, `views_auth.py:54,68,103` | - |
| Audit of name/rank edits, failed sign-ins, lock-outs | **missing** (name/rank changes are saved but not described in the line; failed attempts only counted) | `views_users.py:117-122` | later |
| Session list / revoke by an admin | **missing** as a screen; sessions are ended automatically on role change, disable, password reset and password change | `views_users.py:124-125,143` | later |
| Edit name/rank in the dashboard | **missing in UI** (server allows it) | `Users.tsx` | demo (nice) |
| HTTPS | **missing**: stack serves `http`; `COOKIE_SECURE=false` | `docs/STACK.md` section 4 | before real use: passwords and tickets cross the network readable on `http` |
| Database/volume backup | **exists** (manual script) | `deploy/backup-stack.sh` | scheduled backups still missing |
| `ALLOWED_HOSTS` really enforced | **not enforced** (known, from the stack notes) | `docs/STACK.md` section 9.1 | before real use |

## 7. The real-users procedure (safe, step by step)

Rules first: the repository (Git/GitHub) must hold **no real name, rank, password, `.env`, seed-credentials file or database dump**.
Already ignored: `deploy/.env`, `deploy/*credentials*.txt`, `web/dist/`, `server-django/data/`, `server-django/backups/`, `server-django/.env*`.
**Not ignored (gap):** `*.dump`/`*.tar.gz` backups made outside `server-django/backups`, any CSV of officers, and `docs/` files where someone pastes names. Add patterns (see plan step B1) and never put a user list in `docs/`.

1. **Make a clean database** (only on a PC with no data worth keeping): `docker compose down -v` then `docker compose up -d --build` (a **new, empty** database; do not run `seed`). If the demo data must stay, do **not** do this; instead deactivate every demo account in step 6.
2. **First admin** (you, the owner), on the PC, in PowerShell from the `deploy` folder:
   `docker compose exec django python manage.py createuser --username <yourname> --name "<Your Name>" --rank "<Rank>" --role admin`
   Type the password twice at the prompt (nothing is shown, nothing is printed, nothing goes in chat or a file). Choose a long password you will remember or store it in a password manager. If the super-admin change is built (D1), this account is the super admin.
3. **Sign in** at `http://localhost:8000` and change nothing yet. Open **My account** and, if you wish, change the password to a fresh one only you know.
4. **Add the officers** one by one on the Users page (or with the later CSV command): username (for example `v.singh`), name, rank, role. Use the **Generate** button for the temporary password.
5. **Hand over the temporary password** outside the system, one person at a time: tell it face to face or read it on the phone, or give a sealed paper slip. Do not put it in WhatsApp groups, e-mail, chat, Git or a shared document. The page shows it **once**; if lost, press **Reset password** and hand over the new one.
6. **Require a change.** Today there is no forced change, so instruct each person to open **My account** and change it at first sign-in, and check on the Users page that "Last sign-in" is filled. After plan step B2 is built the server will force it.
7. **Retire the demo users.** Disable the fictional accounts (Users page) rather than deleting; or start from a clean database (step 1). Delete the plain-text demo passwords: `docker compose exec django rm /data/seed-credentials.txt` (only if it exists), and delete the file `deploy/admin-credentials.txt` after the `stackadmin` password has been replaced or the account disabled.
8. **Backups** contain the user table (hashes) and real plans: store them on an encrypted or access-controlled disk, never in Git/GitHub/cloud chat.

**Must NOT go to GitHub:** real names, ranks and usernames of officers; any password (temporary or real); `deploy/.env`; `seed-credentials.txt`; `admin-credentials.txt`; database dumps and `uploads` archives; screenshots showing real names; real plans or field images.
Also keep real names out of chat sessions with any AI tool or third-party service.

## 8. Plan of action

Times are for one person who knows the code, including tests; double them for a first-time contributor. "Owner decides" points to section 9.

### A. Needed for the demo

| # | What changes | Files | How to test | Time | Owner decides |
|---|---|---|---|---|---|
| A1 | **Users page check.** Add name and rank editing; show the one-time password in a copy box with a "Hide" button; show message about forced change once B2 exists. Fix `createuser`'s empty actor in audit if wanted. | `web/src/pages/Users.tsx` (and CSS) | Run the dashboard, sign in as admin, create/disable/reset/edit; try as planner (the page must be refused); `npm run build` | 2-3 h | - |
| A2 | **One-command user tools in Docker.** New `resetpassword` command (reads `DHRUVAR_PASSWORD` or asks twice; writes audit "Password reset by command"; ends sessions) and `listusers` (username, role, active; never hashes). Add the lines to `docs/STACK.md`. | `server-django/api/management/commands/resetpassword.py`, `listusers.py`, `docs/STACK.md` | `docker compose exec django python manage.py resetpassword --username x`; sign in with new password; old tickets refused; Django tests | 2 h | - |
| A3 | **Tests** for the above plus a "who may do what" test table (planner/reviewer/admin/auditor against every route in section 2.1) so the table stays true. | `server-django/api/tests/test_users.py`, `test_commands.py` (new) | `docker compose exec django python manage.py test` | 3 h | - |
| A4 | **Docs.** A short `docs/USERS-HOWTO.md`: how to create the first admin, add users, reset, retire. Add this plan's decisions. | `docs/USERS-HOWTO.md` | A beginner follows it on a clean stack | 2 h | - |

### B. Needed before real use

| # | What changes | Files | How to test | Time | Owner decides |
|---|---|---|---|---|---|
| B1 | **Git safety.** Ignore dumps, archives, CSVs of users; add a pre-commit check that fails on `*.dump`, `*credentials*`, `.env`. | `.gitignore`, a small `scripts/` check | Try to `git add` a dummy dump; `git status` must not list it | 1 h | - |
| B2 | **Forced first-login password change.** New column `must_change_password` (boolean) on `api_user` (one migration). Set true by admin create, admin reset, `createuser`, `resetpassword`. Server: while true, every route except `auth/me`, `auth/change-password`, `auth/logout` answers 403 `"Password change required"`; `/auth/login` and `/auth/token` replies carry `mustChangePassword`; change-password clears it. Dashboard: on that flag, show only the Change password page. Android app: show a change-password screen (the app has none today). | `models.py`, `migrations/0003_...`, `auth.py`, `http.py` (the check lives in the endpoint decorator), `views_auth.py`, `views_users.py`, `createuser.py`, web `Account.tsx`/`App.tsx`/`types.ts`, app `ServerClient.kt` + one screen | New Django tests (flag set, blocked routes, cleared after change); manual dashboard run; phone run (USB) | 1 day server+web, +0.5-1 day Android | D3 (does it apply to the app on day one?) |
| B3 | **Super admin** (if D1 = yes). Role `superadmin`: only it may create/promote/demote/disable/reset admins and super admins; at least one active super admin must remain; created only by `createuser --role superadmin`. | `models.py` (role list, check rule), migration, `rules.py`, `views_users.py`, `web/src/types.ts`, `Users.tsx`, `rules.ts`, tests | Tests: admin cannot create/promote/reset an admin; super admin can; last super admin protected; dashboard shows the extra role only to super admins | 1 day | D1 |
| B4 | **Fixed rank list** (if D2 = yes) with dropdown, server check, list in one file. Existing free-text ranks are tolerated until edited. | `views_users.py`, new `ranks.py`, `Users.tsx` | Tests: unknown rank rejected; dashboard dropdown | 3-4 h | D2 |
| B5 | **Bulk create from CSV.** `manage.py importusers file.csv` (columns username,name,rank,role); each user gets a random temporary password written to **one output file with 0600 mode** (outside the repo, deleted after handing over); `must_change_password` set; one audit line per user; whole file all-or-nothing. | `management/commands/importusers.py`, docs | A 10-line test CSV on a scratch database; a CSV with one bad line creates nobody | 4-5 h | D5 |
| B6 | **HTTPS** in front of Nginx and `COOKIE_SECURE=true`; enforce `ALLOWED_HOSTS` (known gap). | `deploy/nginx/*`, `docker-compose.yml`, `middleware.py` | Browser shows the lock; cookie has `Secure`; wrong `Host` refused | 1-2 days (certificate source depends on the unit) | D6 |
| B7 | **Recovery drill** and runbook: lost admin password via `resetpassword`; lost PC disk via backup; documented and rehearsed once. | `docs/USERS-HOWTO.md`, `deploy/backup-stack.sh` | Do the drill on a scratch stack | 3 h | - |
| B8 | **More audit detail**: record name/rank edits, failed sign-ins and lock-outs (counts per user, not passwords). | `views_users.py`, `views_auth.py`, `auth.py` | Tests read the new audit lines | 3 h | - |

### C. Later

| # | What | Note |
|---|---|---|
| C1 | Unit/formation field and "who sees whose plans" rules (if D4 says several units) | Large: touches `rules.py`, every plan route, dashboard filters, tests; design first. |
| C2 | Password expiry, reuse and common-password checks | Match the unit's IT policy. |
| C3 | Two-factor sign-in (for example one-time codes) | Needs a decision on the device/app used. |
| C4 | Admin screen to list and revoke sessions; sign-in history | Table `api_authsession` already holds the data. |
| C5 | Owner re-assignment of plans when someone leaves; "retire user" checklist | Small server route + audit line. |
| C6 | Scheduled backups; Argon2 hashing (`settings.py:123` note) | After B6. |

Suggested order: A1-A4 -> B1 -> B2 -> B3 -> B4 -> B5 -> B7 -> B6 -> B8.

## 9. Decisions the owner must make (answer in one message, for example "D1 yes, D2 yes, D3 server+web first, D4 one unit, D5 yes, D6 later")

| # | Decision | My recommendation |
|---|---|---|
| D1 | Do we want a **super admin** (only it can create or change admins)? | **Yes**, one or two named people, created only by command line. |
| D2 | **Fixed rank list** (dropdown) or free text? | **Fixed list** for officer ranks; add other ranks only if non-officers will use it. Send me the exact list. |
| D3 | Forced password change: build it for the server and dashboard first and the phone app second, or all together? | Server + dashboard first (B2); app screen right after; until then people change it on the dashboard. |
| D4 | **One unit or several?** Should a reviewer see only their own unit's plans? | One unit for now (today's behaviour); several units is a later project (C1). |
| D5 | Do we need **CSV bulk creation** for the first load? | Yes if more than about 15 users; otherwise the Users page is enough. |
| D6 | HTTPS before the first real officer signs in? | Yes: without it, passwords cross the network readable. Ask who issues the certificate. |
| D7 | Who are the **first admin and the backup admin** (two named people)? | Two people, so a lost password never locks everyone out. |
| D8 | Demo accounts after go-live: delete the database and start clean, or just disable them? | Start clean on the real PC; keep demo data only on a separate demo PC. |

## 10. Final decisions (owner's answers, 2026-11-24)

Context: the internship ends this week. The application and its documentation will be shown **only on the owner's own PC** (dashboard at `http://localhost:8000`) and one connected Google Pixel phone (USB, `adb reverse`). The data is **fictional**; no real officers are loaded.

| # | Decision | Final answer | Effect on the plan |
|---|---|---|---|
| D1 | Super admin | **Yes** (recommendation accepted) | Build B3 if time allows; otherwise document it as the next step. |
| D2 | Rank | **Dropdown**, list below, no arm-specific names | B4 now. |
| D2b | Appointment | New **optional free-text** field, **up to 50 characters** (CO, OC, Adjt ...); **never used for permissions** | New column `appointment` (migration), Users page, display next to the name. |
| D3 | Forced password change | **Server + dashboard first**, phone-app screen after | B2 server+dashboard; the app screen is written up as future work. |
| D4 | One unit | Yes | C1 stays "later". |
| D5 | CSV bulk creation | Not needed for the demo | B5 dropped to "later". |
| D6 | HTTPS | **Not needed**: only this PC (`localhost`) and a USB-connected phone (`adb reverse` makes the phone talk to `localhost` too, so traffic never leaves the cable). Required before any other device or network is used. | B6 moves to "later"; keep as a documented limit. |
| D7 | First and backup admin | Fictional demo only. Two named people when real use starts. | - |
| D8 | Demo vs real | **No real users.** Demo stays on this PC with fictional data; nothing is wiped. | Remove the "start clean" step for now; the section 7 procedure is kept for later. |

**Final rank list (dropdown)**
- Officers: Lieutenant, Captain, Major, Lieutenant Colonel, Colonel, Brigadier, Major General, Lieutenant General, General.
- JCOs: Naib Subedar, Subedar, Subedar Major.
- Other Ranks: Sepoy, Lance Naik, Naik, Havildar.
- Not included (can be added in one line each): Field Marshal, Honorary Lieutenant/Captain, arm-specific names (Sowar, Dafadar, Risaldar, Gunner, Sapper ...), Company/Regimental Havildar Major (these are appointments, so they go in the appointment box).
- The dashboard groups the dropdown as Officers / JCOs / Other Ranks. The database stores the rank as plain text, so existing rows (for example "Lt Col") keep working until edited.

**What "permission" means here.** A *permission* is something the server allows or refuses an account to do: for example approve a plan, create a user, or see the audit log. Today that is decided **only by the role** (`planner`, `reviewer`, `admin`, `auditor`; table in section 2.1). Rank and appointment are only **labels printed next to the name**. So an account with appointment "CO" gets no extra rights; a Major with role `planner` stays a planner and a Captain with role `reviewer` may approve. If a CO should be able to approve, we give that account the `reviewer` role. Reason: postings change often; if rights followed the appointment, every posting would need a code change or a risky rule, and a mistyped appointment could silently grant power.

**Work order for this week (smallest useful set first)**

| Order | Step | Est. |
|---|---|---|
| 1 | Rank dropdown + optional appointment (50 characters): migration, server check, Users page (create and edit), show "rank name, appointment" in the dashboard | 4-5 h |
| 2 | `resetpassword` and `listusers` commands, with tests | 2-3 h |
| 3 | Users page fixes (edit name/rank/appointment, copy box) | 2 h |
| 4 | Forced password change, server + dashboard (B2) | about 1 day |
| 5 | Super admin (B3) | about 1 day |
| 6 | `USERS-HOWTO.md` and this document updated to what was really built; list what was *not* built (phone change-password screen, HTTPS, CSV import, units, expiry, 2-factor) as "future work" | 2-3 h |

Steps 1, 2, 3 and 6 are the minimum for a clean submission. Steps 4 and 5 only if time remains; whatever is not built is documented as future work with this plan.

## 11. What was built (2026-11-24), against the plan in section 8

Not committed at the time of writing. All server tests pass (253 run, 7 skipped, in a throw-away container); each phase was also deployed to the live stack
and clicked through in the browser pane, except where "not verified" is written.

| Plan step | Status | What and where |
|---|---|---|
| A1 Users page | **done** | `web/src/pages/Users.tsx`: rank dropdown (grouped), appointment box, editable name/rank/appointment, "Must change password" note, locked rows for plain admins. |
| B4 + D2 Rank list, appointment | **done** | `server-django/api/ranks.py` (list and `GET /api/ranks`), column `api_user.appointment` (migration 0003, up to 50 characters, optional, never affects permissions). A new account must use a listed rank; an older free-text rank stays valid until edited. |
| A2 One-command tools | **done** | `listusers`, `resetpassword`, `setrole`; `createuser` takes `--appointment` and checks the rank (`server-django/api/management/commands/`). |
| B2 Forced password change | **done for server + dashboard** | Column `api_user.must_change_password` (migration 0004); set by admin create/reset and by `createuser`/`resetpassword`; `auth.forced_change_guard` (called from `middleware.py`) answers 403 "Password change required" to everything except me/change-password/logout/health; dashboard shows only the "Choose your own password" screen (`Account.tsx`, `App.tsx`). The new password must differ from the old. **Phone app screen: not built.** |
| B3 + D1 Super admin | **done** | Role `superadmin` (migration 0005, role check rule updated). Only it may create/edit/disable/reset admins (`views_users.py`); made only by `createuser --role superadmin` or `setrole`; last active admin and last active super admin protected; nobody changes their own role. `stackadmin` was promoted on 2026-11-24. |
| A3 Tests | **done** | `test_commands.py`, `test_forced_change.py`, `test_superadmin.py`, and additions in `test_users.py`. |
| A4 Docs | **done** | `docs/USERS-HOWTO.md`, `docs/STACK.md` commands, this section. |
| B1 Git safety patterns | **not done** | `.gitignore` still lacks `*.dump`, archives and CSVs of users. |
| B5 CSV import, B6 HTTPS and `ALLOWED_HOSTS`, B7 recovery drill, B8 more audit detail, all of C | **not done** | Future work (listed in `docs/USERS-HOWTO.md` section 8). HTTPS is not needed while only this PC and the USB-connected phone are used (decision D6). |

Differences from section 2 and 5 (the table of roles is otherwise unchanged; `superadmin` has all admin rights plus administrator management):
- Section 2.2 ("there is no super admin") is no longer true. Section 5 "Forced password change: does not exist" is no longer true.
- The `createuser` audit line and the new commands' audit lines have an empty actor (no signed-in person), as before.
- Rank on audit lines and old plans still shows the **current** rank and appointment (they are looked up live, not frozen).

Not verified: the phone app signing in as a flagged user or as a super admin (only the same API calls were tested); the Users page clicked through as a
super admin (the server rules are covered by tests and were checked live by API); the exact PBKDF2 iteration count.
