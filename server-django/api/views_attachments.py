"""Field images: upload, show, remove. A port of the attachment routes in server/src/plans.ts (multer + sendFile).

Safety rules (all copied from Node): the type is decided by the file's own first bytes, never by what the
uploader claims; stored names are random (`<uuid>.<ext>`), never the uploaded name; images are only served to
people who may see the plan, as `inline` with `nosniff` so a browser can never treat one as a web page.
"""

import email.utils
import os
import re

from django.conf import settings
from django.http import HttpResponse
from django.http.multipartparser import MultiPartParserError
from django.db import transaction
from django.utils.http import parse_http_date_safe

from .audit import audit
from .http import ApiError, JsResponse, endpoint
from .jsutil import db_text, js_slice, js_trim
from .models import Attachment, new_id, now_ms
from .rules import can_attach, can_remove_attachment
from .views_plans import _actor, _latest_version, _ref, _visible_plan

PNG_MAGIC = bytes([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
IMAGE_TYPES = {
    "image/jpeg": (".jpg", lambda b: b[:3] == b"\xff\xd8\xff"),
    "image/png": (".png", lambda b: b[:8] == PNG_MAGIC),
    "image/webp": (".webp", lambda b: b[:4] == b"RIFF" and b[8:12] == b"WEBP"),
}
MAX_FIELDS = 4  # multer's `fields: 4`
SAFE_NAME = re.compile(r"[^A-Za-z0-9_.\- ()]")


def _uploads_dir() -> str:
    return str(settings.DATA_DIR / "uploads")


def _read_form(request):
    """Reads the multipart body the way multer does, before anything else is looked at.

    Returns (file or None, text fields). Same refusals as Node: a file bigger than the limit is 413
    "Image is too large"; more than one file, a file under another field name, or too many text fields is
    400 "Invalid upload". A request that is not multipart simply has no file.
    """
    if not request.META.get("CONTENT_TYPE", "").lower().startswith("multipart/"):
        return None, {}
    try:
        fields, files = request.POST, request.FILES
    except MultiPartParserError:
        raise  # a broken multipart body is an unexpected error in Node too: 500 "Something went wrong on the server"
    uploads = [(name, f) for name, items in files.lists() for f in items]
    if any(name != "file" for name, _ in uploads) or len(uploads) > 1:
        raise ApiError(400, "Invalid upload")
    if sum(len(values) for _, values in fields.lists()) > MAX_FIELDS:
        raise ApiError(400, "Invalid upload")
    upload = uploads[0][1] if uploads else None
    if upload is not None and upload.size > settings.MAX_UPLOAD_BYTES:
        raise ApiError(413, "Image is too large")
    return upload, {k: ",".join(v) for k, v in fields.lists()}  # repeated fields are joined, like String(array)


def _display_name(original: str) -> str:
    """path.basename(name) with every unsafe character replaced by `_`, at most 100 long (JavaScript rules:
    only ASCII letters/digits count as safe, and an emoji, being two UTF-16 units, becomes two underscores)."""
    base = re.split(r"[\\/]", original)[-1]
    # Node reads the file name one BYTE at a time (Latin-1), so a letter such as "ö" (2 bytes in UTF-8) gives
    # two underscores and an emoji (4 bytes) gives four.
    safe = "".join("_" * len(c.encode("utf-8", "replace")) if SAFE_NAME.match(c) else c for c in base)
    return js_slice(safe, 100)


@endpoint("POST", auth=True)
def plan_attachments(request, plan_id):
    user = request.api_user
    upload, fields = _read_form(request)
    plan = _visible_plan(request, plan_id)
    if not can_attach(_actor(request), _ref(plan)):
        raise ApiError(403, "You cannot add images to this plan")
    if upload is None:
        raise ApiError(400, "No image received")
    mime = (upload.content_type or "text/plain").lower()
    kind = IMAGE_TYPES.get(mime)
    head = upload.read(12)
    upload.seek(0)
    # The declared type is not trusted: the file's own first bytes must match it.
    if kind is None or not kind[1](head):
        raise ApiError(415, "Only JPEG, PNG or WebP images are accepted")
    ext = kind[0]
    caption = db_text(js_slice(js_trim(fields.get("caption", "")), 200))
    image_id = new_id()
    stored = f"{image_id}{ext}"
    display = db_text(_display_name(upload.name or "")) or f"image{ext}"
    path = os.path.join(_uploads_dir(), stored)
    now = now_ms()
    with transaction.atomic():
        plan = _visible_plan(request, plan_id, lock=True)  # the count below must not race another upload
        if not can_attach(_actor(request), _ref(plan)):
            raise ApiError(403, "You cannot add images to this plan")
        if Attachment.objects.filter(plan_id=plan.id).count() >= settings.MAX_IMAGES_PER_PLAN:
            raise ApiError(409, f"A plan can hold at most {settings.MAX_IMAGES_PER_PLAN} images")
        try:
            with open(path, "xb") as out:
                for chunk in upload.chunks():
                    out.write(chunk)
            Attachment.objects.create(
                id=image_id, plan=plan, version=_latest_version(plan.id), file_name=display, stored_name=stored,
                mime=mime, size=upload.size, caption=caption, uploaded_by_id=user.id, uploaded_at=now,
            )
            audit(user.id, "Image added", plan.id, f"{display}{' — ' + caption if caption else ''}", now)
        except BaseException:
            _remove_file(path)  # never leave a file behind that the database does not know about
            raise
    return JsResponse({"id": image_id}, status=201)


def _remove_file(path: str) -> None:
    try:
        os.remove(path)
    except FileNotFoundError:
        pass


def _attachment_or_404(attachment_id: str) -> Attachment:
    found = Attachment.objects.filter(pk=attachment_id).first()
    if found is None:
        raise ApiError(404, "Image not found")
    return found


@endpoint("GET", "DELETE", auth=True)
def attachment(request, attachment_id):
    if request.method == "DELETE":
        return _delete(request, attachment_id)
    return _show(request, attachment_id)


def _show(request, attachment_id):
    found = _attachment_or_404(attachment_id)
    _visible_plan(request, found.plan_id)
    path = os.path.join(_uploads_dir(), os.path.basename(found.stored_name))
    try:
        info = os.stat(path)
        with open(path, "rb") as f:
            data = f.read()
    except OSError:
        raise ApiError(404, "Image not found")
    etag = f'W/"{info.st_size:x}-{int(info.st_mtime * 1000):x}"'
    modified = email.utils.formatdate(int(info.st_mtime), usegmt=True)
    headers = {
        "Content-Type": found.mime,
        "X-Content-Type-Options": "nosniff",
        "Content-Disposition": "inline",
        "Cache-Control": "private, max-age=300",
        "ETag": etag,
        "Last-Modified": modified,
    }
    if _not_modified(request, etag, int(info.st_mtime)):
        response = HttpResponse(status=304)
        del response["Content-Type"]
    else:
        response = HttpResponse(data)
        response["Content-Length"] = str(len(data))
    for key, value in headers.items():
        if response.status_code != 304 or key != "Content-Type":
            response[key] = value
    return response


def _not_modified(request, etag: str, mtime: int) -> bool:
    """Browsers ask "has this changed?" with If-None-Match / If-Modified-Since; if not, send nothing (304)."""
    sent = request.META.get("HTTP_IF_NONE_MATCH")
    if sent:
        wanted = {t.strip() for t in sent.split(",")}
        return "*" in wanted or any(t == etag or t == etag[2:] or "W/" + t == etag for t in wanted)
    since = parse_http_date_safe(request.META.get("HTTP_IF_MODIFIED_SINCE", ""))
    return since is not None and mtime <= since


def _delete(request, attachment_id):
    user = request.api_user
    found = _attachment_or_404(attachment_id)
    plan = _visible_plan(request, found.plan_id)
    if not can_remove_attachment(_actor(request), found.uploaded_by_id):
        raise ApiError(403, "Only the uploader or an administrator can remove an image")
    now = now_ms()
    with transaction.atomic():
        Attachment.objects.filter(pk=found.id).delete()
        audit(user.id, "Image removed", plan.id, found.file_name, now)
    _remove_file(os.path.join(_uploads_dir(), os.path.basename(found.stored_name)))
    return JsResponse({"ok": True})
