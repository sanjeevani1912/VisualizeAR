import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useStore } from '../storeContext'
import { ARMY_LOGO, APP_LOGO_LARGE, LOGIN_PHOTO } from '../branding'
import { DEMO_LOGINS, displayName } from '../data/mock'
import './Login.css'

const DESC = {
  reviewer: 'Review submitted plans and approve, reject or request changes.',
  admin: 'View everything. Cannot approve unless also a reviewer.',
  auditor: 'Read-only access to plans and the full audit log.',
  planner: '',
  superadmin: 'Manage administrators as well as users.',
} as const

const CLASSIFICATION = (import.meta.env.VITE_CLASSIFICATION_BANNER as string | undefined) ?? 'RESTRICTED'

// Material icon paths (inline so the page needs no icon library).
const I = {
  map: 'M20.5 3l-.16.03L15 5.1 9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5zM15 19l-6-2.11V5l6 2.11V19z',
  check: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  cube: 'M18.25 7.6l-5.5-3.18a1.49 1.49 0 00-1.5 0L5.75 7.6c-.46.27-.75.76-.75 1.3v6.35c0 .54.29 1.03.75 1.3l5.5 3.18c.46.27 1.04.27 1.5 0l5.5-3.18c.46-.27.75-.76.75-1.3V8.9c0-.54-.29-1.03-.75-1.3zM7 14.96v-4.62l4 2.32v4.61l-4-2.31zm5-4.03L8 8.61l4-2.31 4 2.31-4 2.32zm1 6.34v-4.61l4-2.32v4.62l-4 2.31z',
  user: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
  lock: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM9 6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9V6zm3 11c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z',
  eye: 'M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z',
  eyeOff:
    'M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46A11.8 11.8 0 001 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z',
}

function Icon({ d }: { d: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d={d} />
    </svg>
  )
}

/** Branded sign-in page: field photo on the left; army and DhruvAR logos above the sign-in card on the right. */
function LoginShell({ banner, children }: { banner: string; children: ReactNode }) {
  return (
    <div className="lp">
      <div className="banner" role="note">{banner}</div>
      <div className="lp-split">
        <section className="lp-hero">
          <img
            className="lp-photo"
            src={LOGIN_PHOTO}
            width={877}
            height={521}
            alt="Soldiers planning a camp layout on a tablet, with the AR overlay of tents and vehicles on the ground"
          />
          <div>
            <div className="lp-tag">Field planning · Augmented reality</div>
            <h2>Plan the camp on the map. See it on the ground.</h2>
            <p>Lay out tents, vehicles and antennas with real distances, send the plan for approval, then walk the site in AR.</p>
            <ul className="lp-feats">
              <li><Icon d={I.map} />Plan on the map</li>
              <li><Icon d={I.check} />Review &amp; approve</li>
              <li><Icon d={I.cube} />View on site in AR</li>
            </ul>
          </div>
        </section>
        <section className="lp-side">
          <div className="lp-corner">
            <figure>
              <img className="lp-army" src={ARMY_LOGO} alt="Indian Army logo" width={95} height={72} />
              <figcaption>Indian Army</figcaption>
            </figure>
            <figure>
              <img className="lp-app" src={APP_LOGO_LARGE} alt="DhruvAR logo" width={72} height={72} />
              <figcaption>DhruvAR</figcaption>
            </figure>
          </div>
          {children}
        </section>
      </div>
      <footer className="lp-foot">
        <span>Indian Army · For official use only</span>
        <span>DhruvAR v1.0</span>
      </footer>
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
  const [showPw, setShowPw] = useState(false)
  if (!ready) return <div className="login-wrap"><p className="muted">Loading…</p></div>
  if (user) return <Navigate to={base || '/'} replace />

  if (mode === 'demo') {
    return (
      <LoginShell banner="RESTRICTED · PRESENTATION DEMO">
        <div className="login-card lp-card">
          <h1>DhruvAR</h1>
          <p className="login-sub">Command Dashboard</p>
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
      </LoginShell>
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
    <LoginShell banner={CLASSIFICATION}>
      <form className="login-card lp-card" onSubmit={submit}>
        <h1>DhruvAR</h1>
        <p className="login-sub">Command Dashboard</p>
        <p className="muted">Sign in to review and manage tactical plans</p>
        <label className="field left">
          <span>Username</span>
          <span className="lp-inp">
            <Icon d={I.user} />
            <input autoFocus autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
          </span>
        </label>
        <label className="field left">
          <span>Password</span>
          <span className="lp-inp">
            <Icon d={I.lock} />
            <input
              type={showPw ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <button
              type="button"
              className="lp-eye"
              onClick={() => setShowPw((v) => !v)}
              aria-label={showPw ? 'Hide password' : 'Show password'}
              aria-pressed={showPw}
            >
              <Icon d={showPw ? I.eyeOff : I.eye} />
            </button>
          </span>
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="btn btn-primary wide" disabled={busy || !username || !password}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="small muted foot-note">
          Access is logged. Accounts are issued by an administrator.
          <br />
          <Link to="/demo/login" className="link">View the presentation demo</Link>
        </p>
      </form>
    </LoginShell>
  )
}
