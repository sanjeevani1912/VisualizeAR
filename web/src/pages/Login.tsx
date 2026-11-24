import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useStore } from '../storeContext'
import { DEMO_LOGINS, displayName } from '../data/mock'

const DESC = {
  reviewer: 'Review submitted plans and approve, reject or request changes.',
  admin: 'View everything. Cannot approve unless also a reviewer.',
  auditor: 'Read-only access to plans and the full audit log.',
  planner: '',
  superadmin: 'Manage administrators as well as users.',
} as const

function Mark() {
  return (
    <div className="brand-mark big" aria-hidden="true">
      <svg viewBox="0 0 32 32" width="30" height="30">
        <path d="M16 5l10 5.5v11L16 27 6 21.5v-11z" fill="none" stroke="currentColor" strokeWidth="2" />
        <circle cx="16" cy="16" r="2.6" fill="currentColor" />
      </svg>
    </div>
  )
}

export function Login() {
  const { user, login, mode, base, ready } = useStore()
  const nav = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  if (!ready) return <div className="login-wrap"><p className="muted">Loading…</p></div>
  if (user) return <Navigate to={base || '/'} replace />

  if (mode === 'demo') {
    return (
      <div className="login-wrap">
        <div className="login-card">
          <Mark />
          <h1>DhruvAR Command Dashboard</h1>
          <p className="muted">Tactical plan review and approval</p>
          <div className="banner-inline">RESTRICTED · PRESENTATION DEMO</div>
          <p className="small muted">Demo sign-in: choose an account. All data is fictional and nothing leaves this browser.</p>
          <ul className="login-list">
            {DEMO_LOGINS.map((u) => (
              <li key={u.id}>
                <button
                  className="login-row"
                  onClick={async () => {
                    await login(u.id)
                    nav(base || '/')
                  }}
                >
                  <span className="user-avatar" aria-hidden="true">{u.name.charAt(0)}</span>
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
          <p className="small"><Link to="/login" className="link">← Back to the live sign-in</Link></p>
        </div>
      </div>
    )
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    const err = await login(username.trim(), password)
    setBusy(false)
    if (err) setError(err)
    else nav('/')
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <Mark />
        <h1>DhruvAR Command Dashboard</h1>
        <p className="muted">Sign in to review and manage tactical plans</p>
        <label className="field left">
          <span>Username</span>
          <input autoFocus autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label className="field left">
          <span>Password</span>
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="btn btn-primary wide" disabled={busy || !username || !password}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="small muted foot-note">
          Access is logged. Accounts are issued by an administrator.
          <br />
          <Link to="/demo/login" className="link">View the presentation demo</Link>
        </p>
      </form>
    </div>
  )
}
