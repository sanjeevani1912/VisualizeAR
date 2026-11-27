# Per-user plans on a shared phone, and the launch splash

Branch: `app/user-plans-splash`. This changes only the Android app. Nothing changed on the server, the API, the layout JSON format, the AR placement code, `web/` or `deploy/`.

## 1. Per-user plans

### The problem
Before this change, a phone kept all plans in one folder. Anyone who signed in on that phone saw every plan made on it, and signing out did not clear the list.

### What changed
- **My plans.** A plan made while you are signed in is saved in your own folder on the phone. The folder is named after your server user id (not your username, so renaming a user keeps their plans). Nobody else who signs in on that phone sees these plans.
- **On this phone** (called "This device" in the code). Plans that were already on the phone before this update, and plans made while nobody is signed in, are "This device" plans. Everyone using the phone can see them.
- **The Home screen** shows one list in two parts:
  - **My plans** with a small count bubble first (only when you are signed in).
  - **On this phone** below it, with a phone icon, a count bubble, an ⓘ button and a fold arrow. Tapping ⓘ explains, in a small box, that these plans were saved without an account and everyone using the phone can see them. Tap the heading to collapse or expand it. The phone remembers this choice separately for each user.
  - When you are signed out, only "On this phone" is shown, with "Sign in to see your own plans" above it.
- **Device plans are view-only when you are signed in.** You can open one on the map and in AR, but you cannot edit, rename, delete or send it. Instead, the menu (⋮) on Home, the copy button in the top bar and the bar at the bottom of the plan all offer **"Make a copy in my plans"**. The copy gets a new id and the name "<name> (copy)", and it is saved in your folder. The original is not changed.
- **Signed out**, device plans work as before: you can edit, rename and delete them. Sending still needs sign-in. If you tap Send while signed out, the app signs you in, **copies the plan into your plans**, then sends the copy. A device plan is never sent directly.
- **Sign-out** clears the plan list and any open plan straight away, goes back to Home, and reloads the list (device plans only). **Sign-in** reloads the list with your plans. The same thing happens when a session expires.
- **Sent-plan records** (the "Sent / Approved / Changes requested" badges) are also kept per user, in one file for each user id. When you are signed out, no badges are shown. The old shared record file is left alone. After you sign in, "Refresh" rebuilds your badges from the server.

### Where the files live on the phone
All of these are in the app's private storage (`/data/data/com.example.dhruvar/files/`). Other apps cannot read them.

| What | Where |
|---|---|
| This device plans (old plans + signed-out plans) | `files/dhruvar_layouts/<plan id>.json` (old files stay exactly where they were and are never moved or deleted) |
| One user's plans | `files/dhruvar_layouts/users/<server user id>/<plan id>.json` |
| One user's sent-plan records | `shared_prefs/dhruvar_sent_plans_u_<server user id>.xml` |
| Old shared sent-plan records (no longer read) | `shared_prefs/dhruvar_sent_plans.xml` (left untouched) |
| "This device" collapsed or expanded, per user | `shared_prefs/dhruvar_home.xml` |

On a debug build you can look at these files with `adb shell run-as com.example.dhruvar ls -R files/dhruvar_layouts`.

### Code
- `data/repository/UserScopedLayoutRepository.kt` (new): the folder rules, view-only check and "copy into my plans".
- `data/repository/DefaultLayoutRepositoryProvider.kt`: now creates the per-user repository.
- `data/remote/SentPlanStore.kt`: one record file per user id.
- `viewmodel/AccountViewModel.kt`: switches folders and records on sign-in, sign-out and expiry. Copies a pending device plan before sending it.
- `viewmodel/PlanningViewModel.kt`: `myPlans` / `devicePlans`, `onAccountChanged`, `copyToMyPlans`, and every edit action is refused on a view-only plan.
- `navigation/AppNavigation.kt`: reacts to account changes. Sign-out goes back to Home.
- `ui/screens/HomeScreen.kt`, `ui/screens/PlanningScreen.kt`: the two sections and the view-only screen.
- `ui/screens/ARVisualizationScreen.kt`: reads the signed-in user's sent-plan record (one line).

## 2. Launch splash

- Every time the app starts, the DhruvAR logo appears in the centre of the app's off-white background, then the normal Home screen opens. There are no onboarding pages.
- It uses the AndroidX SplashScreen library (`androidx.core:core-splashscreen`, pinned to **1.0.1**), so it works on every Android version the app supports (Android 7 and newer).
- On **Android 12 and newer**, the Indian Army logo is also shown small at the bottom (the system "branding image"). Android 11 and older have no place for it, so they show only the DhruvAR logo.
- **Dark mode:** the background becomes the app's near-black (Coal). The logos stay the same.
- **Shown for 2 seconds:** the splash stays on screen for about two seconds (`SPLASH_MILLIS` in `MainActivity.kt`) while the app loads behind it, then Home opens.
- **Not cropped:** the splash cuts its icon to a circle. The logo is drawn at half size in the middle (`res/drawable/splash_logo.xml`), so its rounded corners stay inside that circle. This was checked with a drawn mock-up, not on a phone.
- Files: `res/values/splash.xml`, `res/values-night/splash.xml`, `res/values-v31/splash.xml`, `res/drawable/splash_logo.xml`, `res/drawable/splash_branding.xml`, `AndroidManifest.xml` (activity theme), `MainActivity.kt` (`installSplashScreen()`).

## Build and tests

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew testDebugUnitTest assembleDebug
```

Result on this branch: BUILD SUCCESSFUL. **398 unit tests, 0 failed** (384 tests from `main` + 14 new in `UserPlansTest.kt`).

The new tests check that:
- user A's plans never appear for user B;
- sign-out empties "my plans" and clears the open plan;
- device plans are visible to everyone;
- device plans are view-only when signed in and editable when signed out;
- "copy" makes a new id in the user's folder and leaves the original unchanged;
- sent-plan records are kept per user, and there are none when signed out;
- old files in `dhruvar_layouts/` stay where they are;
- odd user ids cannot escape the `users/` folder.

## Install on the phone

APK: `app\build\outputs\apk\debug\app-debug.apk` (in the `DhruvAR-userplans` folder)

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "C:\Users\itcell\AndroidStudioProjects\DhruvAR-userplans\app\build\outputs\apk\debug\app-debug.apk"
```

`install -r` keeps the existing plans on the phone. They will show up under "On this phone".

## How to test on the phone

1. Install with the command above, then open the app. You should see the DhruvAR logo on the splash, then Home. On Android 12 or newer, the army logo should also appear at the bottom of the splash. Try it again with the phone in dark mode.
2. While signed out, check that the old plans appear under **On this phone** and that "Sign in to see your own plans" is shown.
3. Sign in as planner A. **My plans 0** appears, and the old plans appear below it with "View only".
4. Open a device plan. It opens on the map, and AR works, but there are no edit tools. The bar at the bottom says "View only · saved on this phone". Tap **Make a copy in my plans**. The copy opens and can be edited. Home now shows it under "My plans".
5. Make a new plan as A and save it. Send it for approval.
6. Sign out. Home should go straight back to showing only "On this phone". A's plans must be gone from the list.
7. Sign in as planner B. B must not see A's plans or A's sent badges.
8. Collapse "On this phone" as B, sign out and back in as A. A's choice should not have changed.
9. While signed out, tap Send on a device plan and sign in when asked. The app should copy the plan into your plans and send the copy. The original stays under "On this phone" with no badge.

## Not verified on a device
Everything above was checked only by unit tests and a successful build. None of it has been tried on a real phone yet, including:
- how the splash looks on Android 12+ (including the army logo), on Android 11 or older, and in dark mode;
- the Home sections, view-only screen, copy, sign-out reset and pending send;
- that `install -r` over the current version keeps the existing plans visible under "This device" (the code never moves those files, but this has not been watched on a phone).
