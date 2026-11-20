"""The `seed` and `createuser` commands."""

import io
import json
import shutil
import tempfile
from pathlib import Path
from unittest import mock

from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import Client, override_settings

from api import layout
from api.audit import verify_chain
from api.models import Attachment, AuditEntry, Comment, Decision, Plan, PlanVersion, User, now_ms

from .helpers import ApiTestCase


class SeedTestCase(ApiTestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        (self.dir / "uploads").mkdir()
        patcher = override_settings(DATA_DIR=self.dir)
        patcher.enable()
        self.addCleanup(patcher.disable)
        self.addCleanup(shutil.rmtree, self.dir, ignore_errors=True)

    def seed(self, *args):
        out = io.StringIO()
        call_command("seed", *args, stdout=out)
        return out.getvalue()

    def credentials(self):
        lines = (self.dir / "seed-credentials.txt").read_text(encoding="utf-8").splitlines()[2:]
        return {parts[0]: parts[2] for parts in (line.split() for line in lines)}

    def token(self, username):
        res = Client().post("/api/auth/token", data=json.dumps({"username": username, "password": self.credentials()[username]}), content_type="application/json")
        self.assertEqual(res.status_code, 200, res.content)
        return {"Authorization": f"Bearer {res.json()['token']}"}


class SeedTests(SeedTestCase):
    def test_creates_the_sample_users_and_plans(self):
        out = self.seed()
        self.assertEqual(User.objects.count(), 8)
        self.assertEqual(Plan.objects.count(), 12)
        self.assertEqual(sorted(User.objects.values_list("username", flat=True)), ["bose", "iyer", "khan", "menon", "nair", "rao", "sharma", "singh"])
        self.assertEqual(User.objects.get(username="iyer").role, "admin")
        self.assertIn("Seeded 8 users and 12 sample plans.", out)
        self.assertTrue(PlanVersion.objects.exists() and Decision.objects.exists() and Comment.objects.exists())
        self.assertEqual(Attachment.objects.count(), 0)  # like the Node seed, no images

    def test_passwords_are_random_written_once_to_a_file_and_never_printed(self):
        out = self.seed()
        passwords = self.credentials()
        self.assertEqual(len(passwords), 8)
        self.assertEqual(len(set(passwords.values())), 8)
        for password in passwords.values():
            self.assertNotIn(password, out)
            self.assertGreaterEqual(len(password), 12)
        self.assertEqual(self.token("menon")["Authorization"][:7], "Bearer ")
        self.assertEqual(Client().post("/api/auth/token", data=json.dumps({"username": "menon", "password": "nope"}), content_type="application/json").status_code, 401)

    def test_refuses_to_run_when_users_exist(self):
        self.seed()
        with self.assertRaisesMessage(CommandError, "Users already exist. Use `python manage.py seed --reset`"):
            self.seed()
        self.assertEqual(User.objects.count(), 8)

    def test_reset_wipes_everything_and_starts_again(self):
        self.seed()
        old_password = self.credentials()["menon"]
        extra = Plan.objects.first()
        (self.dir / "uploads" / "leftover.png").write_bytes(b"x")
        User.objects.create_user("extra", "Whatever-123", name="E", rank="R", role="planner")
        self.seed("--reset")
        self.assertEqual((User.objects.count(), Plan.objects.count()), (8, 12))
        self.assertFalse(User.objects.filter(username="extra").exists())
        self.assertEqual(list((self.dir / "uploads").iterdir()), [])
        self.assertEqual(AuditEntry.objects.order_by("seq").first().seq, 1)  # the counter restarted
        self.assertTrue(verify_chain()["ok"])
        self.assertNotEqual(self.credentials()["menon"], old_password)
        self.assertIsNotNone(extra)

    def test_the_audit_chain_verifies_and_is_oldest_first(self):
        self.seed()
        self.assertEqual(verify_chain(), {"ok": True, "entries": 21})
        times = list(AuditEntry.objects.order_by("seq").values_list("at", flat=True))
        self.assertEqual(times, sorted(times))

    def test_times_are_moved_to_the_present(self):
        before = now_ms()
        self.seed()
        newest = max(Plan.objects.values_list("updated_at", flat=True))
        self.assertLessEqual(newest, now_ms())
        self.assertGreater(newest, before - 30 * 86_400_000)
        self.assertLess(min(Plan.objects.values_list("created_at", flat=True)), newest)

    def test_stored_fingerprints_match_the_layout_text(self):
        self.seed()
        for version in PlanVersion.objects.all():
            self.assertEqual(version.content_hash, layout.content_hash(json.loads(version.layout_json)))
            self.assertEqual(layout.layout_text(json.loads(version.layout_json)), version.layout_json)

    def test_roles_see_the_right_plans_through_the_api(self):
        self.seed()
        reviewer = Client().get("/api/plans", headers=self.token("menon")).json()
        self.assertEqual(len(reviewer), 12)
        planner = Client().get("/api/plans", headers=self.token("singh")).json()
        self.assertTrue(planner)
        self.assertTrue(all(p["ownerId"] == "u-p1" for p in planner))
        verified = Client().get("/api/audit/verify", headers=self.token("nair")).json()
        self.assertTrue(verified["ok"] and verified["entries"] >= 21)  # the sample entries plus the sign-ins above
        self.assertEqual(Client().get("/api/audit/verify", headers=self.token("menon")).status_code, 403)


class CreateUserTests(SeedTestCase):
    def create(self, **env):
        args = ["--username", "newguy", "--name", "N. Guy", "--rank", "Captain", "--role", "planner"]
        with mock.patch.dict("os.environ", env):
            call_command("createuser", *args, stdout=io.StringIO())

    def test_creates_a_user_who_can_sign_in(self):
        self.create(DHRUVAR_PASSWORD="Str0ng-Passphrase-1")
        res = Client().post("/api/auth/token", data=json.dumps({"username": "newguy", "password": "Str0ng-Passphrase-1"}), content_type="application/json")
        self.assertEqual(res.status_code, 200)
        self.assertEqual(AuditEntry.objects.get(action="User created").detail, "newguy created as planner")

    def test_refuses_weak_passwords_and_duplicates(self):
        with self.assertRaisesMessage(CommandError, "Password must be at least 10 characters"):
            self.create(DHRUVAR_PASSWORD="weak")
        self.create(DHRUVAR_PASSWORD="Str0ng-Passphrase-1")
        with self.assertRaisesMessage(CommandError, "That username is already taken"):
            self.create(DHRUVAR_PASSWORD="Str0ng-Passphrase-1")
        self.assertEqual(User.objects.count(), 1)
