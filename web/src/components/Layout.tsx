import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useStore } from '../storeContext'
import { OPEN_STATUSES, initials } from '../util'
import { ARMY_LOGO, APP_LOGO } from '../branding'

const ROLE_LABEL = { planner: 'Planner', reviewer: 'Reviewer', admin: 'Administrator', auditor: 'Auditor', superadmin: 'Super administrator' } as const
const CLASSIFICATION = (import.meta.env.VITE_CLASSIFICATION_BANNER as string | undefined) ?? 'RESTRICTED'

export function Layout() {
  const { user, logout, plans, reset, mode, base, nameOf } = useStore()
  const nav = useNavigate()
  const pending = plans.filter((p) => OPEN_STATUSES.includes(p.status)).length
  if (!user) return null
  const planner = user.role === 'planner'
  return (
    <div className="shell">
      <div className={`banner ${mode === 'demo' ? 'banner-demo' : ''}`} role="note">
        {mode === 'demo'
          ? 'RESTRICTED · PRESENTATION DEMO — all plans, people and sites shown are fictional'
          : CLASSIFICATION}
      </div>
      <header className="topbar">
        <img className="logo-army" src={ARMY_LOGO} alt="Army logo" width={58} height={44} />
        <span className="topbar-sep" aria-hidden="true" />
        <img className="logo-app" src={APP_LOGO} alt="" width={40} height={40} />
        <div className="topbar-title">
          <div className="brand-name">DhruvAR</div>
          <div className="brand-sub">Command Dashboard</div>
        </div>
        <div className="topbar-user">
          <div className="user-avatar" aria-hidden="true">{initials(user.name)}</div>
          <div className="user-meta">
            <div className="user-name">{nameOf(user.id)}</div>
            <div className="user-role">{ROLE_LABEL[user.role]}</div>
          </div>
          <button
            className="btn btn-ghost btn-sm"
            onClick={async () => {
              await logout()
              nav(`${base}/login`)
            }}
          >
            Sign out
          </button>
        </div>
      </header>
      <aside className="sidebar">
        <nav className="nav" aria-label="Main">
          <NavLink to={base || '/'} end>
            Overview
          </NavLink>
          <NavLink to={`${base}/plans`}>
            {planner ? 'My plans' : 'Plans'} {!planner && pending > 0 && <span className="nav-count">{pending}</span>}
          </NavLink>
          {mode === 'live' && planner && <NavLink to="/plans/new">Submit a plan</NavLink>}
          <NavLink to={`${base}/activity`}>Activity log</NavLink>
          {mode === 'live' && (user.role === 'admin' || user.role === 'superadmin') && <NavLink to="/users">Users</NavLink>}
          {mode === 'live' && <NavLink to="/account">My account</NavLink>}
        </nav>
        <div className="sidebar-foot">
          {mode === 'demo' && (
            <>
              <button
                className="link-btn"
                onClick={() => {
                  if (window.confirm('Reset all demo data to its starting state?')) reset()
                }}
              >
                Reset demo data
              </button>
              <button className="link-btn" onClick={() => nav('/login')}>
                Exit demo
              </button>
            </>
          )}
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
