"""Plans, decisions, comments and the audit list. A port of server/src/plans.ts (images come in phase 3).

How a change is made safe: every route that changes something runs inside `transaction.atomic()`, writes the
audit entry in that same block, and first re-reads the plan row with `select_for_update()` ("lock this row
until I am done"). If two people act on the same plan at the same moment, the second waits, then sees the
new state and gets the same answer it would have got had it simply arrived later.
"""

import json
from datetime import datetime

from django.db import IntegrityError, transaction
from django.db.models import Max
from django.db.models.functions import Length

from . import layout as layout_checks
from .audit import PLAN_CODE_LOCK, advisory_lock, audit, verify_chain
from .http import AnyField, ApiError, EnumField, JsResponse, NumField, StrField, endpoint, json_body, validate
from .jsutil import db_text
from .models import Attachment, AuditEntry, Comment, Decision, Plan, PlanVersion, new_id, now_ms
from .rules import (
    STATUS_FOR,
    Actor,
    PlanRef,
    can_comment,
    can_create_plan,
    can_decide,
    can_resubmit,
    can_start_review,
    can_view_plan,
    can_withdraw,
    reason_error,
)

NOTE = StrField("note", trim=True, max_len=500, optional=True)  # a missing note means ""
DECISION_FIELDS = [
    EnumField("decision", ["approve", "reject", "changes"]),
    StrField("reason", trim=True, max_len=2000, optional=True),
    NumField("version", integer=True, gt=0),
]
ACTIONS = {"approve": "Approved", "reject": "Rejected", "changes": "Changes requested"}
CODE_ATTEMPTS = 5


def _actor(request) -> Actor:
    return Actor(request.api_user.id, request.api_user.role)


def _ref(plan: Plan) -> PlanRef:
    return PlanRef(plan.owner_id, plan.status)


def _body(request, fields) -> dict:
    """Validated JSON body. Problems read like Node's: `field: message`, or `request: ...` for the whole body."""
    data, error = validate(json_body(request), fields)
    if error:
        raise ApiError(400, "request" + error if error.startswith(": ") else error)
    return data


def _visible_plan(request, plan_id: str, *, lock: bool = False) -> Plan:
    """A plan the user may not see is reported as missing, so ids cannot be probed."""
    query = Plan.objects.select_for_update() if lock else Plan.objects
    plan = query.filter(pk=plan_id).first()
    if plan is None or not can_view_plan(_actor(request), _ref(plan)):
        raise ApiError(404, "Plan not found")
    return plan


def _latest_version(plan_id: str) -> int:
    return PlanVersion.objects.filter(plan_id=plan_id).aggregate(v=Max("version"))["v"] or 1


def _next_code() -> str:
    """TP-YYYY-NNNN, numbered per year (the year of the server's clock, as in Node)."""
    prefix = f"TP-{datetime.now().year}-"
    last = Plan.objects.filter(code__startswith=prefix).order_by(Length("code").desc(), "-code").values_list("code", flat=True).first()
    number = int(last.split("-")[2]) + 1 if last else 1
    return f"{prefix}{number:04d}"


# ---------- reading plans in the shape the dashboard uses ----------
def load_plans(user, only_id: str | None = None) -> list[dict]:
    actor = Actor(user.id, user.role)
    query = Plan.objects.filter(pk=only_id) if only_id else Plan.objects.all()
    plans = [p for p in query.order_by("-updated_at", "created_at", "id") if can_view_plan(actor, _ref(p))]
    ids = [p.id for p in plans]

    def group(model, order):
        grouped: dict[str, list] = {}
        for row in model.objects.filter(plan_id__in=ids).order_by(*order):
            grouped.setdefault(row.plan_id, []).append(row)
        return grouped

    versions = group(PlanVersion, ["version"])
    decisions = group(Decision, ["decided_at", "id"])
    comments = group(Comment, ["created_at", "id"])
    attachments = group(Attachment, ["uploaded_at", "id"])
    return [
        {
            "id": p.id,
            "code": p.code,
            "name": p.name,
            "ownerId": p.owner_id,
            "status": p.status,
            "createdAt": p.created_at,
            "updatedAt": p.updated_at,
            "versions": [
                {
                    "version": v.version,
                    "submittedAt": v.submitted_at,
                    "submittedBy": v.submitted_by_id,
                    "note": v.note,
                    "contentHash": v.content_hash,
                    "layout": json.loads(v.layout_json),
                }
                for v in versions.get(p.id, [])
            ],
            "decisions": [
                {"id": d.id, "planId": d.plan_id, "version": d.version, "reviewerId": d.reviewer_id, "decision": d.decision, "reason": d.reason, "decidedAt": d.decided_at}
                for d in decisions.get(p.id, [])
            ],
            "comments": [
                {"id": c.id, "planId": c.plan_id, "version": c.version, "authorId": c.author_id, "text": c.text, "createdAt": c.created_at}
                for c in comments.get(p.id, [])
            ],
            "attachments": [
                {
                    "id": a.id,
                    "planId": a.plan_id,
                    "version": a.version,
                    "fileName": a.file_name,
                    "mime": a.mime,
                    "size": a.size,
                    "caption": a.caption,
                    "uploadedBy": a.uploaded_by_id,
                    "uploadedAt": a.uploaded_at,
                    "url": f"/api/attachments/{a.id}",
                }
                for a in attachments.get(p.id, [])
            ],
        }
        for p in plans
    ]


def _add_version(plan: Plan, number: int, layout: dict, note: str, user_id: str, at: int) -> None:
    PlanVersion.objects.create(
        plan=plan,
        version=number,
        layout_json=layout_checks.layout_text(layout),
        content_hash=layout_checks.content_hash(layout),
        note=note,
        submitted_by_id=user_id,
        submitted_at=at,
    )


def _checked_layout(raw) -> dict:
    layout, error = layout_checks.parse_layout(raw)
    if layout is None:
        raise ApiError(400, error or "Invalid layout")
    return layout


# ---------- routes ----------
@endpoint("GET", "POST", auth=True)
def plans(request):
    if request.method == "GET":
        return JsResponse(load_plans(request.api_user))
    return _create_plan(request)


def _create_plan(request):
    user = request.api_user
    if user.role != "planner":
        raise ApiError(403, "Your role is not allowed to do this")  # requireRole('planner')
    if not can_create_plan(_actor(request)):
        raise ApiError(403, "Only planners can submit plans")
    raw = json_body(request)
    data = _body_from(raw, [AnyField("layout"), NOTE, StrField("name", trim=True, min_len=1, max_len=120, optional=True)])
    layout = _checked_layout(data["layout"])
    note = db_text(data.get("note", ""))
    name = db_text(data.get("name", layout["name"]))
    now = now_ms()
    plan_id = new_id()
    for attempt in range(CODE_ATTEMPTS):
        try:
            with transaction.atomic():
                advisory_lock(PLAN_CODE_LOCK)  # PostgreSQL: submissions take turns choosing a number (SQLite already does)
                code = _next_code()
                plan = Plan.objects.create(id=plan_id, code=code, name=name, owner_id=user.id, status="SUBMITTED", created_at=now, updated_at=now)
                _add_version(plan, 1, layout, note, user.id, now)
                audit(user.id, "Submitted", plan_id, f"{code} version 1 submitted for approval", now)
            break
        except IntegrityError:
            # Two submissions took the same code at the same moment (the unique rule caught it): try the next number.
            if attempt == CODE_ATTEMPTS - 1:
                raise
    return JsResponse({"id": plan_id, "code": code}, status=201)


def _body_from(raw, fields) -> dict:
    """Same as _body, for a body that was already read (the layout is checked separately)."""
    data, error = validate(raw, fields)
    if error:
        raise ApiError(400, "request" + error if error.startswith(": ") else error)
    return data


@endpoint("POST", roles=("planner",))
def plan_versions(request, plan_id):
    user = request.api_user
    plan = _visible_plan(request, plan_id)
    resubmit_error = ApiError(409, "A new version can be submitted only after changes were requested or the plan was rejected")
    if not can_resubmit(_actor(request), _ref(plan)):
        raise resubmit_error
    raw = json_body(request)
    data = _body_from(raw, [AnyField("layout"), NOTE])
    layout = _checked_layout(data["layout"])
    note = db_text(data.get("note", ""))
    now = now_ms()
    with transaction.atomic():
        plan = _visible_plan(request, plan_id, lock=True)
        if not can_resubmit(_actor(request), _ref(plan)):  # someone else changed the plan while we were checking
            raise resubmit_error
        number = _latest_version(plan.id) + 1
        _add_version(plan, number, layout, note, user.id, now)
        # A new file that still has the default name keeps the plan's current name.
        new_name = plan.name if layout["name"] == layout_checks.DEFAULT_NAME else db_text(layout["name"])
        Plan.objects.filter(pk=plan.id).update(status="SUBMITTED", name=new_name, updated_at=now)
        audit(user.id, "Submitted", plan.id, f"{plan.code} version {number} submitted for approval", now)
    return JsResponse({"ok": True}, status=201)


@endpoint("POST", auth=True)
def plan_withdraw(request, plan_id):
    _visible_plan(request, plan_id)
    now = now_ms()
    with transaction.atomic():
        plan = _visible_plan(request, plan_id, lock=True)
        if not can_withdraw(_actor(request), _ref(plan)):
            raise ApiError(403, "You cannot withdraw this plan")
        Plan.objects.filter(pk=plan.id).update(status="WITHDRAWN", updated_at=now)
        audit(request.api_user.id, "Withdrawn", plan.id, f"{plan.code} withdrawn by planner", now)
    return JsResponse({"ok": True})


@endpoint("POST", auth=True)
def plan_start_review(request, plan_id):
    _visible_plan(request, plan_id)
    now = now_ms()
    with transaction.atomic():
        plan = _visible_plan(request, plan_id, lock=True)
        if not can_start_review(_actor(request), _ref(plan)):
            return JsResponse({"ok": True, "changed": False})  # not an error: opening a plan twice is harmless
        Plan.objects.filter(pk=plan.id).update(status="UNDER_REVIEW", updated_at=now)
        audit(request.api_user.id, "Review started", plan.id, f"{plan.code} opened for review", now)
    return JsResponse({"ok": True, "changed": True})


def _not_decidable(user, plan: Plan) -> ApiError:
    return ApiError(403, "You submitted this plan, so you cannot decide it" if plan.owner_id == user.id else "You cannot decide this plan")


@endpoint("POST", auth=True)
def plan_decision(request, plan_id):
    user = request.api_user
    plan = _visible_plan(request, plan_id)
    data = _body(request, DECISION_FIELDS)
    kind, reason, version = data["decision"], db_text(data.get("reason", "")), int(data["version"])
    if not can_decide(_actor(request), _ref(plan)):
        raise _not_decidable(user, plan)
    problem = reason_error(kind, reason)
    if problem:
        raise ApiError(400, problem)
    now = now_ms()
    with transaction.atomic():
        plan = _visible_plan(request, plan_id, lock=True)
        if not can_decide(_actor(request), _ref(plan)):  # decided by someone else a moment ago
            raise _not_decidable(user, plan)
        latest = _latest_version(plan.id)
        if version != latest:
            raise ApiError(409, f"A newer version (v{latest}) exists. Reload the plan before deciding")
        Decision.objects.create(id=new_id(), plan=plan, version=latest, reviewer_id=user.id, decision=kind, reason=reason, decided_at=now)
        Plan.objects.filter(pk=plan.id).update(status=STATUS_FOR[kind], updated_at=now)
        audit(user.id, ACTIONS[kind], plan.id, f"{plan.code} version {latest}{': ' + reason if reason else ''}", now)
    return JsResponse({"ok": True})


@endpoint("POST", auth=True)
def plan_comments(request, plan_id):
    user = request.api_user
    plan = _visible_plan(request, plan_id)
    if not can_comment(_actor(request), _ref(plan)):
        raise ApiError(403, "You cannot comment on this plan")
    text = db_text(_body(request, [StrField("text", trim=True, min_len=1, max_len=2000)])["text"])
    now = now_ms()
    with transaction.atomic():
        Comment.objects.create(id=new_id(), plan=plan, version=_latest_version(plan.id), author_id=user.id, text=text, created_at=now)
        audit(user.id, "Commented", plan.id, text, now)
    return JsResponse({"ok": True}, status=201)


# ---------- audit ----------
@endpoint("GET", auth=True)
def audit_list(request):
    """The log, newest first. Planners see only entries about their own plans; everyone else sees all."""
    entries = AuditEntry.objects.order_by("-at", "-seq")
    if request.api_user.role == "planner":
        own = Plan.objects.filter(owner_id=request.api_user.id).values_list("id", flat=True)
        entries = entries.filter(plan_id__in=list(own))
    rows = []
    for e in entries:
        row = {"id": str(e.seq), "at": e.at, "actorId": e.actor_id or "", "action": e.action}
        if e.plan_id is not None:
            row["planId"] = e.plan_id
        row["detail"] = e.detail
        rows.append(row)
    return JsResponse(rows)


@endpoint("GET", roles=("admin", "superadmin", "auditor"))
def audit_verify(request):
    return JsResponse(verify_chain())
