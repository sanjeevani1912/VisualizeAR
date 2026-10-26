import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useStore } from '../store'
import { USERS, displayName, userById } from '../data/mock'
import { StatusBadge } from '../components/StatusBadge'
import { ASSET_DEFAULTS, formatRegion } from '../geo'
import { isOverdue, latestVersion, OPEN_STATUSES, shortDate, STATUS_META, waitingLabel } from '../util'
import type { AssetType, Plan, PlanStatus } from '../types'

type SortKey = 'name' | 'planner' | 'assets' | 'submitted' | 'status'

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: 'all', label: 'All statuses' },
  { value: 'open', label: 'Awaiting decision' },
  { value: 'overdue', label: 'Overdue' },
  ...(Object.keys(STATUS_META) as PlanStatus[]).map((s) => ({ value: s, label: STATUS_META[s].label })),
]

export function PlanList() {
  const { plans } = useStore()
  const [params, setParams] = useSearchParams()
  const [sort, setSort] = useState<{ key: SortKey; dir: 1 | -1 }>({ key: 'submitted', dir: -1 })
  const now = Date.now()

  const status = params.get('status') ?? 'all'
  const planner = params.get('planner') ?? 'all'
  const asset = params.get('asset') ?? 'all'
  const q = params.get('q') ?? ''
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''

  const set = (k: string, v: string) => {
    const n = new URLSearchParams(params)
    if (!v || v === 'all') n.delete(k)
    else n.set(k, v)
    setParams(n, { replace: true })
  }

  const rows = useMemo(() => {
    const fromTs = from ? new Date(from + 'T00:00:00').getTime() : -Infinity
    const toTs = to ? new Date(to + 'T23:59:59').getTime() : Infinity
    const needle = q.trim().toLowerCase()
    const list = plans.filter((p) => {
      const v = latestVersion(p)
      if (status === 'open' && !OPEN_STATUSES.includes(p.status)) return false
      if (status === 'overdue' && !isOverdue(p, now)) return false
      if (!['all', 'open', 'overdue'].includes(status) && p.status !== status) return false
      if (planner !== 'all' && p.ownerId !== planner) return false
      if (asset !== 'all' && !v.layout.objects.some((o) => o.assetType === asset)) return false
      if (v.submittedAt < fromTs || v.submittedAt > toTs) return false
      if (needle && !(p.name.toLowerCase().includes(needle) || p.code.toLowerCase().includes(needle))) return false
      return true
    })
    const val = (p: Plan): string | number => {
      switch (sort.key) {
        case 'name': return p.name.toLowerCase()
        case 'planner': return displayName(userById(p.ownerId)).toLowerCase()
        case 'assets': return latestVersion(p).layout.objects.length
        case 'status': return STATUS_META[p.status].label
        default: return latestVersion(p).submittedAt
      }
    }
    return list.sort((a, b) => {
      const x = val(a)
      const y = val(b)
      return (x < y ? -1 : x > y ? 1 : 0) * sort.dir
    })
  }, [plans, status, planner, asset, q, from, to, sort, now])

  const th = (key: SortKey, label: string) => (
    <th aria-sort={sort.key === key ? (sort.dir === 1 ? 'ascending' : 'descending') : 'none'}>
      <button className="th-btn" onClick={() => setSort((s) => ({ key, dir: s.key === key ? ((s.dir * -1) as 1 | -1) : 1 }))}>
        {label}
        <span aria-hidden="true">{sort.key === key ? (sort.dir === 1 ? ' ▲' : ' ▼') : ''}</span>
      </button>
    </th>
  )

  const planners = USERS.filter((u) => u.role === 'planner')
  const filtered = status !== 'all' || planner !== 'all' || asset !== 'all' || q || from || to

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Plans</h1>
          <p className="muted">
            {rows.length} of {plans.length} plans
          </p>
        </div>
      </header>

      <section className="card filters" aria-label="Filters">
        <label className="field grow">
          <span>Search</span>
          <input type="search" placeholder="Plan name or code" value={q} onChange={(e) => set('q', e.target.value)} />
        </label>
        <label className="field">
          <span>Status</span>
          <select value={status} onChange={(e) => set('status', e.target.value)}>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Planner</span>
          <select value={planner} onChange={(e) => set('planner', e.target.value)}>
            <option value="all">All planners</option>
            {planners.map((u) => (
              <option key={u.id} value={u.id}>{displayName(u)}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Contains asset</span>
          <select value={asset} onChange={(e) => set('asset', e.target.value)}>
            <option value="all">Any</option>
            {(Object.keys(ASSET_DEFAULTS) as AssetType[]).map((t) => (
              <option key={t} value={t}>{ASSET_DEFAULTS[t].label}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Submitted from</span>
          <input type="date" value={from} onChange={(e) => set('from', e.target.value)} />
        </label>
        <label className="field">
          <span>to</span>
          <input type="date" value={to} onChange={(e) => set('to', e.target.value)} />
        </label>
        {filtered && (
          <button className="btn btn-ghost btn-sm self-end" onClick={() => setParams({}, { replace: true })}>
            Clear filters
          </button>
        )}
      </section>

      <section className="card flush">
        {rows.length === 0 ? (
          <p className="empty">No plans match these filters.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  {th('name', 'Plan')}
                  {th('planner', 'Planner')}
                  <th>Region</th>
                  {th('assets', 'Assets')}
                  {th('submitted', 'Submitted')}
                  <th>Waiting</th>
                  {th('status', 'Status')}
                </tr>
              </thead>
              <tbody>
                {rows.map((p) => {
                  const v = latestVersion(p)
                  const open = OPEN_STATUSES.includes(p.status)
                  return (
                    <tr key={p.id} className={isOverdue(p, now) ? 'row-overdue' : ''}>
                      <td>
                        <Link to={`/plans/${p.id}`} className="plan-link">
                          <strong>{p.name}</strong>
                          <span className="small muted">{p.code} · v{v.version}</span>
                        </Link>
                      </td>
                      <td>{displayName(userById(p.ownerId))}</td>
                      <td>{formatRegion(v.layout.regionBounds)}</td>
                      <td>{v.layout.objects.length}</td>
                      <td>{shortDate(v.submittedAt)}</td>
                      <td>{open ? waitingLabel(p, now) : '–'}</td>
                      <td><StatusBadge status={p.status} /></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
