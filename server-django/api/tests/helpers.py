"""Shared test helpers. Passwords use a fast hasher here so the suite runs in seconds; production uses PBKDF2."""

import json

from django.test import Client, TestCase, override_settings

from api.models import User, now_ms

FAST_HASH = ["django.contrib.auth.hashers.MD5PasswordHasher"]
XHR = {"X-Requested-With": "dhruvar"}
PASSWORD = "Correct-Horse-42"  # must not contain any test username


@override_settings(PASSWORD_HASHERS=FAST_HASH)
class ApiTestCase(TestCase):
    def make_user(self, username, role="planner", *, name=None, rank="Major", password=PASSWORD, active=True):
        user = User.objects.create_user(username, password, name=name or username.title(), rank=rank, role=role, active=active)
        return user

    def call(self, client, method, path, body=None, *, headers=None, raw=None, content_type="application/json", **extra):
        kwargs = {"content_type": content_type, "headers": headers if headers is not None else XHR, **extra}
        data = raw if raw is not None else (json.dumps(body) if body is not None else "")
        return getattr(client, method)(path, data=data, **kwargs)

    def post(self, client, path, body=None, **kw):
        return self.call(client, "post", path, body, **kw)

    def patch(self, client, path, body=None, **kw):
        return self.call(client, "patch", path, body, **kw)

    def app_login(self, username, password=PASSWORD):
        """Signs in the way the phone app does; returns (token, bearer headers)."""
        res = self.post(Client(), "/api/auth/token", {"username": username, "password": password})
        self.assertEqual(res.status_code, 200, res.content)
        token = res.json()["token"]
        return token, {"Authorization": f"Bearer {token}"}

    def get(self, client, path, headers=None, **kw):
        return client.get(path, headers=headers or {}, **kw)

    def api(self, method, path, who, body=None):
        """A request as a signed-in user. `who` is the bearer headers from app_login()."""
        c = Client()
        if method == "get":
            return c.get(path, headers=who)
        return self.call(c, method, path, body, headers=who)


def ms_ago(minutes=0, seconds=0):
    return now_ms() - int(minutes * 60_000 + seconds * 1000)
