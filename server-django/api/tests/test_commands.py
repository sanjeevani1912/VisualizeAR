import os
from io import StringIO
from unittest import mock

from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import Client

from api.audit import verify_chain
from api.models import AuditEntry, AuthSession, User

from .helpers import PASSWORD, ApiTestCase

NEW = "Fresh-Start-Pass-7"


def run(command, *args, password=None):
    out = StringIO()
    env = {"DHRUVAR_PASSWORD": password} if password is not None else {}
    with mock.patch.dict(os.environ, env):
        call_command(command, *args, stdout=out)
    return out.getvalue()


class ResetPasswordCommandTests(ApiTestCase):
    def setUp(self):
        self.user = self.make_user("iyer", "admin", name="S. Iyer")

    def test_resets_ends_sessions_and_is_audited_without_the_password(self):
        _, who = self.app_login("iyer")
        text = run("resetpassword", "--username", "IYER", password=NEW)  # any letter case
        self.assertNotIn(NEW, text)
        self.assertEqual(Client().get("/api/auth/me", headers=who).status_code, 401)
        self.assertEqual(AuthSession.objects.filter(user=self.user).count(), 0)
        self.assertEqual(self.post(Client(), "/api/auth/token", {"username": "iyer", "password": NEW}).status_code, 200)
        self.assertEqual(self.post(Client(), "/api/auth/token", {"username": "iyer", "password": PASSWORD}).status_code, 401)
        entry = AuditEntry.objects.get(action="Password reset")
        self.assertEqual(entry.detail, "Password reset for iyer (command line)")
        self.assertNotIn(NEW, entry.detail)
        self.assertTrue(verify_chain()["ok"])

    def test_unknown_user_and_weak_passwords_are_refused(self):
        with self.assertRaisesMessage(CommandError, "No such user"):
            run("resetpassword", "--username", "nobody", password=NEW)
        for weak, message in (("short1A", "at least 10"), ("alllowercase123", "upper case"), ("Iyer-Secret-123", "username")):
            with self.assertRaisesMessage(CommandError, message):
                run("resetpassword", "--username", "iyer", password=weak)
        self.assertFalse(AuditEntry.objects.filter(action="Password reset").exists())

    def test_a_disabled_account_is_reset_and_the_admin_is_told(self):
        User.objects.filter(pk=self.user.pk).update(active=False)
        self.assertIn("disabled", run("resetpassword", "--username", "iyer", password=NEW))


class ListUsersCommandTests(ApiTestCase):
    def setUp(self):
        self.make_user("iyer", "admin", name="S. Iyer")
        self.make_user("singh", "planner", name="V. Singh", active=False)
        User.objects.filter(username="iyer").update(appointment="CO")

    def test_lists_everyone_without_secrets(self):
        text = run("listusers")
        self.assertIn("iyer", text)
        self.assertIn("singh", text)
        self.assertIn("CO", text)
        self.assertIn("NO", text)  # singh is disabled
        self.assertIn("2 user(s)", text)
        for secret in (PASSWORD, "md5$", "pbkdf2"):
            self.assertNotIn(secret, text)

    def test_role_filter(self):
        text = run("listusers", "--role", "planner")
        self.assertIn("singh", text)
        self.assertNotIn("iyer", text)
        self.assertIn("1 user(s)", text)


class CreateUserCommandTests(ApiTestCase):
    def test_rank_must_be_from_the_list_and_appointment_is_stored(self):
        args = ["--username", "rao", "--name", "K. Rao", "--role", "planner"]
        with self.assertRaisesMessage(CommandError, "rank must be one of"):
            run("createuser", *args, "--rank", "Lt Col", password=NEW)
        with self.assertRaisesMessage(CommandError, "appointment: at most 50"):
            run("createuser", *args, "--rank", "Major", "--appointment", "x" * 51, password=NEW)
        run("createuser", *args, "--rank", "Major", "--appointment", " Adjt ", password=NEW)
        user = User.objects.get(username="rao")
        self.assertEqual((user.rank, user.appointment, user.role), ("Major", "Adjt", "planner"))
