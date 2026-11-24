"""The super administrator: the only role that may create or change administrators."""

import os
from io import StringIO
from unittest import mock

from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import Client

from api.models import AuditEntry, AuthSession, User

from .helpers import ApiTestCase

ONLY_SUPER = {"error": "Only a super administrator may create or change administrators"}
FROM_COMMAND = {"error": "A super administrator can only be made from the command line (manage.py createuser or setrole)"}
NEW = {"username": "khan", "name": "M. Khan", "rank": "Captain", "role": "planner", "password": "Brand-New-Pass-9"}


class SuperAdminApiTests(ApiTestCase):
    def setUp(self):
        self.sup = self.make_user("chief", "superadmin", name="C. Chief")
        self.adm = self.make_user("iyer", "admin", name="S. Iyer")
        self.adm2 = self.make_user("nair", "admin", name="P. Nair")
        self.planner = self.make_user("singh", "planner")
        _, self.sup_who = self.app_login("chief")
        _, self.adm_who = self.app_login("iyer")

    def create(self, who, **override):
        return self.post(Client(), "/api/admin/users", {**NEW, **override}, headers=who)

    def patch_user(self, user, body, who):
        return self.patch(Client(), f"/api/admin/users/{user.id}", body, headers=who)

    def reset(self, user, who, password="Reset-By-Admin-5"):
        return self.post(Client(), f"/api/admin/users/{user.id}/password", {"password": password}, headers=who)

    # ---- creating ----
    def test_an_admin_creates_ordinary_users_but_not_administrators(self):
        for role in ("planner", "reviewer", "auditor"):
            self.assertEqual(self.create(self.adm_who, username=f"u.{role}", role=role).status_code, 201, role)
        res = self.create(self.adm_who, username="boss", role="admin")
        self.assertEqual((res.status_code, res.json()), (403, ONLY_SUPER))
        self.assertFalse(User.objects.filter(username="boss").exists())

    def test_a_super_admin_creates_administrators(self):
        self.assertEqual(self.create(self.sup_who, username="boss", role="admin").status_code, 201)
        self.assertEqual(User.objects.get(username="boss").role, "admin")

    def test_nobody_creates_a_super_admin_through_the_api(self):
        for who in (self.sup_who, self.adm_who):
            res = self.create(who, username="king", role="superadmin")
            self.assertEqual((res.status_code, res.json()), (403, FROM_COMMAND))
        self.assertFalse(User.objects.filter(username="king").exists())

    # ---- changing ----
    def test_an_admin_cannot_promote_demote_disable_or_edit_another_administrator(self):
        for body in ({"role": "planner"}, {"active": False}, {"name": "Hacked"}, {"appointment": "CO"}):
            res = self.patch_user(self.adm2, body, self.adm_who)
            self.assertEqual((res.status_code, res.json()), (403, ONLY_SUPER), body)
        res = self.patch_user(self.planner, {"role": "admin"}, self.adm_who)  # promoting someone to admin
        self.assertEqual((res.status_code, res.json()), (403, ONLY_SUPER))
        res = self.patch_user(self.sup, {"active": False}, self.adm_who)  # nor can they touch the super admin
        self.assertEqual((res.status_code, res.json()), (403, ONLY_SUPER))
        self.assertEqual(User.objects.get(username="nair").role, "admin")

    def test_an_admin_still_manages_ordinary_users(self):
        self.assertEqual(self.patch_user(self.planner, {"role": "reviewer", "rank": "Colonel", "appointment": "CO"}, self.adm_who).status_code, 200)
        self.assertEqual(self.patch_user(self.planner, {"active": False}, self.adm_who).status_code, 200)

    def test_an_admin_may_edit_their_own_name_but_not_their_role(self):
        self.assertEqual(self.patch_user(self.adm, {"name": "S. Iyer Jr", "appointment": "Adjt"}, self.adm_who).status_code, 200)
        res = self.patch_user(self.adm, {"role": "superadmin"}, self.adm_who)
        self.assertEqual(res.status_code, 409)  # you cannot change your own role

    def test_a_super_admin_manages_administrators(self):
        self.assertEqual(self.patch_user(self.planner, {"role": "admin"}, self.sup_who).status_code, 200)
        self.assertEqual(self.patch_user(self.adm2, {"role": "auditor"}, self.sup_who).status_code, 200)
        self.assertEqual(self.patch_user(self.adm, {"active": False}, self.sup_who).status_code, 200)
        self.assertEqual(AuthSession.objects.filter(user=self.adm).count(), 0)

    def test_the_api_cannot_promote_to_super_admin(self):
        for who in (self.sup_who, self.adm_who):
            res = self.patch_user(self.planner, {"role": "superadmin"}, who)
            self.assertEqual((res.status_code, res.json()), (403, FROM_COMMAND))

    def test_a_super_admin_may_demote_another_super_admin_while_one_remains(self):
        other = self.make_user("deputy", "superadmin", name="D. Deputy")
        self.assertEqual(self.patch_user(other, {"role": "admin"}, self.sup_who).status_code, 200)

    def test_you_cannot_demote_or_disable_yourself_even_as_super_admin(self):
        for body in ({"role": "admin"}, {"active": False}):
            res = self.patch_user(self.sup, body, self.sup_who)
            self.assertEqual((res.status_code, res.json()), (409, {"error": "You cannot demote or deactivate yourself"}), body)

    # ---- passwords ----
    def test_only_a_super_admin_resets_an_administrators_password(self):
        res = self.reset(self.adm2, self.adm_who)
        self.assertEqual((res.status_code, res.json()), (403, ONLY_SUPER))
        self.assertEqual(self.reset(self.adm2, self.sup_who).status_code, 200)
        self.assertEqual(self.reset(self.planner, self.adm_who).status_code, 200)  # ordinary users: any admin
        self.assertEqual(self.reset(self.adm, self.adm_who, "Own-Reset-Pass-3").status_code, 200)  # your own: allowed

    # ---- what a super admin still shares with an admin ----
    def test_a_super_admin_has_all_admin_rights_and_no_plan_rights(self):
        self.assertEqual(Client().get("/api/admin/users", headers=self.sup_who).status_code, 200)
        self.assertEqual(Client().get("/api/audit/verify", headers=self.sup_who).status_code, 200)
        self.assertEqual(Client().get("/api/admin/whatever", headers=self.sup_who).status_code, 404)
        res = self.post(Client(), "/api/plans", {"layout": {}}, headers=self.sup_who)
        self.assertEqual(res.status_code, 403)  # not a planner

    def test_other_roles_are_still_refused_the_admin_area(self):
        _, who = self.app_login("singh")
        self.assertEqual(Client().get("/api/admin/users", headers=who).status_code, 403)

    def test_changes_are_audited_with_the_super_admin_as_actor(self):
        self.patch_user(self.adm2, {"role": "auditor"}, self.sup_who)
        entry = AuditEntry.objects.filter(action="User updated").latest("seq")
        self.assertEqual((entry.detail, entry.actor_id), ("nair: role admin -> auditor", self.sup.id))


def run(command, *args, password=None):
    out = StringIO()
    with mock.patch.dict(os.environ, {"DHRUVAR_PASSWORD": password} if password else {}):
        call_command(command, *args, stdout=out)
    return out.getvalue()


class SetRoleCommandTests(ApiTestCase):
    def setUp(self):
        self.adm = self.make_user("iyer", "admin", name="S. Iyer")
        self.planner = self.make_user("singh", "planner")

    def test_promotes_an_admin_to_the_first_super_admin_and_ends_sessions(self):
        _, who = self.app_login("iyer")
        run("setrole", "--username", "iyer", "--role", "superadmin")
        self.assertEqual(User.objects.get(username="iyer").role, "superadmin")
        self.assertEqual(Client().get("/api/auth/me", headers=who).status_code, 401)
        entry = AuditEntry.objects.get(action="User updated")
        self.assertEqual(entry.detail, "iyer: role admin -> superadmin (command line)")
        self.assertIsNone(entry.actor_id)

    def test_refusals(self):
        with self.assertRaisesMessage(CommandError, "No such user"):
            run("setrole", "--username", "nobody", "--role", "admin")
        with self.assertRaisesMessage(CommandError, "already has the role"):
            run("setrole", "--username", "iyer", "--role", "admin")
        with self.assertRaisesMessage(CommandError, "at least one active administrator"):
            run("setrole", "--username", "iyer", "--role", "planner")  # the only admin

    def test_the_last_super_admin_cannot_be_demoted_even_when_other_admins_exist(self):
        self.make_user("nair", "admin", name="P. Nair")
        run("setrole", "--username", "iyer", "--role", "superadmin")
        with self.assertRaisesMessage(CommandError, "at least one active super administrator"):
            run("setrole", "--username", "iyer", "--role", "admin")

    def test_createuser_can_make_a_super_admin(self):
        run("createuser", "--username", "chief", "--name", "C. Chief", "--rank", "Colonel", "--role", "superadmin", password="Start-Pass-Forty-4")
        self.assertEqual(User.objects.get(username="chief").role, "superadmin")
