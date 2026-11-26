const TAG_STYLES = {
  MANUAL: 'bg-primary-soft text-primary border-primary/20',
  IMPORTED: 'bg-completed-soft text-completed border-completed/20',
  CALCULATED: 'bg-slate-100 text-ink border-border',
  RULE: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  AI: 'bg-pending-soft text-amber-700 border-pending/30',
  DEMO: 'bg-skipped-soft text-skipped border-skipped/20',
};

export default function SourceTag({ source = 'MANUAL' }) {
  const key = String(source).toUpperCase();
  const styles = TAG_STYLES[key] || TAG_STYLES.MANUAL;

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 text-[11px] font-semibold tracking-wide uppercase rounded-pill border ${styles}`}
    >
      {key}
    </span>
  );
}
