# DhruvAR — Work from home + demo tomorrow (guide for 2026-11-10)

Read top to bottom. Part 3 is the evening plan. Part 4 is tomorrow's demo.

---

## Part 1 — What is on GitHub, and how the branches are divided

### Two GitHub repos (called "remotes" in git)

| Remote name | Address | What it is |
|---|---|---|
| `origin` | github.com/sanjeevani1912/VisualizeAR | Your own repo. It is **old** (last commit `a04f9ec`, 13 commits behind your `main`). Not used for the demo. |
| `army` | github.com/vide123456789/DhruvAR | The **team repo**. Your colleague's work is here. Your `main` tracks `army/main`. |

### Branches on the team repo (`army`)

| Branch | What it contains |
|---|---|
| `main` | Everything up to commit `8879061` (app sign-in and send-for-approval). The stable base. |
| `feat/geo-anchor` | Same as `main`, an older pointer. |
| `feature/open-os-geo-ar-phase0` | First colleague commit: "updating to non-ar-core". Early foss start. |
| `feature/new-ar` | **Colleague's newest work**: the foss flavor, sensor AR, terrain, plus VIO (visual-inertial tracking) added on 5 Nov. 27 commits ahead of `main`. Still in progress. |
| `claude/clever-edison-vzj9no` | Another branch with the colleague's work plus cleanups (23 commits ahead). |
| `sanjeevani/demo-main` | **Pushed today by you**: your `main` with the audit docs, design docs and this guide. This is your demo version (ARCore only). |
| `sanjeevani/django-backend` | **Pushed today by you**: the Django backend scaffold. |

Local-only branches on your office PC that are **not** on any GitHub repo: `cursor/gated-planning-setup`, `ui/professional-guide`. They are older UI experiments. Ignore them for the demo.

The tag `demo-safe` exists only on the office PC. On GitHub, the same commit is the tip of `sanjeevani/demo-main` at the time of pushing.

### What is NOT on GitHub (git ignores these on purpose)

| Item | Why it matters | What to do at home |
|---|---|---|
| `local.properties` | Holds the Android SDK path, specific to a PC | Android Studio recreates it when you open the project |
| `server/data/` (database, uploaded images, `seed-credentials.txt`) | Your sample data and passwords | Re-create with `npm run seed` |
| `node_modules/` in `server/` and `web/` | Installed packages | `npm install` |
| `web/dist/` | Built dashboard | `npm run build` (only if you want a single-server setup) |
| `app/build/`, `.gradle/` | Build output | Created by the first build |
| `server-django/.venv` | Python packages | Not needed for the demo |

---

## Part 2 — What to install on the home laptop

| Tool | Version | Check with |
|---|---|---|
| Git | any recent | `git --version` |
| Node.js | **22.13 or newer** (the server needs built-in SQLite) | `node --version` |
| Android Studio | recent (includes the JDK and `adb`) | open it once |
| Google account on the Motorola | needed for ARCore | — |

Python and Docker are **not** needed for tomorrow.

---

## Part 3 — Tonight, at home (about 2–3 hours)

### Step 1 — Get the code (10 min)
```bash
git clone https://github.com/vide123456789/DhruvAR.git
cd DhruvAR
git checkout sanjeevani/demo-main
git log --oneline -3          # top commit should be a docs commit on top of 8879061
```
If asked to sign in, use the GitHub account that has access to the team repo.

### Step 2 — Start the server (10 min)
```bash
cd server
npm install
npm run seed                  # creates users and sample plans; passwords go to server/data/seed-credentials.txt
npm run dev                   # API on http://localhost:4000 — leave this window open
```
Open `server/data/seed-credentials.txt` to read the passwords. Roles: `menon` and `sharma` are reviewers, `iyer` is admin, `nair` is auditor, `singh`, `rao`, `bose`, `khan` are planners.

Seed dates are fixed at the time you run `seed`. If you run it tonight, everything looks fresh. **Run `npm run seed -- --reset` again the night before the demo** so no plan shows as "overdue". (`--reset` wipes all data and makes new passwords. Read the new credentials file afterwards.)

### Step 3 — Start the dashboard (5 min)
In a second terminal:
```bash
cd web
npm install
npm run dev                   # http://localhost:5173
```
Open `http://localhost:5173`, sign in as `menon` (reviewer). Click through Overview, Plans, one plan, Activity log. The dashboard talks to the API on port 4000 by itself.

### Step 4 — Open the Android project (15 min)
1. Android Studio → Open → choose the repo root folder (the one containing `app/` and `gradle/`).
2. Let Gradle sync finish. Accept the SDK/JDK installs it asks for.
3. If it asks for the Android SDK path, accept the default. This creates `local.properties`.

### Step 5 — Connect the Motorola (10 min)
1. On the phone: Settings → About phone → tap **Build number** 7 times → Developer options → turn on **USB debugging**.
2. Plug it into the laptop by USB and accept the "Allow USB debugging" prompt.
3. In Android Studio the phone should appear in the device list at the top.
4. Make sure the phone also has the **Google Play Store** and install **Google Play Services for AR** from the Play Store (search "Google Play Services for AR"). If the Motorola is not on Google's ARCore supported list, the app shows "ARCore Unsupported" and only the 2D planning part works. In that case tell me and we will plan around it.

### Step 6 — The map at home (important)
The map server lives on the office PC, so at home the map will be blank. Two ways:

- **Easy (recommended at home): use the online map.** In `app/build.gradle.kts` change this line to an empty string:
  ```kotlin
  buildConfigField("String", "MAP_SERVER_BASE", "\"\"")
  ```
  The app then uses the free OpenFreeMap online style. Place search is switched off. Needs internet on the phone. **Do not commit this change**; it is only for home testing.
- Do nothing, accept a blank map, and test everything else.

### Step 7 — Build and run (10 min)
Press the green Run button in Android Studio with the Motorola selected. First build takes a few minutes.

### Step 8 — Point the app at the server
The phone and laptop must be on the **same home Wi-Fi**.
1. Find the laptop's address: `ipconfig` (Windows) and look for the IPv4 address, for example `192.168.29.15`.
2. On the app's sign-in screen, type the server address: `http://192.168.29.15:4000` (your number).
3. Sign in as a planner, for example `singh` with the password from the credentials file.

If the phone cannot reach the laptop, use the cable instead:
```bash
adb reverse tcp:4000 tcp:4000
```
Then type `http://localhost:4000` as the server address in the app. (`adb` is in `C:\Users\<you>\AppData\Local\Android\Sdk\platform-tools\`.)

Quick test from the phone's Chrome: open `http://<laptop-ip>:4000/api/health`. You should see `{"ok":true}`.

### Step 9 — Practise the full flow
1. Phone: sign in as `singh` → plan: draw a region on the map → set an origin → place a few assets → open AR → send for approval.
2. Laptop dashboard: sign in as `menon` → open the new plan → decide (approve/reject/request changes). Reject and "changes" need a reason of at least 10 characters.
3. Phone: check the plan's status updates.
4. Dashboard: open Activity log and check the audit entries.

Write down everything that goes wrong. Send me the list tomorrow and we fix it in order.

### Step 10 — Run the checks (5 min, optional)
```bash
cd server && npm test            # expect 8 passed
cd ../web && npm run build       # expect a successful build
```
Android unit tests: `./gradlew testDebugUnitTest` (use Android Studio's terminal so the JDK is found).

---

## Part 4 — Tomorrow at the office

### The network problem to expect
- The map PC is at **192.168.0.147**.
- The office laptop is at **192.168.1.65**, on a **different network** from the phone and the map PC.

So the phone may reach the map PC but not the laptop's API.

### Safest setup: USB cable for the API
1. Plug the demo phone into the demo laptop.
2. Run `adb reverse tcp:4000 tcp:4000`.
3. In the app, sign in with the server address `http://localhost:4000`.
4. The map still comes from the map PC over Wi-Fi, so before the demo build set in `app/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "MAP_SERVER_BASE", "\"http://192.168.0.147:8088\"")
   ```
   and in `app/src/main/res/xml/network_security_config.xml` replace the host `192.168.1.205` with `192.168.0.147`.
5. Test the map in the phone's Chrome first: `http://192.168.0.147:8088/style.json` must load.

### If the demo runs on your Motorola from the home laptop instead
Skip the map PC. Use the online map (Step 6) and your own hotspot or Wi-Fi.

### The night before
- [ ] `npm run seed -- --reset`, then read the new passwords.
- [ ] Phone fully charged, Developer options still on, Play Services for AR installed.
- [ ] Laptop charger and USB cable in your bag.
- [ ] A screen recording of one good run on your phone, as a last resort.

### Demo flow (about 8 minutes)
1. **The problem (30 s):** field planning of positions on a map, hard to visualise and approve.
2. **Phone (3 min):** sign in as planner → region on map → origin → assets → AR view.
3. **Send (30 s):** send for approval.
4. **Dashboard (3 min):** sign in as reviewer → open the plan → show the asset table, map, field images, decision → approve. Show the audit trail.
5. **Roadmap (1 min):** ARCore build now; foss build for devices without Google services; backend moving to Django, PostgreSQL and Nginx, open-source only.

### Be honest about
- AR accuracy depends on phone GPS and compass; the dashboard says "not survey grade".
- The foss build is in progress; the demo uses ARCore.
- Plain `http` is used only for the local demo; deployment will use HTTPS.

---

## Part 5 — If something breaks

| Problem | Likely cause | Fix |
|---|---|---|
| Blank map | Map server not reachable | Test its `/style.json` in phone Chrome; use the online map |
| Sign-in fails, "cannot reach server" | Phone and laptop on different networks, or server not running | `adb reverse`, or same Wi-Fi; check `http://<ip>:4000/api/health` |
| "ARCore unsupported" or "Install ARCore" | Phone not on ARCore list or no Play Services for AR | Install it from Play Store; otherwise show 2D planning |
| Everything shows overdue | Old seed dates | `npm run seed -- --reset` |
| Locked out for 15 minutes | 5 wrong passwords | Wait, or `--reset` the seed |
| Gradle cannot find Java | JDK not set | Run builds from Android Studio's terminal |
| `npm run seed` says users exist | Already seeded | Use `-- --reset` (wipes data) |
| Dashboard says 401 / signed out | Session expired (60 min idle) | Sign in again |

---

## Part 6 — Working on the code at home

- **Dashboard (web):** edit files under `web/src/`. The page reloads by itself while `npm run dev` is running.
- **Server:** edit `server/src/`. `npm run dev` restarts on save.
- **Android:** edit under `app/src/main/`. Press Run again to reinstall.
- **Save your work:** `git add <files>`, `git commit -m "..."`, then `git push` to a branch of **your** name (for example `sanjeevani/demo-main`). **Never push to `army/main`** or to your colleague's `feature/new-ar` without telling them.
- **Get your colleague's work later:** `git fetch`, then look at `army/feature/new-ar`. Merge it on a **separate branch** (see MASTER-PLAN phase P4), never straight into your demo branch.
- **Django backend:** `git checkout sanjeevani/django-backend`, then see `server-django/PLAN.md`. Not needed for tomorrow.

## Part 7 — What comes after the demo
Follow `docs/prompts/demo-and-deployment/MASTER-PLAN.md`: safe merge of the foss work (P4), the Django backend (P6), PostgreSQL, Nginx and Docker (P7), then real-device foss testing (P8).
