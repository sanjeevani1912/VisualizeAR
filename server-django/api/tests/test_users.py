from django.test import Client

from api.audit import verify_chain
from api.models import AuditEntry, AuthSession, User

from .helpers import PASSWORD, ApiTestCase

NEW_USER = {"username": "khan", "name": "M. Khan", "rank": "Lieutenant", "role": "planner", "password": "Brand-New-Pass-9"}


class UserListTests(ApiTestCase):
    def setUp(self):
        self.make_user("iyer", "admin", name="S. Iyer", rank="Lt Col")
        self.make_user("singh", "planner", name="V. Singh")
        self.make_user("rao", "planner", name="K. Rao", active=False)

    def test_names_list_needs_sign_in(self):
        res = Client().get("/api/users")
        self.assertEqual((res.status_code, res.json()), (401, {"error": "Not signed in"}))

    def test_any_signed_in_user_gets_names_only_sorted_by_name(self):
        _, who = self.app_login("singh")
        rows = Client().get("/api/users", headers=who).json()
        self.assertEqual([r["name"] for r in rows], ["K. Rao", "S. Iyer", "V. Singh"])
        self.assertEqual(set(rows[0]), {"id", "name", "rank", "role", "active"})  # no username, no password
        self.assertIs(rows[0]["active"], False)
        self.assertIs(rows[1]["active"], True)

    def test_admin_list_is_admin_only(self):
        _, planner = self.app_login("singh")
        res = Client().get("/api/admin/users", headers=planner)
        self.assertEqual((res.status_code, res.json()), (403, {"error": "Your role is not allowed to do this"}))

    def test_admin_list_shape(self):
        _, admin = self.app_login("iyer")
        rows = Client().get("/api/admin/users", headers=admin).json()
        self.assertEqual(len(rows), 3)
        iyer = next(r for r in rows if r["username"] == "iyer")
        self.assertEqual(set(iyer), {"id", "username", "name", "rank", "role", "active", "createdAt", "lastLoginAt"})
        self.assertIsInstance(iyer["createdAt"], int)
        self.assertIsInstance(iyer["lastLoginAt"], int)  # signed in a moment ago
        rao = next(r for r in rows if r["username"] == "rao")
        self.assertIsNone(rao["lastLoginAt"])
        self.assertNotIn("password", iyer)


class CreateUserTests(ApiTestCase):
    def setUp(self):
        self.admin = self.make_user("iyer", "admin", name="S. Iyer", rank="Lt Col")
        _, self.who = self.app_login("iyer")

    def create(self, **override):
        body = {**NEW_USER, **override}
        for key in [k for k, v in override.items() if v is None]:
            body.pop(key)
        return self.post(Client(), "/api/admin/users", body, headers=self.who)

    def test_creates_a_user_who_can_sign_in(self):
        res = self.create()
        self.assertEqual(res.status_code, 201)
        new_id = res.json()["id"]
        self.assertEqual(set(res.json()), {"id"})
        user = User.objects.get(pk=new_id)
        self.assertEqual((user.username, user.name, user.rank, user.role, user.active), ("khan", "M. Khan", "Lieutenant", "planner", True))
        self.assertNotEqual(user.password, NEW_USER["password"])  # stored hashed
        self.assertEqual(self.post(Client(), "/api/auth/token", {"username": "khan", "password": NEW_USER["password"]}).status_code, 200)

    def test_it_is_audited_by_the_admin(self):
        self.create()
        entry = AuditEntry.objects.get(action="User created")
        self.assertEqual((entry.detail, entry.actor_id), ("khan created as planner", self.admin.id))
        self.assertEqual(verify_chain()["ok"], True)

    def test_only_admins_may_create(self):
        self.make_user("singh", "planner")
        _, planner = self.app_login("singh")
        res = self.post(Client(), "/api/admin/users", NEW_USER, headers=planner)
        self.assertEqual(res.status_code, 403)
        self.assertFalse(User.objects.filter(username="khan").exists())

    def test_the_csrf_header_is_needed_for_cookie_sessions(self):
        c = Client()
        self.post(c, "/api/auth/login", {"username": "iyer", "password": PASSWORD})
        res = self.post(c, "/api/admin/users", NEW_USER, headers={})
        self.assertEqual((res.status_code, res.json()), (403, {"error": "Missing request header"}))
        self.assertEqual(self.post(c, "/api/admin/users", NEW_USER).status_code, 201)

    def test_messages_match_the_node_server(self):
        cases = [
            ({"username": "ab"}, "username: Too small: expected string to have >=3 characters"),
            ({"username": "x" * 41}, "username: Too big: expected string to have <=40 characters"),
            ({"username": "a b c"}, "username: letters, digits, dot, dash or underscore only"),
            ({"username": 123}, "username: Invalid input: expected string, received number"),
            ({"username": None}, "username: Invalid input: expected string, received undefined"),
            ({"name": "   "}, "name: Too small: expected string to have >=1 characters"),
            ({"name": "n" * 81}, "name: Too big: expected string to have <=80 characters"),
            ({"rank": "r" * 41}, "rank: Too big: expected string to have <=40 characters"),
            ({"rank": True}, "rank: Invalid input: expected string, received boolean"),
            ({"role": "boss"}, 'role: Invalid option: expected one of "planner"|"reviewer"|"admin"|"auditor"'),
            ({"role": None}, 'role: Invalid option: expected one of "planner"|"reviewer"|"admin"|"auditor"'),
            ({"password": "p" * 201}, "password: Too big: expected string to have <=200 characters"),
            ({"password": None}, "password: Invalid input: expected string, received undefined"),
            ({"username": "ab", "role": "boss"}, "username: Too small: expected string to have >=3 characters"),  # first field wins
        ]
        for override, message in cases:
            res = self.create(**override)
            self.assertEqual((res.status_code, res.json()), (400, {"error": message}), override)

    def test_a_non_object_body(self):
        res = self.post(Client(), "/api/admin/users", [], headers=self.who)
        self.assertEqual((res.status_code, res.json()), (400, {"error": ": Invalid input: expected object, received array"}))
        res = self.post(Client(), "/api/admin/users", None, headers=self.who)
        self.assertEqual((res.status_code, res.json()), (400, {"error": ": Invalid input: expected object, received undefined"}))

    def test_password_rules(self):
        for password, message in (
            ("Short1a", "Password must be at least 10 characters"),
            ("alllowercase123", "Password needs upper case, lower case and a digit"),
            ("Khan-Secret-123", "Password must not contain the username"),
        ):
            res = self.create(password=password)
            self.assertEqual((res.status_code, res.json()), (400, {"error": message}), password)

    def test_names_are_trimmed(self):
        self.create(username="  khan  ", name="  M. Khan  ")
        user = User.objects.get(username="khan")
        self.assertEqual(user.name, "M. Khan")

    def test_duplicate_usernames_conflict_in_any_case(self):
        self.assertEqual(self.create().status_code, 201)
        for name in ("khan", "KHAN", "Khan"):
            res = self.create(username=name)
            self.assertEqual((res.status_code, res.json()), (409, {"error": "That username is already taken"}), name)
        self.assertEqual(User.objects.filter(username__iexact="khan").count(), 1)


class UpdateUserTests(ApiTestCase):
    def setUp(self):
        self.admin = self.make_user("iyer", "admin", name="S. Iyer", rank="Lt Col")
        self.planner = self.make_user("singh", "planner", name="V. Singh")
        _, self.who = self.app_login("iyer")

    def patch_user(self, user, body, who=None):
        return self.patch(Client(), f"/api/admin/users/{user.id}", body, headers=who or self.who)

    def test_unknown_user_is_404(self):
        res = self.patch(Client(), "/api/admin/users/not-a-real-id", {"name": "X"}, headers=self.who)
        self.assertEqual((res.status_code, res.json()), (404, {"error": "User not found"}))

    def test_validation_messages(self):
        for body, message in (
            ({"role": "boss"}, 'role: Invalid option: expected one of "planner"|"reviewer"|"admin"|"auditor"'),
            ({"active": "yes"}, "active: Invalid input: expected boolean, received string"),
            ({"active": None}, "active: Invalid input: expected boolean, received null"),
            ({"name": ""}, "name: Too small: expected string to have >=1 characters"),
            ({"name": None}, "name: Invalid input: expected string, received null"),
        ):
            res = self.patch_user(self.planner, body)
            self.assertEqual((res.status_code, res.json()), (400, {"error": message}), body)

    def test_changing_a_name_or_rank_keeps_sessions(self):
        _, planner_who = self.app_login("singh")
        res = self.patch_user(self.planner, {"name": "  Maj. V. Singh ", "rank": "Lt Col"})
        self.assertEqual(res.json(), {"ok": True})
        self.planner.refresh_from_db()
        self.assertEqual((self.planner.name, self.planner.rank, self.planner.role), ("Maj. V. Singh", "Lt Col", "planner"))
        self.assertEqual(Client().get("/api/auth/me", headers=planner_who).status_code, 200)
        self.assertEqual(AuditEntry.objects.get(action="User updated").detail, "singh")  # nothing sensitive changed

    def test_changing_the_role_ends_that_users_sessions_and_is_audited(self):
        _, planner_who = self.app_login("singh")
        self.patch_user(self.planner, {"role": "reviewer"})
        self.assertEqual(Client().get("/api/auth/me", headers=planner_who).status_code, 401)
        self.assertEqual(AuditEntry.objects.get(action="User updated").detail, "singh: role planner -> reviewer")

    def test_deactivating_ends_sessions_and_blocks_sign_in(self):
        _, planner_who = self.app_login("singh")
        self.patch_user(self.planner, {"active": False})
        self.assertEqual(Client().get("/api/auth/me", headers=planner_who).status_code, 401)
        self.assertEqual(self.post(Client(), "/api/auth/token", {"username": "singh", "password": PASSWORD}).status_code, 401)
        self.assertEqual(AuditEntry.objects.get(action="User updated").detail, "singh: deactivated")
        self.patch_user(self.planner, {"active": True})
        self.assertEqual(AuditEntry.objects.filter(action="User updated").latest("seq").detail, "singh: activated")
        self.assertEqual(self.post(Client(), "/api/auth/token", {"username": "singh", "password": PASSWORD}).status_code, 200)

    def test_setting_the_same_values_records_no_changes(self):
        self.patch_user(self.planner, {"role": "planner", "active": True})
        self.assertEqual(AuditEntry.objects.get(action="User updated").detail, "singh")

    def test_you_cannot_demote_or_deactivate_yourself(self):
        for body in ({"role": "reviewer"}, {"active": False}):
            res = self.patch_user(self.admin, body)
            self.assertEqual((res.status_code, res.json()), (409, {"error": "You cannot demote or deactivate yourself"}), body)
        self.assertEqual(self.patch_user(self.admin, {"name": "S. Iyer Jr"}).status_code, 200)  # other edits are fine

    def test_an_admin_can_demote_another_admin_but_never_leave_zero(self):
        """The 'at least one active administrator' rule can only fire in a race, because a caller is always an
        active admin themselves. What can be tested through the API is that the last one stays an admin."""
        other = self.make_user("nair", "admin", name="P. Nair")
        _, nair_who = self.app_login("nair")
        self.assertEqual(self.patch_user(other, {"role": "auditor"}).status_code, 200)  # iyer demotes nair: two admins -> one
        self.assertEqual(Client().get("/api/auth/me", headers=nair_who).status_code, 401)  # nair's sessions ended
        from api.auth import active_admin_count

        self.assertEqual(active_admin_count(), 1)
        res = self.patch_user(self.admin, {"role": "planner"})  # iyer is the last admin and cannot demote herself
        self.assertEqual((res.status_code, res.json()), (409, {"error": "You cannot demote or deactivate yourself"}))

    def test_only_admins_may_update(self):
        _, planner_who = self.app_login("singh")
        res = self.patch(Client(), f"/api/admin/users/{self.admin.id}", {"name": "X"}, headers=planner_who)
        self.assertEqual(res.status_code, 403)


class ResetPasswordTests(ApiTestCase):
    def setUp(self):
        self.admin = self.make_user("iyer", "admin", name="S. Iyer")
        self.planner = self.make_user("singh", "planner")
        _, self.who = self.app_login("iyer")

    def reset(self, user_id, password):
        return self.post(Client(), f"/api/admin/users/{user_id}/password", {"password": password}, headers=self.who)

    def test_reset_works_ends_sessions_and_is_audited(self):
        _, planner_who = self.app_login("singh")
        res = self.reset(self.planner.id, "Reset-By-Admin-5")
        self.assertEqual(res.json(), {"ok": True})
        self.assertEqual(Client().get("/api/auth/me", headers=planner_who).status_code, 401)
        self.assertEqual(self.post(Client(), "/api/auth/token", {"username": "singh", "password": "Reset-By-Admin-5"}).status_code, 200)
        self.assertEqual(self.post(Client(), "/api/auth/token", {"username": "singh", "password": PASSWORD}).status_code, 401)
        self.assertEqual(AuditEntry.objects.get(action="Password reset").detail, "Password reset for singh")

    def test_errors(self):
        res = self.post(Client(), f"/api/admin/users/{self.planner.id}/password", {}, headers=self.who)
        self.assertEqual((res.status_code, res.json()), (400, {"error": "A password is required"}))
        res = self.reset("nope", "Reset-By-Admin-5")
        self.assertEqual((res.status_code, res.json()), (404, {"error": "User not found"}))
        res = self.reset(self.planner.id, "Singh-Secret-123")
        self.assertEqual((res.status_code, res.json()), (400, {"error": "Password must not contain the username"}))
        res = self.reset(self.planner.id, "tooshort")
        self.assertEqual(res.status_code, 400)

    def test_only_admins_may_reset(self):
        _, planner_who = self.app_login("singh")
        res = self.post(Client(), f"/api/admin/users/{self.admin.id}/password", {"password": "Reset-By-Admin-5"}, headers=planner_who)
        self.assertEqual(res.status_code, 403)
        self.assertEqual(AuthSession.objects.filter(user=self.admin).count(), 1)  # admin untouched

    def test_the_audit_chain_is_intact_after_everything(self):
        self.reset(self.planner.id, "Reset-By-Admin-5")
        self.assertTrue(verify_chain()["ok"])
