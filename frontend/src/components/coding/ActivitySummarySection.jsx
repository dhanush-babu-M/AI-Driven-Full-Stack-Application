import { useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import Card from '../Card';
import SourceTag from '../SourceTag';
import StatTile from '../StatTile';

const PRACTICE_FORMULA =
  'Coding practice activity = days with recorded coding practice / days in period × 100. Unrecorded days are never treated as failures.';

function PracticeFormulaTooltip() {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen((p) => !p)}
        onBlur={() => setOpen(false)}
        className="text-[11px] font-medium text-primary hover:underline rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        How is this calculated?
      </button>
      {open && (
        <div
          role="tooltip"
          className="absolute left-0 bottom-full mb-2 w-60 z-30 rounded-xl bg-ink text-white text-xs p-3 shadow-card-hover leading-relaxed"
        >
          {PRACTICE_FORMULA}
        </div>
      )}
    </div>
  );
}

export default function ActivitySummarySection({ summary, loading }) {
  const inactivePeriods = summary?.inactivePeriods || [];
  const chartSeries = summary?.chartSeries || [];
  const banner = summary?.banner;

  const pct =
    summary?.practiceActivityPercent !== null && summary?.practiceActivityPercent !== undefined
      ? `${summary.practiceActivityPercent}%`
      : '—';

  return (
    <Card className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-border/70">
        <div>
          <h2 className="text-base font-bold text-ink">Activity Summary</h2>
          <p className="text-xs text-muted mt-0.5">
            Honest streak &amp; practice metrics calculated strictly from saved records.
          </p>
        </div>
        <SourceTag source="CALCULATED" />
      </div>

      {/* Neutral rule-engine encouragement banner */}
      {banner?.message && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-indigo-200 bg-indigo-50/70 px-4 py-3 text-xs sm:text-sm text-ink">
          <span>{banner.message}</span>
          <SourceTag source={banner.source || 'RULE'} />
        </div>
      )}

      {loading ? (
        <p className="text-xs text-muted">Calculating summary...</p>
      ) : (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
            <StatTile
              label="Problems Solved"
              value={summary?.totalSolved ?? 0}
              hint={`${summary?.totalAttempted ?? 0} attempted · ${summary?.totalPracticed ?? 0} practiced`}
              tone="completed"
            />
            <StatTile
              label="Current Streak"
              value={`${summary?.currentStreak ?? 0}d`}
              hint="Honest streak (only recorded days count)"
              tone="primary"
            />
            <StatTile
              label="Longest Streak"
              value={`${summary?.longestStreak ?? 0}d`}
              hint="All-time consecutive recorded days"
              tone="neutral"
            />
            <div className="rounded-card border border-border bg-surface p-4 shadow-card">
              <p className="text-xs font-medium text-muted">Practice Days Rate</p>
              <p className="mt-2 text-2xl font-bold tracking-tight text-ink">{pct}</p>
              <p className="mt-1 text-xs text-muted">
                {summary?.practiceDays ?? 0} / {summary?.daysInPeriod ?? 0} days
              </p>
              <div className="mt-1">
                <PracticeFormulaTooltip />
              </div>
            </div>
            <StatTile
              label="Last Practice Date"
              value={summary?.lastPracticeDate || '—'}
              hint={summary?.lastPracticeDate ? 'Most recent manual log' : 'No practice logged yet'}
              tone="neutral"
            />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 pt-2">
            {/* Recharts Bar Chart */}
            <div className="lg:col-span-8 rounded-xl border border-border bg-canvas/50 p-4">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                  Problems Logged Over Period
                </h3>
                <span className="text-[11px] text-muted">Solved vs Attempted/Practiced</span>
              </div>
              {chartSeries.every((c) => c.total === 0) ? (
                <div className="h-48 flex items-center justify-center text-xs text-muted">
                  No practice entries in this date range to plot yet.
                </div>
              ) : (
                <div className="h-52 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartSeries} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" vertical={false} />
                      <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#64748B' }} />
                      <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#64748B' }} />
                      <Tooltip
                        contentStyle={{
                          borderRadius: '12px',
                          borderColor: '#E2E8F0',
                          fontSize: '12px',
                        }}
                      />
                      <Bar dataKey="solved" name="Solved" stackId="a" fill="#0D9488" radius={[3, 3, 0, 0]} />
                      <Bar dataKey="attempted" name="Attempted" stackId="a" fill="#F59E0B" />
                      <Bar dataKey="practiced" name="Practiced" stackId="a" fill="#2563EB" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </div>

            {/* Inactive Periods List (neutral "no record") */}
            <div className="lg:col-span-4 rounded-xl border border-border bg-canvas/50 p-4 flex flex-col">
              <h3 className="text-xs font-bold uppercase tracking-wider text-muted mb-2">
                Inactive Periods (No Record)
              </h3>
              <p className="text-[11px] text-muted mb-3">
                Days without a manual practice log are listed neutrally as no record — never as failures.
              </p>
              {inactivePeriods.length === 0 ? (
                <p className="text-xs text-completed font-medium">
                  No unrecorded gaps in this period so far.
                </p>
              ) : (
                <ul className="space-y-1.5 overflow-y-auto max-h-44 pr-1 text-xs">
                  {inactivePeriods.map((gap) => (
                    <li
                      key={`${gap.startDate}-${gap.endDate}`}
                      className="flex items-center justify-between gap-2 rounded-lg border border-border/80 bg-surface px-3 py-1.5"
                    >
                      <span className="text-ink font-medium">
                        {gap.startDate === gap.endDate
                          ? gap.startDate
                          : `${gap.startDate} → ${gap.endDate}`}
                      </span>
                      <span className="rounded-pill bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-muted">
                        {gap.days}d · {gap.label}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </>
      )}
    </Card>
  );
}
