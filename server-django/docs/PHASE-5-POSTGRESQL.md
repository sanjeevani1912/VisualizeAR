# PostgreSQL support (milestone B4) — Django backend

Status: **built and verified on PostgreSQL 16. Not committed.** Written 2026-11-20.
Branch `django-backend`, folder `DhruvAR-django/server-django/`. Nothing in `app/`, `web/` or `server/` was changed; the real demo
data (`DhruvAR/server/data`) was not touched. How to run and look after the database: `docs/POSTGRESQL-OPERATIONS.md`.

## 1. What this milestone is, in plain words

Until now the Django server stored its data in SQLite, a single file. Your senior asked for **PostgreSQL**, a real database server that many
people can use at the same time. Django can use either; this milestone makes the choice a setting, runs PostgreSQL in Docker, and, most
importantly, makes sure the rules that depended on SQLite's "one writer at a time" behaviour still hold when several people write at once.

## 2. What was built

| Piece | Where | Notes |
|---|---|---|
| Choose the database from environment variables | `config/settings.py` (`_database()`) | `DB_ENGINE=postgres` + `DB_PASSWORD` (+ `DB_NAME`, `DB_USER`, `DB_HOST`, `DB_PORT`, `DB_CONN_MAX_AGE`). **Unset means SQLite exactly as before.** The SQLite-only options (`transaction_mode`, `timeout`) are not sent to PostgreSQL. A missing password or unknown engine is a clear error. |
| PostgreSQL driver | `requirements.txt` | `psycopg[binary]` 3.x. Licence LGPL-3.0 (open source; used as an unmodified library). |
| PostgreSQL in Docker | `compose.postgres.yml`, `.env.postgres.example` | `postgres:16`, a named volume for the data, health check, published on `127.0.0.1:5433` only. The password lives in `.env.postgres` (git-ignored; an example file is committed). |
| **Audit chain lock** | `api/audit.py` | Writers append one at a time (details in section 3). |
| **Plan-code lock** | `api/views_plans.py` | Submissions take turns choosing `TP-YYYY-NNNN`. |
| `seed --reset` made portable | `api/management/commands/seed.py` | It used `flush` (TRUNCATE), which PostgreSQL refuses inside a transaction with pending checks. It now deletes rows in order and restarts the audit counter on either database. |
| Comparison scripts can use PostgreSQL | `scripts/run_side_by_side.sh`, `scripts/run_conformance.sh` | `USE_POSTGRES=1 bash ...` runs Django on a throwaway PostgreSQL database that is dropped afterwards. |
| Tests | `api/tests/test_concurrency.py` (7, PostgreSQL only), `api/tests/test_settings.py` (5) | See section 4. Suite: **214 tests**. |
| Runbook | `docs/POSTGRESQL-OPERATIONS.md` | Start, stop, back up, restore, password change, upgrades. |

## 3. The concurrency problem, and how it was found and fixed

**Audit chain.** Every audit entry stores the hash of the entry before it. On SQLite every write transaction starts with `BEGIN IMMEDIATE`, so writers
took turns for free. PostgreSQL lets them overlap, so two writers could both read "the latest entry", both chain to it, and fork the chain.
I wrote the test first: 8 threads writing 10 entries each. **On PostgreSQL, before the fix, the chain was already broken at entry 2.**
Fix: a PostgreSQL *advisory lock* (a named lock released automatically when the transaction ends) taken just before reading the latest entry.
(A row lock on the latest entry, the other option in the job file, does not work reliably here: while the second writer waits, PostgreSQL can hand it the
same old row again instead of the new newest one.) After the fix: 80 simultaneous entries give one unbroken chain with 80 distinct predecessors, and a
second test proves the first test can fail (it switches the lock off and the chain breaks again).

**Plan codes.** Before the fix, 12 simultaneous submissions gave **6 successes and 6 errors (500)**: codes were never duplicated (the database's unique
rule refused the clash) but the retries ran out. With the advisory lock, all 12 succeed with 12 distinct codes; the unique rule and retry stay as a safety net.

**Already safe:** decisions, new versions, image counts (the plan's row is locked while checked) and the sign-in lock-out counter. Tested with real parallel requests:
8 simultaneous approvals of one plan: exactly one succeeds, 7 get `403 You cannot decide this plan`; 8 simultaneous uploads with a limit of 3:
exactly 3 succeed, 5 get `409`, and no file is left behind for the refused ones; 5 simultaneous wrong passwords still lock the account, and 12 simultaneous wrong
guesses are counted as exactly 12.

## 4. How it was checked

| Check | Result |
|---|---|
| Whole test suite on **SQLite** (variables unset) | 214 run, all OK (7 skipped: the PostgreSQL-only concurrency tests) |
| Whole test suite on **PostgreSQL 16** | **214 run, all OK, none skipped** |
| `migrate` on PostgreSQL | OK (all migrations) |
| Node vs Django side-by-side, Django on PostgreSQL | **235 requests, 0 differences**, response text byte-for-byte identical |
| Seed: Node's seed vs Django's seed on PostgreSQL | all 10 API views identical |
| The same 13 end-to-end tests (`api-test.ts`) against Node and against Django on PostgreSQL | **13/13 and 13/13** |
| Backup with `pg_dump`, restore into a new database, compare, `verify_chain` | 8 users / 12 plans / 21 audit entries before and after; chain `ok` on the restored copy |

The one test that behaved differently from SQLite was `seed --reset` (section 2). Everything else passed unchanged.

## 5. Not checked (honestly; the items marked "done later" were closed in section 9)

- **Load.** Concurrency was tested with 5 to 12 simultaneous requests, not hundreds. The audit and plan-code locks make writers wait for each other, which is
  correct but means those writes are serial; at the expected number of users this is far from a limit.
- **Several Django processes** (gunicorn workers) were not run; the locks live in the database, so they are expected to work, and the tests use separate connections
  per thread, which is the same situation. A real gunicorn run belongs to the deployment phase.
- **Password change, PostgreSQL upgrades and `down -v`**: described in the runbook as standard practice; only `down -v` was run (to remove my test data).
- **TLS to the database**, remote database servers, `pg_hba` hardening, scheduled backups: not set up.
- **The Android app and a browser** were not run against Django on PostgreSQL (they were on SQLite; the API answers are identical on both, per the 235-request comparison).
- The throwaway test data volume and databases were removed. A local `.env.postgres` with a random password exists on this PC (git-ignored).

## 6. Files

New: `compose.postgres.yml`, `.env.postgres.example`, `api/tests/test_concurrency.py`, `api/tests/test_settings.py`, `docs/POSTGRESQL-OPERATIONS.md`, this file.
Changed: `config/settings.py`, `requirements.txt`, `.gitignore`, `api/audit.py`, `api/views_plans.py`, `api/management/commands/seed.py`, `scripts/run_side_by_side.sh`, `scripts/run_conformance.sh`, `PLAN.md`.

## 7. Words you may not know
- **PostgreSQL:** a database server program that many users can use at once (SQLite is one file used by one program).
- **Advisory lock:** a named "one at a time" lock the database holds until the transaction ends.
- **Transaction:** a group of changes saved all together or not at all.
- **Docker volume:** storage that outlives its container.
- **Dump / restore:** a backup file made by `pg_dump`, and loading it back with `pg_restore`.

## 8. Next
Phase 5 proper (serving the dashboard, security headers, HTTPS cookies, gunicorn behind Nginx; another session builds Nginx) and cut-over planning
(moving Node's existing data is task D4, only at cut-over).

## 9. Follow-up checks (done after the first version of this document)

**Load, with two separate Django processes on one PostgreSQL** (`scripts/load_check.py`; the two processes stand in for gunicorn workers, which do not run on Windows):
200 simultaneous reads of the plan list; 60 simultaneous plan submissions; 60 simultaneous comments; 20 reviewers deciding the same plan at once. Result: **no errors**,
60 distinct plan codes, **exactly 1 decision accepted** (19 refused), audit chain unbroken (156 entries), all plans and comments stored. (Typical response times on
this PC with the development server: reads about 250 ms, writes 0.7 to 0.9 s while 25 were in flight; this shows no failure, it is not a capacity figure.)

**It also found a real problem:** my first setting kept connections open for a minute (`DB_CONN_MAX_AGE=60`). The development server starts a thread per request,
so the burst left hundreds of idle connections and PostgreSQL answered "sorry, too many clients already" (500 errors). The default is now **0** (close after every request;
tested: 0 open connections afterwards) and the runbook explains when a higher value is safe.
(The same burst also overran the development server's tiny connection queue, which showed up as connection resets; that is a limit of the development server, so the test now
keeps at most 25 requests in flight.)

**Password change:** old password refused, new one accepted, original restored (runbook section 6). **Container re-create:** data kept (72 plans before and after).
**Major upgrade rehearsal 16 to 17:** restored copy identical, audit chain ok, **214/214 tests pass on PostgreSQL 17.12**.
**Backups:** `scripts/backup_postgres.sh` (database + images, keeps the newest N): tested, including a restore that matched the original.

**Dashboard on PostgreSQL** (real dashboard, Django on PostgreSQL, browser): signed in as reviewer and admin; opened a plan; **rejected it with a reason** (confirmation step, status and
audit trail updated); as admin **created a user from the form, reset their password** (the old password was refused, the generated one-time password worked) and **disabled the user**
(sign-in then refused; `User created`, `Password reset`, `User updated` entries written). These dashboard buttons had not been clicked before.

**Still not done:** real gunicorn (does not run on Windows; test it in the deployment phase, in Docker/Linux), a test with hundreds of simultaneous *writes* or thousands of users,
TLS to the database, a remote database server, scheduled backups (the script exists; the nightly task is yours to create, see the runbook), and the Android app on PostgreSQL.
