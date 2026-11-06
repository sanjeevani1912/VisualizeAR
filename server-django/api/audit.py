"""Hash-chained audit log, a port of server/src/audit.ts.

Each entry hashes the previous one, so editing or deleting a past row breaks the chain and is caught
by verify_chain(). The hash input is byte-for-byte what JSON.stringify produces in the Node server, so
the two implementations agree on every entry.
"""

import hashlib
import json

from django.db import transaction

from .models import AuditEntry, now_ms

GENESIS = "0" * 64


def entry_hash(prev: str, at: int, actor: str | None, action: str, plan_id: str | None, detail: str) -> str:
    # separators and ensure_ascii=False reproduce JSON.stringify output exactly
    payload = json.dumps([prev, at, actor, action, plan_id, detail], separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def audit(actor_id: str | None, action: str, plan_id: str | None, detail: str, at: int | None = None) -> AuditEntry:
    """Appends one entry. Call it inside the same transaction as the change being recorded."""
    at = now_ms() if at is None else at
    with transaction.atomic():
        last = AuditEntry.objects.order_by("-seq").first()
        prev = last.hash if last else GENESIS
        return AuditEntry.objects.create(
            at=at,
            actor_id=actor_id,
            action=action,
            plan_id=plan_id,
            detail=detail,
            prev_hash=prev,
            hash=entry_hash(prev, at, actor_id, action, plan_id, detail),
        )


def verify_chain() -> dict:
    prev = GENESIS
    entries = 0
    for row in AuditEntry.objects.order_by("seq").iterator():
        entries += 1
        if row.prev_hash != prev or row.hash != entry_hash(prev, row.at, row.actor_id, row.action, row.plan_id, row.detail):
            return {"ok": False, "entries": AuditEntry.objects.count(), "brokenAtSeq": row.seq}
        prev = row.hash
    return {"ok": True, "entries": entries}
