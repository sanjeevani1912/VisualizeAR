# Session D: Android app, connect to the new stack and check the heading

Paste `00-SHARED-CONTEXT.md` first. Working folder for this chat: `C:\Users\itcell\AndroidStudioProjects\DhruvAR`
(you create your own worktree, below). Updated 2026-11-20 after the stack was wired.

## Goal
Make the **Android app** talk to the new one-address stack (`http://<host>:8000`: dashboard `/`, API `/api/`, map `/map/`, backed by Django +
PostgreSQL), so the owner can send a plan from a real phone and see it on the dashboard. Also settle the heading question between the map and AR.
You may not be able to run an Android device yourself: build, run unit tests, and give the owner precise on-phone test steps.

## What exists now (verified)
- Branch `integration` (pushed as `army/sanjeevani/integration`, commit `d6f3172`) = `main` + map/Nginx setup + Django/PostgreSQL backend + dashboard
  icons/basemap + the Docker stack. **Read `docs/STACK.md` (section 8 and 9) and `docs/MAP-AND-NGINX.md` (section 6) first.**
- The stack runs on this PC right now at `http://localhost:8000` (containers `dhruvar-nginx-1`, `dhruvar-django-1`, `dhruvar-postgres-1`, plus the map
  server containers on 8088). Sample users exist; passwords are in a git-ignored file named in `docs/STACK.md`; do not print them.
- What the stack gives the app:
  - Server address (what the user types on the sign-in screen): `http://localhost:8000` over USB (`adb reverse tcp:8000 tcp:8000`), or
    `http://<PC LAN address>:8000` (this PC's cable address was `192.168.1.42`; may change). The app appends `/api/...` itself
    (`ServerClient.kt`: `$baseUrl/api/auth/token`, `/api/plans`, ...). App sign-in route: `POST /api/auth/token` (Bearer token, no cookie, no CSRF header).
  - Map address (`BuildConfig.MAP_SERVER_BASE`, `app/build.gradle.kts` line ~35, currently `http://192.168.1.205:8088`, an old PC): `http://localhost:8000/map` (USB) or
    `http://<PC LAN address>:8000/map`. Nginx rewrites the URLs inside `style.json` to whatever address the client used, so the app's `MapStyleRewriter`
    (which only handles `http://localhost:8080` placeholders) has nothing to do; check that it does no harm.
  - Search is `GET {mapBase}/search?q=...` through the same address.
- The app's network config: `app/src/main/res/xml/network_security_config.xml` (main/release) allows cleartext only for `192.168.1.205` and `10.0.2.2`;
  `app/src/debug/res/xml/network_security_config.xml` allows cleartext everywhere (debug only). Release builds also refuse server addresses not starting with `https://`
  (`ServerUrl.normalize`).
- Debug default server address: `buildTypes.debug` sets `DEFAULT_SERVER_URL` to `http://192.168.1.214:4000` (an old address).
- The Django session already checked the app's request style (commit "Django: match Node's 2 MB JSON limit; check the Android app's request style").

## Setup (worktree from `integration`, NOT from `main`)
```
git -C C:\Users\itcell\AndroidStudioProjects\DhruvAR worktree add ..\DhruvAR-app -b app/connect-stack integration
```
Set `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` before running `gradlew`. `local.properties` is not in git: create it in the worktree with
`sdk.dir=C\:\\Users\\itcell\\AppData\\Local\\Android\\Sdk`. This `main`-based app has **no product flavors** (the `play`/`foss` flavors live only on the colleague's
branch): use `assembleDebug` and `testDebugUnitTest`. Do not touch flavors, ARCore usage, or `domain/ar/**` from the colleague.

## Rules
- Edit only `app/` (plus your own notes under `docs/`). Do not touch `web/`, `server/`, `server-django/`, `deploy/`, `DhruvAR-friend`, `E:\map\map-server`.
- Do not change the layout JSON schema or how coordinates are stored. Do not change the API contract.
- Never claim compass, GNSS or AR behaviour works unless tested on a device: write "not verified on a device".
- Never commit or push unless the owner says so. Never print passwords. Keep cleartext HTTP limited to listed hosts (never `usesCleartextTraffic="true"` app-wide).
- The owner is a beginner and follows your transcript: explain new ideas in one or two plain sentences, say what you verified and did NOT verify.

## Milestone D1: connect to the stack (do this first, then STOP and report)
1. **Map address changeable without rebuilding.** `MAP_SERVER_BASE` is a build-time constant. Add a user-settable map address (for example a second
   field beside the server address on the sign-in screen, or derived from the server address by default as `<server>/map`; choose the simplest design that
   works with one typed address, explain the choice) persisted like `SessionStore`/`ServerUrl`, validated by a function with unit tests (extend
   `ServerUrlTest`; same normalisation rules), defaulting to the build-time value. `MapBasemapConfig` must read the current value. Check every place that calls
   `MapBasemapConfig` (style, TileJSON, search, offline file) and the map view's style loading (`PlanningMapView.kt`, `MapStyleLoader`, `MapStyleRewriter`).
2. **Cleartext for new hosts in release config.** Add `localhost` (USB over adb reverse) to the main `network_security_config.xml`; for a LAN address say how the owner
   adds it (Android cannot take wildcards for IPs; propose a clear, documented procedure, or keep release builds `https`-only and say what that means). Debug builds
   already allow everything.
3. **Stale defaults.** Replace the debug `DEFAULT_SERVER_URL` (`http://192.168.1.214:4000`) with an empty value (user types it) or `http://localhost:8000`, and update
   `MAP_SERVER_BASE`'s default to `http://localhost:8000/map`. Explain the effect on a fresh install.
4. **Build and test.** `gradlew assembleDebug testDebugUnitTest` must pass; report the numbers. Run the relevant tests you changed.
5. **Prove what can be proven without a phone:** from this PC, using the same URLs the app builds, `curl` the sign-in (`POST /api/auth/token`, a known sample user:
   read the password from the git-ignored file named in `docs/STACK.md`, do not print it), `GET /api/plans` with the Bearer token, and the map URLs
   `<map>/style.json`, `<map>/tiles/india.json`, `<map>/search?q=delhi`. Confirm each answer is what the app's parser expects (`ServerClient.kt` parsing).
6. **Write the owner's on-phone test procedure** in `docs/APP-PHONE-TEST.md`: USB steps (developer options, `adb reverse tcp:8000 tcp:8000`, install the debug
   APK, type `http://localhost:8000`, sign in as a sample planner, create a plan with a region, origin and a few assets, send it), what to look at on the dashboard at
   `http://localhost:8000` (the plan, region, origin, icons, positions), and a table of "if X happens, then Y". Also say how to do it over Wi-Fi and the known risk
   (the phone's networks were on a different subnet from this PC; Windows Firewall for port 8000 not checked).

## Milestone D2: the heading question (verify before changing; separate STOP)
A code review found: the map and dashboard treat the saved `anchor.headingDegrees` as "the direction +Z points, clockwise from north", but the AR screen passes that
saved heading into `originYawDegreesForMagneticNorth`, which expects the camera's **live** magnetic heading (`ARVisualizationScreen.kt` ~1036-1040;
`ARCoordinateTransformer.kt` ~456-470; `ARRenderer.kt` ~731-764). If the saved heading is not 0, AR and the map may disagree. Also: the heading is magnetic
(`CompassHeadingController`, rotation vector) with no declination correction (`GeomagneticField`) anywhere; `LatLng` and `DeviceLocationFix` check latitude range but not
longitude; `RegionDraftGeometry` uses 111,320 m per degree while the converter uses R = 6,371,008.77 m.
1. Reproduce on paper and with JVM unit tests (`app/src/test`): for a set of headings and camera yaws, compute where an object at (x, z) lands on the map and in AR; show
   whether they agree. Write the result in `docs/HEADING-REVIEW.md`.
2. Only if a mismatch is proven: propose the smallest fix, show it to the owner and wait, then implement with tests. Do not change the layout schema.
3. Say what can only be verified on a phone (compass, AR placement) and give a short field-test procedure.

## Milestone D3: optional, only if the owner asks: phone map snapshot
Capture the planning map as a PNG when sending a plan and upload it as a field image through the existing attachment route (`POST /api/plans/:id/attachments`,
JPEG/PNG/WebP, 10 MB, 30 per plan) with a caption such as "Planning map (phone)". It needs no API change. Make it optional and failure-tolerant (never block sending).
