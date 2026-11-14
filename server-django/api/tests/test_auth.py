from unittest import mock

from django.conf import settings
from django.test import Client

from api.audit import verify_chain
from api.models import AuditEntry, AuthSession, LoginThrottle, User

from .helpers import PASSWORD, XHR, ApiTestCase, ms_ago


class LoginTests(ApiTestCase):
    def setUp(self):
        self.user = self.make_user("menon", "reviewer", name="A. Menon", rank="Brigadier")

    def login(self, username="menon", password=PASSWORD, client=None, **extra):
        return self.post(client or Client(), "/api/auth/login", {"username": username, "password": password}, **extra)

    def test_login_returns_the_user_and_sets_a_strict_http_only_cookie(self):
        c = Client()
        res = self.login(client=c)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json(), {"id": self.user.id, "username": "menon", "name": "A. Menon", "rank": "Brigadier", "role": "reviewer"})
        cookie = res.cookies[settings.APP_COOKIE_NAME]
        self.assertTrue(cookie["httponly"])
        self.assertEqual(cookie["samesite"], "Strict")
        self.assertEqual(cookie["path"], "/")
        self.assertEqual(int(cookie["max-age"]), 12 * 3600)
        self.assertEqual(res["Cache-Control"], "no-store")

    def test_login_is_audited_and_records_last_login(self):
        self.login()
        entry = AuditEntry.objects.get()
        self.assertEqual((entry.action, entry.detail, entry.actor_id), ("Signed in", "menon signed in", self.user.id))
        self.user.refresh_from_db()
        self.assertIsNotNone(self.user.last_login_at)

    def test_the_session_cookie_signs_you_in(self):
        c = Client()
        self.login(client=c)
        res = c.get("/api/auth/me")
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json()["username"], "menon")

    def test_token_is_stored_only_as_a_hash(self):
        token, _ = self.app_login("menon")
        self.assertFalse(AuthSession.objects.filter(token_hash=token).exists())
        self.assertEqual(AuthSession.objects.count(), 1)

    def test_login_needs_the_csrf_header(self):
        res = self.login(headers={})
        self.assertEqual((res.status_code, res.json()), (403, {"error": "Missing request header"}))

    def test_wrong_password_and_unknown_user_look_identical(self):
        wrong = self.login(password="Wrong-Pass-123")
        unknown = self.login(username="nobody")
        self.assertEqual((wrong.status_code, wrong.json()), (401, {"error": "Incorrect username or password"}))
        self.assertEqual((unknown.status_code, unknown.json()), (401, {"error": "Incorrect username or password"}))

    def test_username_is_case_insensitive(self):
        self.assertEqual(self.login(username="MENON").status_code, 200)

    def test_inactive_user_cannot_sign_in(self):
        User.objects.filter(pk=self.user.pk).update(active=False)
        self.assertEqual(self.login().status_code, 401)

    def test_body_problems_are_400(self):
        c = Client()
        for body in ({}, {"username": "x"}, {"username": 1, "password": "p"}, {"username": "x" * 81, "password": "p"}):
            res = self.post(c, "/api/auth/login", body)
            self.assertEqual((res.status_code, res.json()), (400, {"error": "Username and password are required"}), body)
        res = self.post(c, "/api/auth/login", raw="{not json")
        self.assertEqual((res.status_code, res.json()), (400, {"error": "Malformed JSON"}))
        res = self.post(c, "/api/auth/login", raw="username=a&password=b", content_type="application/x-www-form-urlencoded")
        self.assertEqual(res.status_code, 400)
        self.assertEqual(res.json(), {"error": "Username and password are required"})

    def test_wrong_method_is_not_a_route(self):
        c = Client()
        c.cookies.clear()
        res = c.get("/api/auth/login")  # signed out: told so first, like Express
        self.assertEqual((res.status_code, res.json()), (401, {"error": "Not signed in"}))

    def test_login_replaces_the_old_session(self):
        c = Client()
        self.login(client=c)
        old = c.cookies[settings.APP_COOKIE_NAME].value
        self.login(client=c)
        new = c.cookies[settings.APP_COOKIE_NAME].value
        self.assertNotEqual(old, new)
        other = Client()
        other.cookies[settings.APP_COOKIE_NAME] = old
        self.assertEqual(other.get("/api/auth/me").status_code, 401)  # the old token is dead

    def test_audit_chain_stays_valid(self):
        self.login()
        self.login(password="Wrong-Pass-123")
        self.assertEqual(verify_chain(), {"ok": True, "entries": 1})


class LockoutTests(ApiTestCase):
    def setUp(self):
        self.make_user("menon", "reviewer")

    def fail(self, ip="10.0.0.1", username="menon"):
        return self.post(Client(), "/api/auth/login", {"username": username, "password": "Wrong-Pass-123"}, REMOTE_ADDR=ip)

    def test_five_failures_lock_for_fifteen_minutes(self):
        for _ in range(4):
            self.assertEqual(self.fail().status_code, 401)
        self.assertEqual(self.fail().status_code, 401)  # the 5th failure itself still answers 401
        locked = self.fail()
        self.assertEqual(locked.status_code, 429)
        self.assertEqual(locked.json(), {"error": "Too many failed attempts. Try again in 15 minutes"})

    def test_the_right_password_is_refused_while_locked(self):
        for _ in range(5):
            self.fail()
        res = self.post(Client(), "/api/auth/login", {"username": "menon", "password": PASSWORD}, REMOTE_ADDR="10.0.0.1")
        self.assertEqual(res.status_code, 429)

    def test_the_lock_applies_to_username_and_address_together(self):
        for _ in range(5):
            self.fail(ip="10.0.0.1")
        self.assertEqual(self.fail(ip="10.0.0.2").status_code, 401)  # another address is not locked
        self.assertEqual(self.fail(ip="10.0.0.1", username="MENON").status_code, 429)  # case does not escape it

    def test_the_app_sign_in_shares_the_same_lock(self):
        for _ in range(5):
            self.fail()
        res = self.post(Client(), "/api/auth/token", {"username": "menon", "password": PASSWORD}, REMOTE_ADDR="10.0.0.1")
        self.assertEqual(res.status_code, 429)

    def test_the_lock_expires(self):
        for _ in range(5):
            self.fail()
        later = LoginThrottle.objects.get().locked_until + 1
        with mock.patch("api.auth.now_ms", return_value=later):
            res = self.post(Client(), "/api/auth/login", {"username": "menon", "password": PASSWORD}, REMOTE_ADDR="10.0.0.1")
        self.assertEqual(res.status_code, 200)

    def test_one_minute_is_singular(self):
        for _ in range(5):
            self.fail()
        almost = LoginThrottle.objects.get().locked_until - 30_000
        with mock.patch("api.auth.now_ms", return_value=almost):
            res = self.fail()
        self.assertEqual(res.json(), {"error": "Too many failed attempts. Try again in 1 minute"})

    def test_a_successful_sign_in_clears_earlier_failures(self):
        for _ in range(4):
            self.fail()
        self.post(Client(), "/api/auth/login", {"username": "menon", "password": PASSWORD}, REMOTE_ADDR="10.0.0.1")
        self.assertFalse(LoginThrottle.objects.exists())
        for _ in range(4):
            self.assertEqual(self.fail().status_code, 401)  # the counter started again from zero

    def test_proxy_address_is_used_only_when_trusted(self):
        with self.settings(TRUST_PROXY=True):
            for _ in range(5):
                self.post(Client(), "/api/auth/login", {"username": "menon", "password": "x"}, REMOTE_ADDR="127.0.0.1", HTTP_X_FORWARDED_FOR="9.9.9.9, 7.7.7.7")
        self.assertEqual(LoginThrottle.objects.get().key, "menon|7.7.7.7")  # the last hop, which the proxy added
        LoginThrottle.objects.all().delete()
        self.post(Client(), "/api/auth/login", {"username": "menon", "password": "x"}, REMOTE_ADDR="127.0.0.1", HTTP_X_FORWARDED_FOR="9.9.9.9")
        self.assertEqual(LoginThrottle.objects.get().key, "menon|127.0.0.1")  # ignored without TRUST_PROXY


class AppTokenTests(ApiTestCase):
    def setUp(self):
        self.user = self.make_user("singh", "planner", name="V. Singh")

    def test_token_signs_in_without_a_cookie_and_without_the_csrf_header(self):
        res = self.post(Client(), "/api/auth/token", {"username": "singh", "password": PASSWORD}, headers={})
        self.assertEqual(res.status_code, 200)
        body = res.json()
        self.assertEqual(set(body), {"token", "idleMinutes", "user"})
        self.assertEqual(body["idleMinutes"], 60)
        self.assertIsInstance(body["idleMinutes"], int)  # 60, not 60.0
        self.assertEqual(body["user"]["username"], "singh")
        self.assertNotIn(settings.APP_COOKIE_NAME, res.cookies)
        self.assertRegex(body["token"], r"^[A-Za-z0-9_-]{20,100}$")
        entry = AuditEntry.objects.get()
        self.assertEqual(entry.detail, "singh signed in from the app")

    def test_bearer_token_works_and_needs_no_csrf_header(self):
        _, who = self.app_login("singh")
        self.assertEqual(self.get(Client(), "/api/auth/me", who).json()["username"], "singh")
        res = self.post(Client(), "/api/auth/change-password", {"current": PASSWORD, "next": "Another-Pass-77"}, headers=who)
        self.assertEqual(res.status_code, 200)  # a POST with no X-Requested-With

    def test_a_bad_bearer_is_signed_out_even_with_a_valid_cookie(self):
        c = Client()
        self.post(c, "/api/auth/login", {"username": "singh", "password": PASSWORD})
        for header in ("Bearer short", "Bearer " + "a" * 40, "Basic abc", "Bearer  " + "a" * 30):
            res = c.get("/api/auth/me", headers={"Authorization": header})
            self.assertEqual(res.status_code, 401, header)

    def test_bearer_wins_over_the_cookie(self):
        self.make_user("rao", "planner", name="K. Rao")
        c = Client()
        self.post(c, "/api/auth/login", {"username": "singh", "password": PASSWORD})
        _, rao = self.app_login("rao")
        self.assertEqual(c.get("/api/auth/me", headers=rao).json()["username"], "rao")

    def test_a_cookie_request_that_changes_data_needs_the_header(self):
        c = Client()
        self.post(c, "/api/auth/login", {"username": "singh", "password": PASSWORD})
        res = self.post(c, "/api/auth/change-password", {"current": PASSWORD, "next": "Another-Pass-77"}, headers={})
        self.assertEqual((res.status_code, res.json()), (403, {"error": "Missing request header"}))


class SessionTests(ApiTestCase):
    def setUp(self):
        self.make_user("menon", "reviewer")

    def test_me_without_a_session(self):
        res = Client().get("/api/auth/me")
        self.assertEqual((res.status_code, res.json()), (401, {"error": "Not signed in"}))

    def test_logout_ends_the_session_and_clears_the_cookie(self):
        c = Client()
        self.post(c, "/api/auth/login", {"username": "menon", "password": PASSWORD})
        res = self.post(c, "/api/auth/logout")
        self.assertEqual(res.json(), {"ok": True})
        self.assertEqual(res.cookies[settings.APP_COOKIE_NAME]["max-age"], 0)
        self.assertEqual(AuthSession.objects.count(), 0)

    def test_logout_works_with_a_bearer_token(self):
        _, who = self.app_login("menon")
        self.assertEqual(self.post(Client(), "/api/auth/logout", headers=who).status_code, 200)
        self.assertEqual(Client().get("/api/auth/me", headers=who).status_code, 401)

    def test_logout_when_not_signed_in_is_fine(self):
        self.assertEqual(self.post(Client(), "/api/auth/logout").json(), {"ok": True})

    def test_idle_session_expires(self):
        _, who = self.app_login("menon")
        AuthSession.objects.update(last_seen=ms_ago(minutes=61))
        self.assertEqual(Client().get("/api/auth/me", headers=who).status_code, 401)
        self.assertEqual(AuthSession.objects.count(), 0)  # and is removed

    def test_session_expires_after_the_absolute_limit_even_if_active(self):
        _, who = self.app_login("menon")
        AuthSession.objects.update(created_at=ms_ago(minutes=12 * 60 + 1), last_seen=ms_ago(seconds=1))
        self.assertEqual(Client().get("/api/auth/me", headers=who).status_code, 401)

    def test_last_seen_is_refreshed_only_when_older_than_30_seconds(self):
        _, who = self.app_login("menon")
        AuthSession.objects.update(last_seen=ms_ago(seconds=5))
        before = AuthSession.objects.get().last_seen
        Client().get("/api/auth/me", headers=who)
        self.assertEqual(AuthSession.objects.get().last_seen, before)
        AuthSession.objects.update(last_seen=ms_ago(seconds=45))
        before = AuthSession.objects.get().last_seen
        Client().get("/api/auth/me", headers=who)
        self.assertGreater(AuthSession.objects.get().last_seen, before)

    def test_deactivating_a_user_ends_their_access(self):
        _, who = self.app_login("menon")
        User.objects.filter(username="menon").update(active=False)
        self.assertEqual(Client().get("/api/auth/me", headers=who).status_code, 401)

    def test_expired_sessions_are_purged_during_sign_in(self):
        _, _ = self.app_login("menon")
        AuthSession.objects.update(last_seen=ms_ago(minutes=120))
        with mock.patch("api.auth._last_purge", 0):
            self.post(Client(), "/api/auth/token", {"username": "menon", "password": PASSWORD})
        self.assertEqual(AuthSession.objects.count(), 1)  # only the new one is left


class ChangePasswordTests(ApiTestCase):
    NEW = "Another-Pass-77"

    def setUp(self):
        self.user = self.make_user("menon", "reviewer")

    def change(self, client, current=PASSWORD, new=NEW, **kw):
        return self.post(client, "/api/auth/change-password", {"current": current, "next": new}, **kw)

    def test_requires_sign_in(self):
        res = self.change(Client())
        self.assertEqual((res.status_code, res.json()), (401, {"error": "Not signed in"}))

    def test_body_and_password_rules(self):
        c = Client()
        self.post(c, "/api/auth/login", {"username": "menon", "password": PASSWORD})
        res = self.post(c, "/api/auth/change-password", {"current": PASSWORD})
        self.assertEqual((res.status_code, res.json()), (400, {"error": "Current and new password are required"}))
        res = self.change(c, current="Wrong-Pass-123")
        self.assertEqual((res.status_code, res.json()), (403, {"error": "Current password is incorrect"}))
        for new, message in (
            ("Short1a", "Password must be at least 10 characters"),
            ("alllowercase123", "Password needs upper case, lower case and a digit"),
            ("Menon-Secret-12", "Password must not contain the username"),
        ):
            res = self.change(c, new=new)
            self.assertEqual((res.status_code, res.json()), (400, {"error": message}), new)

    def test_success_ends_other_sessions_and_keeps_this_one(self):
        c = Client()
        self.post(c, "/api/auth/login", {"username": "menon", "password": PASSWORD})
        _, other = self.app_login("menon")
        res = self.change(c)
        self.assertEqual(res.json(), {"ok": True})
        self.assertEqual(Client().get("/api/auth/me", headers=other).status_code, 401)  # other device signed out
        self.assertEqual(c.get("/api/auth/me").status_code, 200)  # this browser carries on
        self.assertEqual(AuthSession.objects.count(), 1)
        self.assertEqual(AuditEntry.objects.filter(action="Password changed").get().detail, "menon changed their password")

    def test_the_new_password_works_and_the_old_one_does_not(self):
        c = Client()
        self.post(c, "/api/auth/login", {"username": "menon", "password": PASSWORD})
        self.change(c)
        self.assertEqual(self.post(Client(), "/api/auth/token", {"username": "menon", "password": self.NEW}).status_code, 200)
        self.assertEqual(self.post(Client(), "/api/auth/token", {"username": "menon", "password": PASSWORD}).status_code, 401)


class UnknownRouteTests(ApiTestCase):
    def test_signed_out_callers_are_told_to_sign_in_first(self):
        res = Client().get("/api/nothing-here")
        self.assertEqual((res.status_code, res.json()), (401, {"error": "Not signed in"}))

    def test_signed_in_callers_get_a_json_404(self):
        self.make_user("menon", "reviewer")
        _, who = self.app_login("menon")
        res = Client().get("/api/nothing-here", headers=who)
        self.assertEqual((res.status_code, res.json()), (404, {"error": "Not found"}))
        self.assertEqual(res["Content-Type"], "application/json")

    def test_unknown_admin_paths_need_the_admin_role(self):
        self.make_user("menon", "reviewer")
        _, who = self.app_login("menon")
        res = Client().get("/api/admin/whatever", headers=who)
        self.assertEqual((res.status_code, res.json()), (403, {"error": "Your role is not allowed to do this"}))

    def test_the_csrf_check_runs_before_routing(self):
        res = Client().post("/api/nothing-here", data="{}", content_type="application/json")
        self.assertEqual((res.status_code, res.json()), (403, {"error": "Missing request header"}))

    def test_health_needs_no_sign_in(self):
        self.assertEqual(Client().get("/api/health").json(), {"ok": True})

    def test_unexpected_errors_are_a_plain_500(self):
        with mock.patch("api.views_auth.check_locked", side_effect=RuntimeError("boom")):
            with self.assertLogs("dhruvar.api", level="ERROR"):
                res = self.post(Client(), "/api/auth/login", {"username": "a", "password": "b"})
        self.assertEqual((res.status_code, res.json()), (500, {"error": "Something went wrong on the server"}))
