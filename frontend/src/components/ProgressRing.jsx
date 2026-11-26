import { useState } from 'react';

export default function ProgressRing({
  percentage = 0,
  size = 96,
  strokeWidth = 8,
  label,
  formulaText,
  color = 'var(--color-primary)',
}) {
  const [showFormula, setShowFormula] = useState(false);
  const clamped = Math.max(0, Math.min(100, Number(percentage) || 0));
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (clamped / 100) * circumference;

  return (
    <div className="inline-flex flex-col items-center gap-2 relative">
      <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
        <svg width={size} height={size} className="-rotate-90" aria-hidden="true">
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="transparent"
            stroke="var(--color-border)"
            strokeWidth={strokeWidth}
          />
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="transparent"
            stroke={color}
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            className="transition-all duration-base"
          />
        </svg>
        <span className="absolute text-sm font-bold text-ink">{Math.round(clamped)}%</span>
      </div>

      {label && <span className="text-xs font-medium text-body text-center">{label}</span>}

      {formulaText && (
        <div className="relative">
          <button
            type="button"
            onClick={() => setShowFormula((prev) => !prev)}
            className="text-[11px] font-medium text-primary hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
          >
            How is this calculated?
          </button>
          {showFormula && (
            <div
              role="tooltip"
              className="absolute left-1/2 -translate-x-1/2 bottom-full mb-2 w-56 z-20 rounded-xl bg-ink text-white text-xs p-3 shadow-card-hover"
            >
              {formulaText}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
