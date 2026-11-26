import { useMemo, useState } from 'react';
import { addDays, format, parseISO, startOfWeek, subDays } from 'date-fns';
import Card from '../Card';

const WEEKDAY_ROWS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

const GREEN_LEVEL_CLASSES = {
  0: 'bg-slate-100 border border-slate-200/70',
  1: 'bg-emerald-200 border border-emerald-300/60',
  2: 'bg-emerald-400 border border-emerald-500/50',
  3: 'bg-emerald-500 border border-emerald-600/50',
  4: 'bg-emerald-700 border border-emerald-800/60',
};

function getCellClass(day, source) {
  if (!day || day.outOfRange) {
    return 'bg-transparent border-transparent cursor-default';
  }
  if (source === 'combined') {
    if (day.combinedCategory === 'BOTH') {
      return day.level >= 3
        ? 'bg-violet-700 border border-violet-800/60'
        : 'bg-violet-500 border border-violet-600/50';
    }
    if (day.combinedCategory === 'MANUAL_ONLY') {
      return day.level >= 2
        ? 'bg-primary border border-primary-hover/60'
        : 'bg-blue-300 border border-blue-400/60';
    }
    if (day.combinedCategory === 'GITHUB_ONLY') {
      return day.level >= 2
        ? 'bg-emerald-600 border border-emerald-700/60'
        : 'bg-emerald-300 border border-emerald-400/60';
    }
    return GREEN_LEVEL_CLASSES[0];
  }
  return GREEN_LEVEL_CLASSES[day.level] || GREEN_LEVEL_CLASSES[0];
}

export default function ContributionHeatmap({
  heatmap,
  source,
  onSourceChange,
  onSelectDay,
  onLogFirstPractice,
  loading,
}) {
  const [hoveredDay, setHoveredDay] = useState(null);

  const { weeks, monthLabels } = useMemo(() => {
    const days = heatmap?.days || [];
    if (!days.length) {
      return { weeks: [], monthLabels: [] };
    }
    const dayMap = new Map(days.map((d) => [d.date, d]));
    const firstDate = parseISO(days[0].date);
    const lastDate = parseISO(days[days.length - 1].date);

    const gridStart = startOfWeek(firstDate, { weekStartsOn: 1 });
    const endWeekStart = startOfWeek(lastDate, { weekStartsOn: 1 });
    const gridEnd = addDays(endWeekStart, 6);

    const builtWeeks = [];
    const labels = [];
    let prevMonth = '';
    let cursor = gridStart;
    let weekIdx = 0;

    while (cursor <= gridEnd) {
      const col = [];
      let monthForCol = '';
      for (let r = 0; r < 7; r++) {
        const cellDate = addDays(cursor, r);
        const iso = format(cellDate, 'yyyy-MM-dd');
        const inRange = cellDate >= firstDate && cellDate <= lastDate;
        if (inRange && !monthForCol && (cellDate.getDate() <= 7 || weekIdx === 0)) {
          const mLabel = format(cellDate, 'MMM');
          if (mLabel !== prevMonth) {
            monthForCol = mLabel;
            prevMonth = mLabel;
          }
        }
        col.push(
          inRange
            ? dayMap.get(iso) || {
                date: iso,
                githubCount: 0,
                githubCommitCount: 0,
                manualCount: 0,
                manualSolvedCount: 0,
                totalCount: 0,
                level: 0,
                combinedCategory: 'NONE',
              }
            : { date: iso, outOfRange: true }
        );
      }
      builtWeeks.push(col);
      labels.push(monthForCol);
      cursor = addDays(cursor, 7);
      weekIdx++;
    }

    return { weeks: builtWeeks, monthLabels: labels };
  }, [heatmap]);

  const totals = heatmap?.totals || {
    githubEvents: 0,
    githubCommits: 0,
    manualEntries: 0,
    manualSolved: 0,
    activeDays: 0,
  };
  const isEmpty = !loading && totals.activeDays === 0;

  return (
    <Card className="flex flex-col justify-between h-full">
      <div>
        <div className="flex flex-wrap items-center justify-between gap-3 pb-4 border-b border-border/70">
          <div>
            <h2 className="text-base font-bold text-ink">Contribution &amp; Practice Heatmap</h2>
            <p className="text-xs text-muted mt-0.5">
              Click any square to inspect GitHub events, practice logs, and learning records for that day.
            </p>
          </div>

          <div
            role="tablist"
            aria-label="Heatmap source switch"
            className="inline-flex rounded-pill border border-border bg-canvas p-1 text-xs font-semibold"
          >
            {[
              { id: 'github', label: 'GitHub imported' },
              { id: 'manual', label: 'Manual practice' },
              { id: 'combined', label: 'Combined' },
            ].map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={source === tab.id}
                onClick={() => onSourceChange(tab.id)}
                className={`rounded-pill px-3 py-1 transition-colors duration-fast ${
                  source === tab.id
                    ? 'bg-primary text-white shadow-sm'
                    : 'text-body hover:text-ink'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>

        {/* Hover readout bar */}
        <div className="mt-3 min-h-[28px] flex flex-wrap items-center justify-between gap-2 text-xs">
          {hoveredDay ? (
            <div className="inline-flex flex-wrap items-center gap-3 rounded-lg bg-canvas px-3 py-1.5 border border-border/80">
              <span className="font-semibold text-ink">
                {format(parseISO(hoveredDay.date), 'EEE, MMM d, yyyy')}
              </span>
              {source !== 'manual' && (
                <span className="text-emerald-700 font-medium">
                  GitHub imported: {hoveredDay.githubEvents ?? hoveredDay.githubCount} event(s) (
                  {hoveredDay.githubCommitCount} commit(s))
                </span>
              )}
              {source !== 'github' && (
                <span className="text-primary font-medium">
                  Manual practice: {hoveredDay.manualCount} entry(ies) ({hoveredDay.manualSolvedCount} solved)
                </span>
              )}
            </div>
          ) : (
            <span className="text-muted">
              Hover a square for per-source counts. Active days in range: <strong className="text-ink">{totals.activeDays}</strong>
            </span>
          )}
        </div>

        {/* Heatmap Grid */}
        <div className="mt-3 overflow-x-auto pb-2">
          {loading ? (
            <div className="h-36 flex items-center justify-center text-xs text-muted">
              Loading activity heatmap...
            </div>
          ) : weeks.length === 0 ? (
            <div className="h-36 flex items-center justify-center text-xs text-muted">
              No dates in selected range.
            </div>
          ) : (
            <div className="inline-block min-w-full">
              {/* Month labels row */}
              <div className="flex items-center gap-1.5 pl-9 mb-1.5">
                {monthLabels.map((lbl, idx) => (
                  <div
                    key={idx}
                    className="w-4 text-[10px] font-medium text-muted whitespace-nowrap"
                  >
                    {lbl}
                  </div>
                ))}
              </div>

              {/* Mon-Sun rows */}
              <div className="flex gap-2">
                <div className="flex flex-col gap-1.5 pr-1 text-[10px] font-medium text-muted select-none">
                  {WEEKDAY_ROWS.map((dayName) => (
                    <div key={dayName} className="h-4 flex items-center leading-none">
                      {dayName}
                    </div>
                  ))}
                </div>

                <div className="flex gap-1.5">
                  {weeks.map((week, wIdx) => (
                    <div key={wIdx} className="flex flex-col gap-1.5">
                      {week.map((day, dIdx) => {
                        if (day.outOfRange) {
                          return <div key={dIdx} className="h-4 w-4" aria-hidden="true" />;
                        }
                        const ariaSummary = `${day.date}: ${day.githubCount} GitHub events, ${day.manualCount} manual practice`;
                        return (
                          <button
                            key={day.date}
                            type="button"
                            aria-label={ariaSummary}
                            onMouseEnter={() => setHoveredDay(day)}
                            onMouseLeave={() => setHoveredDay(null)}
                            onFocus={() => setHoveredDay(day)}
                            onClick={() => onSelectDay(day.date)}
                            className={`h-4 w-4 rounded-[4px] transition-transform duration-fast hover:scale-110 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${getCellClass(
                              day,
                              source
                            )}`}
                          />
                        );
                      })}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Empty-state CTA when no activity exists in selected period */}
        {isEmpty && (
          <div className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-dashed border-border bg-canvas/70 px-4 py-3">
            <div className="text-xs text-body">
              <span className="font-semibold text-ink">No activity recorded in this period yet.</span>{' '}
              Log your manual coding practice or connect GitHub to populate your grid.
            </div>
            <button
              type="button"
              onClick={onLogFirstPractice}
              className="rounded-pill bg-primary px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover"
            >
              Log your first practice
            </button>
          </div>
        )}
      </div>

      {/* Legend + Mandatory Honesty Note */}
      <div className="mt-4 pt-3 border-t border-border/70 flex flex-col gap-2">
        <div className="flex flex-wrap items-center justify-between gap-3 text-[11px] text-muted">
          {source === 'combined' ? (
            <div className="flex flex-wrap items-center gap-3">
              <span className="inline-flex items-center gap-1.5">
                <span className="h-3 w-3 rounded-[3px] bg-slate-100 border border-slate-200/80" />
                No activity
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span className="h-3 w-3 rounded-[3px] bg-emerald-500" />
                GitHub imported only
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span className="h-3 w-3 rounded-[3px] bg-primary" />
                Manual practice only
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span className="h-3 w-3 rounded-[3px] bg-violet-600" />
                Both GitHub &amp; Manual
              </span>
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-3">
              <span className="inline-flex items-center gap-1.5">
                <span className="h-3 w-3 rounded-[3px] bg-slate-100 border border-slate-200/80" />
                No activity
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span>Less</span>
                {[1, 2, 3, 4].map((lvl) => (
                  <span
                    key={lvl}
                    className={`h-3 w-3 rounded-[3px] ${GREEN_LEVEL_CLASSES[lvl]}`}
                  />
                ))}
                <span>More</span>
              </span>
            </div>
          )}

          <span className="text-[11px] text-muted">
            {source === 'github'
              ? `${totals.githubEvents} imported event(s) · ${totals.githubCommits} commit(s)`
              : source === 'manual'
              ? `${totals.manualEntries} manual entry(ies) · ${totals.manualSolved} solved`
              : `${totals.githubEvents} GitHub · ${totals.manualEntries} Manual`}
          </span>
        </div>

        <p className="text-xs font-medium text-body bg-canvas rounded-xl px-3 py-2 border border-border/70">
          Commit counts show activity, not learning. Practice entries are recorded by you.
        </p>
      </div>
    </Card>
  );
}
