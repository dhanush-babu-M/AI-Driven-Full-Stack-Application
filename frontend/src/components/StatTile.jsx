import SourceTag from './SourceTag';

export default function StatTile({ label, value, hint, source, className = '' }) {
  return (
    <div
      className={`bg-surface rounded-card shadow-card border border-border/70 p-5 flex flex-col justify-between gap-3 ${className}`}
    >
      <div className="flex items-start justify-between gap-2">
        <span className="text-xs font-semibold uppercase tracking-wider text-muted">{label}</span>
        {source && <SourceTag source={source} />}
      </div>
      <div className="text-2xl font-bold text-ink tracking-tight">{value}</div>
      {hint && <p className="text-xs text-muted">{hint}</p>}
    </div>
  );
}
