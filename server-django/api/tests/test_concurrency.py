"""Many requests at the same moment. These tests need real parallel database connections, so they run on
PostgreSQL only (on SQLite, writers already take turns because every write transaction starts with BEGIN IMMEDIATE;
that was checked with a real running server in phase 2).

Run them with:  DB_ENGINE=postgres DB_PASSWORD=... python manage.py test api.tests.test_concurrency
"""

import json
import threading
import time
import unittest
import unittest.mock

from django.db import connection, transaction
from django.test import Client, TransactionTestCase, override_settings

from api import audit as audit_module
from api.audit import audit, verify_chain
from api.auth import check_locked
from api.models import AuditEntry, LoginThrottle, Plan, User

from .helpers import FAST_HASH, PASSWORD, XHR
from .test_plans import layout

ON_POSTGRES = connection.vendor == "postgresql"


def run_in_threads(count, work):
    """Runs work(i) in `count` threads that all start at the same moment. Returns the results; re-raises the first error."""
    start = threading.Barrier(count)
    results, errors = [None] * count, []

    def runner(i):
        try:
            start.wait()
            results[i] = work(i)
        except BaseException as e:  # reported below, in the main thread
            errors.append(e)
        finally:
            connection.close()  # each thread has its own database connection

    threads = [threading.Thread(target=runner, args=(i,)) for i in range(count)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    if errors:
        raise errors[0]
    return results


@unittest.skipUnless(ON_POSTGRES, "needs PostgreSQL: parallel connections")
@override_settings(PASSWORD_HASHERS=FAST_HASH)
class AuditChainConcurrencyTests(TransactionTestCase):
    def write_entries(self, per_thread, threads):
        def work(i):
            for n in range(per_thread):
                with transaction.atomic():
                    audit(f"u{i}", "Commented", "p1", f"thread {i} entry {n}")
                    time.sleep(0.002)  # holds the transaction open a moment, so writers really overlap

        run_in_threads(threads, work)

    def test_simultaneous_writes_keep_one_unbroken_chain(self):
        self.write_entries(per_thread=10, threads=8)
        self.assertEqual(verify_chain(), {"ok": True, "entries": 80})
        previous = list(AuditEntry.objects.values_list("prev_hash", flat=True))
        self.assertEqual(len(set(previous)), 80, "two entries chained to the same predecessor")

    def test_without_the_lock_the_chain_would_break(self):
        """Proves the test above can fail: with the lock switched off, simultaneous writers fork the chain."""
        with unittest.mock.patch.object(audit_module, "_lock_chain", lambda: None):
            self.write_entries(per_thread=10, threads=8)
        self.assertFalse(verify_chain()["ok"])


@unittest.skipUnless(ON_POSTGRES, "needs PostgreSQL: parallel connections")
@override_settings(PASSWORD_HASHERS=FAST_HASH)
class PlanCodeConcurrencyTests(TransactionTestCase):
    def test_simultaneous_submissions_never_share_a_plan_code(self):
        users = [User.objects.create_user(f"planner{i}", PASSWORD, name=f"P{i}", rank="Major", role="planner") for i in range(12)]
        tokens = []
        for u in users:
            res = Client().post("/api/auth/token", data=json.dumps({"username": u.username, "password": PASSWORD}), content_type="application/json")
            tokens.append({"Authorization": f"Bearer {res.json()['token']}"})

        def submit(i):
            res = Client().post("/api/plans", data=json.dumps({"layout": layout(f"Plan {i}")}), content_type="application/json", headers={**tokens[i], **XHR})
            return res.status_code, res.json()

        results = run_in_threads(12, submit)
        self.assertEqual([r[0] for r in results], [201] * 12, results)
        codes = sorted(r[1]["code"] for r in results)
        self.assertEqual(len(set(codes)), 12)
        self.assertEqual(Plan.objects.count(), 12)
        self.assertEqual(verify_chain()["entries"], 12 + 12)  # 12 sign-ins + 12 submissions
        self.assertTrue(verify_chain()["ok"])


@unittest.skipUnless(ON_POSTGRES, "needs PostgreSQL: parallel connections")
@override_settings(PASSWORD_HASHERS=FAST_HASH, LOGIN_MAX_FAILURES=5)
class LoginLockoutConcurrencyTests(TransactionTestCase):
    def test_five_simultaneous_wrong_passwords_still_lock_the_account(self):
        User.objects.create_user("victim", PASSWORD, name="V", rank="Major", role="planner")

        def guess(i):
            return Client().post("/api/auth/token", data=json.dumps({"username": "victim", "password": f"wrong-guess-{i}"}), content_type="application/json").status_code

        statuses = run_in_threads(5, guess)
        self.assertEqual(statuses, [401] * 5)
        self.assertGreater(check_locked("victim", "127.0.0.1"), 0, "failures were lost: the counter did not reach 5")
        self.assertEqual(LoginThrottle.objects.count(), 1)
        right = Client().post("/api/auth/token", data=json.dumps({"username": "victim", "password": PASSWORD}), content_type="application/json")
        self.assertEqual(right.status_code, 429)  # even the right password is refused while locked

    def test_simultaneous_guesses_never_lose_a_count(self):
        User.objects.create_user("victim2", PASSWORD, name="V", rank="Major", role="planner")
        with override_settings(LOGIN_MAX_FAILURES=50):
            run_in_threads(12, lambda i: Client().post("/api/auth/token", data=json.dumps({"username": "victim2", "password": f"nope{i}"}), content_type="application/json").status_code)
        self.assertEqual(LoginThrottle.objects.get().count, 12)


@unittest.skipUnless(ON_POSTGRES, "needs PostgreSQL: parallel connections")
@override_settings(PASSWORD_HASHERS=FAST_HASH, MAX_IMAGES_PER_PLAN=3)
class PlanRuleConcurrencyTests(TransactionTestCase):
    def setUp(self):
        import shutil
        import tempfile
        from pathlib import Path

        self.dir = Path(tempfile.mkdtemp())
        (self.dir / "uploads").mkdir()
        patcher = override_settings(DATA_DIR=self.dir)
        patcher.enable()
        self.addCleanup(patcher.disable)
        self.addCleanup(shutil.rmtree, self.dir, ignore_errors=True)
        self.tokens = {}
        for name, role in [("owner", "planner"), ("rev1", "reviewer"), ("rev2", "reviewer")]:
            User.objects.create_user(name, PASSWORD, name=name, rank="Major", role=role)
            res = Client().post("/api/auth/token", data=json.dumps({"username": name, "password": PASSWORD}), content_type="application/json")
            self.tokens[name] = {"Authorization": f"Bearer {res.json()['token']}"}
        res = Client().post("/api/plans", data=json.dumps({"layout": layout()}), content_type="application/json", headers=self.tokens["owner"])
        self.plan_id = res.json()["id"]

    def test_simultaneous_decisions_on_one_plan_let_exactly_one_through(self):
        def decide(i):
            who = self.tokens["rev1" if i % 2 else "rev2"]
            body = {"decision": "approve", "version": 1}
            return Client().post(f"/api/plans/{self.plan_id}/decision", data=json.dumps(body), content_type="application/json", headers=who).status_code

        statuses = run_in_threads(8, decide)
        self.assertEqual(sorted(statuses), [200] + [403] * 7)
        self.assertEqual(Plan.objects.get(pk=self.plan_id).decisions.count(), 1)
        self.assertTrue(verify_chain()["ok"])

    def test_simultaneous_uploads_cannot_pass_the_image_limit(self):
        import base64

        from django.core.files.uploadedfile import SimpleUploadedFile

        png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")

        def upload(i):
            return Client().post(f"/api/plans/{self.plan_id}/attachments", data={"file": SimpleUploadedFile(f"p{i}.png", png, "image/png")}, headers=self.tokens["owner"]).status_code

        statuses = run_in_threads(8, upload)
        self.assertEqual(sorted(statuses), [201] * 3 + [409] * 5)
        self.assertEqual(len(list((self.dir / "uploads").iterdir())), 3)  # no file left behind for the refused ones
        self.assertTrue(verify_chain()["ok"])
