# Session F: users, roles and accounts. Explain everything, then give a detailed plan (NO code changes)

Paste `00-SHARED-CONTEXT.md` first if you have it. Read-only session: you may read any file; you change nothing except
writing one document, `docs/USERS-AND-ROLES-PLAN.md`, in the folder `C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration`
(branch `integration`; do not commit or push).

## Who you are explaining to
The owner is an intern and a beginner with Django, PostgreSQL and security. Use plain words, short sentences, small tables and
pictures made of text. Define every term the first time (hash, role, session, migration, superuser...). The owner must be able to
read the answer in the chat; the document repeats it for later.

## The owner's questions (answer all, in this order)
1. **How do users get made today?** Walk through the real code and commands (Django `createuser` and `seed` commands, the admin
   routes `POST /api/admin/users`, the dashboard Users page `web/src/pages/Users.tsx`). Who can create users, and what happens to the
   password.
2. **Who can create users after we go live?** Explain the roles that exist now (`planner`, `reviewer`, `admin`, `auditor`; see
   `server-django/api/models.py`, `api/rules.py`, and the permission checks in the views) and what each role may and may not do, as one
   table (rows = actions: create plan, send for review, comment, approve/reject, see audit, manage users, reset passwords, upload images...).
   Is there a "super admin"? Say plainly what exists (the `admin` role) and what does not (no Django `/admin/` site, no
   superuser concept in this project; confirm by reading the code). Recommend whether we need a "super admin" and, if so, how it would
   differ from `admin` (for example: the only one who may create other admins). Explain "Django stuff": what Django's own admin site and
   `createsuperuser` are, why this project deliberately does not use them, and what we would do instead.
3. **Where is everything stored?** Is it all in PostgreSQL now? List each kind of data and where it lives: user records (username, name,
   rank, role, active flag), passwords (only a PBKDF2 hash, never the password), sessions/tokens (only the SHA-256 of the token),
   login lock-out counters, plans and their versions, comments and decisions, audit log (hash chain), uploaded images (files in a Docker volume,
   not the database? verify), secrets/config (`deploy/.env`). Show it as a diagram: which table, which column. Verify from the real
   models and migrations (`server-django/api/models.py`, `migrations/`) and, if the stack is running, by looking at the tables with
   `docker exec dhruvar-postgres-1 psql ...` (read-only `\dt` and `\d`; never print password hashes or tokens).
4. **What is the user's rank and name used for?** Where does it show up (dashboard, app, audit lines, plans)? Is it free text? Should it be a
   fixed list of Army ranks? Recommend.
5. **Passwords and accounts, end to end.** First sign-in, forced password change (does it exist? if not, say so and propose), password rules,
   reset by admin, lock-out, session expiry, deactivation instead of deletion (and why), what happens to a user's old plans and audit lines if
   they leave. The app and the dashboard use the same accounts: say how.
6. **Gaps for a real deployment.** Compare what exists with what a real unit needs: bulk creation of users (CSV?), forced change of the
   temporary password, password expiry, 2-factor, unit/formation or "who can see whose plans" (does the code restrict visibility by owner or
   unit, or can every planner see every plan? verify in `views_plans.py`), a way to recover when the only admin forgets their password
   (`createuser` or a reset command inside the container), audit of user changes (exists?), session list/revoke. Mark each: exists / missing /
   not needed for the demo. Be honest; do not invent features.
7. **The real users question.** The seeded users are fictional. How do we put the owner's real officers in? Give the exact, safe procedure
   (who creates the first admin, how temporary passwords are handed over without writing them in chat or Git, how to require a change),
   and what must NOT go to GitHub (real names, ranks, passwords, database dumps).
8. **A detailed plan of action** with numbered steps, each: what changes, which files, how to test it, how long, and what the owner must
   decide. Split into (A) needed for the demo, (B) needed before real use, (C) later. Include: a "super admin" decision, a one-command way to
   create/reset users inside the Docker stack, a first-login password change if missing, a users page check in the dashboard, tests, and
   documentation. Do not start implementing.

## How to work
- Read first: `server-django/api/models.py`, `api/rules.py`, `api/auth.py`, `api/views_auth.py`, `api/views_users.py`, `api/views_plans.py`
  (who sees which plans), `api/management/commands/createuser.py` and `seed.py`, `web/src/pages/Users.tsx`, `web/src/rules.ts`,
  `docs/STACK.md`, `server-django/docs/PHASE-1-AUTH-AND-USERS.md`, `PHASE-5-POSTGRESQL.md`, `POSTGRESQL-OPERATIONS.md`, and how the Android
  app signs in (`ServerClient.kt`, `SessionStore.kt` in `DhruvAR-app`).
- Everything you state about the code must come from reading it; give file and line. Anything not checked: write "not verified".
- Never print or copy passwords, tokens, or the contents of `deploy/.env` or the seed password file. Do not run anything that changes the database.
- Do not edit code, do not commit, do not push.

## Output
1. The full explanation in the chat, following the eight questions above.
2. The same, tidied, written to `docs/USERS-AND-ROLES-PLAN.md`.
3. End with a short list of **decisions the owner must make** (for example: do we want a super admin? fixed rank list? one unit or several?),
   each with your recommendation, so the owner can answer in one message.
