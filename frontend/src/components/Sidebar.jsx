import { useEffect, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { format } from 'date-fns';
import { APP_NAME, APP_TAGLINE } from '../config/app';
import { useAuth } from '../context/AuthContext';

function NavIcon({ name }) {
  const props = {
    className: 'h-4 w-4 shrink-0',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: '2',
    'aria-hidden': 'true',
  };
  switch (name) {
    case 'home':
      return <svg {...props}><path strokeLinecap="round" strokeLinejoin="round" d="M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1v-9.5z" /></svg>;
    case 'planner':
      return <svg {...props}><rect x="3" y="4" width="18" height="17" rx="2" /><path strokeLinecap="round" d="M8 2v4M16 2v4M3 10h18" /></svg>;
    case 'calendar-small':
      return <svg className="h-3.5 w-3.5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><rect x="3" y="4" width="18" height="17" rx="2" /><path strokeLinecap="round" d="M8 2v4M16 2v4M3 10h18" /></svg>;
    case 'coding':
      return <svg {...props}><path strokeLinecap="round" strokeLinejoin="round" d="M8 9l-4 3 4 3M16 9l4 3-4 3M13 6l-2 12" /></svg>;
    case 'projects':
      return <svg {...props}><path strokeLinecap="round" strokeLinejoin="round" d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" /></svg>;
    case 'report':
      return <svg {...props}><path strokeLinecap="round" strokeLinejoin="round" d="M4 19h16M7 16V9M12 16V5M17 16v-4" /></svg>;
    case 'settings':
      return <svg {...props}><circle cx="12" cy="12" r="3" /><path strokeLinecap="round" strokeLinejoin="round" d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.08A1.65 1.65 0 0 0 10 3.09V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.08a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></svg>;
    case 'logout':
      return <svg {...props}><path strokeLinecap="round" strokeLinejoin="round" d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" /></svg>;
    default:
      return null;
  }
}

export default function Sidebar({ open, onClose, onLogoutClick }) {
  const location = useLocation();
  const { user } = useAuth();
  const isPlannerRoute = location.pathname.startsWith('/app/planner');
  const [plannerOpen, setPlannerOpen] = useState(isPlannerRoute);

  useEffect(() => {
    if (isPlannerRoute) {
      setPlannerOpen(true);
    }
  }, [isPlannerRoute]);

  const currentMonth = format(new Date(), 'yyyy-MM');

  const navLinkClass = ({ isActive }) =>
    `flex items-center gap-3 px-3.5 py-2.5 rounded-pill text-sm font-medium transition-colors duration-fast ${
      isActive
        ? 'bg-primary text-white shadow-sm'
        : 'text-body hover:bg-canvas hover:text-ink'
    }`;

  // Active planner child highlighted in light blue while parent Planner stays a filled blue pill
  const childLinkClass = ({ isActive }) =>
    `flex items-center gap-2.5 pl-9 pr-3.5 py-2 rounded-pill text-sm transition-colors duration-fast ${
      isActive
        ? 'bg-primary-soft text-primary font-semibold'
        : 'text-muted font-medium hover:bg-canvas hover:text-ink'
    }`;

  const content = (
    <div className="flex h-full flex-col justify-between bg-surface border-r border-border p-4 overflow-y-auto">
      <div className="space-y-6">
        {/* Brand Header */}
        <div className="flex items-center justify-between px-2 pt-1">
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-white font-bold text-base shadow-sm">
              P
            </div>
            <div>
              <div className="text-base font-bold text-ink leading-tight">{APP_NAME}</div>
              <div className="text-[11px] font-medium text-muted">{APP_TAGLINE}</div>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close navigation menu"
            className="lg:hidden rounded-lg p-1.5 text-muted hover:bg-canvas hover:text-ink"
          >
            ✕
          </button>
        </div>

        {/* Main Navigation */}
        <nav aria-label="Main Navigation" className="space-y-1.5">
          <NavLink to="/app/dashboard" className={navLinkClass} onClick={onClose}>
            <NavIcon name="home" />
            <span>Home</span>
          </NavLink>

          {/* Planner Expandable Group */}
          <div>
            <button
              type="button"
              onClick={() => setPlannerOpen((prev) => !prev)}
              aria-expanded={plannerOpen}
              aria-controls="planner-submenu"
              className={`w-full flex items-center justify-between gap-3 px-3.5 py-2.5 rounded-pill text-sm font-medium transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                isPlannerRoute
                  ? 'bg-primary text-white shadow-sm'
                  : 'text-body hover:bg-canvas hover:text-ink'
              }`}
            >
              <span className="flex items-center gap-3">
                <NavIcon name="planner" />
                <span>Planner</span>
              </span>
              <svg
                className={`h-4 w-4 transition-transform duration-base ${plannerOpen ? 'rotate-180' : ''}`}
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                aria-hidden="true"
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 9l6 6 6-6" />
              </svg>
            </button>

            <div
              id="planner-submenu"
              className={`grid transition-all duration-base ease-out ${
                plannerOpen
                  ? 'grid-rows-[1fr] opacity-100 mt-1'
                  : 'grid-rows-[0fr] opacity-0 invisible'
              }`}
            >
              <div className="overflow-hidden space-y-1">
                <NavLink
                  to="/app/planner/daily"
                  tabIndex={plannerOpen ? 0 : -1}
                  className={childLinkClass}
                  onClick={onClose}
                >
                  <NavIcon name="calendar-small" />
                  <span>Daily Planner</span>
                </NavLink>
                <NavLink
                  to="/app/planner/weekly"
                  tabIndex={plannerOpen ? 0 : -1}
                  className={childLinkClass}
                  onClick={onClose}
                >
                  <NavIcon name="calendar-small" />
                  <span>Weekly Planner</span>
                </NavLink>
                <NavLink
                  to="/app/planner/monthly"
                  tabIndex={plannerOpen ? 0 : -1}
                  className={childLinkClass}
                  onClick={onClose}
                >
                  <NavIcon name="calendar-small" />
                  <span>Monthly Planner</span>
                </NavLink>
              </div>
            </div>
          </div>

          <NavLink to="/app/coding" className={navLinkClass} onClick={onClose}>
            <NavIcon name="coding" />
            <span>Coding Activity</span>
          </NavLink>

          <NavLink to="/app/projects" className={navLinkClass} onClick={onClose}>
            <NavIcon name="projects" />
            <span>Projects and Applications</span>
          </NavLink>

          <NavLink to={`/app/reports/${currentMonth}`} className={navLinkClass} onClick={onClose}>
            <NavIcon name="report" />
            <span>Monthly Report</span>
          </NavLink>
        </nav>
      </div>

      {/* Bottom User Identity, Settings & Logout */}
      <div className="pt-4 border-t border-border space-y-1.5">
        {user && (
          <div className="px-3.5 py-2 mb-1 rounded-xl bg-canvas border border-border/70">
            <div className="text-sm font-semibold text-ink truncate">{user.fullName}</div>
            <div className="text-xs text-muted truncate">{user.email}</div>
          </div>
        )}

        <NavLink to="/app/settings" className={navLinkClass} onClick={onClose}>
          <NavIcon name="settings" />
          <span>Settings</span>
        </NavLink>

        <button
          type="button"
          onClick={() => {
            onClose?.();
            onLogoutClick?.();
          }}
          className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-pill text-sm font-medium text-body hover:bg-skipped-soft hover:text-skipped transition-colors duration-fast"
        >
          <NavIcon name="logout" />
          <span>Logout</span>
        </button>
      </div>
    </div>
  );

  return (
    <>
      {/* Desktop persistent sidebar (>= 1024px) */}
      <aside className="hidden lg:block lg:w-[260px] lg:shrink-0 lg:sticky lg:top-0 lg:h-screen">
        {content}
      </aside>

      {/* Mobile / Tablet slide-in drawer (< 1024px) */}
      <div
        className={`fixed inset-0 z-40 lg:hidden transition-opacity duration-base ${
          open ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
        }`}
      >
        <div className="fixed inset-0 bg-ink/40" onClick={onClose} aria-hidden="true" />
        <aside
          className={`fixed inset-y-0 left-0 w-[260px] shadow-drawer transition-transform duration-base ${
            open ? 'translate-x-0' : '-translate-x-full'
          }`}
        >
          {content}
        </aside>
      </div>
    </>
  );
}
