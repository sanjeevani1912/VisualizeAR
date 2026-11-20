"""Plans, decisions, comments and the audit routes (phase 2)."""

import itertools
import json
from datetime import datetime
from unittest import mock

from django.db import IntegrityError
from django.test import Client

from api import views_plans
from api.audit import verify_chain
from api.layout import content_hash, parse_layout
from api.models import AuditEntry, Comment, Decision, Plan, PlanVersion

from .helpers import ApiTestCase

YEAR = datetime.now().year


def layout(name="Alpha plan", **extra):
    base = {
        "version": 2,
        "id": "layout-1",
        "name": name,
        "regionBounds": {"northLatitude": 28.62, "southLatitude": 28.6, "eastLongitude": 77.22, "westLongitude": 77.2},
        "anchor": {"latitude": 28.61, "longitude": 77.21, "headingDegrees": 12.5, "horizontalAccuracyMeters": 4.2},
        "objects": [{"id": "o1", "assetType": "MILITARY_VEHICLE", "x": 10, "z": -3.5, "rotationDegrees": 45}],
    }
    base.update(extra)
    return base


class PlanTestCase(ApiTestCase):
    def setUp(self):
        self.users = {}
        for username, role in [("pat", "planner"), ("pia", "planner"), ("rex", "reviewer"), ("rhea", "reviewer"), ("ada", "admin"), ("aud", "auditor")]:
            self.users[username] = self.make_user(username, role)
        self.who = {u: self.app_login(u)[1] for u in self.users}

    def req(self, method, path, user, body=None):
        return self.api(method, path, self.who[user], body)

    def submit(self, user="pat", name="Alpha plan", note=""):
        res = self.req("post", "/api/plans", user, {"layout": layout(name), "note": note})
        self.assertEqual(res.status_code, 201, res.content)
        return res.json()["id"]

    def decide(self, plan_id, user="rex", decision="approve", reason="", version=1):
        return self.req("post", f"/api/plans/{plan_id}/decision", user, {"decision": decision, "reason": reason, "version": version})

    def get_plan(self, plan_id, user="rex"):
        return next(p for p in self.req("get", "/api/plans", user).json() if p["id"] == plan_id)

    def assertError(self, res, status, message):
        self.assertEqual((res.status_code, res.json()), (status, {"error": message}))


class CreatePlanTests(PlanTestCase):
    def test_a_planner_submits_and_gets_an_id_and_code(self):
        res = self.req("post", "/api/plans", "pat", {"layout": layout(), "note": "  first draft  "})
        self.assertEqual(res.status_code, 201)
        body = res.json()
        self.assertEqual(set(body), {"id", "code"})
        self.assertEqual(body["code"], f"TP-{YEAR}-0001")
        plan = Plan.objects.get(pk=body["id"])
        self.assertEqual((plan.name, plan.status, plan.owner_id), ("Alpha plan", "SUBMITTED", self.users["pat"].id))
        version = PlanVersion.objects.get(plan=plan)
        self.assertEqual((version.version, version.note), (1, "first draft"))
        stored, _ = parse_layout(json.loads(version.layout_json))
        self.assertEqual(version.content_hash, content_hash(stored))
        self.assertEqual(res["Cache-Control"], "no-store")

    def test_codes_count_up_per_year_and_restart_each_year(self):
        self.assertEqual(Client().get("/api/health").status_code, 200)
        first, second = self.submit(), self.submit()
        self.assertEqual(Plan.objects.get(pk=first).code, f"TP-{YEAR}-0001")
        self.assertEqual(Plan.objects.get(pk=second).code, f"TP-{YEAR}-0002")
        with mock.patch.object(views_plans, "datetime") as fake:
            fake.now.return_value = datetime(YEAR + 1, 1, 1)
            third = self.submit()
        self.assertEqual(Plan.objects.get(pk=third).code, f"TP-{YEAR + 1}-0001")

    def test_codes_keep_counting_past_9999(self):
        Plan.objects.create(code=f"TP-{YEAR}-9999", name="x", owner=self.users["pat"], status="APPROVED")
        self.assertEqual(Plan.objects.get(pk=self.submit()).code, f"TP-{YEAR}-10000")
        self.assertEqual(Plan.objects.get(pk=self.submit()).code, f"TP-{YEAR}-10001")

    def test_a_clash_on_the_code_is_retried_with_the_next_number(self):
        Plan.objects.create(code=f"TP-{YEAR}-0001", name="x", owner=self.users["pat"], status="APPROVED")
        with mock.patch.object(views_plans, "_next_code", side_effect=[f"TP-{YEAR}-0001", f"TP-{YEAR}-0002"]):
            plan = Plan.objects.get(pk=self.submit())
        self.assertEqual(plan.code, f"TP-{YEAR}-0002")
        self.assertEqual(AuditEntry.objects.filter(plan_id=plan.id).count(), 1)  # the failed attempt left nothing behind

    def test_giving_up_after_repeated_clashes_is_a_500(self):
        Plan.objects.create(code=f"TP-{YEAR}-0001", name="x", owner=self.users["pat"], status="APPROVED")
        with mock.patch.object(views_plans, "_next_code", return_value=f"TP-{YEAR}-0001"):
            res = self.req("post", "/api/plans", "pat", {"layout": layout()})
        self.assertError(res, 500, "Something went wrong on the server")

    def test_a_name_in_the_request_wins_over_the_layout_name(self):
        res = self.req("post", "/api/plans", "pat", {"layout": layout("From file"), "name": "  Chosen  "})
        self.assertEqual(Plan.objects.get(pk=res.json()["id"]).name, "Chosen")

    def test_the_default_layout_name_is_used_when_the_file_has_none(self):
        raw = layout()
        del raw["name"]
        res = self.req("post", "/api/plans", "pat", {"layout": raw})
        self.assertEqual(Plan.objects.get(pk=res.json()["id"]).name, "Untitled plan")

    def test_submitting_writes_one_audit_entry(self):
        plan_id = self.submit()
        entry = AuditEntry.objects.get(plan_id=plan_id)
        self.assertEqual((entry.action, entry.detail, entry.actor_id), ("Submitted", f"TP-{YEAR}-0001 version 1 submitted for approval", self.users["pat"].id))
        self.assertTrue(verify_chain()["ok"])

    def test_only_planners_may_submit(self):
        for user in ("rex", "ada", "aud"):
            self.assertError(self.req("post", "/api/plans", user, {"layout": layout()}), 403, "Your role is not allowed to do this")
        self.assertEqual(Plan.objects.count(), 0)

    def test_signed_out_is_401(self):
        self.assertError(self.post(Client(), "/api/plans", {"layout": layout()}), 401, "Not signed in")

    def test_body_problems_use_nodes_messages(self):
        cases = [
            ({"layout": layout(), "note": "x" * 501}, "note: Too big: expected string to have <=500 characters"),
            ({"layout": layout(), "note": 5}, "note: Invalid input: expected string, received number"),
            ({"layout": layout(), "note": None}, "note: Invalid input: expected string, received null"),
            ({"layout": layout(), "name": "   "}, "name: Too small: expected string to have >=1 characters"),
            ({"layout": layout(), "name": "n" * 121}, "name: Too big: expected string to have <=120 characters"),
            ({"layout": layout(), "name": 7}, "name: Invalid input: expected string, received number"),
            ({"note": "no layout"}, "layout: Invalid input: expected nonoptional, received undefined"),
            ({"note": 5}, "layout: Invalid input: expected nonoptional, received undefined"),  # reported first
            ({"layout": None}, "Invalid layout file: layout — Invalid input: expected object, received null"),
            ({"layout": "text"}, "Invalid layout file: layout — Invalid input: expected object, received string"),
            ({"layout": layout(objects="no")}, "Invalid layout file: objects — Invalid input: expected array, received string"),
            ({"layout": layout(anchor={"latitude": 40, "longitude": 77.21})}, "Invalid layout file: the origin lies outside the region"),
            ([1, 2], "request: Invalid input: expected object, received array"),
        ]
        for body, message in cases:
            with self.subTest(message):
                self.assertError(self.req("post", "/api/plans", "pat", body), 400, message)
        self.assertEqual(Plan.objects.count(), 0)

    def test_a_missing_or_wrong_body_is_reported_as_the_request(self):
        res = self.call(Client(), "post", "/api/plans", None, headers=self.who["pat"])
        self.assertError(res, 400, "request: Invalid input: expected object, received undefined")
        res = self.call(Client(), "post", "/api/plans", raw="not json", headers=self.who["pat"])
        self.assertError(res, 400, "Malformed JSON")

    def test_nothing_is_saved_when_the_write_fails_half_way(self):
        with mock.patch.object(views_plans, "_add_version", side_effect=IntegrityError("boom")):
            res = self.req("post", "/api/plans", "pat", {"layout": layout()})
        self.assertEqual(res.status_code, 500)
        self.assertEqual((Plan.objects.count(), AuditEntry.objects.filter(action="Submitted").count()), (0, 0))

    def test_a_lone_half_emoji_in_text_is_stored_safely(self):
        res = self.req("post", "/api/plans", "pat", {"layout": layout(), "note": "bad \ud800 note", "name": "Plan \ud800"})
        self.assertEqual(res.status_code, 201)
        self.assertEqual(Plan.objects.get(pk=res.json()["id"]).name, "Plan �")
        self.assertTrue(verify_chain()["ok"])


class ListPlanTests(PlanTestCase):
    def test_planners_see_only_their_own_plans_others_see_all(self):
        mine, hers = self.submit("pat"), self.submit("pia")
        ids = lambda user: {p["id"] for p in self.req("get", "/api/plans", user).json()}
        self.assertEqual(ids("pat"), {mine})
        self.assertEqual(ids("pia"), {hers})
        for user in ("rex", "ada", "aud"):
            self.assertEqual(ids(user), {mine, hers})

    def test_the_shape_matches_the_node_server(self):
        plan_id = self.submit(note="hello")
        self.req("post", f"/api/plans/{plan_id}/comments", "pat", {"text": "a comment"})
        self.decide(plan_id, decision="reject", reason="Not enough detail")
        plan = self.get_plan(plan_id)
        self.assertEqual(list(plan), ["id", "code", "name", "ownerId", "status", "createdAt", "updatedAt", "versions", "decisions", "comments", "attachments"])
        self.assertEqual(plan["status"], "REJECTED")
        version = plan["versions"][0]
        self.assertEqual(list(version), ["version", "submittedAt", "submittedBy", "note", "contentHash", "layout"])
        self.assertEqual((version["version"], version["note"], version["submittedBy"]), (1, "hello", self.users["pat"].id))
        self.assertEqual(version["layout"]["objects"][0]["widthMeters"], 1.72)
        self.assertEqual(list(plan["decisions"][0]), ["id", "planId", "version", "reviewerId", "decision", "reason", "decidedAt"])
        self.assertEqual(list(plan["comments"][0]), ["id", "planId", "version", "authorId", "text", "createdAt"])
        self.assertEqual(plan["attachments"], [])

    def test_numbers_are_written_like_javascript(self):
        self.submit()
        raw = self.req("get", "/api/plans", "rex").content.decode()
        self.assertIn('"x":10,', raw)  # not 10.0
        self.assertIn('"widthMeters":1.72', raw)
        self.assertNotIn(".0,", raw)
        res = self.req("get", "/api/plans", "rex")
        self.assertEqual(res["Content-Type"], "application/json; charset=utf-8")

    def test_attachments_carry_their_url(self):
        from api.models import Attachment

        plan_id = self.submit()
        Attachment.objects.create(id="a-1", plan_id=plan_id, version=1, file_name="p.png", stored_name="s.png", mime="image/png", size=5, caption="c", uploaded_by=self.users["pat"], uploaded_at=1)
        item = self.get_plan(plan_id)["attachments"][0]
        self.assertEqual(item["url"], "/api/attachments/a-1")
        self.assertEqual(list(item), ["id", "planId", "version", "fileName", "mime", "size", "caption", "uploadedBy", "uploadedAt", "url"])

    def test_most_recently_changed_plan_comes_first(self):
        clock = itertools.count(1_000_000, 1000)
        with mock.patch.object(views_plans, "now_ms", lambda: next(clock)):
            first, second = self.submit(), self.submit()
            self.assertEqual([p["id"] for p in self.req("get", "/api/plans", "rex").json()], [second, first])
            self.req("post", f"/api/plans/{first}/start-review", "rex")
        self.assertEqual([p["id"] for p in self.req("get", "/api/plans", "rex").json()], [first, second])

    def test_signed_out_and_wrong_method(self):
        self.assertError(Client().get("/api/plans"), 401, "Not signed in")
        self.assertError(self.req("delete", "/api/plans", "pat"), 404, "Not found")


class VersionTests(PlanTestCase):
    def setUp(self):
        super().setUp()
        self.plan_id = self.submit(name="Original name")

    def reject(self, kind="changes", version=1):
        self.assertEqual(self.decide(self.plan_id, decision=kind, reason="Please fix the spacing", version=version).status_code, 200)

    def resubmit(self, user="pat", body=None):
        return self.req("post", f"/api/plans/{self.plan_id}/versions", user, body if body is not None else {"layout": layout("Second name"), "note": "fixed"})

    def test_a_new_version_after_changes_were_requested(self):
        self.reject("changes")
        res = self.resubmit()
        self.assertEqual((res.status_code, res.json()), (201, {"ok": True}))
        plan = self.get_plan(self.plan_id)
        self.assertEqual((plan["status"], plan["name"], [v["version"] for v in plan["versions"]]), ("SUBMITTED", "Second name", [1, 2]))
        self.assertEqual(plan["versions"][1]["note"], "fixed")
        entry = AuditEntry.objects.filter(plan_id=self.plan_id).order_by("-seq").first()
        self.assertEqual((entry.action, entry.detail), ("Submitted", f"TP-{YEAR}-0001 version 2 submitted for approval"))

    def test_a_rejected_plan_can_also_be_resubmitted(self):
        self.reject("reject")
        self.assertEqual(self.resubmit().status_code, 201)

    def test_the_old_name_is_kept_when_the_new_file_has_the_default_name(self):
        self.reject()
        raw = layout()
        del raw["name"]
        self.resubmit(body={"layout": raw})
        self.assertEqual(self.get_plan(self.plan_id)["name"], "Original name")
        self.reject(version=2)
        self.resubmit(body={"layout": layout("Untitled plan")})
        self.assertEqual(self.get_plan(self.plan_id)["name"], "Original name")

    def test_not_allowed_while_the_plan_is_open_or_approved(self):
        message = "A new version can be submitted only after changes were requested or the plan was rejected"
        self.assertError(self.resubmit(), 409, message)
        self.req("post", f"/api/plans/{self.plan_id}/start-review", "rex")
        self.assertError(self.resubmit(), 409, message)
        self.decide(self.plan_id)
        self.assertError(self.resubmit(), 409, message)
        self.assertEqual(PlanVersion.objects.count(), 1)

    def test_other_planner_gets_404_and_other_roles_403(self):
        self.reject()
        self.assertError(self.resubmit("pia"), 404, "Plan not found")
        for user in ("rex", "ada", "aud"):
            self.assertError(self.resubmit(user), 403, "Your role is not allowed to do this")

    def test_unknown_plan_is_404(self):
        self.assertError(self.req("post", "/api/plans/nope/versions", "pat", {"layout": layout()}), 404, "Plan not found")

    def test_bad_layout_or_note_is_400_and_nothing_changes(self):
        self.reject()
        self.assertError(self.resubmit(body={"layout": layout(objects=[{"assetType": "TANK", "x": 0, "z": 0}])}), 400, 'Invalid layout file: objects.0.assetType — Invalid option: expected one of "GUNSHIP"|"K131"|"K131_TAKEOFF"|"MILITARY_VEHICLE"|"OFFROAD_VEHICLE"|"STR_MGS"|"TENT"|"TRUCK"|"CAR"|"ANTENNA"|"TRENCH"')
        self.assertError(self.resubmit(body={"layout": layout(), "note": "n" * 501}), 400, "note: Too big: expected string to have <=500 characters")
        self.assertEqual((self.get_plan(self.plan_id)["status"], PlanVersion.objects.count()), ("CHANGES_REQUESTED", 1))

    def test_the_state_is_checked_again_inside_the_transaction(self):
        self.reject()
        with mock.patch.object(views_plans, "can_resubmit", side_effect=[True, False]):
            res = self.resubmit()
        self.assertEqual(res.status_code, 409)
        self.assertEqual(PlanVersion.objects.count(), 1)


class WithdrawTests(PlanTestCase):
    def setUp(self):
        super().setUp()
        self.plan_id = self.submit()

    def withdraw(self, user="pat"):
        return self.req("post", f"/api/plans/{self.plan_id}/withdraw", user)

    def test_the_owner_withdraws_an_open_plan(self):
        self.assertEqual(self.withdraw().json(), {"ok": True})
        self.assertEqual(self.get_plan(self.plan_id)["status"], "WITHDRAWN")
        entry = AuditEntry.objects.order_by("-seq").first()
        self.assertEqual((entry.action, entry.detail), ("Withdrawn", f"TP-{YEAR}-0001 withdrawn by planner"))

    def test_an_under_review_plan_can_be_withdrawn(self):
        self.req("post", f"/api/plans/{self.plan_id}/start-review", "rex")
        self.assertEqual(self.withdraw().status_code, 200)

    def test_a_decided_plan_cannot_be_withdrawn(self):
        self.decide(self.plan_id)
        self.assertError(self.withdraw(), 403, "You cannot withdraw this plan")

    def test_only_the_owning_planner(self):
        self.assertError(self.withdraw("pia"), 404, "Plan not found")
        for user in ("rex", "ada", "aud"):
            self.assertError(self.withdraw(user), 403, "You cannot withdraw this plan")
        self.assertEqual(self.get_plan(self.plan_id)["status"], "SUBMITTED")

    def test_withdrawing_twice_is_refused(self):
        self.withdraw()
        self.assertError(self.withdraw(), 403, "You cannot withdraw this plan")

    def test_unknown_plan(self):
        self.assertError(self.req("post", "/api/plans/nope/withdraw", "pat"), 404, "Plan not found")


class StartReviewTests(PlanTestCase):
    def setUp(self):
        super().setUp()
        self.plan_id = self.submit()

    def start(self, user="rex"):
        return self.req("post", f"/api/plans/{self.plan_id}/start-review", user)

    def test_a_reviewer_opens_a_submitted_plan(self):
        self.assertEqual(self.start().json(), {"ok": True, "changed": True})
        self.assertEqual(self.get_plan(self.plan_id)["status"], "UNDER_REVIEW")
        entry = AuditEntry.objects.order_by("-seq").first()
        self.assertEqual((entry.action, entry.detail, entry.actor_id), ("Review started", f"TP-{YEAR}-0001 opened for review", self.users["rex"].id))

    def test_a_second_open_is_not_an_error(self):
        self.start()
        entries = AuditEntry.objects.count()
        res = self.start("rhea")
        self.assertEqual((res.status_code, res.json()), (200, {"ok": True, "changed": False}))
        self.assertEqual(AuditEntry.objects.count(), entries)

    def test_other_roles_get_changed_false(self):
        for user in ("ada", "aud", "pat"):
            self.assertEqual(self.start(user).json(), {"ok": True, "changed": False})
        self.assertEqual(self.get_plan(self.plan_id)["status"], "SUBMITTED")

    def test_a_hidden_plan_is_404(self):
        self.assertError(self.start("pia"), 404, "Plan not found")

    def test_a_decided_plan_does_not_reopen(self):
        self.decide(self.plan_id)
        self.assertEqual(self.start().json(), {"ok": True, "changed": False})
        self.assertEqual(self.get_plan(self.plan_id)["status"], "APPROVED")

    def test_unknown_plan(self):
        self.assertError(self.req("post", "/api/plans/nope/start-review", "rex"), 404, "Plan not found")


class DecisionTests(PlanTestCase):
    def setUp(self):
        super().setUp()
        self.plan_id = self.submit()

    def test_approve_needs_no_reason(self):
        res = self.decide(self.plan_id, decision="approve")
        self.assertEqual((res.status_code, res.json()), (200, {"ok": True}))
        plan = self.get_plan(self.plan_id)
        self.assertEqual(plan["status"], "APPROVED")
        d = plan["decisions"][0]
        self.assertEqual((d["decision"], d["reason"], d["version"], d["reviewerId"]), ("approve", "", 1, self.users["rex"].id))
        entry = AuditEntry.objects.order_by("-seq").first()
        self.assertEqual((entry.action, entry.detail), ("Approved", f"TP-{YEAR}-0001 version 1"))

    def test_reject_and_changes_need_a_reason_of_ten_characters(self):
        for kind in ("reject", "changes"):
            for reason in ("", "too short", "   padded   "):
                with self.subTest(kind=kind, reason=reason):
                    self.assertError(self.decide(self.plan_id, decision=kind, reason=reason), 400, "A reason of at least 10 characters is required")
        self.assertEqual(self.get_plan(self.plan_id)["status"], "SUBMITTED")
        self.assertEqual(Decision.objects.count(), 0)

    def test_exactly_ten_characters_is_enough_and_the_reason_is_trimmed(self):
        self.decide(self.plan_id, decision="changes", reason="  0123456789  ")
        plan = self.get_plan(self.plan_id)
        self.assertEqual((plan["status"], plan["decisions"][0]["reason"]), ("CHANGES_REQUESTED", "0123456789"))
        entry = AuditEntry.objects.order_by("-seq").first()
        self.assertEqual((entry.action, entry.detail), ("Changes requested", f"TP-{YEAR}-0001 version 1: 0123456789"))

    def test_reject_sets_the_status_and_audit_wording(self):
        self.decide(self.plan_id, decision="reject", reason="The area is wrong")
        self.assertEqual(self.get_plan(self.plan_id)["status"], "REJECTED")
        self.assertEqual(AuditEntry.objects.order_by("-seq").first().action, "Rejected")

    def test_a_plan_under_review_can_be_decided(self):
        self.req("post", f"/api/plans/{self.plan_id}/start-review", "rex")
        self.assertEqual(self.decide(self.plan_id).status_code, 200)

    def test_the_decision_must_name_the_latest_version(self):
        self.decide(self.plan_id, decision="changes", reason="Needs another pass")
        self.req("post", f"/api/plans/{self.plan_id}/versions", "pat", {"layout": layout("v2")})
        self.assertError(self.decide(self.plan_id, version=1), 409, "A newer version (v2) exists. Reload the plan before deciding")
        self.assertError(self.decide(self.plan_id, version=3), 409, "A newer version (v2) exists. Reload the plan before deciding")
        self.assertEqual(self.get_plan(self.plan_id)["status"], "SUBMITTED")
        self.assertEqual(self.decide(self.plan_id, version=2).status_code, 200)
        self.assertEqual(Decision.objects.order_by("decided_at").last().version, 2)

    def test_only_a_reviewer_who_is_not_the_owner(self):
        self.assertError(self.decide(self.plan_id, user="ada"), 403, "You cannot decide this plan")
        self.assertError(self.decide(self.plan_id, user="aud"), 403, "You cannot decide this plan")
        self.assertError(self.decide(self.plan_id, user="pat"), 403, "You submitted this plan, so you cannot decide it")
        self.assertError(self.decide(self.plan_id, user="pia"), 404, "Plan not found")

    def test_a_reviewer_who_owns_the_plan_cannot_decide_it(self):
        own = Plan.objects.create(code="TP-X-1", name="own", owner=self.users["rex"], status="SUBMITTED")
        PlanVersion.objects.create(plan=own, version=1, layout_json="{}", content_hash="x", submitted_by=self.users["rex"])
        self.assertError(self.decide(own.id), 403, "You submitted this plan, so you cannot decide it")

    def test_a_decided_plan_cannot_be_decided_again(self):
        self.decide(self.plan_id)
        self.assertError(self.decide(self.plan_id, user="rhea"), 403, "You cannot decide this plan")
        self.assertEqual(Decision.objects.count(), 1)

    def test_body_problems(self):
        cases = [
            ({}, "decision: Invalid option: expected one of \"approve\"|\"reject\"|\"changes\""),
            ({"decision": "maybe", "version": 1}, "decision: Invalid option: expected one of \"approve\"|\"reject\"|\"changes\""),
            ({"decision": "approve"}, "version: Invalid input: expected number, received undefined"),
            ({"decision": "approve", "version": "1"}, "version: Invalid input: expected number, received string"),
            ({"decision": "approve", "version": 1.5}, "version: Invalid input: expected int, received number"),
            ({"decision": "approve", "version": 0}, "version: Too small: expected number to be >0"),
            ({"decision": "approve", "version": -2}, "version: Too small: expected number to be >0"),
            ({"decision": "approve", "version": 1, "reason": 5}, "reason: Invalid input: expected string, received number"),
            ({"decision": "approve", "version": 1, "reason": "r" * 2001}, "reason: Too big: expected string to have <=2000 characters"),
            ({"decision": "reject", "reason": 5, "version": "x"}, "reason: Invalid input: expected string, received number"),
        ]
        for body, message in cases:
            with self.subTest(message):
                self.assertError(self.req("post", f"/api/plans/{self.plan_id}/decision", "rex", body), 400, message)
        self.assertEqual(self.req("post", f"/api/plans/{self.plan_id}/decision", "rex", {"decision": "approve", "version": 1.0}).status_code, 200)

    def test_body_is_checked_before_permission(self):
        res = self.req("post", f"/api/plans/{self.plan_id}/decision", "ada", {"decision": "nope"})
        self.assertEqual(res.status_code, 400)

    def test_a_decision_made_a_moment_ago_by_someone_else_is_noticed(self):
        with mock.patch.object(views_plans, "can_decide", side_effect=[True, False]):
            res = self.decide(self.plan_id)
        self.assertError(res, 403, "You cannot decide this plan")
        self.assertEqual(Decision.objects.count(), 0)

    def test_unknown_plan(self):
        self.assertError(self.decide("nope"), 404, "Plan not found")

    def test_a_failure_while_writing_leaves_nothing_behind(self):
        with mock.patch.object(views_plans, "audit", side_effect=IntegrityError("boom")):
            res = self.decide(self.plan_id)
        self.assertEqual(res.status_code, 500)
        self.assertEqual((Decision.objects.count(), self.get_plan(self.plan_id)["status"]), (0, "SUBMITTED"))


class CommentTests(PlanTestCase):
    def setUp(self):
        super().setUp()
        self.plan_id = self.submit()

    def comment(self, user, text="Looks fine to me"):
        return self.req("post", f"/api/plans/{self.plan_id}/comments", user, {"text": text})

    def test_owner_reviewer_and_admin_can_comment(self):
        for user in ("pat", "rex", "ada"):
            res = self.comment(user)
            self.assertEqual((res.status_code, res.json()), (201, {"ok": True}))
        self.assertEqual(Comment.objects.count(), 3)

    def test_an_auditor_cannot_comment_and_a_stranger_gets_404(self):
        self.assertError(self.comment("aud"), 403, "You cannot comment on this plan")
        self.assertError(self.comment("pia"), 404, "Plan not found")
        self.assertEqual(Comment.objects.count(), 0)

    def test_the_text_is_trimmed_and_audited_and_tied_to_the_latest_version(self):
        self.decide(self.plan_id, decision="changes", reason="Needs another pass")
        self.req("post", f"/api/plans/{self.plan_id}/versions", "pat", {"layout": layout("v2")})
        self.comment("rex", "  note on v2  ")
        c = self.get_plan(self.plan_id)["comments"][0]
        self.assertEqual((c["text"], c["version"], c["authorId"]), ("note on v2", 2, self.users["rex"].id))
        entry = AuditEntry.objects.order_by("-seq").first()
        self.assertEqual((entry.action, entry.detail), ("Commented", "note on v2"))

    def test_text_problems(self):
        cases = [
            ("   ", "text: Too small: expected string to have >=1 characters"),
            ("t" * 2001, "text: Too big: expected string to have <=2000 characters"),
            (5, "text: Invalid input: expected string, received number"),
            (None, "text: Invalid input: expected string, received null"),
        ]
        for text, message in cases:
            with self.subTest(message):
                self.assertError(self.comment("rex", text), 400, message)
        self.assertError(self.req("post", f"/api/plans/{self.plan_id}/comments", "rex", {}), 400, "text: Invalid input: expected string, received undefined")

    def test_permission_is_checked_before_the_body(self):
        res = self.req("post", f"/api/plans/{self.plan_id}/comments", "aud", {})
        self.assertEqual(res.status_code, 403)

    def test_comments_may_be_added_to_a_closed_plan(self):
        self.decide(self.plan_id)
        self.assertEqual(self.comment("pat").status_code, 201)

    def test_unknown_plan(self):
        self.assertError(self.req("post", "/api/plans/nope/comments", "rex", {"text": "x"}), 404, "Plan not found")


class AuditRouteTests(PlanTestCase):
    def test_reviewers_see_everything_newest_first(self):
        clock = itertools.count(1_000_000, 1000)
        with mock.patch.object(views_plans, "now_ms", lambda: next(clock)):
            plan_id = self.submit()
            self.req("post", f"/api/plans/{plan_id}/start-review", "rex")
        rows = [r for r in self.req("get", "/api/audit", "rex").json() if r["action"] != "Signed in"]
        self.assertEqual([r["action"] for r in rows], ["Review started", "Submitted"])
        self.assertEqual(list(rows[0]), ["id", "at", "actorId", "action", "planId", "detail"])
        self.assertEqual((rows[0]["planId"], rows[0]["actorId"], rows[0]["id"]), (plan_id, self.users["rex"].id, str(AuditEntry.objects.order_by("-seq").first().seq)))
        for user in ("ada", "aud"):
            self.assertEqual(len([r for r in self.req("get", "/api/audit", user).json() if r["action"] != "Signed in"]), 2)

    def test_planners_see_only_entries_about_their_own_plans(self):
        mine, hers = self.submit("pat"), self.submit("pia")
        AuditEntry.objects.create(at=1, actor_id=None, action="System", plan_id=None, detail="no plan", prev_hash="x", hash="y")
        rows = self.req("get", "/api/audit", "pat").json()
        self.assertEqual({r["planId"] for r in rows}, {mine})
        everyone = self.req("get", "/api/audit", "ada").json()
        self.assertEqual({r.get("planId") for r in everyone}, {mine, hers, None})

    def test_entries_without_a_plan_leave_planid_out_and_actor_becomes_empty_text(self):
        AuditEntry.objects.create(at=5, actor_id=None, action="System", plan_id=None, detail="no plan", prev_hash="x", hash="y")
        row = next(r for r in self.req("get", "/api/audit", "ada").json() if r["action"] == "System")
        self.assertEqual(row, {"id": str(AuditEntry.objects.get(action="System").seq), "at": 5, "actorId": "", "action": "System", "detail": "no plan"})

    def test_signed_out_is_401(self):
        self.assertError(Client().get("/api/audit"), 401, "Not signed in")

    def test_verify_is_for_admin_and_auditor(self):
        self.submit()
        for user in ("ada", "aud"):
            self.assertEqual(self.req("get", "/api/audit/verify", user).json(), {"ok": True, "entries": AuditEntry.objects.count()})
        for user in ("pat", "rex"):
            self.assertError(self.req("get", "/api/audit/verify", user), 403, "Your role is not allowed to do this")
        self.assertError(Client().get("/api/audit/verify"), 401, "Not signed in")

    def test_verify_reports_where_the_chain_breaks(self):
        self.submit()
        self.submit()
        first = AuditEntry.objects.order_by("seq").first()
        AuditEntry.objects.filter(pk=first.pk).update(detail="tampered")
        res = self.req("get", "/api/audit/verify", "aud").json()
        self.assertEqual((res["ok"], res["brokenAtSeq"]), (False, first.seq))

    def test_verify_with_another_method_is_not_found(self):
        self.assertError(self.req("post", "/api/audit/verify", "ada", {}), 404, "Not found")

    def test_the_chain_stays_valid_through_a_whole_life_of_a_plan(self):
        plan_id = self.submit()
        self.req("post", f"/api/plans/{plan_id}/start-review", "rex")
        self.req("post", f"/api/plans/{plan_id}/comments", "rex", {"text": "unicode é — ✓ \"quotes\""})
        self.decide(plan_id, decision="changes", reason="Please move the vehicle")
        self.req("post", f"/api/plans/{plan_id}/versions", "pat", {"layout": layout("v2")})
        self.decide(plan_id, version=2)
        # six sign-ins from setUp + submit, review, comment, changes, resubmit, approve
        self.assertEqual(self.req("get", "/api/audit/verify", "aud").json(), {"ok": True, "entries": 12})
