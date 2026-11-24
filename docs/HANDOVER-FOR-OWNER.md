# For you (the owner): what is finished, what you must do by hand

Written 2026-11-24. Plain words. Longer version for the other developers: `docs/HANDOVER.md`. Status table: `docs/FINAL-STATUS.md`.

## What works (I ran it on your PC)
- The stack at `http://localhost:8000`: dashboard, API, database, map. It survives `docker compose down` then `up -d` (about 25 seconds; never add `-v`).
- 8 demo users and 23 plans in every state, 3 generated pictures (one is the wide panorama). Logins: `docs/DEMO-ACCOUNTS.md`; passwords only in `deploy\demo-passwords.txt`.
- Each role behaves as promised (checked with temporary test users, now deleted). The audit chain verifies.
- A backup is in `E:\dhruvar-backups\` and was restored into a scratch database with the same row counts.
- Tests: Django 253 pass; Android 361 pass; dashboard typecheck clean.

## What only you can do (needs the phone)
1. **Install the APK** built from `DhruvAR-integration` (`docs/DEMO-SCRIPT.md` section 5) and run the demo once with a stopwatch.
2. **Test the things I could not:** AR, panorama capture and "Send to plan", the forced password-change screens in the app.
3. **Heading field test** (branch `app/heading-fix`, folder `DhruvAR-heading`): follow `docs/HEADING-FIX-HOWTO.md` (on that branch: `git show app/heading-fix:docs/HEADING-FIX-HOWTO.md`). If it passes, change one line (`ENABLED = true`) and rebuild. Until then the demo build is unchanged.
4. **Record the backup video** (`docs/DEMO-SCRIPT.md` section 4). 5. **Rehearse once** with the checklist.
6. Decide whether to push to the team repo (`army`). I never did; the command is at the end of `docs/FINAL-STATUS.md`.

## Things to know before you present
- The dashboard must be built with `deploy\build-dashboard.ps1` **in PowerShell**. A plain `npm run build` (or the .sh script in Git Bash, before I fixed it) leaves the map blank. I did this by accident today and fixed it.
- Plans **TP-2026-0146** and **TP-2026-0153** are still waiting for a decision: use them for the live approve/reject. Once decided they cannot be un-decided; before a second show, restore a backup or ask a developer to reset (full restore was not rehearsed).
- Old test plans "helloooo" and "new plannn" are yours; I left them.
- AR may look rotated or mirrored. That is the known heading problem: show the dashboard and the panorama instead.
- Passwords of the 8 demo users were **changed** by my script; the old seed passwords no longer work.

## If you only have 10 minutes
`docker compose ps` (three Up) -> open `http://localhost:8000` -> sign in as `menon` -> open TP-2026-0153 -> Field images -> Activity log (as `nair`) -> Verify now. That is the whole no-phone demo.
