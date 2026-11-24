# DhruvAR documentation index

DhruvAR is a field-planning system. Planners lay out equipment on a map (phone app or web dashboard), reviewers approve the plan, and the phone app shows the approved plan in augmented reality on the real ground. It runs as an Android app plus a Docker stack (Nginx, Django, PostgreSQL, React dashboard) on one PC, with an offline map server beside it.

It is a planning and visualisation prototype, not a surveying system. All names and data in the demo are fictional.

## Folder map

| Folder | What is in it |
|---|---|
| `app/` | Android app (Kotlin, Jetpack Compose, ARCore) |
| `web/` | React dashboard (`web/docs/` has its own notes) |
| `server-django/` | Django API for the stack (`server-django/docs/` has its own notes) |
| `server/` | Old Node API, kept as a fallback (it has the terrain routes) |
| `deploy/` | Docker Compose files, Nginx config, set-up and start scripts |
| `docs/` | This folder |

## Run everything

Start here: [RUN-EVERYTHING.md](RUN-EVERYTHING.md) (one script, `deploy/start-all.ps1`). For the long version see [STACK.md](STACK.md). Sign-in names are in [DEMO-ACCOUNTS.md](DEMO-ACCOUNTS.md); passwords are never in Git (they sit in git-ignored files on the PC).

## Feature -> document

Status: **Phone-tested** = the owner tested it on a phone and the dashboard on 2026-11-24 and said it works. **Open** = see "Still open" below.

| Feature | Document(s) | Status |
|---|---|---|
| Docker stack (Nginx, Django, PostgreSQL, dashboard) | [STACK.md](STACK.md), [RUN-EVERYTHING.md](RUN-EVERYTHING.md) | Phone-tested; HTTPS open |
| Map server and Nginx | [MAP-AND-NGINX.md](MAP-AND-NGINX.md) | Works; Wi-Fi from phone open |
| Users and roles | [USERS-HOWTO.md](USERS-HOWTO.md), [USERS-AND-ROLES-PLAN.md](USERS-AND-ROLES-PLAN.md) | Phone-tested; phone change-password screen open |
| Demo accounts and data (no passwords) | [DEMO-ACCOUNTS.md](DEMO-ACCOUNTS.md) | Done |
| Panorama (capture, send to plan, wide viewer) | [PANORAMA-FEATURE.md](PANORAMA-FEATURE.md) | Phone-tested |
| AR ground fix (steady floor height, shadows) | [GROUND-FIX.md](GROUND-FIX.md) | Phone-tested; switched on |
| AR heading review and fix | [HEADING-REVIEW.md](HEADING-REVIEW.md), [HEADING-FIX-HOWTO.md](HEADING-FIX-HOWTO.md) | Fix is **off** (`ArHeadingFix.ENABLED = false`) until a field test |
| Branding (icon, army logo, avatar) | [BRANDING.md](BRANDING.md), [FieldGuide.md](FieldGuide.md) | Done |
| App phone test | [APP-PHONE-TEST.md](APP-PHONE-TEST.md) | Done |
| Demo script | [DEMO-SCRIPT.md](DEMO-SCRIPT.md) | Done |
| Layout file format | [layout-schema-v2.md](layout-schema-v2.md) | Done |
| Design (HLD/LLD with figures) | [design/DhruvAR-HLD-LLD.md](design/DhruvAR-HLD-LLD.md) | Done |
| Django backend phases | [PHASE-1](../server-django/docs/PHASE-1-AUTH-AND-USERS.md), [PHASE-2](../server-django/docs/PHASE-2-PLANS.md), [PHASE-3](../server-django/docs/PHASE-3-IMAGES.md), [PHASE-4](../server-django/docs/PHASE-4-SEED-AND-CONFORMANCE.md), [PHASE-5](../server-django/docs/PHASE-5-POSTGRESQL.md), [PostgreSQL operations](../server-django/docs/POSTGRESQL-OPERATIONS.md) | Done |
| Dashboard | [DASHBOARD-CURRENT-STATE](../web/docs/DASHBOARD-CURRENT-STATE.md), [BASEMAP](../web/docs/BASEMAP.md) | Phone-tested (dashboard) |
| Handover for developers | [HANDOVER.md](HANDOVER.md) (known gaps in section 5) | - |
| Handover for the owner | [HANDOVER-FOR-OWNER.md](HANDOVER-FOR-OWNER.md) | - |
| Final status | [FINAL-STATUS.md](FINAL-STATUS.md) | - |

Older background: [DhruvAR-Project-Proposal.md](DhruvAR-Project-Proposal.md), [Web-Dashboard-Plan.md](Web-Dashboard-Plan.md), [Map-Server-Configuration.md](Map-Server-Configuration.md), [Map-Server-Handover-Notes.md](Map-Server-Handover-Notes.md), [PMTiles-Configuration.md](PMTiles-Configuration.md).

## Still open for the other developers

From [HANDOVER.md](HANDOVER.md) section 5:

1. HTTPS: the stack is plain http; add TLS and set `COOKIE_SECURE=true`.
2. `ALLOWED_HOSTS` is not enforced by the Django code.
3. Terrain routes exist only in the old Node server, not in Django.
4. Backups are manual; schedule them (a full restore over the live database was not rehearsed).
5. The phone app has no change-password screen.
6. The app uses magnetic north; there is no declination correction.
7. Phone over Wi-Fi is untested (only USB `adb reverse`; check the Windows firewall for port 8000).
8. The heading fix is still off pending a field test.

## Build history (prompts used)

The prompts that were used to build the project step by step are kept as project history in [prompts/](prompts/README.md) (session files in [prompts/sessions/](prompts/sessions/README.md)). They contain local folder paths and branch names, but no passwords or keys.
