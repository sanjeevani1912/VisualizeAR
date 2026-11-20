# PostgreSQL operations note (DhruvAR Django server)

Short runbook for the database. Every command here was run while writing it (Windows, Docker Desktop, Git Bash), including the password change, a container re-create and a rehearsal of the 16 to 17 upgrade (section 7). Two things are described but **not** set up for you: scheduled backups (section 4) and TLS to the database.
Background and test results: `docs/PHASE-5-POSTGRESQL.md`.

Words used: a **container** is a sealed program started from an **image** (`postgres:16`); a **volume** is a folder Docker keeps
outside the container so the data survives stopping and removing it; a **dump** is a backup file made by `pg_dump`.

## 1. First time

```bash
cd DhruvAR-django/server-django
cp .env.postgres.example .env.postgres        # then replace the password in .env.postgres with a long random one:
python -c "import secrets; print(secrets.token_urlsafe(32))"
docker compose -f compose.postgres.yml --env-file .env.postgres up -d
docker compose -f compose.postgres.yml --env-file .env.postgres ps     # wait until it says "healthy"
```
`.env.postgres` is git-ignored. **Never commit it.** The database listens on `127.0.0.1:5433` only (this PC, not the network).
The password given in `.env.postgres` is only used the **first time** the volume is created (see "Change the password" below).

## 2. Run Django on it

Django picks PostgreSQL when `DB_ENGINE=postgres`; with the variables unset it uses SQLite as before.

| Variable | Meaning | Default |
|---|---|---|
| `DB_ENGINE` | `postgres` (or `sqlite`) | `sqlite` |
| `DB_PASSWORD` | database password (**required** for postgres) | – |
| `DB_NAME`, `DB_USER` | database and user | `dhruvar`, `dhruvar` |
| `DB_HOST`, `DB_PORT` | where it listens | `127.0.0.1`, `5432` (use `5433` with this compose file) |
| `DB_CONN_MAX_AGE` | seconds a connection is kept open (`0` = close after every request) | `0` |

Git Bash:
```bash
set -a; . ./.env.postgres; set +a
export DB_ENGINE=postgres DB_PASSWORD="$POSTGRES_PASSWORD" DB_PORT="$POSTGRES_PORT"
.venv/Scripts/python manage.py migrate
.venv/Scripts/python manage.py createuser --username iyer --name "S. Iyer" --rank "Lt Col" --role admin   # first admin (asks for a password)
ALLOWED_HOSTS=localhost,127.0.0.1 .venv/Scripts/python manage.py runserver 4001
```
(`manage.py seed` creates sample users and plans instead; it is for development and demos only.)

## 3. Start, stop, remove

```bash
docker compose -f compose.postgres.yml --env-file .env.postgres stop      # stop; data stays
docker compose -f compose.postgres.yml --env-file .env.postgres start     # start again
docker compose -f compose.postgres.yml --env-file .env.postgres down      # remove the container; the DATA volume stays
docker compose -f compose.postgres.yml --env-file .env.postgres down -v   # DANGER: also deletes the data volume
```

## 4. Back up

**Two things hold your data: the database AND the `uploads/` folder** (field images are files in `DATA_DIR/uploads`, not in the database).
Back up both, at the same time of day, and keep copies on another disk.

```bash
mkdir -p backups
MSYS_NO_PATHCONV=1 docker exec dhruvar-postgres pg_dump -U dhruvar -d dhruvar -Fc -f /backups/dhruvar-$(date +%Y%m%d-%H%M).dump
cp -r "$DATA_DIR/uploads" backups/uploads-$(date +%Y%m%d-%H%M)       # the images
```
**One command does both, keeps the newest 14 of each, and refuses to report success if the dump is unreadable:** `bash scripts/backup_postgres.sh` (`bash scripts/backup_postgres.sh 30` keeps 30; `DATA_DIR=...` says where `uploads/` is).
Tested: it produced a readable dump and an images archive, pruned old copies, and a restore of the newest dump matched the original database.
**Copy `backups/` to another disk or machine as well**: a backup on the same disk does not survive the disk.
To run it every night on Windows (not done for you, because it creates a standing task on your PC; this example command was **not run**, so check it first), in PowerShell as yourself:
`schtasks /Create /SC DAILY /ST 02:00 /TN "DhruvAR backup" /TR "\"C:\Program Files\Git\bin\bash.exe\" -lc 'cd /c/path/to/DhruvAR-django/server-django && bash scripts/backup_postgres.sh'"` (adjust the paths; remove it with `schtasks /Delete /TN "DhruvAR backup"`). On Linux use a cron line instead.
`backups/` (this folder, mounted into the container) is git-ignored. `MSYS_NO_PATHCONV=1` is only needed in Git Bash, which would otherwise
rewrite `/backups` into a Windows path; PowerShell does not need it. The dump is in PostgreSQL's compressed "custom" format (restored with `pg_restore`).

## 5. Restore (and check)

Practise this on a **copy** first:
```bash
MSYS_NO_PATHCONV=1 docker exec dhruvar-postgres psql -U dhruvar -d postgres -c "CREATE DATABASE dhruvar_restore"
MSYS_NO_PATHCONV=1 docker exec dhruvar-postgres pg_restore -U dhruvar -d dhruvar_restore --no-owner /backups/<the dump file>
```
Check it: compare row counts with the original, then start Django on the copy (`DB_NAME=dhruvar_restore`) and ask the audit log to verify
itself (`GET /api/audit/verify` as admin or auditor, or the dashboard's "Verify now"). Tested: 8 users, 12 plans, 21 audit entries before and
after, and `{"ok": true, "entries": 21}` on the restored copy.

To **replace** the live database with a backup: stop Django, `DROP DATABASE dhruvar WITH (FORCE)`, `CREATE DATABASE dhruvar`, `pg_restore` into it,
put the matching `uploads/` folder back, start Django, run the audit check.

## 6. Change the password

`POSTGRES_PASSWORD` in `.env.postgres` is read **only when the volume is first created**. Changing the file later does not change the database:
```bash
MSYS_NO_PATHCONV=1 docker exec dhruvar-postgres psql -U dhruvar -d postgres -c "ALTER USER dhruvar PASSWORD 'the new password'"
```
then put the same password in `.env.postgres` and in the `DB_PASSWORD` that Django starts with.

## 7. Upgrades

Minor updates of PostgreSQL 16: `docker compose ... pull` then `up -d` (the volume is compatible). Tested: re-creating the container with
`up -d --force-recreate` kept all data (72 plans before and after). A **major** version (16 to 17) needs a dump and a restore into the new version,
never just a new image tag. Take a backup before either.

Rehearsal done: a dump from PostgreSQL 16 was restored into a separate PostgreSQL 17.12 container: same counts (8 users, 72 plans, 156 audit
entries), `verify_chain` ok, and **the whole test suite (214 tests) passes on PostgreSQL 17**. To do it for real: back up, stop Django, start the
new version on an empty volume, `pg_restore` into it, run the audit check, start Django, keep the old volume until you are sure.

## 7b. Connections (read this before using more than one Django process)

PostgreSQL allows 100 connections by default. Django keeps one per request-handling thread. `DB_CONN_MAX_AGE` (default `0`) closes the connection after
every request. A higher value keeps connections open for re-use, which suits servers with a fixed number of workers (gunicorn), but **must not** be used with
Django's development server (a new thread per request): a burst of 200 requests then left hundreds of idle connections and PostgreSQL answered
"too many clients already". Rule of thumb: workers x threads x number of Django processes must stay below the database's `max_connections`.

## 8. What the server does to stay correct with several users at once

PostgreSQL lets writers run side by side (SQLite made them take turns). Three places must take turns, and the code makes them:
- **The audit chain**: each entry contains the previous entry's hash. A PostgreSQL *advisory lock* (a named lock released at the end of the transaction)
  makes writers append one at a time (`api/audit.py`).
- **Plan codes** `TP-YYYY-NNNN`: submissions take another advisory lock while choosing the number; the unique rule on the code column plus a retry remain as a safety net.
- **Decisions, versions and image counts**: the plan's row is locked while it is checked and changed (`select_for_update`).
- **Sign-in lock-out counter**: also row-locked, so simultaneous wrong guesses cannot lose a count.
All are covered by tests that fire real parallel requests (`api/tests/test_concurrency.py`, run on PostgreSQL).

## 9. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `DB_ENGINE=postgres needs the DB_PASSWORD environment variable` | the password was not exported (section 2) |
| `connection refused` on 5433 | the container is not running or not healthy yet (`ps`), or another program uses 5433 (change `POSTGRES_PORT`) |
| `password authentication failed` | `DB_PASSWORD` differs from the database's password (section 6: the file only matters at first creation) |
| A Git Bash command prints `C:/Program Files/Git/...` paths | add `MSYS_NO_PATHCONV=1` or use PowerShell |
