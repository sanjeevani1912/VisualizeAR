# Session I: put all the finished code on `main` (personal repo first, then the army/team repo) and organise the docs

Paste this whole file into a NEW Claude session whose folder is `C:\Users\itcell\AndroidStudioProjects\DhruvAR` (the main checkout).
Written 2026-11-24 evening. The owner tested the phone app and the dashboard on 2026-11-24 and said "all's good". Nothing in this file has been run yet.

## Owner and style
The owner is an intern (internship ends very soon) and a beginner with Git. Explain in plain words, report in short steps, say what you **verified** (command run,
output seen) and what is **not verified**. Ask the owner before each of the two pushes (see "Stop points"). Never print passwords or tokens.

## Facts (checked 2026-11-24)
- Remotes of the main checkout: `origin` = the owner's personal repo `sanjeevani1912/VisualizeAR`; `army` = the team repo `vide123456789/DhruvAR`.
- Local `main` = `3ac9c8e` (contains branding and the Field Guide). The personal repo's `main` is at `07051af` (older). Tag `demo-v1` is on the personal repo.
- Branch `release/final` (worktree `C:\Users\itcell\AndroidStudioProjects\DhruvAR-final`, commit `cf7ab1b`) = `main` + `app/ground-fix` + `app/heading-fix` (heading fix switched OFF
  by `ArHeadingFix.ENABLED = false`; ground fix ON by `GROUND_FIX_ENABLED = true`). It builds; 384 app unit tests passed; the owner then tested on the phone and said everything is good.
  It is **not pushed**.
- **Uncommitted in `DhruvAR-final`:** modified `docs/DEMO-ACCOUNTS.md`; new `docs/RUN-EVERYTHING.md`; new `deploy/start-all.ps1`.
- Other worktrees/branches: `DhruvAR-integration` (`integration`, `6583c1a` "Final status document"), `DhruvAR-app` (`app/connect-stack`), `DhruvAR-ground` (`app/ground-fix`),
  `DhruvAR-brand` (`brand/logos`), also older branches (`django-backend`, `infra/nginx-map`, `web/dashboard-polish`, `stack/compose`, `app/panorama`, `app/heading-fix`).
- The earlier push to the team repo went to a side branch (`army/sanjeevani/integration`), never to `army/main`.
- Stack (Docker) runs at `http://localhost:8000`; not needed for this task. Do not start, stop or change containers. Do not touch `E:\map\map-server` or `DhruvAR-friend`.

## Rules
- **Never force-push** (no `--force`, no `--force-with-lease`), never delete a remote branch or tag, never rewrite history that is already pushed.
- **No secrets in Git, ever.** Before every push scan what will be pushed (see G2). If anything secret is found, stop and tell the owner.
- Never push to the team repo `army` without the owner's explicit "yes" in this session right before the push, after you have shown what will be pushed.
- Keep the working tree safe: do not delete worktrees or branches. Do not run `git clean`, `git reset --hard`, `git checkout --` on anything you did not create.
- Do not change application code. Only commit what already exists, merge, add and organise docs, and push. If a build or test is broken, report it; do not "fix" app code in this session.

## Phase G0: look before touching (report in a table)
1. `git status` and `git log --oneline -5` in `DhruvAR` (main), `DhruvAR-final`, `DhruvAR-integration`.
2. `git fetch origin` and `git fetch army`. Report: how main relates to `origin/main` and to `army/main` (ahead/behind counts: `git rev-list --left-right --count main...origin/main`, same for army),
   and the list of `army` branches.
3. `git branch --no-merged main` and `git branch --no-merged release/final`: list every branch that has commits not in `release/final`. For each, say what it is and whether it belongs in the final
   code (everything the owner built should be in; abandoned experiments should not). Ask the owner about anything unclear. Expected: nothing important is missing.

## Phase G1: commit and merge
1. In `DhruvAR-final`: commit the three uncommitted items (`docs/DEMO-ACCOUNTS.md`, `docs/RUN-EVERYTHING.md`, `deploy/start-all.ps1`) on `release/final` with a plain message. First check they contain **no password**
   (`DEMO-ACCOUNTS.md` must not list passwords; `start-all.ps1` must not contain any).
2. Bring `main` up to `release/final`. `main` is an ancestor of `release/final`, so a fast-forward should work: from the main checkout `git merge --ff-only release/final`.
   If it is not possible, stop and explain; do not improvise a messy merge.
3. Run the tests one more time on the result and report numbers: app `gradlew testDebugUnitTest` (JAVA_HOME `C:\Program Files\Android\Android Studio\jbr`; create `local.properties` with
   `sdk.dir=C\:\\Users\\itcell\\AppData\\Local\\Android\\Sdk` if missing; it is git-ignored) and the dashboard `npm run build` in `web/` (typecheck included). If the server tests need Docker, say "not run" rather than starting containers.

## Phase G2: make the repository tidy and safe
Goal: someone cloning the repo sees a clean structure, a README, and a `docs/` folder with the documents of every feature.
1. **Scan for things that must not be pushed**, in everything that would be pushed (`git ls-files` and the history of the commits not yet on the remote):
   - files: `.env`, `*credentials*`, `demo-passwords*`, `*.dump`, `*.sqlite`, `*.db` (except none), `local.properties`, `*.jks`/`*.keystore`, `node_modules/`, build outputs (`build/`, `dist/`, `*.apk`), `.venv`, `__pycache__`, `*.pmtiles`, `*.zip`/`*.tar.gz` archives, and any file over 5 MB (`git ls-files -z | xargs -0 du -k`, or a PowerShell loop); `Zone.Identifier` files.
   - text: search tracked files for the two demo passwords used on this project (ask the owner for them or read `deploy/demo-passwords.txt` **locally only**, never print them) and for patterns such as `password=`, `secret`, `token`, `BEGIN PRIVATE KEY`, `AKIA`. Report **counts and file names only**, not values.
   - If anything is found in tracked files or in unpushed history: STOP and tell the owner; do not push. (Removing it from history needs the owner's decision.)
   - Confirm `.gitignore` covers: `deploy/.env`, `deploy/*credentials*.txt`, `deploy/demo-passwords.txt`, `web/dist/`, `web/node_modules/`, `server-django/data/`, `server-django/backups/`, `local.properties`, `*.apk`, build folders.
2. **Organise `docs/`** without deleting or renaming existing files that other docs link to. Create:
   - `docs/README.md`: the index: what DhruvAR is (3 sentences), the folder map, how to run everything (link `RUN-EVERYTHING.md`), and a table "Feature -> document".
   - One short entry per feature in the index, linking the existing document, and create a missing one only if a feature has no document: Stack (`STACK.md`), Map server and Nginx (`MAP-AND-NGINX.md`), Users and roles (`USERS-HOWTO.md`, `USERS-AND-ROLES-PLAN.md`),
     Demo accounts and data (`DEMO-ACCOUNTS.md`, no passwords), Panorama (`PANORAMA-FEATURE.md`), Ground fix (`GROUND-FIX.md`), Heading review and fix (`HEADING-REVIEW.md`, `HEADING-FIX-HOWTO.md`), Branding (`BRANDING.md`), App phone test (`APP-PHONE-TEST.md`),
     Demo script (`DEMO-SCRIPT.md`), Handover for developers (`HANDOVER.md`, `HANDOVER-FOR-OWNER.md`), Final status (`FINAL-STATUS.md`), Layout schema (`layout-schema-v2.md`), Django docs (`server-django/docs/`), dashboard docs (`web/docs/`).
     Check each link exists; list the ones that do not.
   - Move nothing that scripts depend on. Put the AI-session prompt files (`docs/prompts/`) under the index as "Build history (prompts used)"; keep them, they are part of the project history, but make sure they contain no secrets and no real names.
   - Make sure the repository root has a short `README.md` pointing to `docs/README.md` (create it only if missing; do not overwrite an existing one, extend it).
   - Mark clearly in the index which features are **verified on a phone** (the owner tested the app and dashboard on 2026-11-24) and what remains open for the other developers (copy from `HANDOVER.md` section 5: HTTPS, `ALLOWED_HOSTS`, terrain routes, scheduled backups, change-password screen in the phone app, magnetic vs true north, Wi-Fi from the phone, heading fix still off pending a field test).
3. Commit the docs organisation on `main` (small commits, plain messages). Re-run the secret scan on the new commits.

## Stop point 1: ask the owner
Show: the commits that will be pushed (`git log --oneline origin/main..main`), the scan result, test numbers. Ask: "Push to your personal repo `origin` main now?"

## Phase G3: personal repo (`origin`, `sanjeevani1912/VisualizeAR`)
1. `git push origin main` (a normal push; it should be a fast-forward from `07051af`). If rejected because `origin/main` has commits that local `main` lacks: do NOT force; fetch, show what is there, and ask.
2. Tag: `git tag -a demo-v2 -m "..."` on `main`, then `git push origin demo-v2`. Keep `demo-v1`.
3. Also push `release/final` only if the owner wants a backup of that branch name (optional, ask). Verify with `git ls-remote origin main demo-v2` and report the commit hashes.

## Stop point 2: ask the owner before the team repo
Show exactly what would be pushed to `army/main`. First `git fetch army` and report the relationship (`git rev-list --left-right --count main...army/main`):
- If `army/main` is an **ancestor** of local `main` (0 on the army side): a normal fast-forward push is safe. Show the commit list and the number of files changed (`git diff --stat army/main..main | tail -3`) and any file over 5 MB.
- If `army/main` has commits that local `main` does not have (other developers pushed): STOP. Do not force. Explain in plain words and propose the safe option (merge `army/main` into a new branch `main-merge`, run tests, then ask) or a push to a side branch `sanjeevani/final` plus a pull request.
- If the `army` remote is unreachable or the push is denied (permissions): report it; do not look for ways around the permission.
Ask: "Push to the army team repo `main` now?" and wait for an explicit yes.

## Phase G4: team repo (`army`)
Only after the explicit yes: `git push army main:main` (normal push, no force). If it is refused, stop and report the exact message. Verify with `git ls-remote army main` and compare to local `main`.
Tag only if the owner asks (`git push army demo-v2`).

## Final report (in the chat and saved as `docs/PUSH-REPORT.md`, committed on `main` and pushed with the next push only if the owner wants; otherwise just tell the owner)
A short table: remote, branch, commit hash, pushed yes/no, how verified. The test numbers. The secret-scan result (counts only). What is still open (the list above). Where the final code is and how to clone it
(`git clone <url>` then `docs/README.md`). Keep it short and honest.
