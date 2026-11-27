# Branding (app icon, army logo, user avatar, dashboard logos)

Branch: `brand/logos` (worktree `DhruvAR-brand`). Nothing is committed yet.

## Army logo
The owner's real logo (red flag, gold Ashoka lion capital, crossed swords) is `docs/branding/army-logo.png`. `make-icons.py`
finds the red rectangle, crops off the white margin and thin dark border (4px inset), keeps the 4:3 shape (no distortion or
recolouring) and writes it to `app/src/main/res/drawable-nodpi/army_logo.png` and `web/public/army-logo.png`.
To swap it later: replace `docs/branding/army-logo.png` and run `python docs/branding/make-icons.py`. Shown 44dp/px tall in headers, 72 on login.

## What changed
**Android app**
- Launcher icon: adaptive icon (blurred-terrain background + pin/grid/"AR" foreground inside the 66dp safe zone), plus legacy `ic_launcher(.round).webp` at mdpi-xxxhdpi. The white border was measured (art starts at pixel 87 of 1254) and cropped.
- Home screen top bar: army logo top left, round avatar with the user's initials top right (tap = the same menu as before: refresh approval status / Sign out). Signed out, the old person icon still opens Sign in.
- Sign-in screen: the DhruvAR logo replaces the padlock tile.
- Other screens (planning, AR, field guide) are unchanged. AR screen was not touched.
- Test: `UserInitialsTest`.

**Dashboard (`web/`)**
- New top bar on every signed-in page: army logo, DhruvAR logo + name on the left; initials circle, name/rank, role and Sign out on the right (sidebar no longer repeats them).
- Login page (live and demo): centred card with both logos, "DhruvAR / Command Dashboard".
- Favicon, apple-touch icon, theme colour. Logos are small PNGs in `web/public/`. Phone width works (role text hides, avatar + Sign out stay).

## Files
- `docs/branding/`: `app-icon-source.webp`, `make-icons.py`, `app-icon-512.png` (store icon), `old-icons/` (backup of the previous icons)
- App: `ui/components/AppTopBar.kt`, `AccountMenuButton.kt`, `ui/screens/HomeScreen.kt`, `SignInScreen.kt`, `res/mipmap-*`, `res/drawable-nodpi/*` (adaptive layers, `logo_dhruvar.webp`, `army_logo.png`), old `drawable/ic_launcher_*.xml` removed
- Web: `index.html`, `src/branding.ts`, `src/components/Layout.tsx`, `src/pages/Login.tsx`, `src/styles.css`, `src/util.ts` (`initials`), `public/*`

## Regenerate icons
`pip install pillow numpy` then `python docs/branding/make-icons.py` (Pillow 13 was used).

## Verified
- `gradlew testDebugUnitTest assembleDebug`: build OK, 366 unit tests, 0 failures.
- Dashboard: typecheck and `npm run build` OK (the web project has no test runner, so no web test was added).
- Browser at desktop and 375px width: login page and demo home page look right; the only console errors are 500s from the `/api` proxy because no API ran on the dev port.

## NOT verified
- Not installed on a phone: icon under different launcher shapes (circle, squircle, themed icons) and the header on small screens are untested. The icon has no monochrome (themed) layer.
- Live (non-demo) dashboard home was not viewed, only the demo one (same layout).
- The running Docker dashboard was not changed.

## Install the APK
`& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r C:\Users\itcell\AndroidStudioProjects\DhruvAR-brand\app\build\outputs\apk\debug\app-debug.apk`

## See the dashboard in the running stack
After you approve, from the repo that serves the stack run `deploy\build-dashboard.ps1` (rebuilds `web/dist`). Run it in the folder that has this branch's code (merge `brand/logos` or run it from `DhruvAR-brand`).

## Dashboard sign-in page (version B, added 27 Nov 2026)
- Left: field photo (`web/public/login-field.jpg`, cropped from `docs/branding/login-photo-source.webp`), headline and three feature badges.
- Right: army logo top left and DhruvAR logo top right above the sign-in card; icons in the inputs; show/hide password button.
- Code: `web/src/pages/Login.tsx` and `web/src/pages/Login.css` (own `lp-` classes, so the Account and loading screens are unchanged). Sign-in, demo sign-in and error messages work as before.
- The running Docker stack serves `DhruvAR-integration\web\dist` (see `docker inspect dhruvar-nginx-1`). Rebuild into that folder, or restart the stack from this repo, to publish dashboard changes.
