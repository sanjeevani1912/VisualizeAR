import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useStore } from '../storeContext'
import { fullDate } from '../util'
import { AuditVerify } from '../components/AuditVerify'

export function Activity() {
  const { audit, plans, nameOf, base, mode, user } = useStore()
  const [action, setAction] = useState('all')
  const [q, setQ] = useState('')
  const actions = useMemo(() => [...new Set(audit.map((e) => e.action))], [audit])
  const rows = audit.filter((e) => {
    if (action !== 'all' && e.action !== action) return false
    const needle = q.trim().toLowerCase()
    if (!needle) return true
    const plan = plans.find((p) => p.id === e.planId)
    return `${e.detail} ${nameOf(e.actorId)} ${plan?.name ?? ''} ${plan?.code ?? ''}`.toLowerCase().includes(needle)
  })
  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Activity log</h1>
          <p className="muted">Append-only record of submissions, decisions and comments. Entries cannot be edited.</p>
        </div>
      </header>
      {mode === 'live' && (user?.role === 'admin' || user?.role === 'superadmin' || user?.role === 'auditor') && <AuditVerify />}
      <section className="card filters">
        <label className="field grow">
          <span>Search</span>
          <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Person, plan or text" />
        </label>
        <label className="field">
          <span>Action</span>
          <select value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="all">All actions</option>
            {actions.map((a) => (
              <option key={a} value={a}>{a}</option>
            ))}
          </select>
        </label>
      </section>
      <section className="card flush">
        {rows.length === 0 ? (
          <p className="empty">No matching entries.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr><th>When</th><th>Who</th><th>Action</th><th>Plan</th><th>Detail</th></tr>
              </thead>
              <tbody>
                {rows.map((e) => {
                  const plan = plans.find((p) => p.id === e.planId)
                  return (
                    <tr key={e.id}>
                      <td className="nowrap">{fullDate(e.at)}</td>
                      <td>{nameOf(e.actorId)}</td>
                      <td><span className={`chip chip-${e.action.toLowerCase().replace(/\s/g, '-')}`}>{e.action}</span></td>
                      <td>{plan ? <Link to={`${base}/plans/${plan.id}`} className="link">{plan.code}</Link> : '–'}</td>
                      <td className="detail-cell">{e.detail}</td>
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
