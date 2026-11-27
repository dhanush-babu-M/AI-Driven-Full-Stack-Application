import { useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import Toast from '../components/Toast';
import TopBar from '../components/TopBar';
import AgentAssistantModal from '../components/ai/AgentAssistantModal';
import ApprovalInboxModal from '../components/ai/ApprovalInboxModal';
import { useAuth } from '../context/AuthContext';
import { useAiInbox, useAiModalListeners } from '../hooks/useAiInbox';
import { useReminders } from '../hooks/useReminders';

export default function AppShell() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [inboxOpen, setInboxOpen] = useState(false);
  const [agentOpen, setAgentOpen] = useState(false);
  const [aiToast, setAiToast] = useState({ message: '', type: 'info' });
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const {
    pendingProposals,
    pendingCount,
    loading: inboxLoading,
    reloadInbox,
  } = useAiInbox(Boolean(user));

  useAiModalListeners({
    onOpenInbox: () => {
      reloadInbox();
      setInboxOpen(true);
    },
    onOpenAgent: () => setAgentOpen(true),
  });

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
          pendingProposalCount={pendingCount}
          onOpenAiInbox={() => {
            reloadInbox();
            setInboxOpen(true);
          }}
          onOpenAiAgent={() => setAgentOpen(true)}
        />
        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto">
          <Outlet />
        </main>
      </div>

      <ApprovalInboxModal
        open={inboxOpen}
        proposals={pendingProposals}
        loading={inboxLoading}
        onClose={() => setInboxOpen(false)}
        onToast={(message, type = 'info') => setAiToast({ message, type })}
      />

      <AgentAssistantModal
        open={agentOpen}
        onClose={() => setAgentOpen(false)}
      />

      <Toast
        message={aiToast.message || activeToast.message}
        type={aiToast.message ? aiToast.type : activeToast.type}
        onDismiss={() => {
          if (aiToast.message) setAiToast({ message: '', type: 'info' });
          else dismissToast();
        }}
      />
    </div>
  );
}
