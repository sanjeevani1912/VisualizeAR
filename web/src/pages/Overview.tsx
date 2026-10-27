import { Link } from 'react-router-dom'
import { useStore } from '../storeContext'
import { StatusBadge } from '../components/StatusBadge'
import { isOverdue, latestVersion, OPEN_STATUSES, relTime, STATUS_META, waitingHours, waitingLabel } from '../util'
import { formatRegion } from '../geo'
import type { PlanStatus } from '../types'

const ORDER: PlanStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED', 'WITHDRAWN']

export function Overview() {
  const { plans, audit, user, nameOf, base, ready } = useStore()
  const now = Date.now()
  const planner = user?.role === 'planner'
  const count = (s: PlanStatus) => plans.filter((p) => p.status === s).length
  const pending = plans.filter((p) => OPEN_STATUSES.includes(p.status))
  const overdue = plans.filter((p) => isOverdue(p, now))
  // Reviewers: oldest waiting first. Planners: plans that need them to act.
  const queue = planner
    ? plans.filter((p) => p.status === 'CHANGES_REQUESTED' || p.status === 'REJECTED').sort((a, b) => b.updatedAt - a.updatedAt)
    : [...pending].sort((a, b) => latestVersion(a).submittedAt - latestVersion(b).submittedAt)
  const decided = plans.flatMap((p) => p.decisions)
  const avgHours =
    decided.length === 0
      ? null
      : decided.reduce((sum, d) => {
          const p = plans.find((x) => x.id === d.planId)
          const v = p?.versions.find((x) => x.version === d.version)
          return sum + (v ? (d.decidedAt - v.submittedAt) / 3_600_000 : 0)
        }, 0) / decided.length
  const total = plans.length
  const q = (s: string) => `${base}/plans?status=${s}`

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Overview</h1>
          <p className="muted">Welcome, {user ? nameOf(user.id) : ''}. {planner ? 'Here is the state of your plans.' : 'Here is what needs attention.'}</p>
        </div>
      </header>

      <section className="tiles" aria-label="Status summary">
        <Tile label={planner ? 'In review' : 'Awaiting decision'} value={pending.length} tone="wait" to={q('open')} />
        {!planner && <Tile label="Overdue (> 72 h)" value={overdue.length} tone={overdue.length ? 'bad' : 'muted'} to={q('overdue')} />}
        <Tile label="Approved" value={count('APPROVED')} tone="ok" to={q('APPROVED')} />
        <Tile label="Rejected" value={count('REJECTED')} tone="bad" to={q('REJECTED')} />
        <Tile label="Changes requested" value={count('CHANGES_REQUESTED')} tone="warn" to={q('CHANGES_REQUESTED')} />
        <Tile label="Avg. decision time" value={avgHours === null ? '–' : avgHours < 48 ? `${Math.round(avgHours)} h` : `${(avgHours / 24).toFixed(1)} d`} tone="muted" />
      </section>

      <div className="grid-2">
        <section className="card">
          <div className="card-head">
            <h2>{planner ? 'Needs your action' : user?.role === 'reviewer' ? 'Needs your decision' : 'Awaiting decision'}</h2>
            <Link to={q(planner ? 'all' : 'open')} className="link">View all</Link>
          </div>
          {!ready ? (
            <p className="empty">Loading…</p>
          ) : queue.length === 0 ? (
            <p className="empty">{planner ? 'Nothing needs your action.' : 'Nothing is waiting. All submitted plans have been decided.'}</p>
          ) : (
            <table className="table">
              <thead>
                <tr><th>Plan</th><th>Planner</th><th>Region</th><th>{planner ? 'Updated' : 'Waiting'}</th><th>Status</th></tr>
              </thead>
              <tbody>
                {queue.map((p) => (
                  <tr key={p.id} className={isOverdue(p, now) ? 'row-overdue' : ''}>
                    <td>
                      <Link to={`${base}/plans/${p.id}`} className="plan-link">
                        <strong>{p.name}</strong>
                        <span className="small muted">{p.code} · v{latestVersion(p).version}</span>
                      </Link>
                    </td>
                    <td>{nameOf(p.ownerId)}</td>
                    <td>{formatRegion(latestVersion(p).layout.regionBounds)}</td>
                    <td className={!planner && waitingHours(p, now) > 72 ? 'text-bad' : ''}>{planner ? relTime(p.updatedAt, now) : waitingLabel(p, now)}</td>
                    <td><StatusBadge status={p.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="card">
          <h2>Status breakdown</h2>
          <div className="stack-bar" role="img" aria-label="Plans by status">
            {ORDER.map((s) => (count(s) ? <div key={s} className={`seg-${STATUS_META[s].tone}`} style={{ flex: count(s) }} title={`${STATUS_META[s].label}: ${count(s)}`} /> : null))}
          </div>
          <ul className="legend">
            {ORDER.map((s) => (
              <li key={s}>
                <span className={`legend-dot seg-${STATUS_META[s].tone}`} />
                <span>{STATUS_META[s].label}</span>
                <span className="legend-n">{count(s)} <span className="muted small">({total ? Math.round((count(s) / total) * 100) : 0}%)</span></span>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="card">
        <div className="card-head">
          <h2>Recent activity</h2>
          <Link to={`${base}/activity`} className="link">Full log</Link>
        </div>
        {audit.length === 0 && <p className="empty">No activity yet.</p>}
        <ul className="feed">
          {audit.slice(0, 8).map((e) => {
            const plan = plans.find((p) => p.id === e.planId)
            return (
              <li key={e.id}>
                <span className={`feed-dot feed-${e.action.toLowerCase().replace(/\s/g, '-')}`} aria-hidden="true" />
                <div>
                  <div>
                    <strong>{nameOf(e.actorId)}</strong> · {e.action}
                    {plan && (<> — <Link to={`${base}/plans/${plan.id}`} className="link">{plan.name}</Link></>)}
                  </div>
                  <div className="small muted">{relTime(e.at, now)}</div>
                </div>
              </li>
            )
          })}
        </ul>
      </section>
    </div>
  )
}

function Tile({ label, value, tone, to }: { label: string; value: number | string; tone: string; to?: string }) {
  const inner = (
    <>
      <div className="tile-value">{value}</div>
      <div className="tile-label">{label}</div>
    </>
  )
  return to ? <Link to={to} className={`tile tile-${tone}`}>{inner}</Link> : <div className={`tile tile-${tone}`}>{inner}</div>
}
