import { useCallback, useEffect, useMemo, useState } from 'react';
import { addDays, format, parseISO, startOfWeek } from 'date-fns';
import {
  carryForwardTasks,
  createGoal,
  fetchGoals,
  fetchUnfinishedBefore,
  fetchWeeklyProgress,
  fetchWeeklyReview,
  saveWeeklyReview,
  updateGoal,
  updateTaskStatus,
} from '../api/client';
import Card from '../components/Card';
import CategoryChip from '../components/CategoryChip';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
import ProgressBar from '../components/ProgressBar';
import SourceTag from '../components/SourceTag';
import StatTile from '../components/StatTile';
import StatusChip from '../components/StatusChip';
import TaskFormModal from '../components/TaskFormModal';
import Toast from '../components/Toast';
import { useAuth } from '../context/AuthContext';
import { formatTime12h } from '../utils/taskCategories';

function getMondayInTimeZone(timeZone) {
  try {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: timeZone || 'Asia/Kolkata',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(new Date());
    const y = parts.find((p) => p.type === 'year')?.value;
    const m = parts.find((p) => p.type === 'month')?.value;
    const d = parts.find((p) => p.type === 'day')?.value;
    const localDate = parseISO(`${y}-${m}-${d}`);
    return format(startOfWeek(localDate, { weekStartsOn: 1 }), 'yyyy-MM-dd');
  } catch {
    return format(startOfWeek(new Date(), { weekStartsOn: 1 }), 'yyyy-MM-dd');
  }
}

export default function WeeklyPlannerPage() {
  const { user } = useAuth();
  const currentWeekMonday = useMemo(
    () => getMondayInTimeZone(user?.timeZone),
    [user?.timeZone]
  );

  const [weekStart, setWeekStart] = useState(currentWeekMonday);
  const [selectedDay, setSelectedDay] = useState(currentWeekMonday);
  const [showAllDays, setShowAllDays] = useState(false);

  const [progress, setProgress] = useState(null);
  const [weeklyGoals, setWeeklyGoals] = useState([]);
  const [review, setReview] = useState({ whatWentWell: '', carryForwardNotes: '' });
  const [unfinishedEarlier, setUnfinishedEarlier] = useState([]);

  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  // Opening Question
  const [showOpeningQuestion, setShowOpeningQuestion] = useState(false);
  const [openingPanel, setOpeningPanel] = useState(''); // '' | 'last-week' | 'carry'
  const [lastWeekReview, setLastWeekReview] = useState(null);
  const [selectedCarryIds, setSelectedCarryIds] = useState([]);

  // Week Goal inline editor
  const [editingGoal, setEditingGoal] = useState(false);
  const [goalTitleDraft, setGoalTitleDraft] = useState('');

  // Review collapsible & previous-week read-only guard
  const [reviewOpen, setReviewOpen] = useState(true);
  const [reviewEditable, setReviewEditable] = useState(true);
  const [whatWentWellDraft, setWhatWentWellDraft] = useState('');
  const [carryNotesDraft, setCarryNotesDraft] = useState('');
  const [savingReview, setSavingReview] = useState(false);

  // Add Task Modal
  const [addTaskOpen, setAddTaskOpen] = useState(false);

  const isPastWeek = weekStart < currentWeekMonday;

  const loadWeekData = useCallback(async () => {
    setLoading(true);
    try {
      const prevMonday = format(addDays(parseISO(weekStart), -7), 'yyyy-MM-dd');
      const [weeklyData, goalsData, reviewData, prevReviewData, unfinishedData] =
        await Promise.all([
          fetchWeeklyProgress(weekStart),
          fetchGoals('WEEK', weekStart),
          fetchWeeklyReview(weekStart),
          fetchWeeklyReview(prevMonday),
          fetchUnfinishedBefore(weekStart),
        ]);

      setProgress(weeklyData);
      setWeeklyGoals(goalsData || []);
      setGoalTitleDraft(goalsData?.[0]?.title || '');
      setReview(reviewData);
      setWhatWentWellDraft(reviewData?.whatWentWell || '');
      setCarryNotesDraft(reviewData?.carryForwardNotes || '');
      setLastWeekReview(prevReviewData);
      setUnfinishedEarlier(unfinishedData || []);
      setSelectedCarryIds([]);
      setReviewEditable(!isPastWeek);
    } catch (err) {
      setToast({ message: err?.message || 'Unable to load weekly planner.', type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [weekStart, isPastWeek]);

  useEffect(() => {
    loadWeekData();
  }, [loadWeekData]);

  const shiftWeek = (deltaWeeks) => {
    const nextMonday = format(addDays(parseISO(weekStart), deltaWeeks * 7), 'yyyy-MM-dd');
    setWeekStart(nextMonday);
    setSelectedDay(nextMonday);
  };

  const primaryGoal = weeklyGoals[0] || null;
  const weekEnd = format(addDays(parseISO(weekStart), 6), 'yyyy-MM-dd');
  const weekRangeLabel = `${format(parseISO(weekStart), 'd MMM')} - ${format(
    parseISO(weekEnd),
    'd MMM yyyy'
  )}`;

  const visibleTasks = useMemo(() => {
    const all = progress?.tasks || [];
    if (showAllDays) return all;
    return all.filter((t) => t.taskDate === selectedDay);
  }, [progress, showAllDays, selectedDay]);

  const handleSaveGoal = async (e) => {
    e.preventDefault();
    if (!goalTitleDraft.trim()) return;
    try {
      if (primaryGoal) {
        await updateGoal(primaryGoal.id, {
          title: goalTitleDraft.trim(),
          category: primaryGoal.category,
          scope: 'WEEK',
          periodStart: weekStart,
          targetCount: primaryGoal.targetCount || 1,
          doneCount: primaryGoal.doneCount || 0,
          status: primaryGoal.status || 'PENDING',
        });
      } else {
        await createGoal({
          title: goalTitleDraft.trim(),
          category: 'OTHER',
          scope: 'WEEK',
          periodStart: weekStart,
          targetCount: 1,
          doneCount: 0,
          status: 'PENDING',
        });
      }
      setEditingGoal(false);
      setToast({ message: 'Weekly goal saved.', type: 'success' });
      await loadWeekData();
    } catch (err) {
      setToast({ message: err?.message || 'Could not save weekly goal.', type: 'error' });
    }
  };

  const handleToggleTask = async (task) => {
    if (task.status === 'RESCHEDULED') return;
    const nextStatus = task.status === 'COMPLETED' ? 'PENDING' : 'COMPLETED';
    try {
      await updateTaskStatus(task.id, { status: nextStatus });
      await loadWeekData();
    } catch (err) {
      setToast({ message: err?.message || 'Could not update task.', type: 'error' });
    }
  };

  const handleCarryForward = async () => {
    if (selectedCarryIds.length === 0) return;
    try {
      await carryForwardTasks({ taskIds: selectedCarryIds, targetDate: selectedDay });
      setOpeningPanel('');
      setShowOpeningQuestion(false);
      setToast({
        message: `Carried forward ${selectedCarryIds.length} task(s) to ${selectedDay}.`,
        type: 'success',
      });
      await loadWeekData();
    } catch (err) {
      setToast({ message: err?.message || 'Could not carry forward tasks.', type: 'error' });
    }
  };

  const handleSaveReview = async (e) => {
    e.preventDefault();
    if (savingReview) return;
    setSavingReview(true);
    try {
      const saved = await saveWeeklyReview({
        weekStartDate: weekStart,
        whatWentWell: whatWentWellDraft,
        carryForwardNotes: carryNotesDraft,
      });
      setReview(saved);
      if (isPastWeek) setReviewEditable(false);
      setToast({ message: 'Weekly review saved.', type: 'success' });
    } catch (err) {
      setToast({ message: err?.message || 'Could not save review.', type: 'error' });
    } finally {
      setSavingReview(false);
    }
  };

  const dayTabs = progress?.days || [];
  const plannedCount = progress?.plannedCount || 0;
  const completedCount = progress?.completedCount || 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Weekly Planner"
        breadcrumb="Planner > Weekly Planner"
        rightSlot={
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex items-center rounded-pill border border-border bg-surface p-1 shadow-sm">
              <button
                type="button"
                onClick={() => shiftWeek(-1)}
                aria-label="Previous week"
                className="rounded-pill px-2.5 py-1 text-sm font-semibold text-body hover:bg-canvas hover:text-ink"
              >
                ‹
              </button>
              <span className="px-2.5 text-xs sm:text-sm font-semibold text-ink whitespace-nowrap">
                {weekRangeLabel}
              </span>
              <button
                type="button"
                onClick={() => shiftWeek(1)}
                aria-label="Next week"
                className="rounded-pill px-2.5 py-1 text-sm font-semibold text-body hover:bg-canvas hover:text-ink"
              >
                ›
              </button>
            </div>

            <button
              type="button"
              onClick={() => {
                setWeekStart(currentWeekMonday);
                setSelectedDay(currentWeekMonday);
              }}
              className="rounded-pill border border-border bg-surface px-3.5 py-2 text-xs sm:text-sm font-medium text-ink hover:bg-canvas"
            >
              This week
            </button>

            <button
              type="button"
              onClick={() => setShowOpeningQuestion((prev) => !prev)}
              className="rounded-pill border border-border bg-surface px-3.5 py-2 text-xs sm:text-sm font-medium text-primary hover:bg-primary-soft"
            >
              {showOpeningQuestion ? 'Hide options' : 'Week options'}
            </button>

            <button
              type="button"
              onClick={() => setAddTaskOpen(true)}
              className="rounded-pill bg-primary px-4 py-2 text-xs sm:text-sm font-semibold text-white hover:bg-primary-hover shadow-sm"
            >
              + Add Task
            </button>
          </div>
        }
      />

      {/* OPENING QUESTION */}
      {(showOpeningQuestion || (!loading && plannedCount === 0 && !primaryGoal)) && (
        <Card>
          <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
            <h2 className="text-base font-bold text-ink">Plan this week ({weekRangeLabel})</h2>
            {isPastWeek && (
              <span className="rounded-pill bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-muted">
                Past week (preserved)
              </span>
            )}
          </div>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-4">
            <button
              type="button"
              onClick={() => setOpeningPanel(openingPanel === 'last-week' ? '' : 'last-week')}
              className="text-left rounded-card border border-border bg-canvas/50 p-4 hover:border-primary/40 hover:bg-surface transition-all duration-fast"
            >
              <h3 className="text-sm font-bold text-ink">Review last week&apos;s plan</h3>
              <p className="mt-1 text-xs text-muted">
                Inspect what went well and carry-forward notes from the prior week.
              </p>
            </button>

            <button
              type="button"
              onClick={() => setOpeningPanel(openingPanel === 'carry' ? '' : 'carry')}
              className="text-left rounded-card border border-border bg-canvas/50 p-4 hover:border-primary/40 hover:bg-surface transition-all duration-fast"
            >
              <h3 className="text-sm font-bold text-ink">Carry forward unfinished tasks</h3>
              <p className="mt-1 text-xs text-muted">
                {unfinishedEarlier.length} unfinished task(s) from earlier dates available.
              </p>
            </button>

            <button
              type="button"
              onClick={() => setAddTaskOpen(true)}
              className="text-left rounded-card border border-border bg-canvas/50 p-4 hover:border-primary/40 hover:bg-surface transition-all duration-fast"
            >
              <h3 className="text-sm font-bold text-ink">Create a new weekly plan</h3>
              <p className="mt-1 text-xs text-muted">
                Add scheduled tasks across Monday–Sunday for this week.
              </p>
            </button>
          </div>

          {openingPanel === 'last-week' && (
            <div className="mt-4 rounded-xl border border-border bg-canvas p-4 text-xs space-y-2">
              <p className="font-semibold text-ink">Previous week notes:</p>
              <p className="text-body">
                <strong>What went well:</strong>{' '}
                {lastWeekReview?.whatWentWell || 'No notes saved for last week.'}
              </p>
              <p className="text-body">
                <strong>Carry-forward notes:</strong>{' '}
                {lastWeekReview?.carryForwardNotes || 'None noted.'}
              </p>
            </div>
          )}

          {openingPanel === 'carry' && (
            <div className="mt-4 rounded-xl border border-border bg-canvas p-4 space-y-3">
              {unfinishedEarlier.length === 0 ? (
                <p className="text-xs text-muted">No unfinished tasks from earlier days.</p>
              ) : (
                <>
                  <ul className="space-y-2 max-h-48 overflow-y-auto">
                    {unfinishedEarlier.map((t) => (
                      <li
                        key={t.id}
                        className="flex items-center justify-between gap-2 rounded-xl bg-surface border border-border/70 px-3 py-2 text-xs"
                      >
                        <label className="flex items-center gap-2 min-w-0 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={selectedCarryIds.includes(t.id)}
                            onChange={() =>
                              setSelectedCarryIds((prev) =>
                                prev.includes(t.id)
                                  ? prev.filter((id) => id !== t.id)
                                  : [...prev, t.id]
                              )
                            }
                            className="h-4 w-4 rounded border-border text-primary"
                          />
                          <span className="font-medium text-ink truncate">{t.title}</span>
                        </label>
                        <span className="text-muted shrink-0">{t.taskDate}</span>
                      </li>
                    ))}
                  </ul>
                  <div className="flex justify-end">
                    <button
                      type="button"
                      disabled={selectedCarryIds.length === 0}
                      onClick={handleCarryForward}
                      className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
                    >
                      Carry to {selectedDay} ({selectedCarryIds.length})
                    </button>
                  </div>
                </>
              )}
            </div>
          )}
        </Card>
      )}

      {/* TOP: This week's goal + Progress */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="flex flex-col justify-between">
          <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
            <span className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-primary-soft text-primary">
                <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                  <circle cx="12" cy="12" r="9" />
                  <circle cx="12" cy="12" r="5" />
                  <circle cx="12" cy="12" r="1.5" fill="currentColor" />
                </svg>
              </span>
              <h2 className="text-base font-bold text-ink">This week&apos;s goal</h2>
            </span>
            <button
              type="button"
              onClick={() => setEditingGoal((prev) => !prev)}
              className="text-xs font-semibold text-primary hover:underline"
            >
              {editingGoal ? 'Cancel' : primaryGoal ? 'Edit' : '+ Set goal'}
            </button>
          </div>

          {editingGoal ? (
            <form onSubmit={handleSaveGoal} className="mt-4 flex flex-col sm:flex-row gap-2">
              <input
                type="text"
                value={goalTitleDraft}
                onChange={(e) => setGoalTitleDraft(e.target.value)}
                placeholder="e.g. Complete Spring Security + 10 DSA problems"
                aria-label="Weekly goal"
                className="flex-1 rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
              />
              <button
                type="submit"
                className="rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
              >
                Save Goal
              </button>
            </form>
          ) : (
            <div className="mt-4">
              <p className="text-sm font-semibold text-ink">
                {primaryGoal ? primaryGoal.title : 'No weekly goal set yet.'}
              </p>
              <p className="mt-1 text-xs text-muted">
                Week runs Monday to Sunday ({weekStart} to {weekEnd}) in your time zone.
              </p>
            </div>
          )}
        </Card>

        <Card className="flex flex-col justify-between">
          <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
            <h2 className="text-base font-bold text-ink">Progress</h2>
            <SourceTag source="CALCULATED" />
          </div>

          <div className="mt-4 space-y-2.5">
            {plannedCount === 0 ? (
              <p className="text-sm text-muted">No tasks planned yet for this week.</p>
            ) : (
              <>
                <div className="flex items-center justify-between text-sm">
                  <span className="font-semibold text-ink">
                    {completedCount}/{plannedCount} tasks
                  </span>
                  <span className="font-bold text-primary">
                    {Math.round(progress?.completionRate || 0)}%
                  </span>
                </div>
                <ProgressBar value={progress?.completionRate || 0} />
              </>
            )}
          </div>
        </Card>
      </div>

      {/* DAY TABS: Mon - Sun */}
      <Card padding="p-4">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
          <h2 className="text-sm font-bold text-ink">Days of the week</h2>
          <button
            type="button"
            onClick={() => setShowAllDays((prev) => !prev)}
            className={`rounded-pill px-3.5 py-1 text-xs font-semibold border transition-colors duration-fast ${
              showAllDays
                ? 'bg-primary text-white border-primary'
                : 'bg-canvas text-body border-border hover:bg-primary-soft'
            }`}
          >
            {showAllDays ? 'Showing all days' : 'Show all days'}
          </button>
        </div>

        <div className="grid grid-cols-7 gap-1.5 sm:gap-2.5">
          {dayTabs.map((day) => {
            const isSelected = !showAllDays && day.date === selectedDay;
            return (
              <button
                key={day.date}
                type="button"
                onClick={() => {
                  setShowAllDays(false);
                  setSelectedDay(day.date);
                }}
                className={`flex flex-col items-center rounded-xl border px-1.5 py-2.5 text-xs transition-colors duration-fast ${
                  isSelected
                    ? 'bg-primary text-white border-primary shadow-sm'
                    : 'bg-canvas/70 text-body border-border/70 hover:bg-primary-soft hover:text-ink'
                }`}
              >
                <span className={`text-[11px] font-medium ${isSelected ? 'text-blue-100' : 'text-muted'}`}>
                  {format(parseISO(day.date), 'EEE')}
                </span>
                <span className="mt-0.5 text-sm font-bold">
                  {format(parseISO(day.date), 'd')}
                </span>
                <span
                  className={`mt-1.5 h-1.5 w-1.5 rounded-full ${
                    day.hasTasks
                      ? isSelected
                        ? 'bg-white'
                        : 'bg-primary'
                      : 'bg-transparent'
                  }`}
                  aria-hidden="true"
                />
              </button>
            );
          })}
        </div>
      </Card>

      {/* YOUR WEEKLY PLAN */}
      <Card>
        <div className="flex flex-wrap items-center justify-between gap-2 pb-4 border-b border-border/70">
          <h2 className="text-base font-bold text-ink">
            YOUR WEEKLY PLAN —{' '}
            {showAllDays ? weekRangeLabel : format(parseISO(selectedDay), 'EEE, d MMM')}
          </h2>
          <button
            type="button"
            onClick={() => setAddTaskOpen(true)}
            className="rounded-pill bg-primary-soft px-3.5 py-1.5 text-xs font-semibold text-primary hover:bg-primary hover:text-white transition-colors duration-fast"
          >
            + Add Task
          </button>
        </div>

        {loading ? (
          <p className="py-6 text-sm text-muted">Loading weekly plan...</p>
        ) : visibleTasks.length === 0 ? (
          <div className="py-6">
            <EmptyState
              title="No tasks scheduled"
              description={`No tasks found for ${showAllDays ? 'this week' : selectedDay}.`}
              action={
                <button
                  type="button"
                  onClick={() => setAddTaskOpen(true)}
                  className="rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
                >
                  + Add Task
                </button>
              }
            />
          </div>
        ) : (
          <ul className="mt-4 space-y-2.5">
            {visibleTasks.map((task) => {
              const isCompleted = task.status === 'COMPLETED';
              return (
                <li
                  key={task.id}
                  className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl border border-border/70 bg-canvas/60 px-3.5 py-3"
                >
                  <label className="flex items-start gap-3 min-w-0 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={isCompleted}
                      disabled={task.status === 'RESCHEDULED'}
                      onChange={() => handleToggleTask(task)}
                      className="mt-0.5 h-4 w-4 rounded border-border text-primary"
                    />
                    <div className="min-w-0">
                      <span
                        className={`block text-sm font-semibold ${
                          isCompleted ? 'line-through text-muted' : 'text-ink'
                        }`}
                      >
                        {task.title}
                      </span>
                      <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
                        <CategoryChip category={task.category} />
                        <span>{format(parseISO(task.taskDate), 'EEE d MMM')}</span>
                        {task.startTime && (
                          <span>
                            {formatTime12h(task.startTime)}
                            {task.endTime ? ` – ${formatTime12h(task.endTime)}` : ''}
                          </span>
                        )}
                      </div>
                    </div>
                  </label>
                  <StatusChip status={task.status} label={task.status} />
                </li>
              );
            })}
          </ul>
        )}
      </Card>

      {/* WEEKLY SUMMARY */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatTile label="Tasks Completed" value={progress?.completedCount || 0} source="CALCULATED" />
        <StatTile label="Pending" value={progress?.pendingCount || 0} source="CALCULATED" />
        <StatTile label="Skipped" value={progress?.skippedCount || 0} source="CALCULATED" />
        <StatTile label="Missed / Rescheduled" value={progress?.rescheduledCount || 0} source="CALCULATED" />
      </div>

      {/* REVIEW SECTION (collapsible) */}
      <Card>
        <div className="flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={() => setReviewOpen((prev) => !prev)}
            aria-expanded={reviewOpen}
            className="flex items-center gap-2 text-base font-bold text-ink"
          >
            <span>Weekly Review</span>
            <span className="text-xs text-muted">{reviewOpen ? '▾' : '▸'}</span>
          </button>

          {isPastWeek && !reviewEditable && (
            <button
              type="button"
              onClick={() => setReviewEditable(true)}
              className="rounded-pill border border-border px-3 py-1 text-xs font-semibold text-primary hover:bg-primary-soft"
            >
              Edit
            </button>
          )}
        </div>

        {reviewOpen && (
          <form onSubmit={handleSaveReview} className="mt-4 space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="weekly-went-well" className="block text-xs font-semibold text-ink mb-1">
                  What went well
                </label>
                <textarea
                  id="weekly-went-well"
                  rows={3}
                  disabled={!reviewEditable}
                  value={whatWentWellDraft}
                  onChange={(e) => setWhatWentWellDraft(e.target.value)}
                  placeholder="Highlights and wins from this week..."
                  className="w-full rounded-xl border border-border bg-surface p-3 text-sm text-ink disabled:bg-canvas disabled:text-muted focus:outline-none focus:border-primary"
                />
              </div>
              <div>
                <label htmlFor="weekly-carry-notes" className="block text-xs font-semibold text-ink mb-1">
                  Goals to carry forward
                </label>
                <textarea
                  id="weekly-carry-notes"
                  rows={3}
                  disabled={!reviewEditable}
                  value={carryNotesDraft}
                  onChange={(e) => setCarryNotesDraft(e.target.value)}
                  placeholder="What moves to next week..."
                  className="w-full rounded-xl border border-border bg-surface p-3 text-sm text-ink disabled:bg-canvas disabled:text-muted focus:outline-none focus:border-primary"
                />
              </div>
            </div>

            {reviewEditable && (
              <div className="flex justify-end">
                <button
                  type="submit"
                  disabled={savingReview}
                  className="rounded-pill bg-primary px-5 py-2 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                >
                  {savingReview ? 'Saving...' : 'Save Review'}
                </button>
              </div>
            )}
          </form>
        )}
      </Card>

      <TaskFormModal
        open={addTaskOpen}
        defaultDate={selectedDay}
        onClose={() => setAddTaskOpen(false)}
        onSaved={loadWeekData}
      />

      <Toast
        message={toast.message}
        type={toast.type}
        onDismiss={() => setToast({ message: '', type: 'info' })}
      />
    </div>
  );
}
