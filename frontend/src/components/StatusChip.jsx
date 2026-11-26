const STATUS_STYLES = {
  completed: 'bg-completed-soft text-completed border-completed/20',
  pending: 'bg-pending-soft text-amber-700 border-pending/30',
  skipped: 'bg-skipped-soft text-skipped border-skipped/20',
  unrecorded: 'bg-slate-100 text-muted border-border',
};

export default function StatusChip({ status = 'pending', label }) {
  const key = String(status).toLowerCase();
  const styles = STATUS_STYLES[key] || STATUS_STYLES.pending;
  const displayLabel = label || key.charAt(0).toUpperCase() + key.slice(1);

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium rounded-pill border ${styles}`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true" />
      {displayLabel}
    </span>
  );
}
