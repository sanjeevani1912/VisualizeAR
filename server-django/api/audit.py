"""Hash-chained audit log, a port of server/src/audit.ts.

Each entry hashes the previous one, so editing or deleting a past row breaks the chain and is caught
by verify_chain(). The hash input is byte-for-byte what JSON.stringify produces in the Node server, so
the two implementations agree on every entry.
"""

import hashlib
import json

from django.db import connection, transaction

from .models import AuditEntry, now_ms

GENESIS = "0" * 64

# Names of the PostgreSQL advisory locks (arbitrary fixed numbers; each means "one at a time, please").
AUDIT_CHAIN_LOCK = 4_700_001
PLAN_CODE_LOCK = 4_700_002


def advisory_lock(key: int) -> None:
    """Waits for, then takes, the lock called `key`. It is released automatically when the current transaction ends.

    Why: each audit entry stores the hash of the one before it, so two writers must never read "the latest entry"
    at the same moment. SQLite already makes writers take turns (every write transaction starts with BEGIN
    IMMEDIATE). PostgreSQL lets them run side by side, so they must be made to wait for each other explicitly.
    (A row lock on the latest entry is not enough: while the second writer waits, PostgreSQL may hand it the same
    old row again instead of the new latest one.)
    """
    if connection.vendor == "postgresql":
        with connection.cursor() as cursor:
            cursor.execute("SELECT pg_advisory_xact_lock(%s)", [key])


def _lock_chain() -> None:
    advisory_lock(AUDIT_CHAIN_LOCK)


def entry_hash(prev: str, at: int, actor: str | None, action: str, plan_id: str | None, detail: str) -> str:
    # separators and ensure_ascii=False reproduce JSON.stringify output exactly
    payload = json.dumps([prev, at, actor, action, plan_id, detail], separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def audit(actor_id: str | None, action: str, plan_id: str | None, detail: str, at: int | None = None) -> AuditEntry:
    """Appends one entry. Call it inside the same transaction as the change being recorded."""
    at = now_ms() if at is None else at
    with transaction.atomic():
        _lock_chain()  # from here until the transaction ends, no other writer can append, so `last` stays the last
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
