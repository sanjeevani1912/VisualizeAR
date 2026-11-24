"""A password somebody else set must be replaced before anything else works."""

import os
from io import StringIO
from unittest import mock

from django.core.management import call_command
from django.test import Client

from api.models import User

from .helpers import PASSWORD, ApiTestCase

TEMP = "Temporary-Pass-31"
OWN = "My-Own-Choice-58"


class ForcedChangeTests(ApiTestCase):
    def setUp(self):
        self.admin = self.make_user("iyer", "admin", name="S. Iyer")
        self.planner = self.make_user("singh", "planner")
        _, self.admin_who = self.app_login("iyer")

    def flag(self, username, value=True):
        User.objects.filter(username=username).update(must_change_password=value)

    def test_normal_accounts_are_not_flagged_and_replies_keep_their_shape(self):
        res = self.post(Client(), "/api/auth/token", {"username": "singh", "password": PASSWORD})
        self.assertNotIn("mustChangePassword", res.json()["user"])
        _, who = self.app_login("singh")
        self.assertEqual(Client().get("/api/plans", headers=who).status_code, 200)

    def test_an_account_created_by_an_admin_is_flagged(self):
        body = {"username": "khan", "name": "M. Khan", "rank": "Captain", "role": "planner", "password": TEMP}
        self.assertEqual(self.post(Client(), "/api/admin/users", body, headers=self.admin_who).status_code, 201)
        self.assertTrue(User.objects.get(username="khan").must_change_password)

    def test_an_admin_reset_flags_the_account(self):
        res = self.post(Client(), f"/api/admin/users/{self.planner.id}/password", {"password": TEMP}, headers=self.admin_who)
        self.assertEqual(res.status_code, 200)
        self.assertTrue(User.objects.get(username="singh").must_change_password)

    def test_the_commands_flag_the_account(self):
        env = {"DHRUVAR_PASSWORD": TEMP}
        with mock.patch.dict(os.environ, env):
            call_command("resetpassword", "--username", "singh", stdout=StringIO())
            call_command("createuser", "--username", "rao", "--name", "K. Rao", "--rank", "Major", "--role", "planner", stdout=StringIO())
        self.assertTrue(User.objects.get(username="singh").must_change_password)
        self.assertTrue(User.objects.get(username="rao").must_change_password)

    def test_a_flagged_user_can_sign_in_but_nothing_else_works(self):
        self.flag("singh")
        res = self.post(Client(), "/api/auth/token", {"username": "singh", "password": PASSWORD})
        self.assertEqual(res.status_code, 200)
        self.assertIs(res.json()["user"]["mustChangePassword"], True)
        who = {"Authorization": f"Bearer {res.json()['token']}"}
        for method, path in (("get", "/api/plans"), ("get", "/api/users"), ("get", "/api/audit"), ("get", "/api/ranks")):
            r = Client().get(path, headers=who)
            self.assertEqual((r.status_code, r.json()), (403, {"error": "Password change required"}), path)
        r = self.post(Client(), "/api/plans", {"layout": {}}, headers=who)
        self.assertEqual((r.status_code, r.json()), (403, {"error": "Password change required"}))
        me = Client().get("/api/auth/me", headers=who)
        self.assertEqual(me.status_code, 200)
        self.assertIs(me.json()["mustChangePassword"], True)

    def test_a_flagged_admin_cannot_use_admin_routes(self):
        self.flag("iyer")
        res = Client().get("/api/admin/users", headers=self.admin_who)
        self.assertEqual((res.status_code, res.json()), (403, {"error": "Password change required"}))

    def test_changing_the_password_clears_the_flag_and_unblocks(self):
        self.flag("singh")
        _, who = self.app_login("singh")
        self.assertEqual(Client().get("/api/plans", headers=who).status_code, 403)
        res = self.post(Client(), "/api/auth/change-password", {"current": PASSWORD, "next": OWN}, headers=who)
        self.assertEqual(res.status_code, 200)
        self.assertFalse(User.objects.get(username="singh").must_change_password)
        _, fresh = self.app_login("singh", OWN)
        self.assertEqual(Client().get("/api/plans", headers=fresh).status_code, 200)
        self.assertNotIn("mustChangePassword", Client().get("/api/auth/me", headers=fresh).json())

    def test_the_new_password_must_differ_and_follow_the_rules(self):
        self.flag("singh")
        _, who = self.app_login("singh")
        r = self.post(Client(), "/api/auth/change-password", {"current": PASSWORD, "next": PASSWORD}, headers=who)
        self.assertEqual((r.status_code, r.json()), (400, {"error": "The new password must be different from the current one"}))
        r = self.post(Client(), "/api/auth/change-password", {"current": PASSWORD, "next": "weak"}, headers=who)
        self.assertEqual(r.status_code, 400)
        self.assertTrue(User.objects.get(username="singh").must_change_password)

    def test_a_flagged_user_can_sign_out(self):
        self.flag("singh")
        _, who = self.app_login("singh")
        self.assertEqual(self.post(Client(), "/api/auth/logout", headers=who).status_code, 200)
        self.assertEqual(Client().get("/api/auth/me", headers=who).status_code, 401)

    def test_the_cookie_dashboard_is_blocked_the_same_way(self):
        self.flag("singh")
        c = Client()
        self.assertEqual(self.post(c, "/api/auth/login", {"username": "singh", "password": PASSWORD}).status_code, 200)
        self.assertEqual(c.get("/api/plans").status_code, 403)
        self.assertEqual(self.post(c, "/api/auth/change-password", {"current": PASSWORD, "next": OWN}).status_code, 200)
        self.assertEqual(c.get("/api/plans").status_code, 200)  # the reply carried a fresh session cookie
