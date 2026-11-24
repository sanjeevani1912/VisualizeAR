"""Choosing the database from environment variables (config/settings.py)."""

from unittest import mock

from django.core.exceptions import ImproperlyConfigured
from django.test import SimpleTestCase

from config.settings import _database


class DatabaseChoiceTests(SimpleTestCase):
    def choose(self, **env):
        with mock.patch.dict("os.environ", env, clear=False):
            return _database()


    def test_sqlite_is_the_default_and_keeps_its_options(self):
        with mock.patch.dict("os.environ"):
            for key in ("DB_ENGINE", "DB_PASSWORD"):
                __import__("os").environ.pop(key, None)
            db = _database()
        self.assertEqual(db["ENGINE"], "django.db.backends.sqlite3")
        self.assertEqual(db["OPTIONS"], {"transaction_mode": "IMMEDIATE", "timeout": 5})
        self.assertTrue(str(db["NAME"]).endswith("dhruvar-django.db"))

    def test_postgres_is_configured_from_db_variables_without_sqlite_options(self):
        db = self.choose(DB_ENGINE="postgres", DB_PASSWORD="secret", DB_NAME="n", DB_USER="u", DB_HOST="h", DB_PORT="1234")
        self.assertEqual(db["ENGINE"], "django.db.backends.postgresql")
        self.assertEqual((db["NAME"], db["USER"], db["HOST"], db["PORT"], db["PASSWORD"]), ("n", "u", "h", "1234", "secret"))
        self.assertNotIn("OPTIONS", db)  # transaction_mode / timeout would be refused by PostgreSQL

    def test_postgres_defaults(self):
        with mock.patch.dict("os.environ", {"DB_ENGINE": "postgres", "DB_PASSWORD": "x"}):
            for key in ("DB_NAME", "DB_USER", "DB_HOST", "DB_PORT"):
                __import__("os").environ.pop(key, None)
            db = _database()
        self.assertEqual((db["NAME"], db["USER"], db["HOST"], db["PORT"]), ("dhruvar", "dhruvar", "127.0.0.1", "5432"))
        self.assertEqual(db["CONN_MAX_AGE"], 0)  # a thread-per-request server would otherwise leave idle connections open

    def test_postgres_needs_a_password(self):
        with mock.patch.dict("os.environ", {"DB_ENGINE": "postgres"}):
            __import__("os").environ.pop("DB_PASSWORD", None)
            with self.assertRaisesMessage(ImproperlyConfigured, "DB_PASSWORD"):
                _database()

    def test_unknown_engine_is_refused(self):
        with self.assertRaisesMessage(ImproperlyConfigured, "DB_ENGINE must be"):
            self.choose(DB_ENGINE="mysql")
