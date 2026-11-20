"""Sign-in, sign-out and password change. A port of authRouter in server/src/users.ts."""

from django.conf import settings
from django.db import transaction

from .audit import audit
from .auth import (
    authenticate,
    check_locked,
    clear_session_cookie,
    client_ip,
    create_session,
    destroy_session,
    destroy_user_sessions,
    hash_password,
    presented_token,
    purge_expired_sessions,
    set_session_cookie,
    verify_password,
)
from .http import ApiError, JsResponse, StrField, endpoint, js_number, json_body, validate
from .models import User
from .rules import password_error

CREDENTIALS = [StrField("username", max_len=80), StrField("password", max_len=200)]


def _credentials(request) -> tuple[str, str, str]:
    """Checks the body, the lock-out and nothing else. Returns (username, password, ip)."""
    data, error = validate(json_body(request), CREDENTIALS)
    if error:
        raise ApiError(400, "Username and password are required")
    ip = client_ip(request)
    wait = check_locked(data["username"], ip)
    if wait > 0:
        raise ApiError(429, f"Too many failed attempts. Try again in {wait} minute{'' if wait == 1 else 's'}")
    return data["username"], data["password"], ip


@endpoint("POST")
def login(request):
    """Browser sign-in: the session token goes into an HttpOnly cookie."""
    username, password, ip = _credentials(request)
    user = authenticate(username, password, ip)
    if user is None:
        raise ApiError(401, "Incorrect username or password")
    # A fresh token on every login prevents session fixation: the old one is thrown away.
    old = request.COOKIES.get(settings.APP_COOKIE_NAME)
    if old:
        destroy_session(old)
    purge_expired_sessions()
    response = JsResponse(user.as_dict())
    set_session_cookie(response, create_session(user.id))
    audit(user.id, "Signed in", None, f"{user.username} signed in")
    return response


@endpoint("POST")
def token(request):
    """Phone-app sign-in: same checks and lock-out, but the token comes back in the body (sent later as
    `Authorization: Bearer`) and no cookie is set."""
    username, password, ip = _credentials(request)
    user = authenticate(username, password, ip)
    if user is None:
        raise ApiError(401, "Incorrect username or password")
    purge_expired_sessions()
    session_token = create_session(user.id)
    audit(user.id, "Signed in", None, f"{user.username} signed in from the app")
    return JsResponse({"token": session_token, "idleMinutes": js_number(settings.SESSION_IDLE_MINUTES), "user": user.as_dict()})


@endpoint("POST")
def logout(request):
    session_token = presented_token(request)
    if session_token:
        destroy_session(session_token)
    response = JsResponse({"ok": True})
    clear_session_cookie(response)
    return response


@endpoint("GET")
def me(request):
    if request.api_user is None:
        raise ApiError(401, "Not signed in")
    return JsResponse(request.api_user.as_dict())


@endpoint("POST", auth=True)
def change_password(request):
    data, error = validate(json_body(request), [StrField("current", max_len=200), StrField("next", max_len=200)])
    if error:
        raise ApiError(400, "Current and new password are required")
    me_ = request.api_user
    row = User.objects.filter(pk=me_.id).first()
    if row is None or not verify_password(data["current"], row.password):
        raise ApiError(403, "Current password is incorrect")
    problem = password_error(data["next"], me_.username)
    if problem:
        raise ApiError(400, problem)
    with transaction.atomic():
        User.objects.filter(pk=me_.id).update(password=hash_password(data["next"]))
        audit(me_.id, "Password changed", None, f"{me_.username} changed their password")
    # Other sessions are ended; this one continues on a new token.
    destroy_user_sessions(me_.id)
    response = JsResponse({"ok": True})
    set_session_cookie(response, create_session(me_.id))
    return response
