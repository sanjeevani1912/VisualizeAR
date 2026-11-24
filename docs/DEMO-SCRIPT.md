# DhruvAR demo script (8-10 minutes) and rehearsal checklist

Written 2026-11-24. Page 1 is the script, page 2 the checklist, then "if X goes wrong", the no-phone plan, and what to record.
All people and plans are fictional. **Passwords are never written here:** they are in `deploy\demo-passwords.txt` (demo users) and
`deploy\admin-credentials.txt` (`stackadmin`). Keep those two files open in Notepad on the laptop, off-screen, before you start.
Words: **plan** = a layout of vehicles and aircraft on a piece of ground; **region** = the box you draw on the map; **origin** = the point you stand on;
**reviewer** = the officer who approves or rejects; **audit trail** = the tamper-evident log of who did what.

---

## PAGE 1. The script

Set-up: **laptop** browser at `http://localhost:8000` (full screen). **Phone** on USB, app open on its Home screen. Two browser windows on the laptop
help: window 1 = reviewer, window 2 = auditor/admin (use a private window so cookies do not mix).

| Min | Who is signed in, where | What you do | Say (one sentence) |
|---|---|---|---|
| 0:00 | nobody; laptop login page | Show the sign-in page. | "DhruvAR lets a field planner build a deployment plan on a phone and send it to a reviewer, with every step logged." |
| 0:45 | **bose** (planner), phone | Sign in (server `http://localhost:8000`). Map: search "Pune", draw the region, set the origin, add 3-4 assets (K-131, vehicle, gunship), turn one. | "The planner chooses a region on an offline map, places assets in metres from the origin, and can look at them through the camera." |
| 2:30 | bose, phone | **Send for approval.** Note the code shown (TP-2026-01xx). | "Sending creates version 1 of the plan; nobody can change it afterwards, only add a new version." |
| 3:00 | **menon** (reviewer), laptop | Sign in. Plans list: the new plan is on top, status Submitted. Open it. | "The reviewer sees only what the planner sent: region, origin, every asset with its own icon on the real map." |
| 4:00 | menon, laptop | Tab **Field images**: open plan **TP-2026-0153** (the panorama), click the wide picture. | "Pictures from the phone, including a wide panorama, sit with the plan so the reviewer sees the ground." |
| 4:45 | menon, laptop | On the new plan: add a **comment**, then **Request changes** (a reason is required) - or **Approve**. | "A decision always carries a reason, and a reviewer can never decide their own plan." |
| 5:45 | bose, phone | Refresh the plan list in the app: status changed. | "The planner sees the decision straight away." |
| 6:15 | **nair** (auditor), laptop window 2 | **Activity log** > **Verify now** shows the chain is intact. | "Every sign-in, submission and decision is chained to the one before; editing history breaks the chain and this check shows it." |
| 7:15 | **iyer** (admin), laptop window 2 | **Users** page: rank dropdown, appointment box, Add a user (do not finish), Reset password button. | "Admins manage accounts; rank and appointment are labels, the role decides what a person may do." |
| 8:15 | bose, phone | **AR** screen on the plan (only if ARCore works on this phone): set origin, walk round the assets. If it looks rotated: say "heading calibration is in field test" and go to the dashboard map. | "The same plan appears in the real world; fine heading calibration is being field-tested." |
| 9:15 | any | Close: dashboard overview. | "Everything runs on one PC with open-source parts and works with no internet." |

Roles that matter for questions: planner sees only own plans; reviewer decides; auditor reads everything but cannot change anything; admin manages users;
super admin also manages admins.

---

## PAGE 2. Checklist (tick each box before the audience arrives)

**Day before**
- [ ] `docker compose ps` in `deploy`: nginx, django, postgres **Up**, postgres **healthy**. `E:\map\map-server`: tiles, search, caddy **Up**.
- [ ] Take a backup: `bash .\backup-stack.sh` from `deploy`, copy `server-django\backups\*` to `E:\dhruvar-backups\`.
- [ ] Sign in once as each of bose, menon, nair, iyer from `demo-passwords.txt`; each works.
- [ ] Phone charged, **USB debugging on**, debug APK from `DhruvAR-integration` installed, cable that carries data.
- [ ] `adb reverse tcp:8000 tcp:8000` done, phone Chrome shows `http://localhost:8000/api/health` = ok.
- [ ] Do the whole script once with a stopwatch. Record the backup video (section 4).

**Before the demo (15 minutes)**
- [ ] Laptop on charger, sleep off, notifications off (Focus Assist), browser zoom 100%.
- [ ] Docker Desktop running (whale icon steady). If not: Cold start below.
- [ ] `http://localhost:8000/api/health` shows `{"ok":true}` on the laptop.
- [ ] Map shows streets on plan **TP-2026-0153** (wait 5 seconds).
- [ ] Phone: `adb devices` shows `device`; run `adb reverse` again if the cable was touched.
- [ ] Phone signed out of the app; screen timeout 10 minutes; Do Not Disturb on.
- [ ] Spare undecided plans for the live decision: **TP-2026-0146** (Submitted) and **TP-2026-0153** (Under review). Once decided they stay decided.
- [ ] Backup video file open in a player on the laptop, ready.

**Commands (PowerShell)**
```powershell
cd C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration\deploy
docker compose ps                                   # status
docker compose down                                 # cold-start step 1 (data is kept; NEVER add -v)
docker compose up -d                                # step 2; wait about 25 seconds
curl.exe -s http://localhost:8000/api/health        # expect {"ok":true}
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
```
Cold start rehearsed on 2026-11-24: `down` then `up -d` -> healthy in about 25 s, sign-in worked, all 23 plans and 3 pictures still there.
Map server (separate folder): `cd E:\map\map-server; docker compose up -d` if `docker compose ps -a` shows it stopped.

---

## 3. If X goes wrong, do Y

| What happens | Do this |
|---|---|
| Phone cannot reach the server / sign-in times out | `adb devices` (must say `device`), then run `adb reverse tcp:8000 tcp:8000` again; open `http://localhost:8000/api/health` in the phone's Chrome. |
| `adb reverse` lost (cable moved, phone slept) | Run the `adb reverse` line again. Takes 2 seconds. |
| Map is blank or beige with "Basemap unavailable" | Map server down: `cd E:\map\map-server; docker compose up -d`. If you rebuilt the dashboard yourself, use `deploy\build-dashboard.ps1` in **PowerShell** (not Git Bash). Reload the page. |
| Docker not running / page does not load | Start Docker Desktop, wait for the whale to stop moving, then `docker compose up -d` in `deploy`. About 1 minute. |
| Login says "Too many failed attempts" (locked) | Wait 15 minutes **or** sign in as another user of the same role. (The lock is per username and device; it ends by itself.) |
| Forgot a password | Passwords are in `deploy\demo-passwords.txt`. To set a new one: `docker compose exec django python manage.py resetpassword --username bose` (asks twice, shows nothing; then the person must choose a new one at next sign-in). |
| Phone has no ARCore / AR will not start | Skip AR. Say "AR needs Google's ARCore; the deployment build uses a different engine." Show the dashboard map and the panorama instead. |
| AR looks rotated or mirrored | Expected: the heading fix is not switched on yet (`docs/HEADING-REVIEW.md`). Say "heading calibration is in field test"; show the dashboard map and the panorama viewer instead. |
| Everything dies | Play the backup video (section 4) and talk over it. |

## Plan with NO phone at all
1. Skip minutes 0:45-3:00 and 5:45. Say "the phone sends the plan; here is one already sent" and open **TP-2026-0146** as **menon**.
2. Run minutes 3:00-7:15 and 8:15's dashboard part exactly as written (map, images, decision with reason, audit verify, users).
3. Show the phone part from the backup video, or from the screenshots in your own phone gallery.
4. To show "a plan arriving" without a phone, run `python server-django\scripts\demo_data.py` (does nothing if the plans exist) - or just use 0146.

## 4. Record the backup video (do this once, after one clean rehearsal)
Record two clips, each 2-4 minutes, and keep them in `E:\dhruvar-backups\video\` (not in Git):
1. **Phone screen** (the phone's own screen recorder, or `scrcpy`): sign in, draw region, set origin, add assets, send, open AR and walk round, take a panorama, **Send to plan**.
2. **Laptop screen** (Xbox Game Bar: Win+Alt+R): reviewer opens the plan, field images, decision with reason, auditor "Verify now", Users page.
Speak the one-sentence lines from page 1 while recording. Do not show `demo-passwords.txt`.

## 5. USB steps (from `docs/APP-PHONE-TEST.md`, short form)
1. Build once: `cd ...\DhruvAR-integration; $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug` -> `app\build\outputs\apk\debug\app-debug.apk`.
2. Phone: Developer options > USB debugging on; plug in, accept "Allow USB debugging?".
3. `adb devices` must show `device`; `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
4. `adb reverse tcp:8000 tcp:8000` (again after any replug).
5. In the app, server address `http://localhost:8000`, sign in as a planner.

## 6. What this script was and was not checked against
Checked on this PC on 2026-11-24 (curl and the browser pane): every dashboard step on page 1 for the reviewer, auditor-style verify, super-admin Users page, the panorama viewer,
the forced password-change screen, cold start. **Not checked, needs the phone:** every "phone" row (sign-in, drawing, sending, refresh), AR, the panorama capture and
"Send to plan", the forced password-change screen in the app, and the exact timing of the 8-10 minutes. Rehearse once with a stopwatch.
