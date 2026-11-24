// Live dashboard: every read and write goes through the server, which enforces roles and rules.
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, errorText } from './api'
import { StoreContext, fullName, type Ctx } from './storeContext'
import type { AuditEvent, DecisionKind, Plan, User } from './types'

const POLL_MS = 15000

export function LiveProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [plans, setPlans] = useState<Plan[]>([])
  const [audit, setAudit] = useState<AuditEvent[]>([])
  const [ready, setReady] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const [p, a, u] = await Promise.all([api<Plan[]>('GET', '/plans'), api<AuditEvent[]>('GET', '/audit'), api<User[]>('GET', '/users')])
      setPlans(p)
      setAudit(a)
      setUsers(u)
      setLoadError(null)
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setUser(null) // session ended
      else setLoadError(e instanceof ApiError ? e.message : 'Cannot reach the server.')
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const me = await api<User>('GET', '/auth/me')
        if (cancelled) return
        setUser(me)
        if (!me.mustChangePassword) await load()
      } catch {
        /* not signed in */
      } finally {
        if (!cancelled) setReady(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [load])

  // Keep the screen fresh without a manual reload.
  useEffect(() => {
    if (!user || user.mustChangePassword) return
    const tick = () => {
      if (document.visibilityState === 'visible') void load()
    }
    const t = window.setInterval(tick, POLL_MS)
    document.addEventListener('visibilitychange', tick)
    return () => {
      window.clearInterval(t)
      document.removeEventListener('visibilitychange', tick)
    }
  }, [user, load])

  const act = useCallback(
    async (fn: () => Promise<unknown>): Promise<string | null> => {
      try {
        await fn()
        await load()
        return null
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) setUser(null)
        await load()
        return errorText(e)
      }
    },
    [load],
  )

  const nameOf = useCallback((id: string) => fullName(users.find((u) => u.id === id)), [users])

  const value = useMemo<Ctx>(
    () => ({
      mode: 'live',
      base: '',
      ready,
      loadError,
      user,
      users,
      plans,
      audit,
      nameOf,
      login: async (username, password) => {
        try {
          const me = await api<User>('POST', '/auth/login', { username, password })
          setUser(me)
          if (!me.mustChangePassword) await load()
          return null
        } catch (e) {
          return errorText(e)
        }
      },
      logout: async () => {
        try {
          await api('POST', '/auth/logout')
        } catch {
          /* already signed out */
        }
        setUser(null)
        setPlans([])
        setAudit([])
      },
      refresh: load,
      passwordChanged: async () => {
        try {
          setUser(await api<User>('GET', '/auth/me'))
        } catch (e) {
          if (e instanceof ApiError && e.status === 401) setUser(null)
        }
        await load()
      },
      decide: (planId: string, kind: DecisionKind, reason: string) =>
        act(() => {
          const plan = plans.find((p) => p.id === planId)
          const version = plan ? plan.versions[plan.versions.length - 1].version : 1
          return api('POST', `/plans/${planId}/decision`, { decision: kind, reason, version })
        }),
      comment: (planId, text) => act(() => api('POST', `/plans/${planId}/comments`, { text })),
      markReviewing: (planId) => {
        void act(() => api('POST', `/plans/${planId}/start-review`))
      },
      addAttachment: (planId, file, caption) => {
        const form = new FormData()
        form.set('file', file)
        form.set('caption', caption)
        return act(() => api('POST', `/plans/${planId}/attachments`, undefined, form))
      },
      removeAttachment: (_planId, id) => act(() => api('DELETE', `/attachments/${id}`)),
      createPlan: async (layout, note) => {
        try {
          const r = await api<{ id: string }>('POST', '/plans', { layout, note })
          await load()
          return { id: r.id }
        } catch (e) {
          return { error: errorText(e) }
        }
      },
      resubmit: (planId, layout, note) => act(() => api('POST', `/plans/${planId}/versions`, { layout, note })),
      withdraw: (planId) => act(() => api('POST', `/plans/${planId}/withdraw`)),
      reset: () => {},
    }),
    [ready, loadError, user, users, plans, audit, nameOf, load, act],
  )
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}
