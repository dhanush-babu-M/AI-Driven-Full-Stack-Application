import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { APP_NAME } from '../config/app';
import { useAuth } from '../context/AuthContext';
import { formatTime12h } from '../utils/taskCategories';

function getInitials(fullName) {
  if (!fullName) return 'U';
  const cleaned = fullName.replace('(DEMO)', '').trim();
  const parts = cleaned.split(/\s+/).filter(Boolean);
  if (parts.length === 0) return 'U';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export default function TopBar({
  onOpenSidebar,
  alerts = [],
  upcomingReminders = [],
  unreadCount = 0,
  onMarkAllRead,
  pendingProposalCount = 0,
  onOpenAiInbox,
  onOpenAiAgent,
}) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [menuOpen, setMenuOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const menuRef = useRef(null);
  const notifRef = useRef(null);

  useEffect(() => {
    const handleOutsideClick = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setMenuOpen(false);
      }
      if (notifRef.current && !notifRef.current.contains(event.target)) {
        setNotifOpen(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, []);

  const handleLogout = async () => {
    setMenuOpen(false);
    await logout();
    navigate('/login', { replace: true });
  };

  const fullName = user?.fullName || APP_NAME;
  const roleLabel = user?.targetRole || 'Fresher';
  const initials = getInitials(user?.fullName);

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-2 sm:gap-4 border-b border-border bg-surface/95 backdrop-blur px-3 sm:px-6 lg:px-8">
      {/* Left: Mobile drawer toggle + Search field */}
      <div className="flex items-center gap-2 sm:gap-3 min-w-0 flex-1">
        <button
          type="button"
          onClick={onOpenSidebar}
          aria-label="Open navigation menu"
          className="inline-flex lg:hidden shrink-0 items-center justify-center rounded-xl border border-border bg-surface p-2 text-ink hover:bg-canvas transition-colors duration-fast"
        >
          <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>

        <div className="relative flex-1 max-w-[200px] sm:max-w-xs md:max-w-sm">
          <svg
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            aria-hidden="true"
          >
            <circle cx="11" cy="11" r="8" />
            <path strokeLinecap="round" d="m21 21-4.35-4.35" />
          </svg>
          <input
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search anything..."
            aria-label="Search anything"
            className="w-full rounded-pill border border-border bg-canvas pl-9 pr-3 py-1.5 text-xs sm:text-sm text-ink placeholder:text-slate-500 focus:outline-none focus:bg-surface focus:border-primary focus:ring-2 focus:ring-primary/20 transition-colors duration-fast"
          />
        </div>
      </div>

      {/* Right: AI Assistant + Approval Inbox + Notification bell + User chip dropdown */}
      <div className="flex items-center gap-2 sm:gap-2.5 shrink-0">
        {onOpenAiAgent && (
          <button
            type="button"
            onClick={onOpenAiAgent}
            className="hidden sm:inline-flex items-center gap-1 rounded-pill border border-border bg-surface px-3 py-1.5 text-xs font-semibold text-ink hover:bg-primary-soft hover:text-primary transition-colors duration-fast"
          >
            <span aria-hidden="true">✨</span>
            <span>Ask AI</span>
          </button>
        )}

        {onOpenAiInbox && (
          <button
            type="button"
            onClick={onOpenAiInbox}
            aria-label={`AI Approval Inbox${pendingProposalCount > 0 ? ` (${pendingProposalCount} pending)` : ''}`}
            className="relative inline-flex items-center gap-1.5 rounded-pill border border-border bg-surface px-3 py-1.5 text-xs font-semibold text-ink hover:bg-canvas transition-colors duration-fast"
          >
            <span>Inbox</span>
            {pendingProposalCount > 0 && (
              <span className="inline-flex min-w-[18px] h-[18px] items-center justify-center rounded-full bg-primary px-1 text-[10px] font-bold text-white">
                {pendingProposalCount}
              </span>
            )}
          </button>
        )}

        {/* Bell icon with unread dot & count */}
        <div className="relative" ref={notifRef}>
          <button
            type="button"
            onClick={() => setNotifOpen((prev) => !prev)}
            aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ''}`}
            aria-expanded={notifOpen}
            className="relative flex h-9 w-9 items-center justify-center rounded-full border border-border bg-surface text-body hover:bg-canvas hover:text-ink transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>
            {unreadCount > 0 ? (
              <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-primary text-[10px] font-bold text-white flex items-center justify-center ring-2 ring-surface">
                {unreadCount}
              </span>
            ) : (
              upcomingReminders.length > 0 && (
                <span
                  className="absolute top-1.5 right-2 h-2 w-2 rounded-full bg-primary ring-2 ring-surface"
                  aria-hidden="true"
                />
              )
            )}
          </button>

          {notifOpen && (
            <div
              role="region"
              aria-label="Reminders list"
              className="absolute right-0 mt-2 w-72 sm:w-80 rounded-card border border-border bg-surface p-3.5 shadow-card-hover text-xs text-body z-40 space-y-3"
            >
              <div className="flex items-center justify-between gap-2 pb-2 border-b border-border/70">
                <span className="font-semibold text-ink">Today&apos;s Reminders</span>
                {unreadCount > 0 && (
                  <button
                    type="button"
                    onClick={onMarkAllRead}
                    className="text-[11px] font-semibold text-primary hover:underline"
                  >
                    Mark all read
                  </button>
                )}
              </div>

              {alerts.length === 0 && upcomingReminders.length === 0 ? (
                <p className="text-muted py-2">
                  No due, missed, or upcoming reminders right now.
                </p>
              ) : (
                <ul className="space-y-2 max-h-60 overflow-y-auto">
                  {alerts.map((a) => (
                    <li
                      key={a.id}
                      className={`rounded-xl border p-2.5 ${
                        a.read
                          ? 'border-border/70 bg-canvas/60'
                          : 'border-primary/30 bg-primary-soft/60'
                      }`}
                    >
                      <Link
                        to={a.linkPath || `/app/planner/daily?date=${a.taskDate}`}
                        onClick={() => setNotifOpen(false)}
                        className="block"
                      >
                        <span className="font-semibold text-ink">
                          {a.isMissed ? 'Missed: ' : 'Due: '}
                          {a.title}
                        </span>
                        <span className="block text-[11px] text-muted mt-0.5">
                          {a.taskDate} at {a.timeLabel}
                        </span>
                      </Link>
                    </li>
                  ))}
                  {upcomingReminders
                    .filter((u) => {
                      const uKey = u.followupId ? `FOLLOWUP:${u.followupId}:${u.reminderAtUtc}` : `TASK:${u.taskId}:${u.reminderAtUtc}`;
                      return !alerts.some((a) => a.id === uKey);
                    })
                    .map((u) => (
                      <li
                        key={u.followupId ? `followup-${u.followupId}` : `task-${u.taskId}`}
                        className="rounded-xl border border-border/70 bg-canvas/50 p-2.5"
                      >
                        <Link
                          to={u.linkPath || `/app/planner/daily?date=${u.taskDate}`}
                          onClick={() => setNotifOpen(false)}
                          className="block"
                        >
                          <span className="font-semibold text-ink">Upcoming: {u.title}</span>
                          <span className="block text-[11px] text-muted mt-0.5">
                            {u.taskDate} · {u.startTime ? formatTime12h(u.startTime) : ''}
                            {u.inQuietHours ? ' (Quiet hours)' : ''}
                          </span>
                        </Link>
                      </li>
                    ))}
                </ul>
              )}

              <div className="pt-2 border-t border-border/70 flex justify-between items-center">
                <Link
                  to="/app/settings"
                  onClick={() => setNotifOpen(false)}
                  className="text-[11px] font-semibold text-primary hover:underline"
                >
                  Notification settings
                </Link>
                <span className="text-[11px] text-muted">Open-tab only</span>
              </div>
            </div>
          )}
        </div>

        {/* User chip */}
        <div className="relative" ref={menuRef}>
          <button
            type="button"
            onClick={() => setMenuOpen((prev) => !prev)}
            aria-expanded={menuOpen}
            aria-haspopup="menu"
            className="flex items-center gap-2 rounded-pill border border-border bg-surface pl-1.5 pr-2.5 py-1 hover:bg-canvas transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">
              {initials}
            </span>
            <span className="hidden sm:block text-left leading-tight max-w-[130px]">
              <span className="block text-xs font-semibold text-ink truncate">{fullName}</span>
              <span className="block text-[11px] text-muted truncate">{roleLabel}</span>
            </span>
            <svg
              className={`h-3.5 w-3.5 text-muted transition-transform duration-fast ${menuOpen ? 'rotate-180' : ''}`}
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              aria-hidden="true"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="m6 9 6 6 6-6" />
            </svg>
          </button>

          {menuOpen && (
            <div
              role="menu"
              aria-label="User menu"
              className="absolute right-0 mt-2 w-48 rounded-card border border-border bg-surface py-1.5 shadow-card-hover z-40"
            >
              <div className="px-3.5 py-2 border-b border-border/70 sm:hidden">
                <p className="text-xs font-semibold text-ink truncate">{fullName}</p>
                <p className="text-[11px] text-muted">{roleLabel}</p>
              </div>
              <Link
                to="/app/settings"
                role="menuitem"
                onClick={() => setMenuOpen(false)}
                className="block px-3.5 py-2 text-sm text-body hover:bg-canvas hover:text-ink"
              >
                Profile
              </Link>
              <Link
                to="/app/settings"
                role="menuitem"
                onClick={() => setMenuOpen(false)}
                className="block px-3.5 py-2 text-sm text-body hover:bg-canvas hover:text-ink"
              >
                Settings
              </Link>
              <button
                type="button"
                role="menuitem"
                onClick={handleLogout}
                className="w-full text-left px-3.5 py-2 text-sm text-skipped hover:bg-skipped-soft font-medium"
              >
                Logout
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
