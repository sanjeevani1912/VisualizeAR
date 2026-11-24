"""Port of server/src/rules.test.ts: the same cases, so the two implementations cannot drift apart."""

from django.test import SimpleTestCase

from api.rules import (
    Actor,
    PlanRef,
    can_attach,
    can_comment,
    can_decide,
    can_resubmit,
    can_start_review,
    can_view_plan,
    can_withdraw,
    password_error,
    reason_error,
)

reviewer = Actor("r1", "reviewer")
planner = Actor("p1", "planner")
other_planner = Actor("p2", "planner")
admin = Actor("a1", "admin")
auditor = Actor("u1", "auditor")
open_plan = PlanRef("p1", "SUBMITTED")


class RulesTests(SimpleTestCase):
    def test_only_a_non_owner_reviewer_can_decide_an_open_plan(self):
        self.assertTrue(can_decide(reviewer, open_plan))
        self.assertFalse(can_decide(admin, open_plan))
        self.assertFalse(can_decide(auditor, open_plan))
        self.assertFalse(can_decide(planner, open_plan))
        self.assertFalse(can_decide(Actor("p1", "reviewer"), open_plan))  # own plan
        self.assertFalse(can_decide(reviewer, PlanRef("p1", "APPROVED")))
        self.assertFalse(can_decide(reviewer, PlanRef("p1", "WITHDRAWN")))

    def test_planners_only_see_their_own_plans_everyone_else_sees_all(self):
        self.assertTrue(can_view_plan(planner, open_plan))
        self.assertFalse(can_view_plan(other_planner, open_plan))
        for a in (reviewer, admin, auditor):
            self.assertTrue(can_view_plan(a, open_plan))

    def test_review_starts_only_for_a_reviewer_on_a_submitted_plan(self):
        self.assertTrue(can_start_review(reviewer, open_plan))
        self.assertFalse(can_start_review(reviewer, PlanRef("p1", "UNDER_REVIEW")))
        self.assertFalse(can_start_review(admin, open_plan))

    def test_withdraw_and_resubmit_belong_to_the_owning_planner_and_the_right_states(self):
        self.assertTrue(can_withdraw(planner, open_plan))
        self.assertFalse(can_withdraw(other_planner, open_plan))
        self.assertFalse(can_withdraw(planner, PlanRef("p1", "APPROVED")))
        self.assertTrue(can_resubmit(planner, PlanRef("p1", "REJECTED")))
        self.assertTrue(can_resubmit(planner, PlanRef("p1", "CHANGES_REQUESTED")))
        self.assertFalse(can_resubmit(planner, open_plan))
        self.assertFalse(can_resubmit(other_planner, PlanRef("p1", "REJECTED")))

    def test_auditors_cannot_comment_planners_cannot_comment_on_others_plans(self):
        self.assertFalse(can_comment(auditor, open_plan))
        self.assertFalse(can_comment(other_planner, open_plan))
        self.assertTrue(can_comment(planner, open_plan))
        self.assertTrue(can_comment(admin, open_plan))

    def test_field_images_owner_planner_or_reviewer_never_on_withdrawn_plans(self):
        self.assertTrue(can_attach(planner, open_plan))
        self.assertFalse(can_attach(other_planner, open_plan))
        self.assertTrue(can_attach(reviewer, open_plan))
        self.assertFalse(can_attach(admin, open_plan))
        self.assertFalse(can_attach(planner, PlanRef("p1", "WITHDRAWN")))

    def test_reject_and_changes_need_a_real_reason_approve_does_not(self):
        self.assertIsNone(reason_error("approve", ""))
        self.assertIsNotNone(reason_error("reject", "short"))
        self.assertIsNotNone(reason_error("changes", "          "))
        self.assertIsNone(reason_error("reject", "Antenna spacing is too tight"))

    def test_password_policy(self):
        self.assertIsNotNone(password_error("short1A", "sam"))
        self.assertIsNotNone(password_error("alllowercase123", "sam"))
        self.assertIsNotNone(password_error("Contains-Sharma-1", "sharma"))
        self.assertIsNone(password_error("Correct-Horse-9", "sam"))
