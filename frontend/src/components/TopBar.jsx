import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { APP_NAME } from '../config/app';
import { useAuth } from '../context/AuthContext';

function getInitials(fullName) {
  if (!fullName) return 'U';
  const cleaned = fullName.replace('(DEMO)', '').trim();
  const parts = cleaned.split(/\s+/).filter(Boolean);
  if (parts.length === 0) return 'U';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export default function TopBar({ onOpenSidebar }) {
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

      {/* Right: Notification bell + User chip dropdown */}
      <div className="flex items-center gap-2 sm:gap-3 shrink-0">
        {/* Bell icon with unread dot */}
        <div className="relative" ref={notifRef}>
          <button
            type="button"
            onClick={() => setNotifOpen((prev) => !prev)}
            aria-label="Notifications"
            aria-expanded={notifOpen}
            className="relative flex h-9 w-9 items-center justify-center rounded-full border border-border bg-surface text-body hover:bg-canvas hover:text-ink transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>
            <span
              className="absolute top-1.5 right-2 h-2 w-2 rounded-full bg-primary ring-2 ring-surface"
              aria-hidden="true"
            />
          </button>

          {notifOpen && (
            <div
              role="status"
              className="absolute right-0 mt-2 w-64 rounded-card border border-border bg-surface p-3.5 shadow-card-hover text-xs text-body z-40"
            >
              <p className="font-semibold text-ink">Notifications</p>
              <p className="mt-1 text-muted">
                No new alerts yet. Daily reminders will appear here in a later phase.
              </p>
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
