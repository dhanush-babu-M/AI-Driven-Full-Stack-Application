import { Link } from 'react-router-dom';
import PageHeader from './PageHeader';

const PLANNER_MODES = [
  {
    title: 'Daily',
    subtitle: 'Plan your day, set tasks, track progress',
    to: '/app/planner/daily',
    icon: (
      <svg className="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
        <rect x="3" y="4" width="18" height="17" rx="2" />
        <path strokeLinecap="round" d="M8 2v4M16 2v4M3 10h18M9 15l2 2 4-4" />
      </svg>
    ),
  },
  {
    title: 'Weekly',
    subtitle: 'View weekly goals and activities',
    to: '/app/planner/weekly',
    icon: (
      <svg className="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
        <rect x="3" y="4" width="18" height="17" rx="2" />
        <path strokeLinecap="round" d="M8 2v4M16 2v4M3 10h18M7 14h4M13 14h4M7 17h10" />
      </svg>
    ),
  },
  {
    title: 'Monthly',
    subtitle: 'Plan your month, build consistency',
    to: '/app/planner/monthly',
    icon: (
      <svg className="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
        <rect x="3" y="4" width="18" height="17" rx="2" />
        <path strokeLinecap="round" d="M8 2v4M16 2v4M3 10h18" />
        <circle cx="8" cy="14" r="1" fill="currentColor" />
        <circle cx="12" cy="14" r="1" fill="currentColor" />
        <circle cx="16" cy="14" r="1" fill="currentColor" />
      </svg>
    ),
  },
];

export default function PlannerChooser() {
  return (
    <div className="space-y-6">
      <PageHeader title="Planner" breadcrumb="Planner" />

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {PLANNER_MODES.map((mode) => (
          <Link
            key={mode.title}
            to={mode.to}
            className="group bg-surface rounded-card-lg shadow-card border border-border/80 p-6 flex flex-col justify-between gap-5 hover:shadow-card-hover hover:border-primary/40 transition-all duration-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <div>
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-soft text-primary group-hover:bg-primary group-hover:text-white transition-colors duration-fast">
                {mode.icon}
              </div>
              <h2 className="mt-4 text-lg font-bold text-ink">{mode.title}</h2>
              <p className="mt-1.5 text-sm text-muted leading-relaxed">{mode.subtitle}</p>
            </div>

            <span className="inline-flex items-center gap-1 text-sm font-semibold text-primary">
              <span>Open {mode.title}</span>
              <span aria-hidden="true" className="transition-transform duration-fast group-hover:translate-x-0.5">→</span>
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}
