import { Navigate, Outlet } from 'react-router-dom';
import { WELCOME_FLAG_KEY } from '../config/app';
import { useAuth } from '../context/AuthContext';

function AuthLoadingScreen() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="min-h-screen bg-canvas flex items-center justify-center p-6 text-sm font-medium text-slate-600"
    >
      Checking your session...
    </div>
  );
}

export function ProtectedRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <AuthLoadingScreen />;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return children || <Outlet />;
}

export function PublicOnlyRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <AuthLoadingScreen />;
  }

  if (user) {
    const target =
      sessionStorage.getItem(WELCOME_FLAG_KEY) === '1'
        ? '/welcome'
        : '/app/dashboard';
    return <Navigate to={target} replace />;
  }

  return children || <Outlet />;
}
