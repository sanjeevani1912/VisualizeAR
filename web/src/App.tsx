import { Navigate, Route, Routes } from 'react-router-dom'
import { StoreProvider, useStore } from './store'
import { Layout } from './components/Layout'
import { Login } from './pages/Login'
import { Overview } from './pages/Overview'
import { PlanList } from './pages/PlanList'
import { PlanDetail } from './pages/PlanDetail'
import { Activity } from './pages/Activity'

function Protected() {
  const { user } = useStore()
  if (!user) return <Navigate to="/login" replace />
  return <Layout />
}

export default function App() {
  return (
    <StoreProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<Protected />}>
          <Route index element={<Overview />} />
          <Route path="plans" element={<PlanList />} />
          <Route path="plans/:id" element={<PlanDetail />} />
          <Route path="activity" element={<Activity />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </StoreProvider>
  )
}
