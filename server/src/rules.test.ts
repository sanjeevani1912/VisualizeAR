import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canAttach,
  canComment,
  canDecide,
  canResubmit,
  canStartReview,
  canViewPlan,
  canWithdraw,
  passwordError,
  reasonError,
  type Actor,
  type PlanRef,
} from './rules.js'

const reviewer: Actor = { id: 'r1', role: 'reviewer' }
const planner: Actor = { id: 'p1', role: 'planner' }
const otherPlanner: Actor = { id: 'p2', role: 'planner' }
const admin: Actor = { id: 'a1', role: 'admin' }
const auditor: Actor = { id: 'u1', role: 'auditor' }
const open: PlanRef = { ownerId: 'p1', status: 'SUBMITTED' }

test('only a non-owner reviewer can decide an open plan', () => {
  assert.equal(canDecide(reviewer, open), true)
  assert.equal(canDecide(admin, open), false)
  assert.equal(canDecide(auditor, open), false)
  assert.equal(canDecide(planner, open), false)
  assert.equal(canDecide({ id: 'p1', role: 'reviewer' }, open), false) // own plan
  assert.equal(canDecide(reviewer, { ownerId: 'p1', status: 'APPROVED' }), false)
  assert.equal(canDecide(reviewer, { ownerId: 'p1', status: 'WITHDRAWN' }), false)
})

test('planners only see their own plans; everyone else sees all', () => {
  assert.equal(canViewPlan(planner, open), true)
  assert.equal(canViewPlan(otherPlanner, open), false)
  for (const a of [reviewer, admin, auditor]) assert.equal(canViewPlan(a, open), true)
})

test('review starts only for a reviewer on a submitted plan', () => {
  assert.equal(canStartReview(reviewer, open), true)
  assert.equal(canStartReview(reviewer, { ownerId: 'p1', status: 'UNDER_REVIEW' }), false)
  assert.equal(canStartReview(admin, open), false)
})

test('withdraw and resubmit belong to the owning planner and the right states', () => {
  assert.equal(canWithdraw(planner, open), true)
  assert.equal(canWithdraw(otherPlanner, open), false)
  assert.equal(canWithdraw(planner, { ownerId: 'p1', status: 'APPROVED' }), false)
  assert.equal(canResubmit(planner, { ownerId: 'p1', status: 'REJECTED' }), true)
  assert.equal(canResubmit(planner, { ownerId: 'p1', status: 'CHANGES_REQUESTED' }), true)
  assert.equal(canResubmit(planner, open), false)
  assert.equal(canResubmit(otherPlanner, { ownerId: 'p1', status: 'REJECTED' }), false)
})

test('auditors cannot comment; planners cannot comment on others plans', () => {
  assert.equal(canComment(auditor, open), false)
  assert.equal(canComment(otherPlanner, open), false)
  assert.equal(canComment(planner, open), true)
  assert.equal(canComment(admin, open), true)
})

test('field images: owner planner or reviewer, never on withdrawn plans', () => {
  assert.equal(canAttach(planner, open), true)
  assert.equal(canAttach(otherPlanner, open), false)
  assert.equal(canAttach(reviewer, open), true)
  assert.equal(canAttach(admin, open), false)
  assert.equal(canAttach(planner, { ownerId: 'p1', status: 'WITHDRAWN' }), false)
})

test('reject and changes need a real reason; approve does not', () => {
  assert.equal(reasonError('approve', ''), null)
  assert.notEqual(reasonError('reject', 'short'), null)
  assert.notEqual(reasonError('changes', '          '), null)
  assert.equal(reasonError('reject', 'Antenna spacing is too tight'), null)
})

test('password policy', () => {
  assert.notEqual(passwordError('short1A', 'sam'), null)
  assert.notEqual(passwordError('alllowercase123', 'sam'), null)
  assert.notEqual(passwordError('Contains-Sharma-1', 'sharma'), null)
  assert.equal(passwordError('Correct-Horse-9', 'sam'), null)
})
