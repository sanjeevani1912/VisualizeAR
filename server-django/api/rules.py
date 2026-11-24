"""Pure authorisation and state-machine rules, a line-for-line port of server/src/rules.ts.

Kept free of I/O so they can be unit-tested and reused by every view. The server enforces these;
the web UI only mirrors them for display.
"""

from dataclasses import dataclass
import re

from .jsutil import js_len, js_trim

# A super administrator may do everything an administrator may, and in addition manage the administrators.
ADMIN_ROLES = ("admin", "superadmin")
OPEN = ("SUBMITTED", "UNDER_REVIEW")
MIN_REASON = 10

STATUS_FOR = {
    "approve": "APPROVED",
    "reject": "REJECTED",
    "changes": "CHANGES_REQUESTED",
}


@dataclass(frozen=True)
class Actor:
    id: str
    role: str  # planner | reviewer | admin | auditor


@dataclass(frozen=True)
class PlanRef:
    owner_id: str
    status: str


def can_view_plan(a: Actor, p: PlanRef) -> bool:
    return p.owner_id == a.id if a.role == "planner" else True


def can_create_plan(a: Actor) -> bool:
    return a.role == "planner"


def can_decide(a: Actor, p: PlanRef) -> bool:
    return a.role == "reviewer" and p.owner_id != a.id and p.status in OPEN


def can_start_review(a: Actor, p: PlanRef) -> bool:
    return a.role == "reviewer" and p.owner_id != a.id and p.status == "SUBMITTED"


def can_withdraw(a: Actor, p: PlanRef) -> bool:
    return a.role == "planner" and p.owner_id == a.id and p.status in OPEN


def can_resubmit(a: Actor, p: PlanRef) -> bool:
    return a.role == "planner" and p.owner_id == a.id and p.status in ("CHANGES_REQUESTED", "REJECTED")


def can_comment(a: Actor, p: PlanRef) -> bool:
    return a.role != "auditor" and can_view_plan(a, p)


def can_attach(a: Actor, p: PlanRef) -> bool:
    """Field images: the owning planner, or a reviewer. Never on a withdrawn plan."""
    if p.status == "WITHDRAWN":
        return False
    if a.role == "planner":
        return p.owner_id == a.id
    return a.role == "reviewer"


def can_remove_attachment(a: Actor, uploader_id: str) -> bool:
    return a.role in ADMIN_ROLES or uploader_id == a.id


def reason_error(kind: str, reason: str) -> str | None:
    if kind != "approve" and js_len(js_trim(reason)) < MIN_REASON:
        return f"A reason of at least {MIN_REASON} characters is required"
    return None


def password_error(pw: str, username: str) -> str | None:
    if len(pw) < 10:
        return "Password must be at least 10 characters"
    if len(pw) > 200:
        return "Password is too long"
    if len(username) >= 3 and username.lower() in pw.lower():
        return "Password must not contain the username"
    if not re.search(r"[a-z]", pw) or not re.search(r"[A-Z]", pw) or not re.search(r"[0-9]", pw):
        return "Password needs upper case, lower case and a digit"
    return None
