import { Navigate, useNavigate } from 'react-router-dom'
import { useStore } from '../store'
import { DEMO_LOGINS, displayName } from '../data/mock'

const DESC = {
  reviewer: 'Review submitted plans and approve, reject or request changes.',
  admin: 'View everything. Cannot approve unless also a reviewer.',
  auditor: 'Read-only access to plans and the full audit log.',
  planner: '',
} as const

export function Login() {
  const { user, login } = useStore()
  const nav = useNavigate()
  if (user) return <Navigate to="/" replace />
  return (
    <div className="login-wrap">
      <div className="login-card">
        <div className="brand-mark big" aria-hidden="true">
          <svg viewBox="0 0 32 32" width="30" height="30">
            <path d="M16 5l10 5.5v11L16 27 6 21.5v-11z" fill="none" stroke="currentColor" strokeWidth="2" />
            <circle cx="16" cy="16" r="2.6" fill="currentColor" />
          </svg>
        </div>
        <h1>DhruvAR Command Dashboard</h1>
        <p className="muted">Tactical plan review and approval</p>
        <div className="banner-inline">RESTRICTED · DEMONSTRATION BUILD</div>
        <p className="small muted">
          Demo sign-in: choose an account. A production build would authenticate against the organisation’s identity system with
          multi-factor sign-in.
        </p>
        <ul className="login-list">
          {DEMO_LOGINS.map((u) => (
            <li key={u.id}>
              <button
                className="login-row"
                onClick={() => {
                  login(u.id)
                  nav('/')
                }}
              >
                <span className="user-avatar" aria-hidden="true">
                  {u.name.charAt(0)}
                </span>
                <span className="login-meta">
                  <strong>{displayName(u)}</strong>
                  <span className="small muted">
                    {u.role === 'admin' ? 'Administrator' : u.role === 'reviewer' ? 'Reviewer' : 'Auditor'} — {DESC[u.role]}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
