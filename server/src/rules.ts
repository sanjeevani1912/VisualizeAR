// Pure authorisation and state-machine rules. Kept free of I/O so they can be unit-tested and
// reused by every route. The server enforces these; the web UI only mirrors them for display.

export type Role = 'planner' | 'reviewer' | 'admin' | 'auditor'
export type PlanStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'CHANGES_REQUESTED'
  | 'WITHDRAWN'
export type DecisionKind = 'approve' | 'reject' | 'changes'

export interface Actor {
  id: string
  role: Role
}
export interface PlanRef {
  ownerId: string
  status: PlanStatus
}

export const OPEN: PlanStatus[] = ['SUBMITTED', 'UNDER_REVIEW']
export const MIN_REASON = 10

export function canViewPlan(a: Actor, p: PlanRef): boolean {
  return a.role === 'planner' ? p.ownerId === a.id : true
}

export function canCreatePlan(a: Actor): boolean {
  return a.role === 'planner'
}

export function canDecide(a: Actor, p: PlanRef): boolean {
  return a.role === 'reviewer' && p.ownerId !== a.id && OPEN.includes(p.status)
}

export function canStartReview(a: Actor, p: PlanRef): boolean {
  return a.role === 'reviewer' && p.ownerId !== a.id && p.status === 'SUBMITTED'
}

export function canWithdraw(a: Actor, p: PlanRef): boolean {
  return a.role === 'planner' && p.ownerId === a.id && OPEN.includes(p.status)
}

export function canResubmit(a: Actor, p: PlanRef): boolean {
  return a.role === 'planner' && p.ownerId === a.id && (p.status === 'CHANGES_REQUESTED' || p.status === 'REJECTED')
}

export function canComment(a: Actor, p: PlanRef): boolean {
  return a.role !== 'auditor' && canViewPlan(a, p)
}

/** Field images: the owning planner, or a reviewer. Never on a withdrawn plan. */
export function canAttach(a: Actor, p: PlanRef): boolean {
  if (p.status === 'WITHDRAWN') return false
  if (a.role === 'planner') return p.ownerId === a.id
  return a.role === 'reviewer'
}

export function canRemoveAttachment(a: Actor, uploaderId: string): boolean {
  return a.role === 'admin' || uploaderId === a.id
}

export const STATUS_FOR: Record<DecisionKind, PlanStatus> = {
  approve: 'APPROVED',
  reject: 'REJECTED',
  changes: 'CHANGES_REQUESTED',
}

export function reasonError(kind: DecisionKind, reason: string): string | null {
  if (kind !== 'approve' && reason.trim().length < MIN_REASON) {
    return `A reason of at least ${MIN_REASON} characters is required`
  }
  return null
}

export function passwordError(pw: string, username: string): string | null {
  if (pw.length < 10) return 'Password must be at least 10 characters'
  if (pw.length > 200) return 'Password is too long'
  if (pw.toLowerCase().includes(username.toLowerCase()) && username.length >= 3) return 'Password must not contain the username'
  if (!/[a-z]/.test(pw) || !/[A-Z]/.test(pw) || !/[0-9]/.test(pw)) return 'Password needs upper case, lower case and a digit'
  return null
}
