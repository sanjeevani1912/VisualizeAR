"""Creates the sample accounts and fictional plans so the dashboard is usable straight away.
A port of server/scripts/seed.ts.

    python manage.py seed             first-time setup (refuses to run if users already exist)
    python manage.py seed --reset     wipes ALL data (users, plans, images, audit log) and seeds again

Passwords are random and written once to DATA_DIR/seed-credentials.txt (the data folder is git-ignored).
They are never printed. Change them after the first sign-in. This is for development and demos, not for
loading real plans (real data is moved with a separate, one-off import at cut-over).

The sample data is api/seed_data/sample_data.json, exported once from web/src/data/mock.ts
(scripts/export_seed_data.mts). Its times are shifted so the newest entries are "just now".
"""

import json
import os
import secrets
from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.core.management.color import no_style
from django.db import connection, transaction

from api import layout as layout_checks
from api.audit import audit
from api.auth import hash_password
from api.models import Attachment, AuditEntry, AuthSession, Comment, Decision, LoginThrottle, Plan, PlanVersion, User, now_ms
from api.rules import password_error

DATA_FILE = Path(__file__).resolve().parent.parent.parent / "seed_data" / "sample_data.json"


def wipe_everything() -> None:
    """Deletes every row (children before parents) and restarts the audit counter at 1.

    Plain DELETEs rather than Django's `flush`, because `flush` uses TRUNCATE, which PostgreSQL refuses while a
    transaction still has pending foreign-key checks.
    """
    with transaction.atomic():
        for model in (Comment, Decision, Attachment, PlanVersion, Plan, AuthSession, LoginThrottle, AuditEntry, User):
            model.objects.all().delete()
        table = AuditEntry._meta.db_table
        with connection.cursor() as cursor:
            if connection.vendor == "sqlite":
                cursor.execute("DELETE FROM sqlite_sequence WHERE name = %s", [table])
            else:  # PostgreSQL: setval(..., 1, false) makes the next number 1 again (the table is empty now)
                for sql in connection.ops.sequence_reset_by_name_sql(no_style(), [{"table": table, "column": "seq"}]):
                    cursor.execute(sql)


def new_password(username: str) -> str:
    while True:
        password = f"{secrets.token_urlsafe(9)}-Aa1"
        if password_error(password, username) is None:
            return password


class Command(BaseCommand):
    help = "Create sample users and fictional plans (refuses to run if users exist, unless --reset)."

    def add_arguments(self, parser):
        parser.add_argument("--reset", action="store_true", help="wipe ALL data first (users, plans, images, audit log)")

    def handle(self, *args, **opts):
        if User.objects.exists() and not opts["reset"]:
            raise CommandError("Users already exist. Use `python manage.py seed --reset` to wipe everything and start over.")
        data = json.loads(DATA_FILE.read_text(encoding="utf-8"))
        now = now_ms()
        shift = now - data["baseTime"]  # moves every sample time so the newest is "just now"

        if opts["reset"]:
            wipe_everything()
            uploads = settings.DATA_DIR / "uploads"
            for leftover in uploads.iterdir() if uploads.exists() else []:
                if leftover.is_file():
                    leftover.unlink()

        lines = ["username   role       password", "---------  ---------  ----------------"]
        with transaction.atomic():
            for u in data["users"]:
                password = new_password(u["username"])
                User.objects.create(
                    id=u["id"], username=u["username"], name=u["name"], rank=u["rank"], role=u["role"],
                    password=hash_password(password), active=True, created_at=now,
                )
                lines.append(f"{u['username']:<10} {u['role']:<10} {password}")
            for p in data["plans"]:
                plan = Plan.objects.create(
                    id=p["id"], code=p["code"], name=p["name"], owner_id=p["ownerId"], status=p["status"],
                    created_at=p["createdAt"] + shift, updated_at=p["updatedAt"] + shift,
                )
                for v in p["versions"]:
                    PlanVersion.objects.create(
                        plan=plan, version=v["version"], layout_json=layout_checks.layout_text(v["layout"]),
                        content_hash=layout_checks.content_hash(v["layout"]), note=v["note"],
                        submitted_by_id=v["submittedBy"], submitted_at=v["submittedAt"] + shift,
                    )
                for d in p["decisions"]:
                    Decision.objects.create(
                        id=d["id"], plan=plan, version=d["version"], reviewer_id=d["reviewerId"], decision=d["decision"],
                        reason=d["reason"], decided_at=d["decidedAt"] + shift,
                    )
                for c in p["comments"]:
                    Comment.objects.create(
                        id=c["id"], plan=plan, version=c["version"], author_id=c["authorId"], text=c["text"],
                        created_at=c["createdAt"] + shift,
                    )
            # Oldest first, so the hash chain follows real time.
            for e in sorted(data["audit"], key=lambda e: e["at"]):
                audit(e["actorId"], e["action"], e.get("planId"), e["detail"], e["at"] + shift)

        credentials = settings.DATA_DIR / "seed-credentials.txt"
        fd = os.open(credentials, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
        self.stdout.write(self.style.SUCCESS(f"Seeded {len(data['users'])} users and {len(data['plans'])} sample plans."))
        self.stdout.write(f"Passwords written to {credentials}")
