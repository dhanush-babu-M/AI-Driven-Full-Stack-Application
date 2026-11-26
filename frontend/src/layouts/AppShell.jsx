import { useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import Toast from '../components/Toast';
import TopBar from '../components/TopBar';
import { useAuth } from '../context/AuthContext';
import { useReminders } from '../hooks/useReminders';

export default function AppShell() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { logout } = useAuth();
  const navigate = useNavigate();

  const {
    upcomingReminders,
    alerts,
    unreadCount,
    activeToast,
    dismissToast,
    markAllRead,
  } = useReminders();

  const handleLogoutClick = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen flex bg-canvas text-body">
      <Sidebar
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onLogoutClick={handleLogoutClick}
      />

      <div className="flex-1 min-w-0 flex flex-col">
        <TopBar
          onOpenSidebar={() => setDrawerOpen(true)}
          alerts={alerts}
          upcomingReminders={upcomingReminders}
          unreadCount={unreadCount}
          onMarkAllRead={markAllRead}
        />
        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto">
          <Outlet />
        </main>
      </div>

      <Toast
        message={activeToast.message}
        type={activeToast.type}
        onDismiss={dismissToast}
      />
    </div>
  );
}
