"""User lists and administration. A port of userRouter and adminRouter in server/src/users.ts."""

from django.db import IntegrityError, transaction

from .audit import audit
from .auth import active_admin_count, destroy_user_sessions, hash_password
from .http import ApiError, BoolField, EnumField, JsResponse, StrField, endpoint, json_body, validate
from .models import ROLES, User, new_id, now_ms
from .rules import password_error

ROLE_NAMES = [r for r, _ in ROLES]
USERNAME = StrField(
    "username",
    trim=True,
    min_len=3,
    max_len=40,
    pattern=r"[A-Za-z0-9._-]+",
    pattern_message="letters, digits, dot, dash or underscore only",
)


@endpoint("GET", auth=True)
def user_names(request):
    """Names only, so every signed-in user can see who wrote or decided something."""
    rows = User.objects.order_by("name")
    return JsResponse([{"id": u.id, "name": u.name, "rank": u.rank, "role": u.role, "active": u.active} for u in rows])


def _admin_row(u: User) -> dict:
    return {
        "id": u.id,
        "username": u.username,
        "name": u.name,
        "rank": u.rank,
        "role": u.role,
        "active": u.active,
        "createdAt": u.created_at,
        "lastLoginAt": u.last_login_at,
    }


@endpoint("GET", "POST", roles=("admin",))
def admin_users(request):
    if request.method == "GET":
        return JsResponse([_admin_row(u) for u in User.objects.order_by("name")])
    return _create_user(request)


def _create_user(request):
    data, error = validate(
        json_body(request),
        [
            USERNAME,
            StrField("name", trim=True, min_len=1, max_len=80),
            StrField("rank", trim=True, min_len=1, max_len=40),
            EnumField("role", ROLE_NAMES),
            StrField("password", max_len=200),
        ],
    )
    if error:
        raise ApiError(400, error)
    problem = password_error(data["password"], data["username"])
    if problem:
        raise ApiError(400, problem)
    taken = ApiError(409, "That username is already taken")
    if User.objects.filter(username__iexact=data["username"]).exists():
        raise taken
    user_id = new_id()
    try:
        with transaction.atomic():
            User.objects.create(
                id=user_id,
                username=data["username"],
                name=data["name"],
                rank=data["rank"],
                role=data["role"],
                password=hash_password(data["password"]),
                active=True,
                created_at=now_ms(),
            )
            audit(request.api_user.id, "User created", None, f"{data['username']} created as {data['role']}")
    except IntegrityError:  # two admins creating the same name at once
        raise taken
    return JsResponse({"id": user_id}, status=201)


@endpoint("PATCH", roles=("admin",))
def admin_user(request, user_id):
    data, error = validate(
        json_body(request),
        [
            StrField("name", trim=True, min_len=1, max_len=80, optional=True),
            StrField("rank", trim=True, min_len=1, max_len=40, optional=True),
            EnumField("role", ROLE_NAMES, optional=True),
            BoolField("active", optional=True),
        ],
    )
    if error:
        raise ApiError(400, error)
    target = User.objects.filter(pk=user_id).first()
    if target is None:
        raise ApiError(404, "User not found")
    next_role = data.get("role", target.role)
    next_active = data.get("active", target.active)
    demoting = next_role != "admin" or not next_active
    if user_id == request.api_user.id and demoting:
        raise ApiError(409, "You cannot demote or deactivate yourself")
    if target.role == "admin" and target.active and demoting and active_admin_count() <= 1:
        raise ApiError(409, "There must always be at least one active administrator")
    with transaction.atomic():
        User.objects.filter(pk=user_id).update(
            name=data.get("name", target.name),
            rank=data.get("rank", target.rank),
            role=next_role,
            active=next_active,
        )
        changes = []
        if "role" in data and data["role"] != target.role:
            changes.append(f"role {target.role} -> {data['role']}")
        if "active" in data and data["active"] != target.active:
            changes.append("activated" if data["active"] else "deactivated")
        audit(request.api_user.id, "User updated", None, f"{target.username}{': ' + ', '.join(changes) if changes else ''}")
    # Permissions changed, so existing sessions must not keep the old ones.
    if next_role != target.role or not next_active:
        destroy_user_sessions(user_id)
    return JsResponse({"ok": True})


@endpoint("POST", roles=("admin",))
def admin_user_password(request, user_id):
    data, error = validate(json_body(request), [StrField("password", max_len=200)])
    if error:
        raise ApiError(400, "A password is required")
    target = User.objects.filter(pk=user_id).first()
    if target is None:
        raise ApiError(404, "User not found")
    problem = password_error(data["password"], target.username)
    if problem:
        raise ApiError(400, problem)
    with transaction.atomic():
        User.objects.filter(pk=user_id).update(password=hash_password(data["password"]))
        audit(request.api_user.id, "Password reset", None, f"Password reset for {target.username}")
    destroy_user_sessions(user_id)
    return JsResponse({"ok": True})
