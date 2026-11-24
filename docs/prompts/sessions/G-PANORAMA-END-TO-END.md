# Session G: panoramic image, end to end (capture -> save -> open -> send to plan -> dashboard)

Working folder: create your own worktree from the app branch (it has the stack connection and the heading review):
```
git -C C:\Users\itcell\AndroidStudioProjects\DhruvAR-app worktree add ..\DhruvAR-pano -b app/panorama app/connect-stack
```
Set `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`; create `local.properties` with
`sdk.dir=C\:\\Users\\itcell\\AppData\\Local\\Android\\Sdk` (not in git). No product flavors on this branch: use `assembleDebug` and `testDebugUnitTest`.
For the dashboard part the worktree `C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration` (branch `integration`) holds `web/` and `server-django/`; another session is
editing the Users page there, so make your dashboard changes in **your own** worktree (the app branch also contains `web/`): only touch
`web/src/components/FieldImages.tsx` and what it needs, and say exactly which files you touched.

## Goal
A planner on the phone sweeps a panorama in AR, sees it, saves it, can open it from inside the app, and sends it to the plan on the server with one tap.
A reviewer opens the plan on the dashboard (`http://localhost:8000`) and sees the panorama well (wide image, scrollable/zoomable), with the caption and who sent it.
Everything optional and failure-tolerant: a failed upload never loses the picture and never blocks sending a plan.

## Owner and style
The owner is an intern and a beginner who follows your transcript. Explain each new idea in one or two plain sentences. Say what you verified (unit tests,
curl, dashboard in the browser) and what is "not verified on a device". Never claim camera, ARCore or stitching quality works unless tested on a phone.
**Stop and report after every phase.** No commits or pushes unless the owner says so. Never print passwords or tokens.

## What exists (verified by reading the code on 2026-11-24)
- Panorama capture: button "Panorama" in `app/src/main/java/com/example/dhruvar/ui/screens/ARVisualizationScreen.kt` (~1116-1150): hold and sweep, release; shots from
  `arRenderer.startPanorama()/stopPanorama()` are stitched by `ui/ar/render/PanoramaStitcher.kt` (equirectangular, max 2880x1440) into a Bitmap; a preview dialog (~1240-1352)
  shows it (horizontal scroll) with **Retake** and **Save**.
- Save: `ui/ar/capture/ARCaptureManager.kt` `saveImageToGallery` writes a JPEG (quality 95) via MediaStore to `Pictures/DhruvAR` as `DHRUV_<layout>_<time>.jpg`
  and shows the message "Visualization saved to Pictures/DhruvAR". The returned Uri is not kept and nothing opens the image.
- The app has **no upload code** and no screen to view saved images (grep for attachments/upload/multipart in `app/src/main` finds nothing).
- Server (Django, `server-django/api/views_attachments.py`, routes `plans/<id>/attachments` and `attachments/<id>` in `api/urls.py`): upload JPEG/PNG/WebP, 10 MB,
  30 per plan; who may upload is in `api/rules.py` (~62-68); also check which plan states allow it. Read the route yourself for the exact multipart field names, caption
  limits, the reply JSON, and the download route (bearer token needed to download).
- Dashboard: `web/src/components/FieldImages.tsx` (thumbnails, opens an image, caption, add and remove) with `rules.ts` `canAttach/imageError`, store function `addAttachment`.
- Stack and the app's server address: `docs/STACK.md`, `docs/APP-PHONE-TEST.md` (USB `adb reverse tcp:8000 tcp:8000`, app address `http://localhost:8000`).
  App networking: `data/remote/ServerClient.kt` (OkHttp? check), `SessionStore.kt`, `ServerUrl.kt`.
- Ownership: how the app knows the server's plan id after "Send for approval" (look in the plan send code, `ServerClient`, the layout repository). A plan that has not been sent has no
  server id yet.

## Phase G0: read and design (no code). STOP and report.
1. Read everything above. Write `docs/PANORAMA-FEATURE.md` (in your worktree) with: the flow in a text diagram; the exact upload request (method, URL, headers, multipart field
   names, size/type limits, error replies) taken from `views_attachments.py` and tested with `curl` against `http://localhost:8000` (sign in as a sample planner: password in the
   git-ignored file named in `docs/STACK.md`, never print it); who may upload and when (plan status) from `rules.py`; the size of a real panorama JPEG (estimate from the pixel
   size; say it is an estimate).
2. Decide and explain: (a) when "Send to plan" is offered (only when the AR screen knows the server plan id; otherwise offer "Save only" and tell the user to send the plan first, or
   queue it); (b) the caption default ("Panorama (phone)" plus date); (c) JPEG quality and a size guard (re-compress lower if over 9 MB); (d) what to show on failure and a Retry; (e) whether
   to keep the saved gallery copy (yes: always save locally first, then send).
3. List the files you will change and the tests you will add. Wait for the owner's go-ahead.

## Phase G1: keep and open the saved image. STOP and report.
- Make `saveImageToGallery` return the Uri (and file name); keep it in the AR screen state; after Save show a toast/snackbar with an **Open** action that opens the picture in the phone's
  gallery/viewer with `Intent.ACTION_VIEW` (grant read permission, `image/jpeg`; handle "no app to open" without crashing). Show the saved name and folder in the message.
- Tests: JVM unit test for the pure parts (file name building, intent building if separable). Say what needs a phone.

## Phase G2: upload to the plan. STOP and report.
- New small class (for example `data/remote/AttachmentUploader.kt`) using the same HTTP client, base URL and bearer token as `ServerClient`; multipart upload of the JPEG bytes
  with the caption; typed result (Success(attachmentId) / Failure(reason in plain words: not signed in, plan not sent, too large, not allowed, server unreachable)). Run it off the main thread.
- Compress safely (never hold more than one bitmap copy; recycle). Guard 10 MB.
- UI: in the preview dialog add **Send to plan** (enabled only when signed in and the plan has a server id), a progress state, success message ("Sent to plan TP-... as field image"),
  failure message with **Retry**; the image is already saved locally either way. Same for ordinary AR screenshots (they are field images too).
- Unit tests with a fake server (OkHttp MockWebServer if the project already has it; otherwise a fake interface): success, 401, 413, 403, network failure, request shape (field names,
  Authorization header, content type). Run `gradlew assembleDebug testDebugUnitTest`; report numbers.
- Also upload once from this PC with `curl` using the same request shape to prove the server side accepts it, and look at it in the dashboard.

## Phase G3: dashboard viewing. STOP and report.
- Check how `FieldImages.tsx` shows a 2:1 panorama today (thumbnail crop, viewer). Make a wide image usable: thumbnail shows the whole wide picture (not a square crop), the viewer opens
  large with horizontal scroll or pan/zoom, caption, who sent it (rank + name), when, plus a download button. Keep keyboard and screen-reader basics (alt text, close with Escape).
- Verify in the Browser pane at `http://localhost:8000` (ask the owner to show the pane if hidden): upload a test panorama-shaped image (for example 2880x1440 generated locally, not a real
  photo), view it, remove it. No console errors. `npm run build` and the web tests pass. Screenshot as proof.
- Rebuild the dashboard for the stack with `deploy/build-dashboard.ps1` only if the owner agrees; say how.

## Phase G4: end-to-end and owner test procedure. STOP and report.
- Write the on-phone test steps into `docs/PANORAMA-FEATURE.md` (USB, install the APK, sign in as a planner, make and send a plan, open AR, set origin, sweep a panorama, Save, Open, Send to plan,
  see it on the dashboard as the reviewer), with an "if X then Y" table and the list of what is verified (unit tests, curl, browser) and **not verified on a device** (stitching quality,
  ARCore on the Motorola, speed and memory while stitching, big-file upload on slow links).
- Build the final debug APK and give the install command: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r <path to app-debug.apk>`.

## Rules
- Do not change the layout JSON schema, the server API contract, auth, or `domain/ar/**` placement maths (another review is open on the heading). Do not touch `server-django` unless something
  is broken: stop and tell the owner first.
- Keep cleartext limited to the already listed hosts; no new permissions in the manifest unless required (say why).
- Keep it failure-tolerant and memory-safe (the stitcher already allocates large arrays; do not add more copies).
