# Panorama, end to end: capture, save, open, send to the plan, see on the dashboard

Written 2026-11-24 (session G, branch `app/panorama`, worktree `DhruvAR-pano`). Sections 1-9 are the design (G0); sections 10-14 say what was built (G1-G3) and how to test it on a phone (G4). Nothing is committed.
For a beginner: new words are explained where they first appear.

## 1. The flow

```
 PHONE (AR screen)                                              SERVER (http://localhost:8000)            DASHBOARD
 -----------------                                              ------------------------------            ---------
 hold "Panorama", sweep, release
   -> PanoramaStitcher -> Bitmap (<= 2880x1440)
 preview dialog:  [Retake] [Save] [Send to plan]
   |
   | Save (always first)
   v
 MediaStore -> Pictures/DhruvAR/DHRUV_<plan>_<time>.jpg      (the local copy is never deleted)
   -> keep the Uri + name; message "Saved <name> in Pictures/DhruvAR"  [Open]  -> Intent.ACTION_VIEW (gallery)
   |
   | Send to plan (only when signed in AND this plan was already sent = has a server plan id)
   v
 AttachmentUploader (background thread, same OkHttp client / base URL / bearer token as ServerClient)
   POST /api/plans/<planId>/attachments  multipart: file=<jpeg>, caption=<text>  -------->  checks, stores file
   <-- 201 {"id": "..."}                                                                      |
 "Sent to plan TP-2026-0142 as field image"                                                   v
   or plain-words failure + [Retry]  (picture stays saved)                          plan page "Field images":
                                                                                    wide thumbnail, viewer with
                                                                                    scroll/zoom, caption, who, when
```

A **multipart upload** is a normal HTTP POST whose body is made of named parts (here a file part and a text part). **Bearer token** = the
sign-in token the app already keeps (encrypted) and sends in the `Authorization` header.

## 2. The exact upload request (read from `server-django/api/views_attachments.py`, then tested with curl)

| Item | Value |
|---|---|
| Method and URL | `POST http://localhost:8000/api/plans/<planId>/attachments` (`planId` is the server's id, e.g. `plan-139`; the app already receives it when a plan is sent: `SubmittedPlan.planId`) |
| Header | `Authorization: Bearer <token>` (required). `Content-Type: multipart/form-data; boundary=...` (OkHttp writes it) |
| File part | field name **exactly `file`**; exactly one file; any other field name or more than one file gives 400 "Invalid upload" |
| Text part | `caption` (optional); server trims it and keeps at most 200 characters. At most 4 text fields in total |
| File type | JPEG, PNG or WebP. The server decides by the **first bytes** of the file, not by the claimed type, so the part must also say `image/jpeg` (the declared type must be on the list) |
| Size | at most 10 MB (`MAX_UPLOAD_BYTES`); Nginx allows 12 MB (`client_max_body_size 12m`); at most 30 images per plan |
| Reply | `201 {"id":"<attachment id>"}` |
| Download (for the dashboard) | `GET /api/attachments/<id>` with the bearer token. Plan details (`GET /api/plans/<id>`) list `attachments[]` with `id, fileName, mime, size, caption, uploadedBy, uploadedAt, version, url` |
| Remove | `DELETE /api/attachments/<id>`: the uploader or an admin |

Error replies seen with curl on 2026-11-24 (real stack, sample planner `singh`, plan `plan-139`; the password was read inside the script and never printed):

| What was sent | Reply |
|---|---|
| good 2880x1440 JPEG (1,802,910 bytes) + caption | **201** `{"id":"..."}`; download returned 200, `image/jpeg`, same 1,802,910 bytes; then removed again (200) |
| text file pretending to be a JPEG | **415** "Only JPEG, PNG or WebP images are accepted" |
| 11.5 MB file starting with JPEG bytes | **413** "Image is too large" |
| field named `image` instead of `file` | **400** "Invalid upload" |
| plan that is WITHDRAWN (`plan-134`) | **403** "You cannot add images to this plan" |
| no token at all | **403** (see the warning below) |

**Warning found while testing (not a bug in this feature; the server contract is not changed).** A POST without a valid token is answered by a
header check **before** the sign-in check: `403 {"error":"Missing request header"}`, not 401. So when the phone's token has expired, an upload
(and "send plan") answers **403**, not 401. The uploader must therefore treat a 403 whose message is "Missing request header" as
"your session has expired, sign in again". A 403 "You cannot add images to this plan" really means "not allowed".

## 3. Who may upload, and when (`api/rules.py`, `can_attach`)

- A **planner** may attach only to a plan **they own**. A **reviewer** may attach to any plan. An **admin** and an **auditor** may not.
- Never on a plan whose status is `WITHDRAWN`. Every other status (SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, CHANGES_REQUESTED) is fine.
- A plan that was never sent has **no server id yet**, so there is nothing to upload to. The app records the id in `SentPlanStore`, keyed by the layout id
  (`uiState.currentLayout.id` on the AR screen), after "Send for approval".
- The status in `SentPlanStore` can be stale (a reviewer may have withdrawn or decided it since). The server's answer is the truth: a 403 "cannot add images" is
  shown in plain words ("This plan no longer accepts images, e.g. it was withdrawn").

## 4. Size of a real panorama JPEG (an estimate, not measured on a phone)

The stitcher output is at most 2880 x 1440 = 4.1 million pixels. A JPEG at quality 95 of photographic content is usually about 0.6 to 1.2 bytes per pixel,
so **roughly 2.5 to 5 MB**. My synthetic test picture (random coloured blobs, same pixel size, quality 95) came to 1.8 MB. Very detailed or noisy scenes
(grass, gravel, low light) could approach 6 to 7 MB. So 10 MB is unlikely, but the guard below is still needed. Ordinary AR screenshots (phone-screen size) are about 0.3 to 1.5 MB.

## 5. Decisions

(a) **When "Send to plan" is offered.** The preview dialog always has Retake and Save. **Send to plan** is shown enabled only when the phone is signed in and
`SentPlanStore` has a server id for the current layout. Otherwise it is shown disabled with one line of explanation: "Sign in first" or "Send this plan for
approval first, then send images". We do not queue uploads: a queue needs storage, retries and a background service, which is more risk than a beginner's first
version needs; the picture is already in the gallery and can be sent from a later screen (not in scope now). Sending still saves first.

(b) **Caption.** Default `Panorama (phone) 2026-11-24 14:05` (local time); ordinary AR screenshots get `AR view (phone) <date time>`. Under 200 characters always.
No text field in the first version (fewer taps in the field); the caption can be extended later.

(c) **JPEG quality and size guard.** Save keeps quality 95 (unchanged). For sending: use the saved bytes if <= 9 MB; if larger, re-compress the same bitmap at 85, then 75,
then 65 (each attempt into a reused byte stream, then the bitmap is not copied; at most one extra compressed buffer, about the file size, is alive). If still over
9 MB, fail with "Picture is too large to send (limit 10 MB)". 9 MB leaves room for the multipart headers.

(d) **Failure messages (plain words) with Retry.** Typed result `Success(attachmentId)` or `Failure(reason)`:

| Cause | Message |
|---|---|
| no token / expired (401, or 403 "Missing request header") | "Not signed in. Sign in again, then retry." |
| plan not sent yet | "Send this plan for approval first." |
| 413 or still too big after re-compression | "Picture is too large (limit 10 MB)." |
| 403 "cannot add images" | "This plan does not accept images (not your plan, or it was withdrawn)." |
| 409 | "This plan already has 30 images." |
| 415 | "The server does not accept this image type." |
| cannot connect / timeout | "Cannot reach the server. Check the USB link (`adb reverse`) or network, then retry." |
| other | "The server answered with error NNN." |

The dialog stays open on failure with a **Retry** button (the picture is already saved; Retry re-sends the same bitmap). A failed upload never blocks anything else,
and plan sending is a separate flow that does not call the uploader.

(e) **Local copy.** Always saved first, never removed. "Send to plan" saves (if not saved yet) and then uploads, so a failed upload always leaves the
file in `Pictures/DhruvAR`. Pressing Save and then Send does not save twice (the saved Uri is kept in the dialog state).

## 6. Files I will change (and why)

| File | Change |
|---|---|
| `app/.../ui/ar/capture/ARCaptureManager.kt` | `saveImageToGallery` returns a small `SavedImage(uri, fileName, folder)` instead of only a `Uri`; a pure helper to build the "Saved ..." message |
| `app/.../ui/screens/ARVisualizationScreen.kt` | keep the saved image in state; **Open** action in the banner; **Send to plan**, progress, success, Retry in the preview dialog. Existing tests must still pass |
| `app/.../data/remote/AttachmentUploader.kt` (new) | multipart upload, typed result, size guard and plain-words failures |
| `app/.../data/remote/ServerClient.kt` | only to expose the shared OkHttp client (e.g. `internal val httpClient`) so the uploader does not create a second one; no behaviour change |
| `app/.../util` or `ui/ar/capture/ImageViewIntent.kt` (new, small) | builds the `ACTION_VIEW` intent (pure parts testable) |
| `app/build.gradle.kts` + `gradle/libs.versions.toml` | add `testImplementation` of OkHttp **MockWebServer** (same version 4.12.0): a fake server in the unit tests. **Not currently in the project**, so this is a new test-only dependency. Say no if you would rather use a fake interface |
| `web/src/components/FieldImages.tsx` (+ a few CSS lines in the dashboard stylesheet, only if needed) | wide thumbnails, large viewer with scroll/zoom, who sent it, download, Escape to close |
| `docs/PANORAMA-FEATURE.md` | this file, finished in G4 |

Not touched: layout JSON schema, server API, auth, `domain/ar/**`, `server-django`, the manifest (the upload uses INTERNET, which the app already has; `localhost` is
already on the cleartext list in `network_security_config.xml`; opening the picture uses the system viewer with a read grant, no new permission).

## 7. Tests I will add

- `PanoramaSaveNamingTest` (JVM): file name building and sanitising, the "Saved ... in Pictures/DhruvAR" message, caption defaults (date formatting with a fixed clock/time zone).
- `ImageViewIntentTest` (JVM; if Android intents are not available in plain JUnit, only the pure parts are tested and I will say so): MIME `image/jpeg`, read-permission flag set, "no app" handled.
- `AttachmentUploaderTest` (JVM, MockWebServer): success 201, request shape (method, path, `Authorization`, multipart with `file` + `caption`, `image/jpeg`), 401, 403 "Missing request header", 403 "cannot add",
  413, 415, 409, network failure, and the re-compress guard as a pure function.
- `web` tests (Vitest, if the project already has them for `FieldImages`/rules): wide-image layout helper if any logic is separable.

## 8. What cannot be proven without a phone (so I will not claim it)

Camera and ARCore tracking, whether the stitch looks good, speed and memory while stitching, the gallery app that opens the picture, USB `adb reverse` upload on the real phone,
and uploads over a slow link. I will report these as "not verified on a device".

## 9. Things I noticed (follow-ups, not part of this work)

- `saveImageToGallery` runs on the main thread in the Save button handler (JPEG compression of up to 4 megapixels); I will move it to a background thread in G1 because the new Open/Send flow depends on the result. Say if you would rather I leave that alone.
- The expired-token 403 above also affects "Send for approval" in `ServerClient`: the app shows the server's text instead of "sign in again". Not changed here.

## 10. What was built (G1 to G3)

**App (all in `app/src/main/java/com/example/dhruvar/`)**
- `ui/ar/capture/SavedImage.kt` (new): saved picture = address + file name; the "Saved <name> in Pictures/DhruvAR" text; the `ACTION_VIEW` "open" request.
- `ui/ar/capture/ARCaptureManager.kt`: `saveImageToGallery` returns the `SavedImage`.
- `data/remote/AttachmentUploader.kt` (new): upload, size guard (qualities 95, 85, 75, 65; limit 9 MB), caption text, "can I send?" rule, failure wording.
- `data/remote/ServerClient.kt`: one line exposing the shared HTTP client.
- `ui/screens/ARVisualizationScreen.kt`: Save runs off the main thread; **Open** in the banner after Save; **Send to plan** / **Retry send to plan** in the preview dialog (panoramas and ordinary AR screenshots). Send saves a gallery copy first, then uploads.
- Tests: `SavedImageTest` (5), `AttachmentUploaderTest` (17, against a fake server: MockWebServer). `app/build.gradle.kts` and `gradle/libs.versions.toml` gained the test-only MockWebServer dependency.

**Dashboard:** `web/src/components/FieldImages.tsx` and `web/src/styles.css`. Wide pictures (ratio 1.6 or more) get a double-width thumbnail showing the whole picture (no square crop); the viewer opens large, has zoom out / in (Fit, 2x, 3x, 4x) with scrollbars, shows caption, "Sent by <rank> <name>", date and size, a **Download** link and Close; Escape closes it and gives focus back to the thumbnail. Nothing else in `web/` was changed.

## 11. What was verified

| Check | Result |
|---|---|
| `gradlew assembleDebug testDebugUnitTest` | passes: 361 tests, 0 failures (344 after G1, +17 uploader tests) |
| Upload request against the real stack with curl (same field names as the app) | 201, 415, 413, 400, 403 as in section 2 |
| Dashboard `npm run build` (includes the TypeScript check) | passes. The dashboard has no unit-test runner, so there are no web tests |
| Dashboard in the browser (a test copy of the new build served on port 5180 and forwarded to the real stack; signed in as planner `singh`) | a generated 2880x1440 picture and an 800x600 picture were uploaded with curl; the panorama showed whole at double width, the viewer opened, zoomed to 2x with a horizontal scrollbar, Escape closed it and focus returned; both pictures were then removed with the dashboard's Remove button. The only console error was one 502 from the very first load, before my test server was up |

**The stack's own dashboard (port 8000) still shows the old screen** until `deploy/build-dashboard.ps1` is run (not done; it replaces the files Nginx serves, so it waits for the owner's yes).

## 12. Not verified on a device (do not assume these work)

Camera and ARCore tracking on the Motorola; stitching quality; speed and memory while stitching; **Open** in the phone's gallery (and the "no app" message);
saving in the background while the stitcher's memory is still held; the **Send to plan** button on the real phone (sign-in state, `adb reverse`); the layout of the new button
and message in the preview dialog on a small screen; uploads over a slow or lost link (the retry path is unit-tested only against a fake server).

## 13. Owner test on the phone (about 20 minutes)

Prepare (PC): stack running (`docker compose ps`: nginx, django, postgres Up), map server running. Phone: USB debugging on, cable plugged in.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r C:\Users\itcell\AndroidStudioProjects\DhruvAR-pano\app\build\outputs\apk\debug\app-debug.apk
```
1. Open the app, sign in as a sample **planner** (`singh`, `rao`, `bose` or `khan`; address `http://localhost:8000`; the password is in the Django container file, see `docs/APP-PHONE-TEST.md`). Do not paste the password anywhere.
2. Plan: set a region, an origin inside it, add three or more assets; **Send for approval**. Note the plan code (for example TP-2026-0150). *A plan must be sent first: pictures go to a sent plan.*
3. Open AR **from that same plan**, set the origin. Hold **Panorama** and sweep slowly (the banner shows shots and degrees), release.
4. In the preview, scroll the wide picture. Under it you see either a grey line ("Sign in first..." / "Send this plan for approval first...", button greyed) or nothing (button ready).
5. Tap **Save**: banner "Saved DHRUV_...jpg in Pictures/DhruvAR" with **Open**. Tap Open: the gallery shows the picture.
6. Take another panorama (or an ordinary AR capture) and tap **Send to plan**: "Sending..." then the banner "Sent to plan TP-... as field image. Saved as DHRUV_...jpg".
7. On the PC open `http://localhost:8000`, sign in as a **reviewer** (`menon` or `sharma`), open the plan, **Field images**: caption "Panorama (phone) <date time>", "Sent by Major V. Singh". Until the dashboard is rebuilt (section 11) the picture is listed with the old square thumbnail; after the rebuild it is wide with the new viewer.

| If you see | Then |
|---|---|
| Send button greyed with "Sign in first..." | sign in (person icon on Home) with the server address `http://localhost:8000` |
| greyed with "Send this plan for approval first..." | send the plan (step 2); the AR screen uses the plan it is showing, so open AR from that same plan |
| "Not signed in. Sign in again, then retry." | session expired: sign in again, return to AR, retake or retry (the picture is saved) |
| "Cannot reach the server..." | run `adb reverse tcp:8000 tcp:8000` again; Chrome on the phone: `http://localhost:8000/api/health` should say ok; then **Retry send to plan** |
| "This plan does not accept pictures..." | the plan was withdrawn, or you are signed in as someone who does not own it |
| "Picture is too large..." | very detailed picture even at quality 65: sweep a smaller angle |
| "This plan already has 30 pictures." | remove some on the dashboard |
| Open shows "No app on this phone can open pictures" | install or enable a gallery app (Google Photos or the Motorola gallery) |
| the app closes while stitching | memory: note how many shots, sweep shorter, and tell me (stitching memory is not changed by this work) |

## 14. Install command for the final debug APK

`app-debug.apk` is at `C:\Users\itcell\AndroidStudioProjects\DhruvAR-pano\app\build\outputs\apk\debug\app-debug.apk` (about 168 MB, debug build, built after the last app code change):
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r C:\Users\itcell\AndroidStudioProjects\DhruvAR-pano\app\build\outputs\apk\debug\app-debug.apk
```
