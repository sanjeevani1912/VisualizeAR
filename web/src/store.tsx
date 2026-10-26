import { createContext, useCallback, useContext, useEffect, useMemo, useReducer, type ReactNode } from 'react'
import { buildInitialData, userById } from './data/mock'
import type { AuditEvent, Comment, Decision, DecisionKind, Plan, PlanStatus, User } from './types'

const DATA_KEY = 'dhruvar-dashboard-v1'
const USER_KEY = 'dhruvar-dashboard-user'

interface State {
  plans: Plan[]
  audit: AuditEvent[]
  userId: string | null
}

type Action =
  | { type: 'login'; userId: string }
  | { type: 'logout' }
  | { type: 'decide'; planId: string; kind: DecisionKind; reason: string }
  | { type: 'comment'; planId: string; text: string }
  | { type: 'markReviewing'; planId: string }
  | { type: 'reset' }

function uid(p: string): string {
  return `${p}-${Math.random().toString(36).slice(2, 9)}`
}

const STATUS_FOR: Record<DecisionKind, PlanStatus> = {
  approve: 'APPROVED',
  reject: 'REJECTED',
  changes: 'CHANGES_REQUESTED',
}
const ACTION_FOR: Record<DecisionKind, string> = {
  approve: 'Approved',
  reject: 'Rejected',
  changes: 'Changes requested',
}

export const DECIDABLE: PlanStatus[] = ['SUBMITTED', 'UNDER_REVIEW']

function load(): { plans: Plan[]; audit: AuditEvent[] } {
  try {
    const raw = localStorage.getItem(DATA_KEY)
    if (raw) return JSON.parse(raw)
  } catch {
    /* ignore corrupt or unavailable storage */
  }
  return buildInitialData(Date.now())
}

function initState(): State {
  let userId: string | null = null
  try {
    userId = sessionStorage.getItem(USER_KEY)
  } catch {
    /* ignore */
  }
  return { ...load(), userId }
}

function reducer(state: State, a: Action): State {
  switch (a.type) {
    case 'login':
      return { ...state, userId: a.userId }
    case 'logout':
      return { ...state, userId: null }
    case 'reset': {
      const fresh = buildInitialData(Date.now())
      return { ...state, ...fresh }
    }
    case 'markReviewing': {
      const user = state.userId ? userById(state.userId) : undefined
      if (!user || user.role !== 'reviewer') return state
      return {
        ...state,
        plans: state.plans.map((p) => (p.id === a.planId && p.status === 'SUBMITTED' ? { ...p, status: 'UNDER_REVIEW' } : p)),
      }
    }
    case 'decide': {
      const user = state.userId ? userById(state.userId) : undefined
      const plan = state.plans.find((p) => p.id === a.planId)
      // Server-side rules, mirrored here: reviewers only, never the owner, only open plans.
      if (!user || user.role !== 'reviewer' || !plan || plan.ownerId === user.id) return state
      if (!DECIDABLE.includes(plan.status)) return state
      const reason = a.reason.trim()
      if (a.kind !== 'approve' && reason.length < 10) return state
      const version = plan.versions[plan.versions.length - 1].version
      const now = Date.now()
      const decision: Decision = {
        id: uid('d'),
        planId: plan.id,
        version,
        reviewerId: user.id,
        decision: a.kind,
        reason,
        decidedAt: now,
      }
      const event: AuditEvent = {
        id: uid('a'),
        at: now,
        actorId: user.id,
        action: ACTION_FOR[a.kind],
        planId: plan.id,
        detail: `${plan.code} version ${version}${reason ? `: ${reason}` : ''}`,
      }
      return {
        ...state,
        audit: [event, ...state.audit],
        plans: state.plans.map((p) =>
          p.id === plan.id
            ? { ...p, status: STATUS_FOR[a.kind], updatedAt: now, decisions: [...p.decisions, decision] }
            : p,
        ),
      }
    }
    case 'comment': {
      const user = state.userId ? userById(state.userId) : undefined
      const plan = state.plans.find((p) => p.id === a.planId)
      const text = a.text.trim()
      if (!user || user.role === 'auditor' || !plan || !text) return state
      const now = Date.now()
      const c: Comment = {
        id: uid('c'),
        planId: plan.id,
        version: plan.versions[plan.versions.length - 1].version,
        authorId: user.id,
        text,
        createdAt: now,
      }
      const event: AuditEvent = { id: uid('a'), at: now, actorId: user.id, action: 'Commented', planId: plan.id, detail: text }
      return {
        ...state,
        audit: [event, ...state.audit],
        plans: state.plans.map((p) => (p.id === plan.id ? { ...p, comments: [...p.comments, c] } : p)),
      }
    }
  }
}

interface Ctx {
  plans: Plan[]
  audit: AuditEvent[]
  user: User | null
  login: (userId: string) => void
  logout: () => void
  decide: (planId: string, kind: DecisionKind, reason: string) => void
  comment: (planId: string, text: string) => void
  markReviewing: (planId: string) => void
  reset: () => void
}

const StoreContext = createContext<Ctx | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, undefined, initState)

  useEffect(() => {
    try {
      localStorage.setItem(DATA_KEY, JSON.stringify({ plans: state.plans, audit: state.audit }))
    } catch {
      /* storage may be unavailable */
    }
  }, [state.plans, state.audit])

  useEffect(() => {
    try {
      if (state.userId) sessionStorage.setItem(USER_KEY, state.userId)
      else sessionStorage.removeItem(USER_KEY)
    } catch {
      /* ignore */
    }
  }, [state.userId])

  const login = useCallback((userId: string) => dispatch({ type: 'login', userId }), [])
  const logout = useCallback(() => dispatch({ type: 'logout' }), [])
  const decide = useCallback((planId: string, kind: DecisionKind, reason: string) => dispatch({ type: 'decide', planId, kind, reason }), [])
  const comment = useCallback((planId: string, text: string) => dispatch({ type: 'comment', planId, text }), [])
  const markReviewing = useCallback((planId: string) => dispatch({ type: 'markReviewing', planId }), [])
  const reset = useCallback(() => dispatch({ type: 'reset' }), [])

  const value = useMemo<Ctx>(
    () => ({
      plans: state.plans,
      audit: state.audit,
      user: state.userId ? (userById(state.userId) ?? null) : null,
      login,
      logout,
      decide,
      comment,
      markReviewing,
      reset,
    }),
    [state, login, logout, decide, comment, markReviewing, reset],
  )
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function useStore(): Ctx {
  const c = useContext(StoreContext)
  if (!c) throw new Error('useStore outside StoreProvider')
  return c
}
