import { useState } from 'react';
import { Link } from 'react-router-dom';
import { format, parseISO } from 'date-fns';
import { markFollowupDone, rescheduleFollowup } from '../../api/client';
import { notifyRemindersChanged } from '../../hooks/useReminders';
import Card from '../Card';
import { JobStatusChip } from './jobMeta';

function formatDue(dateStr) {
  if (!dateStr) return '—';
  try {
    return format(parseISO(dateStr), 'EEE, MMM d');
  } catch {
    return dateStr;
  }
}

export default function UpcomingFollowupsCard({
  followups = [],
  onUpdated,
  onOpenJob,
  onToast,
  showViewAllLink = false,
}) {
  const [rescheduleId, setRescheduleId] = useState(null);
  const [newDate, setNewDate] = useState('');
  const [busyId, setBusyId] = useState(null);

  const handleMarkDone = async (followupId) => {
    setBusyId(followupId);
    try {
      await markFollowupDone(followupId);
      notifyRemindersChanged();
      await onUpdated?.();
      onToast?.('Follow-up marked done.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to mark follow-up done.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const handleRescheduleSubmit = async (e, followupId) => {
    e.preventDefault();
    if (!newDate) return;
    setBusyId(followupId);
    try {
      await rescheduleFollowup(followupId, { dueDate: newDate });
      setRescheduleId(null);
      setNewDate('');
      notifyRemindersChanged();
      await onUpdated?.();
      onToast?.('Follow-up rescheduled.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to reschedule follow-up.', 'error');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <Card className="flex flex-col justify-between">
      <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
        <div>
          <h3 className="text-base font-bold text-ink">Upcoming Follow-ups</h3>
          <p className="text-xs text-muted">Next 7 days &amp; overdue items</p>
        </div>
        {showViewAllLink && (
          <Link
            to="/app/projects?tab=applications"
            className="text-xs font-semibold text-primary hover:underline"
          >
            Open Applications →
          </Link>
        )}
      </div>

      {followups.length === 0 ? (
        <p className="py-5 text-xs text-muted text-center">
          No follow-ups due in the next 7 days.
        </p>
      ) : (
        <ul className="mt-4 space-y-2.5">
          {followups.map((item) => {
            const isOverdue = Boolean(item.overdue);
            const isRescheduling = rescheduleId === item.id;

            return (
              <li
                key={item.id}
                className={`rounded-xl border p-3 space-y-2 ${
                  isOverdue
                    ? 'border-pending/50 bg-pending-soft/75'
                    : 'border-border/80 bg-canvas/50'
                }`}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      {onOpenJob ? (
                        <button
                          type="button"
                          onClick={() => onOpenJob(item.jobApplicationId)}
                          className="text-sm font-semibold text-ink hover:text-primary hover:underline text-left truncate"
                        >
                          {item.companyName} — {item.roleTitle}
                        </button>
                      ) : (
                        <Link
                          to={`/app/projects?tab=applications&jobId=${item.jobApplicationId}`}
                          className="text-sm font-semibold text-ink hover:text-primary hover:underline truncate"
                        >
                          {item.companyName} — {item.roleTitle}
                        </Link>
                      )}
                      {isOverdue && (
                        <span className="rounded-pill border border-amber-300 bg-amber-100 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-amber-900">
                          Overdue
                        </span>
                      )}
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
                      <span className={isOverdue ? 'font-semibold text-amber-900' : ''}>
                        Due {formatDue(item.dueDate)}
                      </span>
                      <JobStatusChip status={item.applicationStatus} />
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      disabled={busyId === item.id}
                      onClick={() => handleMarkDone(item.id)}
                      className="rounded-pill bg-completed-soft border border-completed/25 px-2.5 py-1 text-[11px] font-semibold text-completed hover:bg-completed hover:text-white transition-colors"
                    >
                      Mark done
                    </button>
                    <button
                      type="button"
                      disabled={busyId === item.id}
                      onClick={() => {
                        setRescheduleId(isRescheduling ? null : item.id);
                        setNewDate(item.dueDate || new Date().toISOString().slice(0, 10));
                      }}
                      className="rounded-pill border border-border bg-surface px-2.5 py-1 text-[11px] font-semibold text-body hover:text-ink"
                    >
                      Reschedule
                    </button>
                  </div>
                </div>

                {isRescheduling && (
                  <form
                    onSubmit={(e) => handleRescheduleSubmit(e, item.id)}
                    className="flex flex-wrap items-center gap-2 pt-2 border-t border-border/60"
                  >
                    <input
                      type="date"
                      value={newDate}
                      onChange={(e) => setNewDate(e.target.value)}
                      required
                      aria-label="New follow-up date"
                      className="rounded-xl border border-border bg-surface px-2.5 py-1 text-xs text-ink"
                    />
                    <button
                      type="submit"
                      disabled={busyId === item.id}
                      className="rounded-pill bg-primary px-3 py-1 text-[11px] font-semibold text-white hover:bg-primary-hover"
                    >
                      Save
                    </button>
                    <button
                      type="button"
                      onClick={() => setRescheduleId(null)}
                      className="text-[11px] text-muted hover:text-ink"
                    >
                      Cancel
                    </button>
                  </form>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
