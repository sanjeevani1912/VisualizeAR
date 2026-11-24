"""Field images (phase 3): upload, show, remove."""

import base64
import os
import shutil
import tempfile
from pathlib import Path
from unittest import mock

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import Client, override_settings

from api import views_attachments
from api.audit import verify_chain
from api.models import Attachment, AuditEntry

from .helpers import XHR
from .test_plans import PlanTestCase, layout

PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 20
WEBP = b"RIFF\x1a\x00\x00\x00WEBPVP8L" + b"\x00" * 10


class ImageTestCase(PlanTestCase):
    def setUp(self):
        super().setUp()
        self.dir = Path(tempfile.mkdtemp())
        (self.dir / "uploads").mkdir()
        patcher = override_settings(DATA_DIR=self.dir, MAX_UPLOAD_BYTES=1000, MAX_IMAGES_PER_PLAN=3)
        patcher.enable()
        self.addCleanup(patcher.disable)
        self.addCleanup(shutil.rmtree, self.dir, ignore_errors=True)
        self.plan_id = self.submit()

    def files_on_disk(self):
        return sorted(os.listdir(self.dir / "uploads"))

    def upload(self, user="pat", name="gate.png", data=PNG, ctype="image/png", plan_id=None, **fields):
        body = {"file": SimpleUploadedFile(name, data, content_type=ctype), **fields}
        return Client().post(f"/api/plans/{plan_id or self.plan_id}/attachments", data=body, headers=self.who[user])

    def add(self, user="pat", **kw):
        res = self.upload(user, **kw)
        self.assertEqual(res.status_code, 201, res.content)
        return res.json()["id"]

    def show(self, image_id, user="pat", **headers):
        return Client().get(f"/api/attachments/{image_id}", headers={**self.who[user], **headers})

    def remove(self, image_id, user="pat"):
        return self.req("delete", f"/api/attachments/{image_id}", user)


class UploadTests(ImageTestCase):
    def test_a_png_is_stored_under_a_random_name_and_listed(self):
        image_id = self.add(caption="  Front gate  ")
        row = Attachment.objects.get(pk=image_id)
        self.assertEqual((row.file_name, row.mime, row.size, row.caption, row.version, row.uploaded_by_id), ("gate.png", "image/png", len(PNG), "Front gate", 1, self.users["pat"].id))
        self.assertEqual(row.stored_name, f"{image_id}.png")
        self.assertEqual((self.dir / "uploads" / row.stored_name).read_bytes(), PNG)
        item = self.get_plan(self.plan_id)["attachments"][0]
        self.assertEqual((item["url"], item["fileName"], item["caption"]), (f"/api/attachments/{image_id}", "gate.png", "Front gate"))
        entry = AuditEntry.objects.order_by("-seq").first()
        self.assertEqual((entry.action, entry.detail), ("Image added", "gate.png — Front gate"))
        self.assertTrue(verify_chain()["ok"])

    def test_jpeg_and_webp_are_accepted_and_the_response_is_just_an_id(self):
        res = self.upload(name="a.jpg", data=JPEG, ctype="image/jpeg")
        self.assertEqual(list(res.json()), ["id"])
        self.add(name="b.webp", data=WEBP, ctype="image/webp")
        self.assertEqual(sorted(f.rsplit(".", 1)[1] for f in self.files_on_disk()), ["jpg", "webp"])

    def test_the_image_belongs_to_the_latest_version(self):
        self.decide(self.plan_id, decision="changes", reason="Please redo the layout")
        self.req("post", f"/api/plans/{self.plan_id}/versions", "pat", {"layout": layout("v2")})
        self.assertEqual(Attachment.objects.get(pk=self.add()).version, 2)

    def test_file_names_are_made_safe(self):
        cases = {
            "../../evil name.png": "evil name.png",
            "C:\\temp\\x y.png": "x y.png",
            "***.png": "___.png",
            "a<b>&\"c\".png": "a_b___c_.png",
            "Fotö 🚁 (1).png": "Fot__ ____ (1).png",  # one underscore per UTF-8 byte, as Node does
            "n" * 150 + ".png": ("n" * 100),
        }
        for original, expected in cases.items():
            with self.subTest(original):
                self.assertEqual(Attachment.objects.get(pk=self.add(name=original)).file_name, expected)
                Attachment.objects.all().delete()

    def test_caption_is_trimmed_and_cut_at_200(self):
        self.assertEqual(len(Attachment.objects.get(pk=self.add(caption="c" * 300)).caption), 200)
        self.assertEqual(Attachment.objects.get(pk=self.add()).caption, "")

    def test_who_may_add_images(self):
        for user in ("pat", "rex"):
            self.add(user)
        for user in ("ada", "aud"):
            res = self.upload(user)
            self.assertError(res, 403, "You cannot add images to this plan")
        self.assertError(self.upload("pia"), 404, "Plan not found")
        self.assertError(self.upload(plan_id="nope"), 404, "Plan not found")
        self.assertError(Client().post(f"/api/plans/{self.plan_id}/attachments", data={"file": SimpleUploadedFile("a.png", PNG, "image/png")}, headers=XHR), 401, "Not signed in")
        self.assertEqual(Attachment.objects.count(), 2)

    def test_a_withdrawn_plan_takes_no_images(self):
        self.req("post", f"/api/plans/{self.plan_id}/withdraw", "pat")
        self.assertError(self.upload(), 403, "You cannot add images to this plan")

    def test_no_file(self):
        res = Client().post(f"/api/plans/{self.plan_id}/attachments", data={"caption": "text only"}, headers=self.who["pat"])
        self.assertError(res, 400, "No image received")
        res = self.req("post", f"/api/plans/{self.plan_id}/attachments", "pat", {"caption": "json"})
        self.assertError(res, 400, "No image received")
        # a file part with an empty file name is ignored (the test client cannot build one, so write the body by hand)
        body = b'--b\r\nContent-Disposition: form-data; name="file"; filename=""\r\nContent-Type: image/png\r\n\r\n' + PNG + b"\r\n--b--\r\n"
        res = Client().post(f"/api/plans/{self.plan_id}/attachments", data=body, content_type="multipart/form-data; boundary=b", headers=self.who["pat"])
        self.assertError(res, 400, "No image received")

    def test_the_type_is_decided_by_the_first_bytes(self):
        message = "Only JPEG, PNG or WebP images are accepted"
        cases = [
            ("declared gif", PNG, "image/gif"),
            ("png bytes declared jpeg", PNG, "image/jpeg"),
            ("html declared png", b"<html>not an image</html>", "image/png"),
            ("empty declared png", b"", "image/png"),
            ("riff but not webp", b"RIFF\x00\x00\x00\x00WAVEfmt ", "image/webp"),
            ("short jpeg", b"\xff\xd8", "image/jpeg"),
        ]
        for label, data, ctype in cases:
            with self.subTest(label):
                self.assertError(self.upload(data=data, ctype=ctype), 415, message)
        self.assertEqual((Attachment.objects.count(), self.files_on_disk()), (0, []))

    def test_declared_type_is_case_insensitive(self):
        self.assertEqual(Attachment.objects.get(pk=self.add(ctype="IMAGE/PNG")).mime, "image/png")

    def test_oversize_is_refused_before_the_plan_is_even_looked_up(self):
        big = PNG + b"\x00" * (1001 - len(PNG))
        self.assertError(self.upload(data=big), 413, "Image is too large")
        self.assertError(self.upload(data=big, plan_id="nope"), 413, "Image is too large")
        exact = PNG + b"\x00" * (1000 - len(PNG))
        self.assertEqual(self.upload(data=exact).status_code, 201)

    def test_other_malformed_uploads_are_invalid(self):
        c, h = Client(), self.who["pat"]
        url = f"/api/plans/{self.plan_id}/attachments"
        png = lambda n="a.png": SimpleUploadedFile(n, PNG, "image/png")
        self.assertError(c.post(url, data={"photo": png()}, headers=h), 400, "Invalid upload")
        self.assertError(c.post(url, data={"file": [png("a.png"), png("b.png")]}, headers=h), 400, "Invalid upload")
        extra = {f"f{i}": "v" for i in range(5)}
        self.assertError(c.post(url, data={"file": png(), **extra}, headers=h), 400, "Invalid upload")
        four = {f"f{i}": "v" for i in range(4)}
        self.assertEqual(c.post(url, data={"file": png(), **four}, headers=h).status_code, 201)

    def test_a_broken_multipart_body_is_a_500_like_node(self):
        res = Client().post(f"/api/plans/{self.plan_id}/attachments", data=b"garbage", content_type="multipart/form-data", headers=self.who["pat"])
        self.assertError(res, 500, "Something went wrong on the server")

    def test_a_plan_holds_at_most_the_configured_number(self):
        for _ in range(3):
            self.add()
        self.assertError(self.upload(), 409, "A plan can hold at most 3 images")
        self.assertEqual(len(self.files_on_disk()), 3)
        self.assertError(self.upload(data=b"x"), 415, "Only JPEG, PNG or WebP images are accepted")  # type is checked first
        other = self.submit()
        self.assertEqual(self.upload(plan_id=other).status_code, 201)  # the limit is per plan

    def test_no_file_is_left_behind_when_saving_fails(self):
        with mock.patch.object(views_attachments, "audit", side_effect=RuntimeError("boom")):
            res = self.upload()
        self.assertEqual(res.status_code, 500)
        self.assertEqual((Attachment.objects.count(), self.files_on_disk()), (0, []))


class ShowTests(ImageTestCase):
    def test_the_bytes_and_the_safety_headers(self):
        image_id = self.add()
        res = self.show(image_id, "rex")
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.content, PNG)
        self.assertEqual(res["Content-Type"], "image/png")
        self.assertEqual(res["X-Content-Type-Options"], "nosniff")
        self.assertEqual(res["Content-Disposition"], "inline")
        self.assertEqual(res["Cache-Control"], "private, max-age=300")
        self.assertEqual(res["Content-Length"], str(len(PNG)))
        self.assertTrue(res["ETag"] and res["Last-Modified"])

    def test_everything_else_is_still_no_store(self):
        self.assertEqual(self.req("get", "/api/plans", "pat")["Cache-Control"], "no-store")
        self.assertEqual(self.show("nope")["Cache-Control"], "no-store")

    def test_who_may_see_it(self):
        image_id = self.add()
        for user in ("pat", "rex", "ada", "aud"):
            self.assertEqual(self.show(image_id, user).status_code, 200)
        self.assertError(self.show(image_id, "pia"), 404, "Plan not found")
        self.assertError(Client().get(f"/api/attachments/{image_id}"), 401, "Not signed in")

    def test_unknown_image_and_missing_file(self):
        self.assertError(self.show("nope"), 404, "Image not found")
        image_id = self.add()
        os.remove(self.dir / "uploads" / Attachment.objects.get(pk=image_id).stored_name)
        self.assertError(self.show(image_id), 404, "Image not found")

    def test_a_browser_cache_check_gets_304(self):
        image_id = self.add()
        first = self.show(image_id)
        again = self.show(image_id, **{"If-None-Match": first["ETag"]})
        self.assertEqual((again.status_code, again.content), (304, b""))
        self.assertEqual(self.show(image_id, **{"If-None-Match": "*"}).status_code, 304)
        self.assertEqual(self.show(image_id, **{"If-None-Match": '"other"'}).status_code, 200)
        self.assertEqual(self.show(image_id, **{"If-Modified-Since": "Wed, 01 Jan 2200 00:00:00 GMT"}).status_code, 304)
        self.assertEqual(self.show(image_id, **{"If-Modified-Since": "Mon, 01 Jan 1990 00:00:00 GMT"}).status_code, 200)

    def test_only_get_and_delete_exist(self):
        image_id = self.add()
        self.assertError(self.req("post", f"/api/attachments/{image_id}", "pat", {}), 404, "Not found")


class RemoveTests(ImageTestCase):
    def test_the_uploader_removes_an_image_and_its_file(self):
        image_id = self.add(name="gate.png")
        res = self.remove(image_id)
        self.assertEqual((res.status_code, res.json()), (200, {"ok": True}))
        self.assertEqual((Attachment.objects.count(), self.files_on_disk()), (0, []))
        entry = AuditEntry.objects.order_by("-seq").first()
        self.assertEqual((entry.action, entry.detail, entry.actor_id), ("Image removed", "gate.png", self.users["pat"].id))
        self.assertTrue(verify_chain()["ok"])
        self.assertError(self.show(image_id), 404, "Image not found")
        self.assertError(self.remove(image_id), 404, "Image not found")

    def test_an_administrator_may_remove_anyones_image(self):
        self.assertEqual(self.remove(self.add("rex"), "ada").status_code, 200)

    def test_others_may_not(self):
        image_id = self.add("pat")
        message = "Only the uploader or an administrator can remove an image"
        for user in ("rex", "rhea", "aud"):
            self.assertError(self.remove(image_id, user), 403, message)
        self.assertError(self.remove(image_id, "pia"), 404, "Plan not found")
        self.assertError(self.remove("nope"), 404, "Image not found")
        self.assertError(Client().delete(f"/api/attachments/{image_id}", headers=XHR), 401, "Not signed in")
        self.assertEqual(Attachment.objects.count(), 1)

    def test_a_reviewers_own_image_can_be_removed_by_that_reviewer(self):
        self.assertEqual(self.remove(self.add("rex"), "rex").status_code, 200)
