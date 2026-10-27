// Mirror of server/src/rules.ts. The server is the authority and re-checks every action; these
// copies only decide which buttons to show. Keep the two files in step (a shared package is a later step).
import type { DecisionKind, PlanStatus, Role } from './types'

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

export const canDecide = (a: Actor, p: PlanRef) => a.role === 'reviewer' && p.ownerId !== a.id && OPEN.includes(p.status)
export const canStartReview = (a: Actor, p: PlanRef) => a.role === 'reviewer' && p.ownerId !== a.id && p.status === 'SUBMITTED'
export const canWithdraw = (a: Actor, p: PlanRef) => a.role === 'planner' && p.ownerId === a.id && OPEN.includes(p.status)
export const canResubmit = (a: Actor, p: PlanRef) =>
  a.role === 'planner' && p.ownerId === a.id && (p.status === 'CHANGES_REQUESTED' || p.status === 'REJECTED')
export const canComment = (a: Actor) => a.role !== 'auditor'
export const canAttach = (a: Actor, p: PlanRef) => {
  if (p.status === 'WITHDRAWN') return false
  if (a.role === 'planner') return p.ownerId === a.id
  return a.role === 'reviewer'
}
export const canRemoveAttachment = (a: Actor, uploaderId: string) => a.role === 'admin' || uploaderId === a.id

export const reasonError = (kind: DecisionKind, reason: string): string | null =>
  kind !== 'approve' && reason.trim().length < MIN_REASON ? `A reason of at least ${MIN_REASON} characters is required` : null

export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const MAX_IMAGE_BYTES = 10 * 1024 * 1024

export function imageError(f: File): string | null {
  if (!IMAGE_TYPES.includes(f.type)) return 'Only JPEG, PNG or WebP images can be attached'
  if (f.size > MAX_IMAGE_BYTES) return 'Image is larger than 10 MB'
  return null
}
