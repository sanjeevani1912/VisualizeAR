// Presentation demo: fictional data held in the browser. Nothing here talks to a server.
import { useCallback, useEffect, useMemo, useReducer, type ReactNode } from 'react'
import { buildInitialData, USERS } from './data/mock'
import { canComment, canDecide, canStartReview, reasonError } from './rules'
import { StoreContext, fullName, type Ctx } from './storeContext'
import type { Attachment, AuditEvent, Comment, Decision, DecisionKind, Plan, PlanStatus } from './types'

const DATA_KEY = 'dhruvar-dashboard-demo-v2'
const USER_KEY = 'dhruvar-dashboard-demo-user'

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
  | { type: 'attach'; planId: string; attachment: Attachment }
  | { type: 'detach'; planId: string; attachmentId: string }
  | { type: 'reset' }

const uid = (p: string) => `${p}-${Math.random().toString(36).slice(2, 9)}`

const STATUS_FOR: Record<DecisionKind, PlanStatus> = { approve: 'APPROVED', reject: 'REJECTED', changes: 'CHANGES_REQUESTED' }
const ACTION_FOR: Record<DecisionKind, string> = { approve: 'Approved', reject: 'Rejected', changes: 'Changes requested' }

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

const me = (s: State) => USERS.find((u) => u.id === s.userId)

function reducer(state: State, a: Action): State {
  switch (a.type) {
    case 'login':
      return { ...state, userId: a.userId }
    case 'logout':
      return { ...state, userId: null }
    case 'reset':
      return { ...state, ...buildInitialData(Date.now()) }
    case 'markReviewing': {
      const user = me(state)
      const plan = state.plans.find((p) => p.id === a.planId)
      if (!user || !plan || !canStartReview(user, { ownerId: plan.ownerId, status: plan.status })) return state
      return { ...state, plans: state.plans.map((p) => (p.id === plan.id ? { ...p, status: 'UNDER_REVIEW' } : p)) }
    }
    case 'decide': {
      const user = me(state)
      const plan = state.plans.find((p) => p.id === a.planId)
      if (!user || !plan || !canDecide(user, { ownerId: plan.ownerId, status: plan.status })) return state
      const reason = a.reason.trim()
      if (reasonError(a.kind, reason)) return state
      const version = plan.versions[plan.versions.length - 1].version
      const now = Date.now()
      const decision: Decision = { id: uid('d'), planId: plan.id, version, reviewerId: user.id, decision: a.kind, reason, decidedAt: now }
      const event: AuditEvent = { id: uid('a'), at: now, actorId: user.id, action: ACTION_FOR[a.kind], planId: plan.id, detail: `${plan.code} version ${version}${reason ? `: ${reason}` : ''}` }
      return {
        ...state,
        audit: [event, ...state.audit],
        plans: state.plans.map((p) => (p.id === plan.id ? { ...p, status: STATUS_FOR[a.kind], updatedAt: now, decisions: [...p.decisions, decision] } : p)),
      }
    }
    case 'comment': {
      const user = me(state)
      const plan = state.plans.find((p) => p.id === a.planId)
      const text = a.text.trim()
      if (!user || !canComment(user) || !plan || !text) return state
      const now = Date.now()
      const c: Comment = { id: uid('c'), planId: plan.id, version: plan.versions[plan.versions.length - 1].version, authorId: user.id, text, createdAt: now }
      const event: AuditEvent = { id: uid('a'), at: now, actorId: user.id, action: 'Commented', planId: plan.id, detail: text }
      return { ...state, audit: [event, ...state.audit], plans: state.plans.map((p) => (p.id === plan.id ? { ...p, comments: [...p.comments, c] } : p)) }
    }
    case 'attach': {
      const user = me(state)
      if (!user) return state
      const event: AuditEvent = { id: uid('a'), at: Date.now(), actorId: user.id, action: 'Image added', planId: a.planId, detail: a.attachment.fileName }
      return { ...state, audit: [event, ...state.audit], plans: state.plans.map((p) => (p.id === a.planId ? { ...p, attachments: [...p.attachments, a.attachment] } : p)) }
    }
    case 'detach': {
      const user = me(state)
      if (!user) return state
      const event: AuditEvent = { id: uid('a'), at: Date.now(), actorId: user.id, action: 'Image removed', planId: a.planId, detail: a.attachmentId }
      return { ...state, audit: [event, ...state.audit], plans: state.plans.map((p) => (p.id === a.planId ? { ...p, attachments: p.attachments.filter((x) => x.id !== a.attachmentId) } : p)) }
    }
  }
}

const DEMO_ONLY = 'Not available in the presentation demo'

export function DemoProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, undefined, initState)

  useEffect(() => {
    try {
      // Images added during a demo are session-only (blob URLs do not survive a reload).
      const plans = state.plans.map((p) => ({ ...p, attachments: p.attachments.filter((a) => !a.url.startsWith('blob:')) }))
      localStorage.setItem(DATA_KEY, JSON.stringify({ plans, audit: state.audit }))
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

  const nameOf = useCallback((id: string) => fullName(USERS.find((u) => u.id === id)), [])

  const value = useMemo<Ctx>(
    () => ({
      mode: 'demo',
      base: '/demo',
      ready: true,
      user: USERS.find((u) => u.id === state.userId) ?? null,
      users: USERS,
      plans: state.plans,
      audit: state.audit,
      nameOf,
      login: async (userId) => {
        dispatch({ type: 'login', userId })
        return null
      },
      logout: async () => dispatch({ type: 'logout' }),
      refresh: async () => {},
      decide: async (planId, kind, reason) => {
        dispatch({ type: 'decide', planId, kind, reason })
        return null
      },
      comment: async (planId, text) => {
        dispatch({ type: 'comment', planId, text })
        return null
      },
      markReviewing: (planId) => dispatch({ type: 'markReviewing', planId }),
      addAttachment: async (planId, file, caption) => {
        const user = USERS.find((u) => u.id === state.userId)
        const plan = state.plans.find((p) => p.id === planId)
        if (!user || !plan) return 'Not signed in'
        const attachment: Attachment = {
          id: uid('img'),
          planId,
          version: plan.versions[plan.versions.length - 1].version,
          fileName: file.name,
          mime: file.type,
          size: file.size,
          caption,
          uploadedBy: user.id,
          uploadedAt: Date.now(),
          url: URL.createObjectURL(file),
        }
        dispatch({ type: 'attach', planId, attachment })
        return null
      },
      removeAttachment: async (planId, attachmentId) => {
        dispatch({ type: 'detach', planId, attachmentId })
        return null
      },
      createPlan: async () => ({ error: DEMO_ONLY }),
      resubmit: async () => DEMO_ONLY,
      withdraw: async () => DEMO_ONLY,
      reset: () => dispatch({ type: 'reset' }),
    }),
    [state, nameOf],
  )
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}
