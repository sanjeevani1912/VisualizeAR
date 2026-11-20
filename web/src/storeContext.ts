import { createContext, useContext } from 'react'
import type { AuditEvent, DecisionKind, Plan, User } from './types'

/**
 * Everything the pages need. Two providers implement it: the presentation demo (mock data in the
 * browser, mounted at /demo) and the live dashboard (REST API, mounted at /). Pages never know which.
 * Action methods resolve to an error message, or null on success.
 */
export interface Ctx {
  mode: 'demo' | 'live'
  /** URL prefix: '/demo' or ''. */
  base: string
  ready: boolean
  /** Set when the last attempt to load data from the server failed (live mode only). */
  loadError?: string | null
  user: User | null
  users: User[]
  plans: Plan[]
  audit: AuditEvent[]
  nameOf: (id: string) => string
  login: (a: string, b?: string) => Promise<string | null>
  logout: () => Promise<void>
  refresh: () => Promise<void>
  decide: (planId: string, kind: DecisionKind, reason: string) => Promise<string | null>
  comment: (planId: string, text: string) => Promise<string | null>
  markReviewing: (planId: string) => void
  addAttachment: (planId: string, file: File, caption: string) => Promise<string | null>
  removeAttachment: (planId: string, attachmentId: string) => Promise<string | null>
  createPlan: (layout: unknown, note: string) => Promise<{ id?: string; error?: string }>
  resubmit: (planId: string, layout: unknown, note: string) => Promise<string | null>
  withdraw: (planId: string) => Promise<string | null>
  reset: () => void
}

export const StoreContext = createContext<Ctx | null>(null)

export function useStore(): Ctx {
  const c = useContext(StoreContext)
  if (!c) throw new Error('useStore outside a provider')
  return c
}

export const fullName = (u: Pick<User, 'rank' | 'name'> | null | undefined): string => (u ? `${u.rank} ${u.name}` : 'Unknown user')
