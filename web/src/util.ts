import type { Plan, PlanStatus } from './types'

export const STATUS_META: Record<PlanStatus, { label: string; tone: 'wait' | 'ok' | 'bad' | 'warn' | 'muted' | 'info' }> = {
  SUBMITTED: { label: 'Submitted', tone: 'wait' },
  UNDER_REVIEW: { label: 'Under review', tone: 'info' },
  APPROVED: { label: 'Approved', tone: 'ok' },
  REJECTED: { label: 'Rejected', tone: 'bad' },
  CHANGES_REQUESTED: { label: 'Changes requested', tone: 'warn' },
  WITHDRAWN: { label: 'Withdrawn', tone: 'muted' },
}

export const OPEN_STATUSES: PlanStatus[] = ['SUBMITTED', 'UNDER_REVIEW']

export function latestVersion(p: Plan) {
  return p.versions[p.versions.length - 1]
}

export function relTime(ts: number, now = Date.now()): string {
  const s = Math.max(0, Math.round((now - ts) / 1000))
  if (s < 60) return 'just now'
  const m = Math.round(s / 60)
  if (m < 60) return `${m} min ago`
  const h = Math.round(m / 60)
  if (h < 48) return `${h} h ago`
  return `${Math.round(h / 24)} days ago`
}

export function waitingHours(p: Plan, now = Date.now()): number {
  return (now - latestVersion(p).submittedAt) / 3_600_000
}

export function waitingLabel(p: Plan, now = Date.now()): string {
  const h = waitingHours(p, now)
  if (h < 1) return '< 1 h'
  if (h < 48) return `${Math.round(h)} h`
  return `${Math.round(h / 24)} days`
}

export const OVERDUE_HOURS = 72

export function isOverdue(p: Plan, now = Date.now()): boolean {
  return OPEN_STATUSES.includes(p.status) && waitingHours(p, now) > OVERDUE_HOURS
}

export function fullDate(ts: number): string {
  return new Date(ts).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function shortDate(ts: number): string {
  return new Date(ts).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
}
