import { useEffect } from 'react';
import { format, parseISO } from 'date-fns';
import SourceTag from '../SourceTag';

export default function DayActivityDrawer({
  dateStr,
  detail,
  loading,
  onClose,
  onLogForDate,
}) {
  useEffect(() => {
    if (!dateStr) return undefined;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [dateStr, onClose]);

  if (!dateStr) return null;

  const formattedTitle = (() => {
    try {
      return format(parseISO(dateStr), 'EEE, MMM d, yyyy');
    } catch {
      return dateStr;
    }
  })();

  const githubEvents = detail?.githubEvents || [];
  const codingActivities = detail?.codingActivities || [];
  const learningRecords = detail?.learningRecords || [];
  const projectWorklogs = detail?.projectWorklogs || [];

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-ink/35 backdrop-blur-[1px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="day-drawer-title"
      onClick={onClose}
    >
      <aside
        className="h-full w-full max-w-md bg-surface border-l border-border shadow-drawer flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between gap-3 px-6 py-4 border-b border-border">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-muted">
              Daily Breakdown
            </p>
            <h2 id="day-drawer-title" className="text-lg font-bold text-ink">
              Activity on {formattedTitle}
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close day activity drawer"
            className="rounded-lg p-1.5 text-muted hover:text-ink hover:bg-canvas"
          >
            ✕
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-6 text-sm">
          {loading ? (
            <p className="text-xs text-muted">Loading activity for {dateStr}...</p>
          ) : (
            <>
              {/* GitHub Imported Events */}
              <section className="space-y-2.5">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                    Imported GitHub Events ({githubEvents.length})
                  </h3>
                  <SourceTag source="IMPORTED" />
                </div>
                {githubEvents.length === 0 ? (
                  <p className="rounded-xl border border-dashed border-border px-3.5 py-3 text-xs text-muted">
                    No imported GitHub events recorded on this date.
                  </p>
                ) : (
                  <ul className="space-y-2">
                    {githubEvents.map((ev) => (
                      <li
                        key={ev.id}
                        className="rounded-xl border border-border bg-canvas/60 p-3 space-y-1"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-semibold text-ink truncate">{ev.repoName}</span>
                          <SourceTag source={ev.dataSource || 'IMPORTED'} />
                        </div>
                        <p className="text-xs text-body">
                          {ev.payloadSummary || ev.eventType}
                          {ev.commitCount > 0 ? ` · ${ev.commitCount} commit(s)` : ''}
                        </p>
                        {ev.htmlUrl && (
                          <a
                            href={ev.htmlUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-block text-xs font-semibold text-primary hover:underline"
                          >
                            View on GitHub ↗
                          </a>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              {/* Manual Practice Entries */}
              <section className="space-y-2.5">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                    Manual Practice Entries ({codingActivities.length})
                  </h3>
                  <SourceTag source="MANUAL" />
                </div>
                {codingActivities.length === 0 ? (
                  <p className="rounded-xl border border-dashed border-border px-3.5 py-3 text-xs text-muted">
                    No manual coding practice logged for this date.
                  </p>
                ) : (
                  <ul className="space-y-2">
                    {codingActivities.map((item) => (
                      <li
                        key={item.id}
                        className="rounded-xl border border-border bg-canvas/60 p-3 space-y-1.5"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-semibold text-ink">{item.problemTitle}</span>
                          <SourceTag source={item.dataSource || 'MANUAL'} />
                        </div>
                        <div className="flex flex-wrap gap-1.5 text-[11px]">
                          <span className="rounded-pill bg-surface border border-border px-2 py-0.5 text-body font-medium">
                            {item.platform}
                          </span>
                          {item.topic && (
                            <span className="rounded-pill bg-surface border border-border px-2 py-0.5 text-body">
                              {item.topic}
                            </span>
                          )}
                          {item.difficulty && (
                            <span className="rounded-pill bg-surface border border-border px-2 py-0.5 text-body">
                              {item.difficulty}
                            </span>
                          )}
                          <span className="rounded-pill bg-completed-soft text-completed px-2 py-0.5 font-semibold">
                            {item.result}
                          </span>
                        </div>
                        {item.notes && (
                          <p className="text-xs text-body whitespace-pre-wrap">{item.notes}</p>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              {/* Learning Records */}
              <section className="space-y-2.5">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                    Learning Records ({learningRecords.length})
                  </h3>
                  <SourceTag source="MANUAL" />
                </div>
                {learningRecords.length === 0 ? (
                  <p className="rounded-xl border border-dashed border-border px-3.5 py-3 text-xs text-muted">
                    No learning records saved for this date.
                  </p>
                ) : (
                  <ul className="space-y-2">
                    {learningRecords.map((lr) => (
                      <li
                        key={lr.id}
                        className="rounded-xl border border-border bg-canvas/60 p-3 space-y-1"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-semibold text-ink">{lr.topic}</span>
                          <span className="text-xs text-muted">{lr.minutesSpent} min</span>
                        </div>
                        {lr.notes && (
                          <p className="text-xs text-body whitespace-pre-wrap">{lr.notes}</p>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              {/* Project Work Logs */}
              <section className="space-y-2.5">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                    Project Work Logs ({projectWorklogs.length})
                  </h3>
                  <SourceTag source="MANUAL" />
                </div>
                {projectWorklogs.length === 0 ? (
                  <p className="rounded-xl border border-dashed border-border px-3.5 py-3 text-xs text-muted">
                    No project work logs recorded for this date.
                  </p>
                ) : (
                  <ul className="space-y-2">
                    {projectWorklogs.map((wl) => (
                      <li
                        key={wl.id}
                        className="rounded-xl border border-border bg-canvas/60 p-3 space-y-1"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-semibold text-ink">{wl.projectTitle}</span>
                          <span className="text-xs text-muted">{wl.minutesSpent} min</span>
                        </div>
                        <p className="text-xs text-body whitespace-pre-wrap">{wl.summary}</p>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </>
          )}
        </div>

        <div className="px-6 py-4 border-t border-border flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={() => onLogForDate?.(dateStr)}
            className="rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
          >
            + Log Practice for {dateStr}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-pill border border-border px-4 py-2 text-xs font-semibold text-body hover:bg-canvas"
          >
            Close
          </button>
        </div>
      </aside>
    </div>
  );
}
