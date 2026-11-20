# Phase 2 — Plans, decisions, comments and the audit log (Django backend)

Status: **built and tested. Not committed.** Written 2026-11-20 (milestone B1).
Branch `django-backend`, folder `DhruvAR-django/server-django/`. Nothing in `app/`, `web/` or `server/` was changed,
and the real demo database (`DhruvAR/server/data`) was not touched (checked by file times afterwards).

---

## 1. What this phase is, in plain words

This is the heart of the application: a planner **submits** a plan, a reviewer **opens** it, **decides** (approve,
reject, ask for changes) or **comments**, the planner can send a **new version** or **withdraw** it, and every step is
written to a tamper-evident **audit log**. Phase 1 answered "who is calling?"; this phase answers "what may they do
with plans?".

As before, "same" means exact: same addresses, JSON field names, status numbers and error sentences as the Node
server, so the phone app and the dashboard need no change.

Routes added: 9. With phases 0 and 1 that makes **20 of the 23 routes**; only the three image routes (phase 3) are missing:

| Route | Who | What it does |
|---|---|---|
| `GET /api/plans` | signed in | Plans with versions, decisions, comments, attachments. Planners see only their own. |
| `POST /api/plans` | planner | Submit a new plan (201 `{id, code}`). |
| `POST /api/plans/<id>/versions` | owning planner | Send a new version after changes were requested or the plan was rejected. |
| `POST /api/plans/<id>/withdraw` | owning planner | Withdraw an open plan. |
| `POST /api/plans/<id>/start-review` | reviewer | Mark a submitted plan "under review". Not an error when it does not apply. |
| `POST /api/plans/<id>/decision` | reviewer (not the owner) | Approve / reject / changes requested, recorded against the latest version. |
| `POST /api/plans/<id>/comments` | everyone except auditors | Add a comment. |
| `GET /api/audit` | signed in | The log, newest first. Planners see only entries about their own plans. |
| `GET /api/audit/verify` | admin, auditor | Re-computes the hash chain; says where it breaks. |

> Note: your job file says "`GET /audit`, `GET /audit/verify` (admin and auditor only)". In the Node code only
> **verify** is limited to admin and auditor; `GET /audit` is open to every signed-in user (planners get a filtered
> list). I copied Node, because the dashboard relies on it. Tell me if the list should be restricted instead.

---

## 2. The new and changed files

| File | Purpose |
|---|---|
| `api/jsutil.py` (new) | Makes Python behave like JavaScript where it matters: printing numbers (`10` not `10.0`, `1e-7` not `1e-07`), JSON text escapes, `trim()`, string length/slice, `%`. |
| `api/layout.py` (new) | Layout checking and the content hash. A port of `server/src/layout.ts`. |
| `api/views_plans.py` (new) | All plan routes and the two audit routes. A port of `plans.ts` + `listAudit`. |
| `api/urls.py` | The nine addresses above. |
| `api/http.py` | Added: `JsResponse` (writes JSON exactly like Express), `check_number`/`NumField`/`AnyField` (zod's number and "must be present" rules), JavaScript `trim` in the string validator, and `NaN`/`Infinity` in a request body now count as "Malformed JSON" (JavaScript refuses them; Python used to accept them). |
| `api/rules.py` | The "reason of at least 10 characters" check now measures like JavaScript. |
| `api/views.py`, `views_auth.py`, `views_users.py` | Only changed to use `JsResponse` (see section 5). |
| `api/tests/test_layout.py`, `test_plans.py`, `fixtures/layout_vectors.json` | 82 new tests (see section 6). |
| `scripts/compare_servers.py`, `run_side_by_side.sh`, `seed_node_users.mts` | The side-by-side tool (section 7). |
| `scripts/make_layout_vectors.mts` | Runs the **real** Node `layout.ts` and writes `layout_vectors.json`. |

---

## 3. Layout checking and the "content hash" (the risky part)

**What the hash is.** When a plan version is saved, the server stores a *fingerprint* of its content: the first 16
characters of `sha256(JSON.stringify(layout))`. The dashboard shows it so a reviewer knows exactly which content they
approved. For the same layout, Django must produce the **same fingerprint as Node**, otherwise old and new data would
disagree and any check against the Node value would fail.

**Why it is hard.** A fingerprint changes completely if one character of the text changes. Python's `json.dumps` and
JavaScript's `JSON.stringify` write the same data differently:

| Value | JavaScript | Python |
|---|---|---|
| `10.0` | `10` | `10.0` |
| `0.0000001` | `1e-7` | `1e-07` |
| `1e16` | `10000000000000000` | `1e+16` |
| `-0` | `0` | `-0.0` |
| a lone half-emoji | `"\ud800"` | an error |

`api/jsutil.py` rewrites JavaScript's printing rules (the ECMAScript "Number to string" algorithm and JSON string
escaping) so the text is identical. The layout is also built with the keys in exactly Node's order.

**How it was proven.** `scripts/make_layout_vectors.mts` runs the real `server/src/layout.ts` on **544 inputs** and
records the answer: either the exact error text, or the normalised layout plus its hash. They include the app's own
export (`server/scripts/fixtures/app-layout-v2.json`), numbers like `1e-7`, `0.1+0.2`, `5e-324`, `1.79e308`, `-0`,
`1e400`, rotations such as `-1e-20` and `725.5`, unicode, emoji and lone-surrogate names, duplicate/empty/oversized ids,
all 11 asset types, the 500-object limit, and 400 seeded random mutations of a good layout (first error wins).
`api/tests/test_layout.py` replays every vector: **all 544 give the identical error text or the identical layout text
and hash.** Generated ids are made predictable in the test so those hashes are compared too.

**What the checker does** (same as Node): accepts the app's layout schema v2 and ignores extra keys; rejects anything
malformed with `Invalid layout file: <path> — <message>` (note: a real em dash "—", not a hyphen); region must have an
area and the origin must lie inside it; at most 500 objects; missing sizes get the defaults of the object's asset type;
missing/duplicate ids get fresh ones; missing names become `Gunship 1`, `K131_takeoff 2`…; rotation is normalised to
0–360; heading defaults to 0; the plan name defaults to `Untitled plan`.

Three things the vectors taught me, copied from Node's zod library: lengths are counted in *code points* (an emoji
counts 1) for the validation limits but in UTF-16 units for the 80-character cut-off of names; "too many objects" is
reported *before* a problem inside an object; and a body with no `layout` key at all says
`layout: Invalid input: expected nonoptional, received undefined`.

---

## 4. Rules copied exactly from the Node server

- A plan the user may not see is **404 "Plan not found"**, never 403 (so ids cannot be probed).
- **Submit**: planners only (others: `403 Your role is not allowed to do this`). Body problems read `note: …`, `name: …`,
  `request: …`, or `Invalid layout file: …`; a missing `layout` is reported before note/name problems.
- **New version**: only the owner, only when the plan is `CHANGES_REQUESTED` or `REJECTED` (else `409`). If the new
  layout still has the default name `Untitled plan`, the plan keeps its old name. The plan goes back to `SUBMITTED`.
- **Withdraw**: owner, only while `SUBMITTED` or `UNDER_REVIEW` (`403 You cannot withdraw this plan`).
- **Start review**: reviewers other than the owner, only from `SUBMITTED`. Anything else answers
  `{"ok":true,"changed":false}` with status 200 and writes nothing.
- **Decision**: reviewers who are not the owner, on an open plan. `reject`/`changes` need a reason of ≥ 10 characters
  after trimming; `approve` does not. The version in the request must be the **latest** (else `409 A newer version (vN)
  exists. Reload the plan before deciding`) and is recorded against that version. Checks run in Node's order: body →
  permission → reason → version.
- **Comment**: everyone except auditors who can see the plan; stored against the latest version.
- **Plan codes** `TP-YYYY-NNNN`, numbered per calendar year.
- **Audit**: wording identical to Node (`Submitted` / `TP-2026-0001 version 1 submitted for approval`, `Approved`,
  `Changes requested`, `Commented` with the comment text, …). Every change and its audit entry are written in **one
  transaction**: if anything fails, neither is saved (tested by forcing a failure half-way).
- `GET /plans` is shaped exactly like Node's `loadPlans()`: keys and their order, parsed `layout`, `attachments[].url`
  `/api/attachments/<id>`, plans newest-changed first.

**How two people at the same moment are kept safe** (new words): a *transaction* is a group of database changes that
are saved all together or not at all. `select_for_update()` means "lock this plan's row until I am finished". Each
changing route locks the plan, **re-checks the rules on the fresh data**, then writes. So if two reviewers press
Approve together, the second one waits and then gets the same `403 You cannot decide this plan` it would have got had
it arrived a moment later. Plan codes work differently: the code column has a *unique* rule, and if two submissions
pick the same number the database refuses the second one and the server simply tries the next number (up to 5 times).

---

## 5. Where Django deliberately differs from Node (and why)

| Topic | Node | Django | Effect |
|---|---|---|---|
| Code numbering after 9999 per year | sorts codes as text, so `TP-2026-10000` sorts below `…9999` and the next number repeats | counts numerically | None below 10,000 plans a year. Above that Node would break. |
| Text containing a lone half-emoji (only possible via escaped JSON such as `"\ud800"`) | stores a replacement character but hashes the original, which breaks Node's own audit chain | replaces it with U+FFFD *before* saving and hashing, so everything agrees | Only for such odd input. |
| Two requests changing one plan at the same instant | cannot happen (Node runs one request at a time) | locks the row and re-checks (section 4) | Same answers as one-after-the-other. |
| `Cache-Control: no-store` on "Malformed JSON" | missing | present | Stricter, harmless (known since phase 1). |
| Plans with identical "last changed" millisecond | order unspecified | then by creation time and id | Invisible in practice. |

**Phase 1 clean-ups made now** (so the two servers match byte for byte): every answer is now written by `JsResponse`,
which uses `{"ok":true}` (no space) and `Content-Type: application/json; charset=utf-8`, exactly like Express. This
removes two differences listed in phase 1 (JSON spacing; content type). One sentence in the phase 1 document was also
wrong: zod counts string length in **code points**, the same as Python, so there is no UTF-16 difference there.

---

## 6. How it was checked

**1. Automated tests: 166 pass** (84 before + 82 new). Run:
```bash
cd DhruvAR-django/server-django
.venv/Scripts/python manage.py test
```
The new tests cover: the 544 Node vectors (error text, layout text and hash); JavaScript number/string behaviour with
values printed by Node; every route for every role (planner owner, other planner, reviewer, admin, auditor, signed
out); every body problem message; the 10-character reason rule (9 vs 10 characters, padding, `approve` exempt); latest
version rule (older and future version); resubmission and the kept name; start-review in all five "does not apply"
cases; code numbering (per year, past 9999, retry after a clash, giving up after repeated clashes); that nothing is
saved when a write fails half-way; the re-check inside the lock; audit list filtering, order and shape; `verify` for
roles, and detecting a tampered row; the whole life of a plan keeps the chain valid.

**2. Side-by-side with the real Node server: 138 requests, 0 differences.**
A throwaway Node server (port 4100) and a throwaway Django server (port 4101), each with its own temporary data
folder and the same six users (2 planners, 2 reviewers, admin, auditor), received the **same 138 scripted requests**:
signed-out and wrong-role calls, 25 invalid submissions, all four roles listing plans, every decision rule and error,
versions, comments, withdrawals, cookie sign-in with and without the CSRF header, the app's own export fixture, and the
final plan lists and audit lists as every role. Ids and times were replaced by placeholders (each server makes its own),
**fingerprints were compared as they are** except for one plan whose layout had no ids, so each server invented its own.
Result: **identical status and body in all 138**, and **identical response text byte for byte in all 138**; headers
identical except `Cache-Control` on 2 "Malformed JSON" answers (known, section 5). It also found the
`nonoptional` message above on its first run, which is now fixed and tested.

**3. Simultaneous requests on the running Django server:** 20 submissions at once gave 20 different codes
(`TP-2026-0001` … `0020`, no errors); 8 simultaneous decisions on one plan gave exactly one `200` and seven
`403 You cannot decide this plan`; the audit chain still verified.

Both throwaway servers were stopped and their temporary folders deleted; no server was left listening on 4100/4101.

**Not checked (honestly):**
- The phone app and the browser dashboard have **not** been pointed at Django yet (milestone B3).
- **PostgreSQL**: everything ran on SQLite. The concurrency results above partly rely on SQLite's write lock; on
  PostgreSQL the row lock and the audit-chain lock must be tested separately (milestone B4; the audit writer still
  needs its own lock before PostgreSQL goes live).
- Plans with **images**: the list shape is unit-tested with an attachment row inserted by hand, but upload/serve/delete
  does not exist yet (phase 3), so Node was never compared with attachments present.
- Request-size limit (Node: 2 MB; Django's default is a little higher) and URLs with a trailing slash (`/api/plans/`)
  were not compared. HEAD requests were not compared.
- The comparison used one fixed sequence of requests. It is a strong check, not a proof for every possible input.

---

## 7. How to run the comparison yourself

Needs the Node server's packages installed (`npm install` in `DhruvAR/server`, done already) and the Django `.venv`.
In Git Bash:
```bash
bash DhruvAR-django/server-django/scripts/run_side_by_side.sh          # add --raw to explain any text difference
```
It creates two temporary data folders, starts both servers, sends the 138 requests, prints a summary, stops both
servers and deletes the folders. It never reads or writes `DhruvAR/server/data`. To regenerate the Node vectors
after a change to `layout.ts`: `cd DhruvAR/server && node ../../DhruvAR-django/server-django/scripts/make_layout_vectors.mts <output.json>`.

---

## 8. Words you may not know

- **Content hash / fingerprint:** a short code computed from the plan's exact content; any change gives a different code.
- **Test vector:** an input together with the answer a trusted program (here the Node server) gave, used to check another program.
- **zod:** the validation library the Node server uses; its error sentences are what the app and dashboard show.
- **Transaction / row lock:** see section 4.
- **Unique rule (constraint):** the database itself refuses a second row with the same value (here, plan code).
- **Audit hash chain:** each log entry contains the hash of the previous one, so changing or deleting an old entry is detectable.

## 9. Next: milestone B2 (not started)

Images: `POST /api/plans/<id>/attachments` (upload), `GET /api/attachments/<id>`, `DELETE /api/attachments/<id>`.
