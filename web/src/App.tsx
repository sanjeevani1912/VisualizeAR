import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useStore } from './storeContext'
import { DemoProvider } from './store'
import { LiveProvider } from './liveStore'
import { Layout } from './components/Layout'
import { Login } from './pages/Login'
import { Overview } from './pages/Overview'
import { PlanList } from './pages/PlanList'
import { PlanDetail } from './pages/PlanDetail'
import { Activity } from './pages/Activity'
import { NewPlan } from './pages/NewPlan'
import { Users } from './pages/Users'
import { Account } from './pages/Account'
import type { Role } from './types'

function Protected() {
  const { user, ready, base } = useStore()
  if (!ready) return <div className="login-wrap"><p className="muted">Loading…</p></div>
  if (!user) return <Navigate to={`${base}/login`} replace />
  // A password someone else set must be replaced before anything else is shown.
  if (user.mustChangePassword) return <Account forced />
  return <Layout />
}

/** Only the listed roles may see the page; anyone else is sent home. */
function RoleGate({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user, base } = useStore()
  if (!user || !roles.includes(user.role)) return <Navigate to={base || '/'} replace />
  return <>{children}</>
}

function Fallback() {
  const { base } = useStore()
  return <Navigate to={base || '/'} replace />
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="login" element={<Login />} />
      <Route element={<Protected />}>
        <Route index element={<Overview />} />
        <Route path="plans" element={<PlanList />} />
        <Route path="plans/new" element={<RoleGate roles={['planner']}><NewPlan /></RoleGate>} />
        <Route path="plans/:id" element={<PlanDetail />} />
        <Route path="activity" element={<Activity />} />
        <Route path="users" element={<RoleGate roles={['admin', 'superadmin']}><Users /></RoleGate>} />
        <Route path="account" element={<Account />} />
      </Route>
      <Route path="*" element={<Fallback />} />
    </Routes>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/demo/*" element={<DemoProvider><AppRoutes /></DemoProvider>} />
      <Route path="/*" element={<LiveProvider><AppRoutes /></LiveProvider>} />
    </Routes>
  )
}
