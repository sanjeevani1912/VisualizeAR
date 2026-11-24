# On-phone test: Android app against the one-address stack

Written 2026-11-20 (session D, milestone D1). For a beginner. Branch `app/connect-stack` (worktree `DhruvAR-app`), nothing committed.
The app was **built and unit-tested, and its requests were reproduced with curl from the PC. It has not been run on a phone yet** (section 8).

## 1. What changed in the app, in plain words

- **One typed address.** On the sign-in screen you type only the server address (e.g. `http://localhost:8000`). After a successful sign-in the app
  remembers it and uses `<that address>/map` for the map (style, tiles, search). No rebuild is needed to change it: sign in again with another address.
  *Why this design:* the stack publishes the dashboard, `/api` and `/map` at one address, so a second field would only be another thing to mistype.
  Before the first sign-in (and after a fresh install) the map uses the built-in default `http://localhost:8000/map`.
- **Stale addresses removed.** The old map PC (`192.168.1.205:8088`) and old debug server (`192.168.1.214:4000`) are gone.
  Debug build: the sign-in screen starts pre-filled with `http://localhost:8000`. Release build: empty (you type an `https://` address).
- **Cleartext (plain `http://`) rules.** *Cleartext = unencrypted web traffic; Android blocks it unless a host is listed.*
  Debug builds allow any host (the file `src/debug/res/xml/network_security_config.xml`). Release builds now allow only `localhost`, `127.0.0.1`
  (USB) and `10.0.2.2` (emulator); release also refuses non-`https` server addresses. A Wi-Fi PC address in a release build needs one extra line
  in `app/src/main/res/xml/network_security_config.xml` (Android accepts no wildcards for IP addresses) and a rebuild, or an HTTPS front door.

## 2. What you need

- The stack running on this PC (`docker compose ps` in `DhruvAR-integration\deploy` or the stack folder: nginx, django, postgres Up) and the map server running.
- The Motorola phone, a USB cable that carries data (not charge-only), **Developer options** and **USB debugging** switched on
  (Settings > About phone > tap *Build number* 7 times; then Settings > System > Developer options > USB debugging).
- Android Studio (it has the build tools and `adb`, the program that talks to the phone over USB).

## 3. Build and install the debug app

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\itcell\AndroidStudioProjects\DhruvAR-app
.\gradlew.bat assembleDebug
```
The APK appears at `app\build\outputs\apk\debug\app-debug.apk`. Plug in the phone, accept the "Allow USB debugging?" prompt on it, then:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```
It must list the phone as `device` (not `unauthorized`).
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```
(Or press Run in Android Studio with the worktree `DhruvAR-app` open and the phone selected.)

## 4. USB test (recommended first)

`adb reverse` makes the phone's own `localhost:8000` lead to port 8000 on this PC, through the cable, so no Wi-Fi is involved.
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
```
Run it again whenever the cable is replugged or adb restarts. Sanity check on the phone: open Chrome and visit `http://localhost:8000/api/health` (should show an "ok").

Then in the app:
1. Open the app; open **Sign in** (person icon on Home, or tap *Send for approval*, which asks you to sign in).
2. *Server address*: `http://localhost:8000` (already filled in the debug build).
3. Sign in as a sample **planner**: `rao`, `bose`, `singh` or `khan`. Passwords are in the git-ignored file in the Django container; show it **only** when you need it:
   ```powershell
   docker exec dhruvar-django-1 cat /data/seed-credentials.txt
   ```
   (Do not paste it into chats or commit it.)
4. Plan: open the map, search a place (e.g. "Delhi"), set a **region** (box), set the **origin** (a point inside the box), add a few assets
   (use at least three different types, e.g. a vehicle, a gunship, a K131), move or rotate one.
5. **Send for approval**. Expect a "sent" message and a plan code like `TP-2026-01xx`.

## 5. What to look at on the dashboard

On the PC open `http://localhost:8000`, sign in as a reviewer (`menon` or `sharma`; same password file). Open the new plan (its code is on the phone).
- Status **Submitted**, planner name = the phone user, your note shown.
- The **basemap** is visible (streets, not a plain beige square) and the **region box** sits where you drew it.
- The **origin** marker is where you set it, inside the box.
- Each asset shows its **own icon** (6 app types have distinct icons), positioned and turned like on the phone. Pick one asset and check it is the
  same distance and direction from the origin as on the phone.
- The plan **audit trail** has a "submitted" entry by your user.
Phone side: the plan shows as sent/submitted; after a reviewer approves or rejects on the dashboard, use the app's refresh and check the status changes.

## 6. If X happens, then Y

| What you see | Likely cause and what to do |
|---|---|
| `adb devices` shows nothing / `unauthorized` | Cable is charge-only, USB debugging off, or you did not accept the prompt on the phone. Replug, set USB mode to "File transfer", accept the prompt. |
| Sign-in says it cannot connect / timeout | `adb reverse` not active (run it again), or the stack is down (`docker compose ps`). Test `http://localhost:8000/api/health` in the phone's Chrome first. |
| "Incorrect username or password" | Wrong password, or you typed a reviewer name; five wrong tries lock that user for 15 minutes (a "too many attempts" message). Wait or use another user. |
| Sign-in works but map is blank / "Map server unreachable (...)" | The map server on port 8088 is down (`docker compose ps -a` in `E:\map\map-server`), or the saved address is wrong: sign in again with the correct address. Check `http://localhost:8000/map/style.json` in the phone's Chrome. |
| Map shows but no search results | Search container down: `curl http://localhost:8000/map/search?q=delhi` on the PC. |
| "Set a region and an origin before sending" | The plan needs both; set them first. |
| Send fails with an error message in red | Read the message (it is the server's own text). Look at `docker compose logs django` on the PC. |
| Plan is on the dashboard but an icon or position looks wrong | Note which asset, its type and screenshots of phone and dashboard; this is a finding, do not change data. |
| Release build refuses `http://` | By design: release needs `https://`. Use the debug build for this test. |

## 7. Wi-Fi test (after USB works)

1. Phone and PC must be on the **same network**. Known risk: earlier notes say the phone's networks (`192.168.137.x`, `192.168.0.x`) were not the PC's `192.168.1.x`. If they differ, Wi-Fi will not work; use USB or put both on one network (the same router, or a phone hotspot with the PC joined to it).
2. Find the PC's address (`ipconfig`, the "IPv4 Address" of the adapter on that network; the cable one was `192.168.1.42` on 2026-11-20 and may change).
3. In `DhruvAR-integration\deploy\.env` (or the stack's `.env`) set `LAN_HOST` to it; `docker compose up -d django`.
4. **Windows Firewall for port 8000 has not been checked.** On the phone's Chrome open `http://<PC address>:8000/api/health`. If it does not answer while it works on the PC, add an inbound allow rule for TCP 8000 (needs administrator): Windows Security > Firewall > Advanced settings > Inbound Rules > New Rule > Port > TCP 8000 > Allow, for the Private (and, if your network is "Public", also Public) profile.
5. In the app sign in with server address `http://<PC address>:8000`. The map then uses `http://<PC address>:8000/map` automatically (Nginx rewrites the addresses inside the map style to the one you used: verified with curl for `192.168.1.42`).
6. Debug builds accept this. A release build would need that IP listed in `network_security_config.xml` or HTTPS.

## 8. What was verified, and what was NOT

**Verified on 2026-11-20 (PC only)**
- `gradlew assembleDebug` and `testDebugUnitTest`: see the numbers in the report that came with this note (also in section 9).
- With curl and the same URLs the app builds, through `http://localhost:8000`:
  - `POST /api/auth/token` (sample planner `rao`): 200, JSON with `token`, `user{id,username,name,rank,role}`, `idleMinutes`; the app's parser reads exactly `token` and those user fields. A wrong password gives 401 `{"error":"Incorrect username or password"}`.
  - `GET /api/plans` with the Bearer token: 200, a list of 3 plans; each has `id`, `code`, `status`, `versions` (the fields the app reads).
  - `GET /map/style.json` (200, 6,897 bytes), `/map/tiles/india.json` (200), `/map/search?q=delhi&limit=2` (200, `hits[]` with `name` and `_geo{lat,lng}`, the shape `PlaceSearchResponseParser` expects).
  - The style's glyph/tile addresses already carry the address used: `localhost:8000/map/...`; with `Host: 192.168.1.42:8000` they carry `192.168.1.42:8000/map/...`. The app's `MapStyleRewriter` finds no `localhost:8080` placeholder, so that step does nothing, and it still inlines the tile template (its source address starts with the map address). Covered by the existing `MapStyleRewriterTest`.
- Unit tests added: `ServerUrlTest` (map address = server + `/map`, typed map address rules).

**NOT verified**
- Anything on a real phone or emulator: install, sign-in screen, map drawing, search, sending a plan, AR, compass, GNSS.
- `adb reverse` and the Wi-Fi path; Windows Firewall for port 8000; a second device on the network.
- Sending a plan (`POST /api/plans`) was not exercised by me; the Django session and the stack session verified it with an app export.
- Release build behaviour (only `assembleDebug` was built).
- The map persisted address after app restart (code path is simple but not run).

## 9. Build record

`gradlew assembleDebug testDebugUnitTest` (JAVA_HOME = Android Studio's jbr): **BUILD SUCCESSFUL** (3 min). 334 unit tests, 0 failed, 0 errors, 0 skipped
(including 3 new ones in `ServerUrlTest`). The debug APK is `app\build\outputs\apk\debug\app-debug.apk` (about 168 MB).
