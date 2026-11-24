from django.test import SimpleTestCase, TestCase

from api.audit import GENESIS, audit, entry_hash, verify_chain
from api.models import AuditEntry


class AuditHashTests(SimpleTestCase):
    def test_hashes_match_the_node_server(self):
        # Vectors produced by server/src/audit.ts (JSON.stringify + sha256) for the same inputs.
        e1 = entry_hash(GENESIS, 1700000000000, "u-p1", "Submitted", "p-1", "TP-2026-0001 version 1 submitted for approval")
        self.assertEqual(e1, "002407087929b769905e627104d996ae6d4b2a1bb184e4b5d55234ef8a8749b6")
        e2 = entry_hash(e1, 1700000060000, None, "Note", "p-1", 'Quote " backslash \\ tab \t unicode é — ✓')
        self.assertEqual(e2, "1900673666ab1cd24b0f4d4ca99cf211393cacdaf8fd303bd04cdd192a09bb65")


class AuditChainTests(TestCase):
    def test_empty_log_is_valid(self):
        self.assertEqual(verify_chain(), {"ok": True, "entries": 0})

    def test_entries_chain_and_verify(self):
        audit("u1", "Submitted", "p1", "first", at=1)
        audit("u1", "Approved", "p1", "second", at=2)
        self.assertEqual(verify_chain(), {"ok": True, "entries": 2})
        first, second = AuditEntry.objects.order_by("seq")
        self.assertEqual(first.prev_hash, GENESIS)
        self.assertEqual(second.prev_hash, first.hash)

    def test_editing_an_old_entry_is_detected(self):
        audit("u1", "Submitted", "p1", "first", at=1)
        audit("u1", "Approved", "p1", "second", at=2)
        first = AuditEntry.objects.order_by("seq").first()
        AuditEntry.objects.filter(pk=first.pk).update(detail="tampered")
        result = verify_chain()
        self.assertFalse(result["ok"])
        self.assertEqual(result["brokenAtSeq"], first.seq)

    def test_deleting_an_entry_is_detected(self):
        for i in range(3):
            audit("u1", "Commented", "p1", f"c{i}", at=i)
        AuditEntry.objects.order_by("seq")[1].delete()
        self.assertFalse(verify_chain()["ok"])
