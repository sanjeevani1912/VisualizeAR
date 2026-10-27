# DhruvAR API server

Node.js + TypeScript (Express 5) with SQLite (`node:sqlite`, no native build). Real accounts, server-enforced
roles, an append-only audit log and field-image storage. Backs the web dashboard in `../web`.

## Run

```bash
cd server
npm install
npm run seed          # first time only: creates users + fictional sample plans
npm run dev           # http://localhost:4000
```

`npm run seed` writes random passwords to `data/seed-credentials.txt` (git-ignored). Change them after first
sign-in. `npm run seed -- --reset` wipes everything and seeds again. Seed data is for development and demos.

Requires Node 22.13+ (built-in SQLite). Data lives in `server/data/` (`dhruvar.db`, `uploads/`).

## Tests

```bash
npm test         # pure rules (roles, state machine, password policy)
npm run test:api # end-to-end against a throwaway server + database (10 tests)
```

## Roles

| Role | Can |
|---|---|
| planner | Submit plans (upload layout JSON), see only their own plans, comment and attach field images on them, withdraw, submit a new version after changes/rejection |
| reviewer | See all plans, comment, attach field images, approve / reject / request changes (never their own plan) |
| admin | See all plans, comment, manage users (create, role, disable, reset password). Cannot decide plans |
| auditor | Read-only: all plans, activity log, audit-chain verification |

## Security notes

- Passwords: scrypt with per-user salt; min 10 chars with upper, lower and digit.
- Sessions: random token in an `httpOnly`, `SameSite=Strict` cookie (set `COOKIE_SECURE=true` behind HTTPS); only a hash is stored. 60 min idle / 12 h absolute. Role or status changes end the user's sessions.
- Login: 5 failures lock that username+IP for 15 minutes; unknown users cost the same time as wrong passwords.
- All state-changing requests need an `X-Requested-With: dhruvar` header (CSRF guard). Strict CSP via helmet.
- Every action is re-checked on the server; a plan you may not see returns 404.
- Decisions apply to a specific version; deciding a stale version is refused.
- Field images: JPEG/PNG/WebP only, checked by file content (not the declared type), 10 MB, 30 per plan, random stored names, served only to users who can see the plan, with `nosniff`.
- Audit log: each entry hashes the previous one; `GET /api/audit/verify` (admin/auditor) detects edits or deletions.

## Not done yet (before real use)

HTTPS termination, identity-system/MFA integration, EXIF handling for photos, database backups, rate limiting
beyond login, unit/cluster grouping, and the Android sync client (`POST /api/plans` is the intake the app will call).

## Configuration (env)

`PORT` (4000), `DATA_DIR`, `COOKIE_SECURE`, `TRUST_PROXY`, `SESSION_IDLE_MINUTES`, `SESSION_ABSOLUTE_HOURS`,
`MAX_UPLOAD_MB`, `MAX_IMAGES_PER_PLAN`, `LOGIN_MAX_FAILURES`, `LOGIN_LOCK_MINUTES`, `MAP_ORIGIN`, `WEB_DIST`.
If `web/dist` exists the server also serves the built dashboard, so production is a single origin.
