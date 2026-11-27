import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  addDays,
  addMonths,
  endOfMonth,
  format,
  getDay,
  parseISO,
  startOfMonth,
  subMonths,
} from 'date-fns';
import {
  carryForwardGoals,
  createGoal,
  deleteGoal,
  fetchGoals,
  fetchMonthlyProgress,
  updateGoal,
} from '../api/client';
import Card from '../components/Card';
import CategoryChip from '../components/CategoryChip';
import EmptyState from '../components/EmptyState';
import Modal from '../components/Modal';
import PageHeader from '../components/PageHeader';
import ProgressBar from '../components/ProgressBar';
import ProgressRing from '../components/ProgressRing';
import SourceTag from '../components/SourceTag';
import Toast from '../components/Toast';
import { useAuth } from '../context/AuthContext';
import { GOAL_CATEGORIES, GROUP_CHIP_STYLES } from '../utils/taskCategories';

const OVERVIEW_GROUPS = [
  { key: 'learning', label: 'Learning', color: 'var(--color-primary)' },
  { key: 'practice', label: 'Practice', color: 'var(--color-completed)' },
  { key: 'projects', label: 'Projects', color: 'var(--color-pending)' },
  { key: 'applications', label: 'Applications', color: '#6366F1' },
];

const FORMULA_TEXT =
  'Task completion rate = completed tasks / planned tasks × 100 (RESCHEDULED excluded from both sides).';

function getCurrentMonthInTimeZone(timeZone) {
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
    return { month: `${y}-${m}`, todayIso: `${y}-${m}-${d}` };
  } catch {
    return {
      month: format(new Date(), 'yyyy-MM'),
      todayIso: format(new Date(), 'yyyy-MM-dd'),
    };
  }
}

export default function MonthlyPlannerPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const tzClock = useMemo(() => getCurrentMonthInTimeZone(user?.timeZone), [user?.timeZone]);

  const [selectedMonth, setSelectedMonth] = useState(tzClock.month);
  const [monthly, setMonthly] = useState(null);
  const [goals, setGoals] = useState([]);
  const [prevGoals, setPrevGoals] = useState([]);
  const [prevMonthly, setPrevMonthly] = useState(null);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  // Opening Question
  const [showOpeningQuestion, setShowOpeningQuestion] = useState(false);
  const [openingPanel, setOpeningPanel] = useState(''); // '' | 'prev' | 'carry'
  const [selectedCarryGoalIds, setSelectedCarryGoalIds] = useState([]);

  // Add Goal Modal
  const [goalModalOpen, setGoalModalOpen] = useState(false);
  const [goalForm, setGoalForm] = useState({
    title: '',
    category: 'TECHNOLOGIES_TO_LEARN',
    targetCount: '5',
    doneCount: '0',
    description: '',
  });
  const [goalError, setGoalError] = useState('');
  const [savingGoal, setSavingGoal] = useState(false);

  const monthStartIso = `${selectedMonth}-01`;

  const loadMonthData = useCallback(async () => {
    setLoading(true);
    try {
      const prevMonth = format(subMonths(parseISO(monthStartIso), 1), 'yyyy-MM');
      const prevMonthStart = `${prevMonth}-01`;

      const [mData, gData, prevMData, prevGData] = await Promise.all([
        fetchMonthlyProgress(selectedMonth),
        fetchGoals('MONTH', monthStartIso),
        fetchMonthlyProgress(prevMonth),
        fetchGoals('MONTH', prevMonthStart),
      ]);

      setMonthly(mData);
      setGoals(gData || []);
      setPrevMonthly(prevMData);
      setPrevGoals(prevGData || []);
      setSelectedCarryGoalIds([]);
    } catch (err) {
      setToast({ message: err?.message || 'Unable to load monthly planner.', type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [selectedMonth, monthStartIso]);

  useEffect(() => {
    loadMonthData();
  }, [loadMonthData]);

  const shiftMonth = (delta) => {
    const base = parseISO(monthStartIso);
    const next = delta > 0 ? addMonths(base, delta) : subMonths(base, Math.abs(delta));
    setSelectedMonth(format(next, 'yyyy-MM'));
  };

  // Build 7-col Mon-Sun calendar grid
  const calendarCells = useMemo(() => {
    const first = startOfMonth(parseISO(monthStartIso));
    const last = endOfMonth(first);
    // Convert Sunday=0..Saturday=6 into Monday=0..Sunday=6 offset
    const leadingBlanks = (getDay(first) + 6) % 7;
    const summaryMap = new Map((monthly?.daySummaries || []).map((s) => [s.date, s]));

    const cells = [];
    for (let i = 0; i < leadingBlanks; i++) {
      cells.push({ empty: true, key: `blank-${i}` });
    }
    for (let d = first; d <= last; d = addDays(d, 1)) {
      const iso = format(d, 'yyyy-MM-dd');
      cells.push({
        empty: false,
        iso,
        dayNum: format(d, 'd'),
        summary: summaryMap.get(iso) || null,
      });
    }
    return cells;
  }, [monthStartIso, monthly]);

  const primaryGoal = goals[0] || null;
  const primaryGoalPercent = primaryGoal
    ? Math.min(100, Math.round((primaryGoal.doneCount * 100) / Math.max(1, primaryGoal.targetCount)))
    : null;

  const unfinishedPrevGoals = useMemo(
    () => prevGoals.filter((g) => g.status !== 'COMPLETED' && g.status !== 'RESCHEDULED'),
    [prevGoals]
  );

  const handleCreateGoal = async (e) => {
    e.preventDefault();
    if (savingGoal) return;
    if (!goalForm.title.trim()) {
      setGoalError('Goal title is required.');
      return;
    }
    setGoalError('');
    setSavingGoal(true);
    try {
      await createGoal({
        title: goalForm.title.trim(),
        category: goalForm.category,
        scope: 'MONTH',
        periodStart: monthStartIso,
        targetCount: Math.max(1, Number(goalForm.targetCount) || 1),
        doneCount: Math.max(0, Number(goalForm.doneCount) || 0),
        description: goalForm.description.trim() || null,
        status: 'PENDING',
      });
      setGoalModalOpen(false);
      setGoalForm({
        title: '',
        category: 'TECHNOLOGIES_TO_LEARN',
        targetCount: '5',
        doneCount: '0',
        description: '',
      });
      setToast({ message: 'Monthly goal added.', type: 'success' });
      await loadMonthData();
    } catch (err) {
      setGoalError(err?.message || 'Unable to create goal.');
    } finally {
      setSavingGoal(false);
    }
  };

  const handleIncrementManualGoal = async (goal, delta) => {
    const nextDone = Math.max(0, goal.doneCount + delta);
    try {
      await updateGoal(goal.id, {
        title: goal.title,
        category: goal.category,
        scope: 'MONTH',
        periodStart: goal.periodStart,
        targetCount: goal.targetCount,
        doneCount: nextDone,
        description: goal.description,
        status: nextDone >= goal.targetCount ? 'COMPLETED' : 'PENDING',
      });
      await loadMonthData();
    } catch (err) {
      setToast({ message: err?.message || 'Could not update goal.', type: 'error' });
    }
  };

  const handleDeleteGoal = async (goal) => {
    try {
      await deleteGoal(goal.id);
      setToast({ message: `Removed "${goal.title}".`, type: 'success' });
      await loadMonthData();
    } catch (err) {
      setToast({ message: err?.message || 'Could not remove goal.', type: 'error' });
    }
  };

  const handleCarryGoals = async () => {
    if (selectedCarryGoalIds.length === 0) return;
    try {
      await carryForwardGoals({
        goalIds: selectedCarryGoalIds,
        targetPeriodStart: monthStartIso,
      });
      setOpeningPanel('');
      setShowOpeningQuestion(false);
      setToast({
        message: `Carried forward ${selectedCarryGoalIds.length} goal(s) to ${selectedMonth}.`,
        type: 'success',
      });
      await loadMonthData();
    } catch (err) {
      setToast({ message: err?.message || 'Could not carry forward goals.', type: 'error' });
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Monthly Planner"
        breadcrumb="Planner > Monthly Planner"
        rightSlot={
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex items-center rounded-pill border border-border bg-surface p-1 shadow-sm">
              <button
                type="button"
                onClick={() => shiftMonth(-1)}
                aria-label="Previous month"
                className="rounded-pill px-2.5 py-1 text-sm font-semibold text-body hover:bg-canvas hover:text-ink"
              >
                ‹
              </button>
              <input
                type="month"
                value={selectedMonth}
                onChange={(e) => e.target.value && setSelectedMonth(e.target.value)}
                aria-label="Select month"
                className="bg-transparent px-2 text-xs sm:text-sm font-semibold text-ink focus:outline-none cursor-pointer"
              />
              <button
                type="button"
                onClick={() => shiftMonth(1)}
                aria-label="Next month"
                className="rounded-pill px-2.5 py-1 text-sm font-semibold text-body hover:bg-canvas hover:text-ink"
              >
                ›
              </button>
            </div>

            <button
              type="button"
              onClick={() => setShowOpeningQuestion((prev) => !prev)}
              className="rounded-pill border border-border bg-surface px-3.5 py-2 text-xs sm:text-sm font-medium text-primary hover:bg-primary-soft"
            >
              {showOpeningQuestion ? 'Hide options' : 'Month options'}
            </button>

            <button
              type="button"
              onClick={() => setGoalModalOpen(true)}
              className="rounded-pill bg-primary px-4 py-2 text-xs sm:text-sm font-semibold text-white hover:bg-primary-hover shadow-sm"
            >
              + Add Goal
            </button>
          </div>
        }
      />

      {/* OPENING QUESTION */}
      {(showOpeningQuestion || (!loading && goals.length === 0 && (monthly?.plannedCount || 0) === 0)) && (
        <Card>
          <h2 className="text-base font-bold text-ink pb-3 border-b border-border/70">
            Plan {format(parseISO(monthStartIso), 'MMMM yyyy')}
          </h2>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-4">
            <button
              type="button"
              onClick={() => setOpeningPanel(openingPanel === 'prev' ? '' : 'prev')}
              className="text-left rounded-card border border-border bg-canvas/50 p-4 hover:border-primary/40 hover:bg-surface transition-all duration-fast"
            >
              <h3 className="text-sm font-bold text-ink">Review previous month&apos;s activities</h3>
              <p className="mt-1 text-xs text-muted">
                {prevMonthly?.plannedCount > 0
                  ? `${prevMonthly.completedCount}/${prevMonthly.plannedCount} tasks completed (${Math.round(prevMonthly.completionRate || 0)}%).`
                  : 'No tasks recorded in the previous month.'}
              </p>
            </button>

            <button
              type="button"
              onClick={() => setOpeningPanel(openingPanel === 'carry' ? '' : 'carry')}
              className="text-left rounded-card border border-border bg-canvas/50 p-4 hover:border-primary/40 hover:bg-surface transition-all duration-fast"
            >
              <h3 className="text-sm font-bold text-ink">Carry forward unfinished goals</h3>
              <p className="mt-1 text-xs text-muted">
                {unfinishedPrevGoals.length} unfinished goal(s) from previous month.
              </p>
            </button>

            <button
              type="button"
              onClick={() => setGoalModalOpen(true)}
              className="text-left rounded-card border border-border bg-canvas/50 p-4 hover:border-primary/40 hover:bg-surface transition-all duration-fast"
            >
              <h3 className="text-sm font-bold text-ink">Create a new monthly plan</h3>
              <p className="mt-1 text-xs text-muted">
                Add new learning, coding, project, or job application targets.
              </p>
            </button>
          </div>

          {openingPanel === 'carry' && (
            <div className="mt-4 rounded-xl border border-border bg-canvas p-4 space-y-3">
              {unfinishedPrevGoals.length === 0 ? (
                <p className="text-xs text-muted">No unfinished goals from the previous month.</p>
              ) : (
                <>
                  <ul className="space-y-2">
                    {unfinishedPrevGoals.map((g) => (
                      <li
                        key={g.id}
                        className="flex items-center justify-between gap-2 rounded-xl bg-surface border border-border/70 px-3 py-2 text-xs"
                      >
                        <label className="flex items-center gap-2 cursor-pointer min-w-0">
                          <input
                            type="checkbox"
                            checked={selectedCarryGoalIds.includes(g.id)}
                            onChange={() =>
                              setSelectedCarryGoalIds((prev) =>
                                prev.includes(g.id)
                                  ? prev.filter((id) => id !== g.id)
                                  : [...prev, g.id]
                              )
                            }
                            className="h-4 w-4 rounded border-border text-primary"
                          />
                          <span className="font-medium text-ink truncate">{g.title}</span>
                        </label>
                        <span className="text-muted shrink-0">
                          {g.doneCount}/{g.targetCount}
                        </span>
                      </li>
                    ))}
                  </ul>
                  <div className="flex justify-end">
                    <button
                      type="button"
                      disabled={selectedCarryGoalIds.length === 0}
                      onClick={handleCarryGoals}
                      className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
                    >
                      Carry forward ({selectedCarryGoalIds.length})
                    </button>
                  </div>
                </>
              )}
            </div>
          )}
        </Card>
      )}

      {/* TOP: This Month's Goal + Progress */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="flex flex-col justify-between">
          <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
            <h2 className="text-base font-bold text-ink">This Month&apos;s Goal</h2>
            {primaryGoal && <SourceTag source={primaryGoal.doneCountSource} />}
          </div>

          <div className="mt-4">
            <p className="text-sm font-semibold text-ink">
              {primaryGoal
                ? primaryGoal.title
                : `No primary goal set for ${format(parseISO(monthStartIso), 'MMMM yyyy')} yet.`}
            </p>
            <p className="mt-1 text-xs text-muted">
              Each month stays separate — switching months never overwrites previous plans.
            </p>
          </div>
        </Card>

        <Card className="flex flex-col justify-between">
          <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
            <h2 className="text-base font-bold text-ink">Progress</h2>
            <SourceTag source={primaryGoal ? primaryGoal.doneCountSource : 'CALCULATED'} />
          </div>

          <div className="mt-4 space-y-2.5">
            {primaryGoal ? (
              <>
                <div className="flex items-center justify-between text-sm">
                  <span className="font-semibold text-ink">
                    {primaryGoal.doneCount}/{primaryGoal.targetCount} {primaryGoal.categoryLabel.toLowerCase()}
                  </span>
                  <span className="font-bold text-primary">{primaryGoalPercent}%</span>
                </div>
                <ProgressBar value={primaryGoalPercent} />
              </>
            ) : monthly?.completionRate != null ? (
              <>
                <div className="flex items-center justify-between text-sm">
                  <span className="font-semibold text-ink">
                    {monthly.completedCount}/{monthly.plannedCount} tasks
                  </span>
                  <span className="font-bold text-primary">
                    {Math.round(monthly.completionRate)}%
                  </span>
                </div>
                <ProgressBar value={monthly.completionRate} />
              </>
            ) : (
              <p className="text-sm text-muted">No monthly goals or tasks recorded yet.</p>
            )}
          </div>
        </Card>
      </div>

      {/* LEFT: Calendar Grid | RIGHT: Monthly Tasks / Goals */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6 items-start">
        <Card className="xl:col-span-6">
          <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
            <h2 className="text-base font-bold text-ink">
              {format(parseISO(monthStartIso), 'MMMM yyyy')} Calendar
            </h2>
            <span className="text-xs text-muted">Click a day to open Daily Planner</span>
          </div>

          <div className="mt-4 grid grid-cols-7 gap-1.5 text-center text-[11px] font-semibold text-muted">
            {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => (
              <div key={d} className="py-1">
                {d}
              </div>
            ))}
          </div>

          <div className="grid grid-cols-7 gap-1.5">
            {calendarCells.map((cell) => {
              if (cell.empty) {
                return <div key={cell.key} className="h-12 rounded-xl" />;
              }
              const isToday = cell.iso === tzClock.todayIso;
              const s = cell.summary;
              return (
                <button
                  key={cell.iso}
                  type="button"
                  onClick={() => navigate('/app/planner/daily')}
                  className={`h-12 rounded-xl border flex flex-col items-center justify-center text-xs transition-colors duration-fast ${
                    isToday
                      ? 'bg-primary text-white border-primary font-bold shadow-sm'
                      : 'bg-canvas/60 text-ink border-border/70 hover:bg-primary-soft'
                  }`}
                >
                  <span>{cell.dayNum}</span>
                  <span className="mt-1 flex h-1.5 items-center gap-0.5">
                    {s?.completedCount > 0 && <span className="h-1.5 w-1.5 rounded-full bg-completed" />}
                    {s?.pendingCount > 0 && <span className="h-1.5 w-1.5 rounded-full bg-pending" />}
                    {s?.skippedCount > 0 && <span className="h-1.5 w-1.5 rounded-full bg-skipped" />}
                  </span>
                </button>
              );
            })}
          </div>
        </Card>

        <Card className="xl:col-span-6">
          <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
            <h2 className="text-base font-bold text-ink">Monthly Tasks / Goals</h2>
            <button
              type="button"
              onClick={() => setGoalModalOpen(true)}
              className="text-xs font-semibold text-primary hover:underline"
            >
              + Add Goal
            </button>
          </div>

          {goals.length === 0 ? (
            <div className="py-6">
              <EmptyState
                title="No goals for this month yet"
                description="Add measurable targets for technologies, coding problems, projects, or applications."
                action={
                  <button
                    type="button"
                    onClick={() => setGoalModalOpen(true)}
                    className="rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
                  >
                    + Add Goal
                  </button>
                }
              />
            </div>
          ) : (
            <ul className="mt-4 space-y-3">
              {goals.map((g) => {
                const chipClass = GROUP_CHIP_STYLES[g.colorGroup] || GROUP_CHIP_STYLES.Personal;
                const pct = Math.min(
                  100,
                  Math.round((g.doneCount * 100) / Math.max(1, g.targetCount))
                );
                return (
                  <li
                    key={g.id}
                    className="rounded-card border border-border/80 bg-canvas/50 p-3.5 space-y-2.5"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-ink">{g.title}</p>
                        <div className="mt-1 flex flex-wrap items-center gap-2">
                          <span
                            className={`inline-flex items-center px-2.5 py-0.5 text-[11px] font-medium rounded-pill border ${chipClass}`}
                          >
                            {g.categoryLabel}
                          </span>
                          <SourceTag source={g.doneCountSource} />
                        </div>
                      </div>

                      <div className="flex items-center gap-2 shrink-0">
                        <span className="text-sm font-bold text-ink">
                          {g.doneCount}/{g.targetCount}
                        </span>
                        {g.doneCountSource === 'MANUAL' && (
                          <button
                            type="button"
                            onClick={() => handleIncrementManualGoal(g, 1)}
                            aria-label={`Increment ${g.title}`}
                            className="rounded-lg border border-border bg-surface px-2 py-0.5 text-xs font-bold text-primary hover:bg-primary-soft"
                          >
                            +1
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => handleDeleteGoal(g)}
                          aria-label={`Delete ${g.title}`}
                          className="rounded-lg px-2 py-0.5 text-xs text-muted hover:text-skipped"
                        >
                          ✕
                        </button>
                      </div>
                    </div>

                    <ProgressBar value={pct} />
                    <p className="text-[11px] text-muted">{g.calculationNote}</p>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>
      </div>

      {/* MONTH OVERVIEW: 4 Rings with previous-month change chip only when previous month has data */}
      <Card>
        <div className="flex items-center justify-between gap-2 pb-4 border-b border-border/70">
          <h2 className="text-base font-bold text-ink">MONTH OVERVIEW</h2>
          <SourceTag source="CALCULATED" />
        </div>

        <div className="mt-5 grid grid-cols-2 md:grid-cols-4 gap-6 place-items-center">
          {OVERVIEW_GROUPS.map((group) => {
            const rate = monthly?.categoryRates?.[group.key];
            const delta = monthly?.categoryChanges?.[group.key];
            return (
              <div key={group.key} className="flex flex-col items-center gap-2">
                {rate == null ? (
                  <div className="flex flex-col items-center gap-1.5">
                    <div className="h-24 w-24 rounded-full border-[8px] border-border flex items-center justify-center text-xs font-medium text-muted text-center px-2">
                      No tasks
                    </div>
                    <span className="text-xs font-medium text-body">{group.label}</span>
                  </div>
                ) : (
                  <ProgressRing
                    percentage={rate}
                    label={group.label}
                    color={group.color}
                    formulaText={FORMULA_TEXT}
                  />
                )}

                {delta != null && (
                  <span
                    className={`rounded-pill px-2.5 py-0.5 text-[11px] font-semibold ${
                      delta >= 0
                        ? 'bg-completed-soft text-completed'
                        : 'bg-skipped-soft text-skipped'
                    }`}
                  >
                    {delta >= 0 ? `+${delta}%` : `${delta}%`} vs prev month
                  </span>
                )}
              </div>
            );
          })}
        </div>
      </Card>

      {/* MONTHLY SUMMARY + MILESTONES */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
              <h2 className="text-base font-bold text-ink">MONTHLY SUMMARY</h2>
              <SourceTag source="CALCULATED" />
            </div>

            <div className="mt-4 space-y-3 text-sm">
              <p className="text-body">
                {monthly?.plannedCount > 0
                  ? `${monthly.completedCount} of ${monthly.plannedCount} planned tasks completed (${Math.round(
                      monthly.completionRate || 0
                    )}%).`
                  : `No tasks planned for ${selectedMonth} yet.`}
              </p>

              <div className="rounded-xl border border-dashed border-border bg-canvas p-3 text-xs text-muted">
                <span className="font-semibold text-ink">AI summary:</span> Disabled until Prompts 11 &amp; 12 connect the proposal-only backend assistant.
              </div>
            </div>
          </div>

          <div className="mt-5 pt-3 border-t border-border/70">
            <Link
              to={`/app/reports/${selectedMonth}`}
              className="inline-flex items-center rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
            >
              View Full Report →
            </Link>
          </div>
        </Card>

        <Card>
          <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
            <h2 className="text-base font-bold text-ink">Milestones</h2>
            <Link to="/app/projects" className="text-xs font-semibold text-primary hover:underline">
              Open Projects
            </Link>
          </div>

          {!monthly?.milestones?.length ? (
            <div className="py-4">
              <EmptyState
                title="No dated milestones this month"
                description="Project milestones with due dates in this month will appear here."
              />
            </div>
          ) : (
            <ul className="mt-4 space-y-2.5">
              {monthly.milestones.map((m) => (
                <li
                  key={m.id}
                  className="flex items-center justify-between gap-3 rounded-xl border border-border/70 bg-canvas/60 px-3.5 py-2.5 text-xs"
                >
                  <div className="min-w-0">
                    <p className="font-semibold text-ink truncate">{m.title}</p>
                    <p className="text-muted mt-0.5">Due {m.dueDate}</p>
                  </div>
                  <CategoryChip label={m.status} />
                </li>
              ))}
            </ul>
          )}

          <div className="mt-6 pt-4 border-t border-border/70 space-y-2.5">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                Project Work Logs ({monthly?.projectWorklogs?.length || 0})
              </h3>
              <SourceTag source="MANUAL" />
            </div>
            {!monthly?.projectWorklogs?.length ? (
              <p className="text-xs text-muted">
                No project work logs recorded in {selectedMonth} yet.
              </p>
            ) : (
              <ul className="space-y-2 max-h-56 overflow-y-auto pr-1">
                {monthly.projectWorklogs.map((wl) => (
                  <li
                    key={wl.id}
                    className="rounded-xl border border-border/70 bg-canvas/60 px-3.5 py-2.5 text-xs space-y-1"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-ink truncate">{wl.projectTitle}</span>
                      <span className="shrink-0 text-muted">
                        {wl.workDate} · {wl.minutesSpent} min
                      </span>
                    </div>
                    <p className="text-body line-clamp-2">{wl.summary}</p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      </div>

      {/* ADD GOAL MODAL */}
      <Modal open={goalModalOpen} title="Add Monthly Goal" onClose={() => setGoalModalOpen(false)}>
        <form onSubmit={handleCreateGoal} noValidate className="space-y-3.5">
          {goalError && (
            <p role="alert" className="rounded-xl bg-skipped-soft p-2.5 text-xs font-medium text-skipped">
              {goalError}
            </p>
          )}
          <div>
            <label htmlFor="goal-title" className="block text-xs font-semibold text-ink mb-1">
              Goal Title *
            </label>
            <input
              id="goal-title"
              type="text"
              value={goalForm.title}
              onChange={(e) => setGoalForm((p) => ({ ...p, title: e.target.value }))}
              placeholder="e.g. Solve 50 LeetCode problems"
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>

          <div>
            <label htmlFor="goal-category" className="block text-xs font-semibold text-ink mb-1">
              Category
            </label>
            <select
              id="goal-category"
              value={goalForm.category}
              onChange={(e) => setGoalForm((p) => ({ ...p, category: e.target.value }))}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink bg-surface focus:outline-none focus:border-primary"
            >
              {GOAL_CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="goal-target" className="block text-xs font-semibold text-ink mb-1">
                Target Count
              </label>
              <input
                id="goal-target"
                type="number"
                min={1}
                value={goalForm.targetCount}
                onChange={(e) => setGoalForm((p) => ({ ...p, targetCount: e.target.value }))}
                className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
              />
            </div>
            <div>
              <label htmlFor="goal-done" className="block text-xs font-semibold text-ink mb-1">
                Initial Done Count
              </label>
              <input
                id="goal-done"
                type="number"
                min={0}
                value={goalForm.doneCount}
                onChange={(e) => setGoalForm((p) => ({ ...p, doneCount: e.target.value }))}
                className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
              />
            </div>
          </div>

          <div className="pt-2 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setGoalModalOpen(false)}
              className="rounded-pill border border-border px-4 py-2 text-xs font-semibold text-body hover:bg-canvas"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={savingGoal}
              className="rounded-pill bg-primary px-5 py-2 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
            >
              {savingGoal ? 'Saving...' : 'Create Goal'}
            </button>
          </div>
        </form>
      </Modal>

      <Toast
        message={toast.message}
        type={toast.type}
        onDismiss={() => setToast({ message: '', type: 'info' })}
      />
    </div>
  );
}
