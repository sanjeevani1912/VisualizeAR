# Demo audit — 2026-11-06 (phase P1)

Scope: `main` at tag `demo-safe`. Demo phone: Pixel 6a with Google Play Services (confirmed by owner).
Method: dashboard walked in the in-app browser against the local API (reviewer account `menon`; 4 of 5 pages opened:
Overview, Plans list, Plan detail, Activity log, Account). Android was reviewed **in code only**; nothing was run on a phone.
Not yet checked: admin Users page, planner "New plan" page, approve/reject actions, audit verify, image gallery, map view interaction.

## What looks good
- Dashboard loads, signs in, no real errors in the console (the two 401s are the pre-login session check; React Router "future flag" warnings are harmless).
- Plan detail has decision panel, site summary, offline map, assets table, versions, comments, audit trail, and a clear "not survey grade" note.
- Mobile width (375 px) lays out cleanly.
- Seed data is realistic (12 plans in every status, 8 users across 4 roles).

## Findings

| # | Sev | Where | Finding | Suggested fix | Effort |
|---|---|---|---|---|---|
| 1 | Major | Dashboard seed data | Seed dates are 10–14 days old, so **all 6 open plans show "Overdue (> 72 h)"** and "waiting 13 days". The overview looks alarming and unrealistic in a demo. | Make the demo seed relative to today (re-seed with fresh timestamps) just before the demo. | S |
| 2 | Major | Android build config | `DEFAULT_SERVER_URL` in debug is hard-coded to `http://192.168.1.214:4000` (a specific PC on the office LAN). If the demo PC has another IP, sign-in and send fail. | Confirm the demo PC's IP, rebuild or use the in-app server URL setting (`ServerUrl.kt`); pin the PC to a fixed IP or hotspot. | S |
| 3 | Major | Android map | Basemap comes from `MAP_SERVER_BASE = http://192.168.1.205:8088`, a map server on another office PC. Off that network the map is blank. | Decide the demo network; run the map server on it or use an offline PMTiles fallback; test on the demo Wi-Fi. | M |
| 4 | Major | Android AR (play) | ARCore availability flow exists (supported / needs install / unsupported / checking). Untested on the Pixel 6a: needs a real run for tracking, placement, north alignment and 100 m clipping noted in the friend's plan doc. | Do a full on-device pass in P2 and log every issue. | M |
| 5 | Major | Cleartext traffic | App uses plain `http` to LAN servers via `network_security_config`. Works for a LAN demo but is not acceptable for deployment. | Keep for demo; plan HTTPS behind Nginx in P7. Mention it honestly. | S (demo) |
| 6 | Minor | Dashboard overview | Several seeded "Signed in" rows dominate "Recent activity"; the plan story is less visible. | Filter sign-ins out of the overview feed or show them dimmed. | S |
| 7 | Minor | Dashboard wording | Header banner says "RESTRICTED", and ranks/names are placeholder personas. Fine for demo but confirm with the senior that this labelling is acceptable. | Confirm wording. | S |
| 8 | Minor | Dashboard bundle | Production JS is about 1 MB (295 KB gzip). Slow on a poor network. | Code-split later; not needed for demo. | M |
| 9 | Minor | Large files | `ARVisualizationScreen.kt` (1793 lines) and `PlanningViewModel.kt` (1692 lines) are very large; risky to change quickly. | Avoid refactors before the demo; touch narrowly. | – |
| 10 | Info | Flavors | `main` has no play/foss flavors; the friend's branch does and moves ARCore files into `src/play/`. Do not merge before the demo (P4 decides). | Follow P4 on a separate branch. | – |

No TODO/FIXME markers were found in the Android sources.

## Questions for the owner
1. What network will the demo use (office Wi-Fi, phone hotspot, laptop hotspot)? This decides findings 2 and 3.
2. Which PC will run the server, and will it keep a fixed IP?
3. Should the audience see the app on a mirrored phone screen, or only the dashboard on a projector?

## Next step
Run the debug build on the Pixel 6a and complete the end-to-end path (sign in, plan, region, origin, assets, AR, send), then
add Android findings to this table. That on-device pass is the first task of P2.
