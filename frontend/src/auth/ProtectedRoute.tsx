import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './AuthContext'

export function ProtectedRoute() {
  const { loading, user } = useAuth()
  const location = useLocation()

  if (loading) {
    return <p role="status">세션을 확인하고 있습니다.</p>
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}
