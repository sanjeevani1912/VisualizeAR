# Final status (2026-11-24)

Plain words. "Not verified on a device" means: built and tested on the PC only; the phone, ARCore and compass were not used.

## Pieces

| Piece | State | Where |
|---|---|---|
| Docker stack (Nginx, Django 5.2, PostgreSQL 16, dashboard, map) at `http://localhost:8000` | done, verified (cold `down` / `up -d` keeps data) | `integration`, `docs/STACK.md` |
| Users and roles (rank dropdown, appointment, super admin, forced password change) | done; API and dashboard verified; app screens **not verified on a device** | `integration`, `docs/USERS-HOWTO.md` |
| Panorama (capture, save, open, send to plan, dashboard wide viewer) | dashboard viewer verified; capture and "Send to plan" **not verified on a device** | `integration`, `docs/PANORAMA-FEATURE.md` |
| Demo data (8 plans in every state, comments, 3 generated pictures incl. 2880x1440) | done, verified | `server-django/scripts/demo_data.py`, `docs/DEMO-ACCOUNTS.md` |
| Role click-through | done (API checks 20/22 + 2 wrong expectations; browser pane for the pages) | this file, section "Checks" |
| Backup and restore rehearsal | backup + scratch restore done (8 tables match); **full live replace not rehearsed** | `E:\dhruvar-backups\`, `docs/STACK.md` section 6 |
| Demo script and checklist | written; **not rehearsed with the phone or a stopwatch** | `docs/DEMO-SCRIPT.md` |
| Handover documents | done | `docs/HANDOVER.md`, `docs/HANDOVER-FOR-OWNER.md` |
| AR heading fix | done on its own branch, **switch OFF, not verified on a device, not merged** | branch `app/heading-fix` (commits `ffc7537` and later), `docs/HEADING-FIX-HOWTO.md` on that branch |
| Build scripts for the dashboard | fixed (Git Bash path mangling, npm warning) | `deploy/build-dashboard.*` |
| Pushing to the team repo (`army`) | **not done**, owner's decision | command below |

## Test numbers
Django 253 OK; Android 361 OK (345 on `app/heading-fix`, which has no panorama tests); dashboard typecheck clean and build OK (no dashboard test suite); the Node API tests were not run.

## Checks done in H2 (so you can believe the table)
Five temporary users (planner, reviewer, admin, auditor, super admin; deleted afterwards) and the demo planner: forced password change blocks everything until changed; a new planner sees 0 plans; `bose` sees only 6 of 23; reviewer and auditor see all;
planner, auditor and admin cannot decide; auditor cannot comment; a reject without a reason is refused; planner and reviewer cannot open the Users API; only the auditor, admin and super admin may verify the audit; an admin cannot create an admin, a super admin can; a bad rank is refused;
changing your own role is refused (409). Browser pane: plan page with icons and basemap, panorama viewer, forced-change screen, Users page, Activity log. Browser console had no errors except expected 401/403 from my own earlier wrong-password tries.

## Pushed (only to `origin` = your personal repo `sanjeevani1912/VisualizeAR`)
`main`, `integration`, `app/connect-stack`, `app/heading-fix` and tag `demo-v1`. Nothing was pushed to `army`. The exact `git ls-remote origin` result is in the chat report.

## What you must still do by hand
1. Install the APK from `DhruvAR-integration` and run the demo once with a stopwatch.
2. Phone tests: AR, panorama capture and "Send to plan", forced password-change screens.
3. Heading field test, then (if it passes) set `ArHeadingFix.ENABLED = true` on `app/heading-fix` and rebuild.
4. Record the backup video. 5. Rehearse once.
6. Decide about the team repo. To push (only if you want to): `git push army main:sanjeevani/demo-v1` is the safest (a new branch name, nothing overwritten); the team can then open a pull request. I did not run it.

## What the other developers must do
Everything in the gaps table of `docs/HANDOVER.md` section 5: TLS first, then `ALLOWED_HOSTS`, terrain routes in Django, scheduled backups, remove plain-text demo password files, the heading fix after its field test, magnetic versus true north, Wi-Fi from the phone, a non-ARCore AR engine for devices without Google services.
