import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useStore } from '../store'
import { displayName } from '../data/mock'
import { OPEN_STATUSES } from '../util'

const ROLE_LABEL = { planner: 'Planner', reviewer: 'Reviewer', admin: 'Administrator', auditor: 'Auditor' } as const

export function Layout() {
  const { user, logout, plans, reset } = useStore()
  const nav = useNavigate()
  const pending = plans.filter((p) => OPEN_STATUSES.includes(p.status)).length
  if (!user) return null
  return (
    <div className="shell">
      <div className="banner" role="note">
        RESTRICTED · DEMONSTRATION DATA — all plans, people and sites shown are fictional
      </div>
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 32 32" width="22" height="22">
              <path d="M16 5l10 5.5v11L16 27 6 21.5v-11z" fill="none" stroke="currentColor" strokeWidth="2" />
              <circle cx="16" cy="16" r="2.6" fill="currentColor" />
            </svg>
          </div>
          <div>
            <div className="brand-name">DhruvAR</div>
            <div className="brand-sub">Command Dashboard</div>
          </div>
        </div>
        <nav className="nav" aria-label="Main">
          <NavLink to="/" end>
            Overview
          </NavLink>
          <NavLink to="/plans">
            Plans {pending > 0 && <span className="nav-count">{pending}</span>}
          </NavLink>
          <NavLink to="/activity">Activity log</NavLink>
        </nav>
        <div className="sidebar-foot">
          <div className="user-card">
            <div className="user-avatar" aria-hidden="true">
              {user.name.charAt(0)}
            </div>
            <div className="user-meta">
              <div className="user-name">{displayName(user)}</div>
              <div className="user-role">{ROLE_LABEL[user.role]}</div>
            </div>
          </div>
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => {
              logout()
              nav('/login')
            }}
          >
            Sign out
          </button>
          <button
            className="link-btn"
            onClick={() => {
              if (window.confirm('Reset all demo data to its starting state?')) reset()
            }}
          >
            Reset demo data
          </button>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
