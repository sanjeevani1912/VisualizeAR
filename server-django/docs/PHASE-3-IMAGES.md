# Phase 3 — Field images (Django backend)

Status: **built and tested. Not committed.** Written 2026-11-20 (milestone B2).
Branch `django-backend`, folder `DhruvAR-django/server-django/`. Phase 2 is committed as `440de80`.
Nothing in `app/`, `web/` or `server/` was changed; the real demo data (`DhruvAR/server/data`) was not touched.

With this phase **all 23 routes exist**.

---

## 1. What this phase is, in plain words

Planners and reviewers can attach photos ("field images") to a plan, see them, and remove them. Three routes:

| Route | Who | What it does |
|---|---|---|
| `POST /api/plans/<id>/attachments` | the owning planner, or any reviewer (never on a withdrawn plan) | Upload one image (`multipart/form-data`, field `file`, optional text field `caption`). Answers 201 `{"id": ...}`. |
| `GET /api/attachments/<id>` | anyone who may see the plan | The image itself. |
| `DELETE /api/attachments/<id>` | the person who uploaded it, or an admin | Removes the image and its file. |

New word, **multipart/form-data**: the way a browser or phone sends a file in a web request. The body is cut into
"parts" (the file, the caption) separated by a boundary line.

---

## 2. Files

| File | Purpose |
|---|---|
| `api/views_attachments.py` (new) | The three routes. A port of the attachment code in `plans.ts` (Node uses the libraries *multer* and *express sendFile*). |
| `api/urls.py` | Two new addresses. |
| `api/middleware.py` | One line: `Cache-Control: no-store` is added only if the answer has not set its own (images use `private, max-age=300`). |
| `api/tests/test_attachments.py` | 25 new tests. |
| `scripts/compare_servers.py` | The side-by-side tool now also uploads real PNG/JPEG/WebP files, downloads them (comparing the bytes) and sends malformed uploads. 229 requests now. |

---

## 3. Rules copied exactly from the Node server

**Upload**
- The body is read first, **before** the plan is looked up. So a too-big file gets `413 Image is too large` even for a
  plan that does not exist; a second file, a file under another field name, or more than 4 text fields gets `400 Invalid upload`.
- Then, in this order: plan not visible → `404 Plan not found`; not allowed → `403 You cannot add images to this plan`
  (admins and auditors may not; nobody may on a withdrawn plan); no file → `400 No image received`; wrong type →
  `415 Only JPEG, PNG or WebP images are accepted`; plan already full → `409 A plan can hold at most 30 images`.
- **The type is decided by the file's own first bytes**, not by what the sender says (JPEG `FF D8 FF`, PNG 8-byte
  signature, WebP `RIFF....WEBP`). The declared type must also be one of the three. So an HTML file renamed `x.png`
  is refused.
- Size limit `MAX_UPLOAD_MB` (default 10 MB; exactly the limit is accepted). Maximum per plan `MAX_IMAGES_PER_PLAN` (default 30). Both are settings, like in Node.
- The file is saved as `DATA_DIR/uploads/<new uuid>.jpg|png|webp`. The name the sender used is **never** used on disk.
  It is kept only as a display name, cleaned: folders removed (`../../x.png` → `x.png`, also Windows `C:\a\b.png`),
  every character other than letters, digits, `_ . - ( )` and space becomes `_`, at most 100 characters. Node reads the
  name byte by byte, so one non-ASCII letter becomes several underscores (`ö` → `__`, an emoji → `____`); Django does the same so the names agree.
- The caption is trimmed and cut to 200 characters.
- The audit entry `Image added` (detail `gate.png — Front gate`) is written **in the same transaction** as the database row.
  If anything fails, the file is deleted again, so no orphan file is left behind (tested).

**Show**
- Unknown image `404 Image not found`; plan not visible `404 Plan not found` (so ids cannot be probed); file missing on disk `404 Image not found`.
- Headers: `Content-Type` (the stored type), `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`,
  `Cache-Control: private, max-age=300`, plus `ETag` and `Last-Modified`. Browsers that already have the image get `304` (nothing re-sent).

**Remove**
- `403 Only the uploader or an administrator can remove an image` for everyone else. Writes `Image removed` (detail = the file name) in the same transaction, then deletes the file.

**Safe against two uploads at once:** the plan row is locked while the image count is checked and the row is written,
so two simultaneous uploads cannot both squeeze past the 30 limit.

---

## 4. Where Django deliberately differs from Node (and why)

| Topic | Node | Django | Effect |
|---|---|---|---|
| A multipart body that is cut off in the middle | `500` | the part is ignored, usually `400 No image received` | Only happens when a connection drops, so nobody reads the answer. |
| Oversize upload | stops reading at the limit | receives the whole body, then refuses | Same answer. Put a body-size limit in Nginx (`client_max_body_size`, phase 5) so huge bodies never reach Django. |
| Range requests (`206`, partial image) | supported by `sendFile` | not supported (whole image is always sent) | Browsers and the app load images whole; matters only for resuming huge downloads. |
| `ETag` on JSON answers | added automatically by Express | not added | No effect on the app or dashboard. (Images do have an ETag in both.) |

A broken multipart body with no boundary at all is a `500 Something went wrong on the server` in **both** servers (copied on purpose).

---

## 5. How it was checked

**1. Automated tests: 191 pass** (166 + 25 new): all three formats; the stored name and bytes on disk; audit entry
and chain; version number; cleaned names (folders, Windows paths, symbols, unicode, 150 characters); caption
trimming/truncation; every role on every route; withdrawn plan; no file / JSON body / empty file name; fake types
(wrong declared type, HTML as PNG, empty file, RIFF that is not WebP, too-short JPEG); upper-case declared type;
413 before 404 and exactly-the-limit accepted; unexpected field, two files, five/four text fields; the 30-image
rule with a per-plan limit and "type is checked before the limit"; nothing left on disk after a failed save; show
headers, `no-store` still used for everything else, missing file, 304 variants; removal rules and file deletion.
Tests use a temporary data folder with a tiny size limit, so they never write into `server-django/data`.

**2. Side-by-side with the real Node server: 229 requests, 0 differences** (every response text identical, 138
from phase 2 + 91 new), using real small PNG, JPEG and WebP files. The new requests include: uploads of all three
formats by a planner and a reviewer; 7 awkward file names; every role; every refusal above; a real **10 MB + 1 byte**
file (413) with and without an existing plan; a broken multipart body; **31 uploads to one plan** (30 accepted, the
31st refused); downloads by 5 roles with the **image bytes compared by checksum** and the headers `Content-Type`,
`Cache-Control`, `X-Content-Type-Options`, `Content-Disposition` compared, `ETag`/`Last-Modified` present in both;
`If-None-Match` / `If-Modified-Since` variants; HEAD; removals by every role; downloads after removal; and the plan and
audit lists afterwards. The first run found two real differences (file-name bytes, and a stray header on 304 answers); both fixed.

**Not checked (honestly):**
- The Android app and the dashboard have not been pointed at Django, so real uploads from the app (its exact
  multipart layout, the dashboard's file picker) are untested. Node's code expects field `file`; Django does too.
- The sample JPEG and WebP are tiny hand-made files; the server never decodes images, only checks their first bytes.
- Large concurrent uploads, memory use with many 10 MB uploads, and PostgreSQL were not tested.
- Truncated uploads and Range requests differ from Node (section 4).

## 6. Words you may not know
- **multipart/form-data:** see section 1.
- **nosniff:** tells the browser to believe the declared type and never guess, so an uploaded file can never be run as a web page.
- **304 Not Modified:** the browser asks "has it changed?"; the server says no and sends nothing.
- **Orphan file:** a file on disk that no database row points to.

## 7. Next: milestone B3 (not started)
`seed` command, the end-to-end test file runnable against either server, and pointing the real dashboard at Django.
