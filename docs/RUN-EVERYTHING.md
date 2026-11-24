# DhruvAR: how to start, use and stop everything (PowerShell)

Use this every time you want to run the demo. Run each command in **PowerShell**, in this order.
Docker Desktop must be running first (wait until its whale icon says "running").

Folders used:
- Map server: `E:\map\map-server`
- Stack (database + API + dashboard + Nginx): `C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration\deploy`
- Final code (branch `release/final`): `C:\Users\itcell\AndroidStudioProjects\DhruvAR-final`

---

## 1. Start the map server
```powershell
Set-Location E:\map\map-server
```
```powershell
docker compose -f docker-compose.yml -f C:\Users\itcell\AndroidStudioProjects\DhruvAR-infra\deploy\mapserver.override.yml up -d
```

## 2. Start the stack
```powershell
Set-Location C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration\deploy
```
```powershell
docker compose up -d
```

## 3. Check everything is up
You should see three `dhruvar-*` containers (postgres says "healthy") and three `map-server-*` containers.
```powershell
docker ps --format "table {{.Names}}\t{{.Status}}"
```
Health check (should print `{"ok":true}`):
```powershell
Invoke-RestMethod http://localhost:8000/api/health
```

## 4. Open the dashboard
```powershell
Start-Process http://localhost:8000
```
Sign in with a demo account. Names are in `docs\DEMO-ACCOUNTS.md`. Passwords are in the git-ignored files named there. Never paste passwords into chats or Git.

## 5. Connect the phone (ADB)
ADB is Android's command-line tool. The phone must be plugged in by USB with USB debugging on.

Allow the phone to reach the stack through the cable (run it again every time you unplug and replug the phone):
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
```
Check the phone is seen (should list one device marked `device`):
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```
In the app, the **server address** is: `http://localhost:8000`

### Install or update the app on the phone
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r C:\Users\itcell\AndroidStudioProjects\DhruvAR-final\app\build\outputs\apk\debug\app-debug.apk
```
Always install from the `DhruvAR-final` folder. APKs from other folders (`DhruvAR-app`, `DhruvAR-ground`, `DhruvAR-brand`) have only part of the work and replace the full app.

---

## 6. If you changed the dashboard code: rebuild it
```powershell
& C:\Users\itcell\AndroidStudioProjects\DhruvAR-final\deploy\build-dashboard.ps1
```
```powershell
Copy-Item C:\Users\itcell\AndroidStudioProjects\DhruvAR-final\web\dist\* C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration\web\dist -Recurse -Force
```
Then reload the page with Ctrl+F5. The stack serves the files from `DhruvAR-integration\web\dist`.

## 7. If you changed the app code: rebuild the APK
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; Set-Location C:\Users\itcell\AndroidStudioProjects\DhruvAR-final; .\gradlew.bat testDebugUnitTest assembleDebug
```
Then install with the command in section 5.

---

## 8. Stop everything (your data stays)
```powershell
Set-Location C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration\deploy; docker compose down
```
```powershell
Set-Location E:\map\map-server; docker compose down
```
**Never add `-v` to `docker compose down`.** It deletes the database and the uploaded images.

## 9. Back up the demo data (do this before big changes)
See `docs\STACK.md` (backup section) and `deploy\backup-stack.sh`.

---

## If something goes wrong
| Problem | What to do |
|---|---|
| Dashboard map is blank / "Basemap unavailable" | The map server is not running: repeat step 1. |
| 502 error on the page | Django is still starting. Wait 10 seconds and reload. |
| Page does not open at all | Docker Desktop is not running, or step 2 was skipped. |
| Phone says it cannot reach the server | Run the `adb reverse` command again (section 5). Check the app server address is `http://localhost:8000`. |
| `adb devices` shows no device or `unauthorized` | Re-plug the cable, unlock the phone, accept the "Allow USB debugging" prompt. |
| Sign-in says too many attempts | The account is locked for 15 minutes. Wait, or use another account. |
| A new password is asked at first sign-in | Normal: accounts made or reset by an admin must set their own password first. |
| Dashboard still shows the old look | Do section 6, then Ctrl+F5. |
| Port 8000 already in use | Another copy is running: `docker ps` and stop the old one (`deploy-nginx-1`). |

## What to keep in mind
- The app and dashboard talk to the same server, so a plan sent from the phone shows on the dashboard.
- Over Wi-Fi the phone may not reach this PC (different network ranges). Use the USB cable for the demo.
- More detail: `docs\STACK.md`, `docs\DEMO-SCRIPT.md`, `docs\HANDOVER.md`.
