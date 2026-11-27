export const PROJECT_STATUS_META = {
  PLANNED: {
    label: 'Planned',
    classes: 'bg-slate-100 text-slate-700 border-slate-200',
    dotClass: 'bg-slate-400',
  },
  CURRENT: {
    label: 'Current',
    classes: 'bg-primary-soft text-primary border-primary/25',
    dotClass: 'bg-primary',
  },
  COMPLETED: {
    label: 'Completed',
    classes: 'bg-completed-soft text-completed border-completed/25',
    dotClass: 'bg-completed',
  },
  PAUSED: {
    label: 'Paused',
    classes: 'bg-pending-soft text-amber-800 border-pending/30',
    dotClass: 'bg-pending',
  },
};

export const PROJECT_STATUS_OPTIONS = [
  { value: 'PLANNED', label: 'Planned' },
  { value: 'CURRENT', label: 'Current' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'PAUSED', label: 'Paused' },
];

export const PROJECT_ICON_OPTIONS = [
  { value: 'code', label: 'Code' },
  { value: 'rocket', label: 'Launch' },
  { value: 'layers', label: 'Stack' },
  { value: 'database', label: 'Data' },
  { value: 'sparkles', label: 'AI' },
  { value: 'terminal', label: 'CLI' },
];

export function getProjectStatusMeta(status) {
  const key = String(status || 'PLANNED').toUpperCase();
  if (key === 'IN_PROGRESS' || key === 'ACTIVE') {
    return PROJECT_STATUS_META.CURRENT;
  }
  if (key === 'PENDING') {
    return PROJECT_STATUS_META.PLANNED;
  }
  return PROJECT_STATUS_META[key] || PROJECT_STATUS_META.PLANNED;
}

export function ProjectStatusChip({ status }) {
  const meta = getProjectStatusMeta(status);
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-pill border px-2.5 py-0.5 text-xs font-semibold ${meta.classes}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${meta.dotClass}`} aria-hidden="true" />
      {meta.label}
    </span>
  );
}

export function ProjectIconTile({ icon = 'code', size = 'md' }) {
  const dim = size === 'lg' ? 'h-12 w-12 rounded-2xl' : 'h-10 w-10 rounded-xl';
  const svgDim = size === 'lg' ? 'h-6 w-6' : 'h-5 w-5';

  const renderGlyph = () => {
    switch (icon) {
      case 'rocket':
        return (
          <svg className={svgDim} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5" />
          </svg>
        );
      case 'layers':
        return (
          <svg className={svgDim} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
          </svg>
        );
      case 'database':
        return (
          <svg className={svgDim} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <ellipse cx="12" cy="5" rx="9" ry="3" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
          </svg>
        );
      case 'sparkles':
        return (
          <svg className={svgDim} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 3l1.8 4.2L18 9l-4.2 1.8L12 15l-1.8-4.2L6 9l4.2-1.8L12 3zM5 18l.8 1.7L7.5 20.5l-1.7.8L5 23l-.8-1.7-1.7-.8 1.7-.8L5 18zM19 14l.8 1.7 1.7.8-1.7.8-.8 1.7-.8-1.7-1.7-.8 1.7-.8L19 14z" />
          </svg>
        );
      case 'terminal':
        return (
          <svg className={svgDim} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 17l6-6-6-6M12 19h8" />
          </svg>
        );
      case 'code':
      default:
        return (
          <svg className={svgDim} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M16 18l6-6-6-6M8 6l-6 6 6 6" />
          </svg>
        );
    }
  };

  return (
    <div
      className={`flex ${dim} shrink-0 items-center justify-center bg-primary-soft text-primary border border-primary/15`}
    >
      {renderGlyph()}
    </div>
  );
}
