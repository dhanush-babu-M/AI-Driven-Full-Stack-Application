export default function ProgressBar({ value = 0, label, colorClass = 'bg-primary' }) {
  const clamped = Math.max(0, Math.min(100, Number(value) || 0));

  return (
    <div className="w-full space-y-1.5">
      {label && (
        <div className="flex items-center justify-between text-xs font-medium">
          <span className="text-body">{label}</span>
          <span className="text-ink font-semibold">{Math.round(clamped)}%</span>
        </div>
      )}
      <div
        className="w-full h-2.5 rounded-pill bg-slate-200/80 overflow-hidden"
        role="progressbar"
        aria-valuenow={Math.round(clamped)}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className={`h-full rounded-pill transition-all duration-base ${colorClass}`}
          style={{ width: `${clamped}%` }}
        />
      </div>
    </div>
  );
}
