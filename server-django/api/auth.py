"""Passwords, sign-in throttling and sessions. A port of server/src/auth.ts.

Differences from Node, on purpose:
  * passwords use Django's PBKDF2 hasher (Node used scrypt; the two cannot be converted);
  * the failed-sign-in counter is in the database, not in memory (see LoginThrottle);
  * expired sessions are cleaned up while people sign in, because Django has no background timer.
"""

import hashlib
import re
import secrets
from dataclasses import dataclass

from django.conf import settings
from django.contrib.auth.hashers import check_password, make_password
from django.db import transaction

from .http import ApiError
from .models import AuthSession, LoginThrottle, User, now_ms

BEARER = re.compile(r"^Bearer ([A-Za-z0-9_-]{20,100})$")
MINUTE_MS = 60_000
HOUR_MS = 3_600_000


@dataclass(frozen=True)
class SessionUser:
    id: str
    username: str
    name: str
    rank: str
    role: str
    must_change_password: bool = False

    def as_dict(self) -> dict:
        data = {"id": self.id, "username": self.username, "name": self.name, "rank": self.rank, "role": self.role}
        if self.must_change_password:  # only present when true, so the normal reply keeps its old shape
            data["mustChangePassword"] = True
        return data


def _session_user(user: User) -> SessionUser:
    return SessionUser(
        id=user.id, username=user.username, name=user.name, rank=user.rank, role=user.role,
        must_change_password=user.must_change_password,
    )


# ---------- passwords ----------
def hash_password(password: str) -> str:
    return make_password(password)


def verify_password(password: str, encoded: str) -> bool:
    return check_password(password, encoded)


_dummy_hashes: dict[str, str] = {}


def _dummy_hash() -> str:
    """A real hash to compare against when the username does not exist, so an unknown username costs the
    same time as a wrong password. Built once per configured hasher."""
    key = settings.PASSWORD_HASHERS[0]
    if key not in _dummy_hashes:
        _dummy_hashes[key] = make_password("dummy-password-for-timing")
    return _dummy_hashes[key]


# ---------- client address ----------
def client_ip(request) -> str:
    """The caller's IP. Behind one trusted proxy (TRUST_PROXY) it is the address that proxy appended."""
    if settings.TRUST_PROXY:
        forwarded = request.META.get("HTTP_X_FORWARDED_FOR", "")
        if forwarded:
            return forwarded.split(",")[-1].strip()
    return request.META.get("REMOTE_ADDR") or "unknown"


# ---------- sign-in throttling ----------
def _throttle_key(username: str, ip: str) -> str:
    return f"{username.lower()}|{ip}"


def check_locked(username: str, ip: str) -> int:
    """Minutes left on a lock-out, or 0 when the caller may try."""
    row = LoginThrottle.objects.filter(key=_throttle_key(username, ip)).first()
    now = now_ms()
    if row and row.locked_until > now:
        return -(-(row.locked_until - now) // MINUTE_MS)  # ceiling division
    return 0


def _record_failure(username: str, ip: str) -> None:
    with transaction.atomic():
        row, _ = LoginThrottle.objects.select_for_update().get_or_create(key=_throttle_key(username, ip))
        row.count += 1
        if row.count >= settings.LOGIN_MAX_FAILURES:
            row.locked_until = now_ms() + int(settings.LOGIN_LOCK_MINUTES * MINUTE_MS)
            row.count = 0
        row.save()


def authenticate(username: str, password: str, ip: str) -> SessionUser | None:
    """The user on success, otherwise None. Never reveals whether the username exists."""
    user = User.objects.filter(username__iexact=username).first()
    ok = verify_password(password, user.password if user else _dummy_hash())
    if user is None or not ok or not user.active:
        _record_failure(username, ip)
        return None
    LoginThrottle.objects.filter(key=_throttle_key(username, ip)).delete()
    User.objects.filter(pk=user.pk).update(last_login_at=now_ms())
    return _session_user(user)


# ---------- sessions ----------
def _sha(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def create_session(user_id: str) -> str:
    """A new random token. Only its SHA-256 is stored, so a leaked database cannot be used to sign in."""
    token = secrets.token_urlsafe(32)
    now = now_ms()
    AuthSession.objects.create(token_hash=_sha(token), user_id=user_id, created_at=now, last_seen=now)
    return token


def destroy_session(token: str) -> None:
    AuthSession.objects.filter(token_hash=_sha(token)).delete()


def destroy_user_sessions(user_id: str) -> None:
    AuthSession.objects.filter(user_id=user_id).delete()


_last_purge = 0


def purge_expired_sessions() -> None:
    """Deletes idle or too-old sessions, at most once every 10 minutes per process."""
    global _last_purge
    now = now_ms()
    if now - _last_purge < 10 * MINUTE_MS:
        return
    _last_purge = now
    idle = now - int(settings.SESSION_IDLE_MINUTES * MINUTE_MS)
    absolute = now - int(settings.SESSION_ABSOLUTE_HOURS * HOUR_MS)
    from django.db.models import Q

    AuthSession.objects.filter(Q(last_seen__lt=idle) | Q(created_at__lt=absolute)).delete()


def set_session_cookie(response, token: str) -> None:
    response.set_cookie(
        settings.APP_COOKIE_NAME,
        token,
        max_age=int(settings.SESSION_ABSOLUTE_HOURS * 3600),
        httponly=True,
        samesite="Strict",
        secure=settings.COOKIE_SECURE,
        path="/",
    )


def clear_session_cookie(response) -> None:
    response.delete_cookie(settings.APP_COOKIE_NAME, path="/", samesite="Strict")


def user_for_token(token: str) -> SessionUser | None:
    now = now_ms()
    session = AuthSession.objects.select_related("user").filter(token_hash=_sha(token)).first()
    if session is None or not session.user.active:
        return None
    if now - session.last_seen > settings.SESSION_IDLE_MINUTES * MINUTE_MS or now - session.created_at > settings.SESSION_ABSOLUTE_HOURS * HOUR_MS:
        destroy_session(token)
        return None
    if now - session.last_seen > 30_000:  # refresh at most every 30 s to avoid a write on every request
        AuthSession.objects.filter(pk=session.pk).update(last_seen=now)
    return _session_user(session.user)


# ---------- per-request checks (called by the middleware) ----------
def presented_token(request) -> str | None:
    """The session token the caller sent. An Authorization bearer token wins over the cookie."""
    header = request.headers.get("Authorization")
    if header:
        match = BEARER.match(header)
        return match.group(1) if match else None
    cookie = request.COOKIES.get(settings.APP_COOKIE_NAME)
    return cookie or None


def load_user(request) -> None:
    request.api_user = None
    request.via_bearer = False
    token = presented_token(request)
    if token:
        request.api_user = user_for_token(token)
        request.via_bearer = bool(request.headers.get("Authorization"))


def csrf_guard(request) -> None:
    """Requests that change data must carry a custom header, which another website cannot add.
    A bearer token is never sent by a browser on its own, so those requests are exempt, as is the
    app sign-in (it sets no cookie and needs the password)."""
    if request.via_bearer and request.api_user:
        return
    if request.method == "POST" and request.path_info == "/api/auth/token":
        return
    if request.method not in ("GET", "HEAD", "OPTIONS") and request.headers.get("X-Requested-With") != "dhruvar":
        raise ApiError(403, "Missing request header")


FORCED_CHANGE_ALLOWED = ("/api/auth/me", "/api/auth/change-password", "/api/auth/logout", "/api/health")


def forced_change_guard(request) -> None:
    """Until a person has replaced the password someone else set for them, the only things they may do are
    see who they are, change the password and sign out."""
    user = request.api_user
    if user and user.must_change_password and request.path_info not in FORCED_CHANGE_ALLOWED:
        raise ApiError(403, "Password change required")


def active_admin_count() -> int:
    return User.objects.filter(role__in=("admin", "superadmin"), active=True).count()


def active_superadmin_count() -> int:
    return User.objects.filter(role="superadmin", active=True).count()
