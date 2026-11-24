# Session H: finish everything that does not need the phone, and hand over (the owner's internship ends tomorrow)

Working folder: `C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration` (branch `integration`, at `62f7950`). Main checkout:
`C:\Users\itcell\AndroidStudioProjects\DhruvAR` (branch `main` at `47819c1`, tag `demo-safe`). App worktree: `DhruvAR-app` (branch `app/connect-stack`).
Remotes: `origin` = the owner's personal repo (`sanjeevani1912/VisualizeAR`), `army` = the team repo (`vide123456789/DhruvAR`).

## Owner and style
The owner is an intern and a beginner, leaving tomorrow. They follow this transcript and must be able to show the system to their officers and hand it to other
developers. Plain words, one or two sentences per new idea, small tables. Always say what you **verified** (command run, output seen) and what is **not verified**
(anything needing the phone, ARCore, real compass). Report after every phase in 5-8 lines, then keep going unless a rule below says stop.

## What is already true (verified 2026-11-24)
- `integration` holds everything, 21 commits ahead of `main`, 0 behind: map/Nginx, Django 5.2 + PostgreSQL 16, dashboard (icons, basemap, wide field-image viewer),
  the Docker stack at `http://localhost:8000` (containers `dhruvar-nginx-1`, `dhruvar-django-1`, `dhruvar-postgres-1` plus `map-server-*`), app connected to the stack
  (USB `adb reverse tcp:8000 tcp:8000`, address `http://localhost:8000`), users/roles work (rank dropdown, appointment, super admin, forced password change; migrations
  0003-0005 applied), and the panorama flow (Open, Send to plan, dashboard viewer). Server tests 235 pass; app 361 pass.
- Pushed so far: `army/sanjeevani/integration` is at `d6f3172` (older). Nothing newer is pushed. `main` is untouched.
- The owner tested the app and dashboard over USB with `docs/APP-PHONE-TEST.md` and it worked. Not yet tested on the phone: panorama, forced password change screens,
  AR heading. Wi-Fi from the phone is parked.
- Users: 9 (bose, iyer, khan, menon, nair, rao, sharma, singh = fictional demo users; `stackadmin` = super admin). Passwords live only in git-ignored files
  (`deploy/admin-credentials.txt`, `/data/seed-credentials.txt` inside the Django container). Never print them.
- Heading problem (docs `DhruvAR-app\docs\HEADING-REVIEW.md`): AR placement is proven in PC tests to disagree with the map (rotation uses the saved heading where the live compass
  is expected; plan probably mirrored left/right). Unfixed; the owner will field-test. The two uncommitted edits in `DhruvAR-app` (`HeadingReviewTest.kt`, `HEADING-REVIEW.md`) belong to it.
- Docs to read first: `docs/STACK.md`, `docs/MAP-AND-NGINX.md`, `docs/USERS-AND-ROLES-PLAN.md`, `docs/PANORAMA-FEATURE.md`, `docs/APP-PHONE-TEST.md`,
  `docs/prompts/demo-and-deployment/MASTER-PLAN.md`, `docs/prompts/HOME-GUIDE.md` (in the main folder), `web/docs/*`, `server-django/docs/*`.

## Rules
- You may make **local** commits on `integration`, on `main` (merge only), and in `DhruvAR-app`. You may push **only to `origin`** (the owner's personal repo), and only in H9.
  **Never push to `army`** and never force-push. Never delete a branch, `docker compose down -v`, `seed --reset`, or wipe a volume without asking the owner first.
- Never print or commit passwords, tokens, `deploy/.env`, credential files, database dumps or uploads. Check `git status` and `git diff --cached --stat` before each commit.
- Do not change the layout JSON schema or the API contract. Do not touch `E:\map\map-server` files or `DhruvAR-friend`.
- Anything touching the AR placement maths (heading fix) goes on its own branch and is **not** merged to `main` (H5).
- The Browser pane may be hidden: if browser tools are refused, ask the owner once to show the pane; if still unavailable, test through `curl` and say so.
- Put every new document under `docs/`. Keep line endings LF in scripts and Nginx files.

## Phase H0: check the ground (10 minutes)
`git status` and `git log` in all three folders; `docker compose ps` in `deploy`; `/api/health` through port 8000; confirm the containers use the current code (migrations applied,
`web/dist` newer than the last merge). Run the Django tests (`docker compose exec django python manage.py test`), the dashboard typecheck/tests/build, and `gradlew testDebugUnitTest`
from the app worktree (JAVA_HOME `C:\Program Files\Android\Android Studio\jbr`). Report the numbers. Fix only what is clearly broken and tell the owner.

## Phase H1: clean, believable demo data
- Decide with `docs/prompts/demo-and-deployment/MASTER-PLAN.md` what the demo story is: a planner builds a field plan, sends it, a reviewer reviews it, approves/rejects with a reason, the auditor checks the trail, the
  admin/super admin manage users. Create a small, realistic set in the **live** database without wiping the demo users: use the API or a small script in `server-django/scripts/` (not the `seed --reset`), 
  6-8 plans in different states (draft is not a server state: use SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED with a reason, CHANGES_REQUESTED, WITHDRAWN), real-looking names and codes, regions near a place in India
  that the offline map covers (check the map server returns tiles there), assets from the full asset library, dates within the last two weeks, a few comments, two field images (generated locally, not real photos), 
  one panorama-shaped image (2880x1440, generated). Use only fictional text; nothing that looks like a real unit location or classified detail.
- Remove nothing real; leave `stackadmin`. Audit chain must still verify (`GET /api/audit/verify`).
- Write the demo logins (usernames and where the passwords are kept, never the passwords) into `docs/DEMO-ACCOUNTS.md`. If a demo user's password is unknown or it must be changed at first sign-in, 
  use `manage.py resetpassword` and make the first-login change part of the script, or set known demo passwords in a git-ignored file `deploy/demo-passwords.txt` (mode 0600), and say where.

## Phase H2: click-through as every role (browser pane or, if unavailable, curl)
Sign in to `http://localhost:8000` as each role (planner, reviewer, admin, super admin, auditor) using temporary test users you create and delete afterwards (names starting `qa.`; they own nothing, so they can be removed
through `manage.py shell` as done before; leave the audit chain valid). Check and report with screenshots/outputs: forced password change, rank dropdown + appointment, who may see what (planner sees only their plans;
reviewer cannot decide own plan), plan page (icons, positions, basemap), field-image viewer with the wide panorama, comments, decisions, audit trail and verify, Users page, console free of real errors. Fix small frontend/server bugs you find
(on `integration`, with a test where practical), and list anything bigger instead of fixing it.

## Phase H3: full test and cold-start proof
Run all test suites again. Then prove a cold start the owner can repeat on the demo PC: `docker compose down` (data stays) then `up -d`, sign in, plans still there, images still there. Check `E:\map\map-server` containers come back
(`docker compose ps -a` there) and the map loads through `/map/`. Write the exact steps into the demo script (H6). Do NOT use `down -v`.

## Phase H4: backup and restore rehearsal
Use `deploy/backup-stack.sh` (read it first). Make a backup of the demo state to a folder **outside Git** (for example `E:\dhruvar-backups\`), restore it into a scratch database (`restore_test`, as in `docs/STACK.md`) and confirm row counts match; drop the scratch database.
Record the result and the backup path in the demo doc (no secrets).

## Phase H5: prepare the heading fix on its own branch (do not merge)
Create a branch `app/heading-fix` from `app/connect-stack` in a new worktree. Implement the candidate fix described in `docs/HEADING-REVIEW.md` section 4 (yaw = saved heading minus live heading minus forward angle; and the mirror), consistently in
`ARCoordinateTransformer.kt`, `ARRenderer.kt` placement/drag/hit-test code and `ARVisualizationScreen.kt`, **behind a clear single switch** (a constant or build config flag, default OFF) so the owner can turn it on after the field test without more code.
Add/adjust unit tests (`HeadingReviewTest.kt` already encodes the expected outcome). Run `gradlew testDebugUnitTest assembleDebug`. Write `docs/HEADING-FIX-HOWTO.md`: how to turn it on, the field test steps, how to turn it off. Say plainly: **not verified on a device**. Do not merge into `integration` or `main`.

## Phase H6: demo script and rehearsal checklist
Write `docs/DEMO-SCRIPT.md` (one printable page plus a one-page checklist): 8-10 minutes, minute by minute; who is signed in where (phone + laptop); what to say in one sentence per screen; the cold-start commands; the "if X goes wrong, do Y" table
(phone cannot reach the server, `adb reverse` lost, map blank, Docker not running, login locked out, forgot a password, ARCore unsupported on the phone, AR looks rotated -> show the dashboard and the panorama instead), and a fallback plan that works with no phone at all.
Include the USB steps from `docs/APP-PHONE-TEST.md`. Tell the owner what to record as the backup video.

## Phase H7: handover for the other developers (the intern leaves tomorrow)
Write `docs/HANDOVER.md` for developers who will do VAPT testing and deployment, and a short `docs/HANDOVER-FOR-OWNER.md` for the owner. Contents: the system in one picture; folder and branch map; how to run everything from nothing (clone, `.env`, build, up); where secrets live and how to rotate them;
every known gap with severity (HTTP only, `ALLOWED_HOSTS` not enforced, Django lacks the terrain routes, heading mismatch, Wi-Fi from phone untested, magnetic north, no scheduled backups, plain-text demo password files, `TRUST_PROXY` assumptions, cleartext allowed for localhost on release builds, etc.);
test commands and current numbers; licences list (open-source only); what was verified vs not; the list of docs to read in order; how to create real users safely (`docs/USERS-AND-ROLES-PLAN.md`). Be honest and specific; no marketing language.

## Phase H8: commit and tidy
Commit in small logical commits on `integration` (docs, demo script, demo data script, fixes), with plain messages ending with the standard Claude co-author line. Commit the two heading-doc edits in `DhruvAR-app` on `app/connect-stack`. Commit `docs/prompts/sessions/` and the prompts docs on `main` only through the merge in H9
(copy them via a normal commit on `main`, not by cherry-picking secrets). Run `git status` in all folders: nothing uncommitted except git-ignored secrets.

## Phase H9: release to `main` and push to the owner's personal repo
1. Final full test run (H0 list) on `integration`. If anything fails, stop and report.
2. Merge `integration` into `main` (`--no-ff`, message "Merge integration: stack, users, panorama, demo prep"), tag `demo-v1`, keep `demo-safe`.
3. Push `main`, `integration`, `app/connect-stack`, `app/heading-fix` and the tag `demo-v1` to **`origin` only**. Check the push output; confirm with `git ls-remote origin`. Never push to `army`.
4. Ask the owner (do not do it yourself) whether to also push to the team repo, and give the exact command.

## Final report (end of session, in the chat and in `docs/FINAL-STATUS.md`)
A table: each piece, state (done / done but not verified on a device / not done), where it is (branch, commit, doc). The test numbers. Everything pushed and where. What the owner must still do by hand (phone tests: AR, panorama, forced password change screens; heading field test then switch the fix on;
record the backup video; rehearse once). What the other developers must do (the gaps list). Keep it short, honest and in plain words.
