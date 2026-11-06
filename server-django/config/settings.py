"""Django settings for the DhruvAR API.

Every tunable mirrors the environment variable of the Node server (server/src/config.ts), so the
two can be configured the same way while the migration is in progress.
"""

import os
import secrets
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent


def _num(name: str, fallback: float) -> float:
    try:
        return float(os.environ[name])
    except (KeyError, ValueError):
        return fallback


def _flag(name: str) -> bool:
    return os.environ.get(name) == "true"


# ---- application settings (same names and defaults as the Node server) ----
DATA_DIR = Path(os.environ["DATA_DIR"]).resolve() if os.environ.get("DATA_DIR") else BASE_DIR / "data"
DATA_DIR.mkdir(parents=True, exist_ok=True)
(DATA_DIR / "uploads").mkdir(exist_ok=True)

COOKIE_SECURE = _flag("COOKIE_SECURE")  # set true behind HTTPS
TRUST_PROXY = _flag("TRUST_PROXY")
SESSION_IDLE_MINUTES = _num("SESSION_IDLE_MINUTES", 60)
SESSION_ABSOLUTE_HOURS = _num("SESSION_ABSOLUTE_HOURS", 12)
MAX_UPLOAD_BYTES = int(_num("MAX_UPLOAD_MB", 10) * 1024 * 1024)
MAX_IMAGES_PER_PLAN = int(_num("MAX_IMAGES_PER_PLAN", 30))
LOGIN_MAX_FAILURES = int(_num("LOGIN_MAX_FAILURES", 5))
LOGIN_LOCK_MINUTES = int(_num("LOGIN_LOCK_MINUTES", 15))
MAP_ORIGIN = os.environ.get("MAP_ORIGIN", "")  # tile server the dashboard may load from (CSP)
WEB_DIST = Path(os.environ["WEB_DIST"]).resolve() if os.environ.get("WEB_DIST") else BASE_DIR.parent / "web" / "dist"
APP_COOKIE_NAME = "dhruvar_sid"  # same cookie name as the Node server


# ---- Django core ----
def _secret_key() -> str:
    """From DJANGO_SECRET_KEY, else generated once and kept in the data directory."""
    env = os.environ.get("DJANGO_SECRET_KEY")
    if env:
        return env
    path = DATA_DIR / "secret_key"
    if not path.exists():
        path.write_text(secrets.token_urlsafe(64))
        try:
            path.chmod(0o600)
        except OSError:
            pass  # not supported on every filesystem
    return path.read_text().strip()


SECRET_KEY = _secret_key()
DEBUG = _flag("DJANGO_DEBUG")
# A phone on the LAN reaches the server by IP, so list it here, e.g. ALLOWED_HOSTS=192.168.1.214
ALLOWED_HOSTS = [h.strip() for h in os.environ.get("ALLOWED_HOSTS", "localhost,127.0.0.1,[::1]").split(",") if h.strip()]
if TRUST_PROXY:
    USE_X_FORWARDED_HOST = True
    SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")

INSTALLED_APPS = [
    "django.contrib.auth",  # password hashing and the user base class
    "django.contrib.contenttypes",
    "api",
]

# Deliberately minimal. Sessions, the CSRF header check and CSP are the app's own (api/), because they
# must behave exactly as the Node server does: bearer tokens or the dhruvar_sid cookie, a custom
# X-Requested-With header instead of Django's CSRF token, and JSON error bodies.
MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"
WSGI_APPLICATION = "config.wsgi.application"
AUTH_USER_MODEL = "api.User"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.sqlite3",
        # A different file from the Node server's dhruvar.db, so the two never touch each other.
        "NAME": DATA_DIR / "dhruvar-django.db",
        "OPTIONS": {
            "transaction_mode": "IMMEDIATE",  # like the Node server's BEGIN IMMEDIATE
            "timeout": 5,
        },
    }
}

# Django's default PBKDF2 hasher. Argon2 can be added later (pip install argon2-cffi).
PASSWORD_HASHERS = ["django.contrib.auth.hashers.PBKDF2PasswordHasher"]

X_FRAME_OPTIONS = "DENY"
SECURE_CONTENT_TYPE_NOSNIFF = True
SECURE_REFERRER_POLICY = "no-referrer"

LANGUAGE_CODE = "en-us"
TIME_ZONE = "UTC"
USE_TZ = True  # unused by the models, which store epoch milliseconds like the Node server
