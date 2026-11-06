"""Database models. These mirror the tables in server/src/db.ts so the API can return identical JSON.

Conventions kept from the Node server on purpose:
  * ids are UUID strings, not integers (the app and dashboard treat them as opaque text);
  * every timestamp is an integer of epoch milliseconds, never a datetime.
"""

import time
import uuid

from django.contrib.auth.base_user import AbstractBaseUser, BaseUserManager
from django.db import models
from django.db.models.functions import Lower

ROLES = [("planner", "planner"), ("reviewer", "reviewer"), ("admin", "admin"), ("auditor", "auditor")]
STATUSES = [
    ("SUBMITTED", "SUBMITTED"),
    ("UNDER_REVIEW", "UNDER_REVIEW"),
    ("APPROVED", "APPROVED"),
    ("REJECTED", "REJECTED"),
    ("CHANGES_REQUESTED", "CHANGES_REQUESTED"),
    ("WITHDRAWN", "WITHDRAWN"),
]
DECISIONS = [("approve", "approve"), ("reject", "reject"), ("changes", "changes")]


def now_ms() -> int:
    return int(time.time() * 1000)


def new_id() -> str:
    return str(uuid.uuid4())


class UserManager(BaseUserManager):
    def create_user(self, username: str, password: str, **extra):
        user = self.model(username=username, **extra)
        user.set_password(password)
        user.save(using=self._db)
        return user


class User(AbstractBaseUser):
    id = models.CharField(primary_key=True, max_length=36, default=new_id, editable=False)
    # unique is required by Django for USERNAME_FIELD; the Lower() constraint below adds case-insensitivity.
    username = models.CharField(max_length=40, unique=True)
    name = models.CharField(max_length=80)
    rank = models.CharField(max_length=40)
    role = models.CharField(max_length=10, choices=ROLES)
    active = models.BooleanField(default=True)
    created_at = models.BigIntegerField(default=now_ms)
    last_login_at = models.BigIntegerField(null=True, blank=True)

    last_login = None  # the Node server keeps its own last_login_at in epoch milliseconds

    objects = UserManager()
    USERNAME_FIELD = "username"
    REQUIRED_FIELDS: list[str] = []

    class Meta:
        constraints = [
            # Usernames are case-insensitive, as in the Node server (COLLATE NOCASE).
            models.UniqueConstraint(Lower("username"), name="uniq_username_ci"),
            models.CheckConstraint(condition=models.Q(role__in=[r for r, _ in ROLES]), name="user_role_valid"),
        ]

    @property
    def is_active(self) -> bool:  # type: ignore[override]
        return self.active

    def __str__(self) -> str:
        return self.username


class AuthSession(models.Model):
    """One signed-in session. The token itself is never stored, only its SHA-256."""

    token_hash = models.CharField(primary_key=True, max_length=64)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="sessions")
    created_at = models.BigIntegerField()
    last_seen = models.BigIntegerField()


class Plan(models.Model):
    id = models.CharField(primary_key=True, max_length=36, default=new_id, editable=False)
    code = models.CharField(max_length=20, unique=True)  # TP-YYYY-NNNN
    name = models.CharField(max_length=120)
    owner = models.ForeignKey(User, on_delete=models.PROTECT, related_name="+")
    status = models.CharField(max_length=20, choices=STATUSES)
    created_at = models.BigIntegerField(default=now_ms)
    updated_at = models.BigIntegerField(default=now_ms)

    class Meta:
        constraints = [models.CheckConstraint(condition=models.Q(status__in=[s for s, _ in STATUSES]), name="plan_status_valid")]


class PlanVersion(models.Model):
    plan = models.ForeignKey(Plan, on_delete=models.CASCADE, related_name="versions")
    version = models.PositiveIntegerField()
    layout_json = models.TextField()
    content_hash = models.CharField(max_length=16)
    note = models.TextField(default="")
    submitted_by = models.ForeignKey(User, on_delete=models.PROTECT, related_name="+")
    submitted_at = models.BigIntegerField(default=now_ms)

    class Meta:
        ordering = ["version"]
        constraints = [models.UniqueConstraint(fields=["plan", "version"], name="uniq_plan_version")]


class Decision(models.Model):
    id = models.CharField(primary_key=True, max_length=36, default=new_id, editable=False)
    plan = models.ForeignKey(Plan, on_delete=models.CASCADE, related_name="decisions")
    version = models.PositiveIntegerField()
    reviewer = models.ForeignKey(User, on_delete=models.PROTECT, related_name="+")
    decision = models.CharField(max_length=10, choices=DECISIONS)
    reason = models.TextField(default="")
    decided_at = models.BigIntegerField(default=now_ms)


class Comment(models.Model):
    id = models.CharField(primary_key=True, max_length=36, default=new_id, editable=False)
    plan = models.ForeignKey(Plan, on_delete=models.CASCADE, related_name="comments")
    version = models.PositiveIntegerField()
    author = models.ForeignKey(User, on_delete=models.PROTECT, related_name="+")
    text = models.TextField()
    created_at = models.BigIntegerField(default=now_ms)


class Attachment(models.Model):
    id = models.CharField(primary_key=True, max_length=36, default=new_id, editable=False)
    plan = models.ForeignKey(Plan, on_delete=models.CASCADE, related_name="attachments")
    version = models.PositiveIntegerField()
    file_name = models.CharField(max_length=100)
    stored_name = models.CharField(max_length=60)
    mime = models.CharField(max_length=30)
    size = models.PositiveIntegerField()
    caption = models.CharField(max_length=200, default="", blank=True)
    uploaded_by = models.ForeignKey(User, on_delete=models.PROTECT, related_name="+")
    uploaded_at = models.BigIntegerField(default=now_ms)


class AuditEntry(models.Model):
    """Append-only, hash-chained log. actor_id and plan_id are plain text, not foreign keys, so the
    log still reads correctly if a user or plan row is ever removed."""

    seq = models.BigAutoField(primary_key=True)
    at = models.BigIntegerField()
    actor_id = models.CharField(max_length=36, null=True, blank=True)
    action = models.CharField(max_length=60)
    plan_id = models.CharField(max_length=36, null=True, blank=True, db_index=True)
    detail = models.TextField()
    prev_hash = models.CharField(max_length=64)
    hash = models.CharField(max_length=64)
