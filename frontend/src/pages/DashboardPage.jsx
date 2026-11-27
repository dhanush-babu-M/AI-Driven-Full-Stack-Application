import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  createAiTaskProposal,
  fetchAiRecommendation,
  fetchDashboard,
} from '../api/client';
import { notifyAiInboxChanged, openAiAgent, openAiInbox } from '../hooks/useAiInbox';
import Card from '../components/Card';
import CategoryChip from '../components/CategoryChip';
import EmptyState from '../components/EmptyState';
import ProfileQuickLinkPills from '../components/ProfileQuickLinkPills';
import ProgressBar from '../components/ProgressBar';
import SourceTag from '../components/SourceTag';
import StatTile from '../components/StatTile';
import Toast from '../components/Toast';
import UpcomingFollowupsCard from '../components/jobs/UpcomingFollowupsCard';
import { JobStatusChip } from '../components/jobs/jobMeta';
import { useAuth } from '../context/AuthContext';

const DAILY_FORMULA_TEXT =
  'Task completion rate = completed tasks / planned tasks × 100. Rescheduled tasks are excluded from both sides of the formula.';
const PROJECT_FORMULA_TEXT =
  'Project progress = completed milestones / planned milestones × 100 (only shown when milestones exist).';

const CATEGORY_LEGEND = [
  { key: 'learning', label: 'Learning', dotClass: 'bg-primary' },
  { key: 'practice', label: 'Practice', dotClass: 'bg-completed' },
  { key: 'projects', label: 'Projects', dotClass: 'bg-pending' },
  { key: 'applications', label: 'Applications', dotClass: 'bg-indigo-500' },
];

const WEEKDAY_ROWS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

const HEATMAP_LEVEL_CLASSES = {
  '-1': 'bg-transparent',
  0: 'bg-slate-100 border border-slate-200/70',
  1: 'bg-emerald-200',
  2: 'bg-emerald-400',
  3: 'bg-emerald-500',
  4: 'bg-emerald-700',
};

function getTimeZoneClock(timeZone) {
  const safeTz = timeZone || 'Asia/Kolkata';
  const now = new Date();
  try {
    const hourParts = new Intl.DateTimeFormat('en-US', {
      timeZone: safeTz,
      hour: 'numeric',
      hour12: false,
    }).formatToParts(now);
    const hour = Number(hourParts.find((p) => p.type === 'hour')?.value ?? now.getHours());

    const dateParts = new Intl.DateTimeFormat('en-CA', {
      timeZone: safeTz,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(now);
    const y = dateParts.find((p) => p.type === 'year')?.value;
    const m = dateParts.find((p) => p.type === 'month')?.value;
    const d = dateParts.find((p) => p.type === 'day')?.value;

    const displayDate = new Intl.DateTimeFormat('en-GB', {
      timeZone: safeTz,
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(now);

    return {
      hour,
      isoDate: `${y}-${m}-${d}`,
      displayDate,
    };
  } catch {
    return {
      hour: now.getHours(),
      isoDate: now.toISOString().slice(0, 10),
      displayDate: now.toLocaleDateString('en-GB', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      }),
    };
  }
}

function getGreetingPrefix(hour) {
  if (hour < 12) return 'Good Morning';
  if (hour < 17) return 'Good Afternoon';
  return 'Good Evening';
}

function FormulaTooltip({ text }) {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        onBlur={() => setOpen(false)}
        className="text-xs font-medium text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
      >
        How is this calculated?
      </button>
      {open && (
        <div
          role="tooltip"
          className="absolute left-0 sm:left-1/2 sm:-translate-x-1/2 bottom-full mb-2 w-64 max-w-[calc(100vw-3rem)] z-30 rounded-xl bg-ink text-white text-xs p-3 shadow-card-hover leading-relaxed"
        >
          {text}
        </div>
      )}
    </div>
  );
}

function DailyHeroRing({ completionRate, completedCount, plannedCount }) {
  const size = 140;
  const strokeWidth = 11;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const isEmpty = completionRate === null || completionRate === undefined || plannedCount === 0;
  const clamped = isEmpty ? 0 : Math.max(0, Math.min(100, Number(completionRate)));
  const offset = circumference - (clamped / 100) * circumference;

  return (
    <div className="flex flex-col items-center gap-2.5 shrink-0">
      <div
        className="relative inline-flex items-center justify-center"
        style={{ width: size, height: size }}
      >
        <svg width={size} height={size} className="-rotate-90" aria-hidden="true">
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="transparent"
            stroke="var(--color-border)"
            strokeWidth={strokeWidth}
          />
          {!isEmpty && (
            <circle
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="transparent"
              stroke="var(--color-primary)"
              strokeWidth={strokeWidth}
              strokeLinecap="round"
              strokeDasharray={circumference}
              strokeDashoffset={offset}
              className="transition-all duration-base"
            />
          )}
        </svg>

        <div className="absolute inset-0 flex flex-col items-center justify-center text-center px-3">
          {isEmpty ? (
            <>
              <span className="text-lg font-bold text-muted">—</span>
              <span className="text-[11px] font-medium text-muted leading-tight">
                No tasks planned yet
              </span>
            </>
          ) : (
            <>
              <span className="text-2xl font-bold text-ink leading-none">
                {Math.round(clamped)}%
              </span>
              <span className="mt-1 text-xs font-medium text-muted">Completed</span>
              <span className="text-[11px] text-slate-500">
                {completedCount}/{plannedCount}
              </span>
            </>
          )}
        </div>
      </div>

      <FormulaTooltip text={DAILY_FORMULA_TEXT} />
    </div>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const timeZone = user?.timeZone || 'Asia/Kolkata';
  const clock = getTimeZoneClock(timeZone);

  const [dashboard, setDashboard] = useState(null);
  const [aiRec, setAiRec] = useState(null);
  const [aiRefreshing, setAiRefreshing] = useState(false);
  const [proposingTask, setProposingTask] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [hideGithubCard, setHideGithubCard] = useState(false);
  const [toast, setToast] = useState({ message: '', type: 'info' });

  const loadDashboard = async () => {
    setLoading(true);
    setError('');
    try {
      const [data, recData] = await Promise.all([
        fetchDashboard(clock.isoDate),
        fetchAiRecommendation({ date: clock.isoDate }).catch(() => null),
      ]);
      setDashboard(data);
      if (recData) setAiRec(recData);
    } catch (err) {
      setError(err?.message || 'Unable to load dashboard progress.');
    } finally {
      setLoading(false);
    }
  };

  const handleRefreshRecommendation = async () => {
    setAiRefreshing(true);
    try {
      const recData = await fetchAiRecommendation({ date: clock.isoDate, refresh: true });
      setAiRec(recData);
    } catch (err) {
      setToast({ message: err?.message || 'Unable to refresh suggestion.', type: 'error' });
    } finally {
      setAiRefreshing(false);
    }
  };

  const handleProposeTask = async () => {
    const draft = recommendation?.proposedTask;
    if (!draft) return;
    setProposingTask(true);
    try {
      await createAiTaskProposal({
        title: draft.title,
        description: draft.description,
        category: draft.category || 'CODING_PRACTICE',
        taskDate: draft.taskDate || clock.isoDate,
        priority: draft.priority || 'MEDIUM',
      });
      notifyAiInboxChanged();
      setToast({
        message: 'Suggested task queued in Approval Inbox for your review.',
        type: 'success',
      });
    } catch (err) {
      setToast({ message: err?.message || 'Unable to queue task proposal.', type: 'error' });
    } finally {
      setProposingTask(false);
    }
  };

  useEffect(() => {
    loadDashboard();
    // Re-load if signed-in user changes
  }, [user?.id]);

  const greetingName =
    dashboard?.greetingName ||
    user?.fullName?.replace('(DEMO)', '').trim().split(/\s+/)[0] ||
    'there';

  const today = dashboard?.today || {
    plannedCount: 0,
    completedCount: 0,
    pendingCount: 0,
    skippedCount: 0,
    rescheduledCount: 0,
    completionRate: null,
    categoryRates: {},
    focusTasks: [],
  };

  const monthly = dashboard?.monthly || {
    month: clock.isoDate.slice(0, 7),
    plannedCount: 0,
    completedCount: 0,
    completionRate: null,
  };

  const codingStreak = dashboard?.codingStreak || {
    days: 0,
    recordedToday: false,
    message: 'No coding entries recorded yet.',
    dataSource: 'CALCULATED',
  };

  const recommendation = aiRec || dashboard?.recommendation || {
    text: 'No tasks planned for today yet. Add 2–3 focused tasks to build daily momentum.',
    source: 'RULE',
    disclaimer: 'Rule-based from your saved records.',
    actionLabel: 'Plan today',
    actionPath: '/app/planner/daily',
  };

  const githubWeeks = dashboard?.githubWeeks || [];
  const hasGithubActivity = Boolean(dashboard?.hasGithubActivity);
  const currentProject = dashboard?.currentProject || null;
  const recentApplications = dashboard?.recentApplications || [];
  const links = dashboard?.links || {};
  const sourceBadge = dashboard?.demo ? 'DEMO' : 'CALCULATED';

  return (
    <div className="space-y-6">
      {/* Greeting Header */}
      <header className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-2xl sm:text-3xl font-bold text-ink tracking-tight">
              {getGreetingPrefix(clock.hour)}, {greetingName}!{' '}
              <span aria-hidden="true">👋</span>
            </h1>
            {dashboard?.demo && <SourceTag source="DEMO" />}
          </div>
          <p className="mt-1 text-sm text-muted">Today is {clock.displayDate}</p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <Link
            to="/app/planner/daily"
            className="inline-flex items-center justify-center rounded-pill bg-primary px-4 py-2 text-xs sm:text-sm font-semibold text-white hover:bg-primary-hover transition-colors duration-fast"
          >
            Plan today
          </Link>
        </div>
      </header>

      {error && (
        <div
          role="alert"
          className="rounded-card border border-skipped/30 bg-skipped-soft p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
        >
          <p className="text-sm font-medium text-skipped">{error}</p>
          <button
            type="button"
            onClick={loadDashboard}
            className="self-start sm:self-auto rounded-pill bg-surface px-3.5 py-1.5 text-xs font-semibold text-ink border border-border hover:bg-canvas"
          >
            Retry
          </button>
        </div>
      )}

      {loading && !dashboard ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-12 gap-5 sm:gap-6">
          {[5, 4, 3, 3, 3, 3, 3].map((span, idx) => (
            <Card key={idx} className={`xl:col-span-${span} animate-pulse min-h-[180px]`}>
              <div className="h-4 w-32 rounded bg-slate-200" />
              <div className="mt-4 h-20 rounded-xl bg-slate-100" />
            </Card>
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-12 gap-5 sm:gap-6 items-stretch">
          {/* ROW 1: Daily Progress | Today's Focus | AI Suggestion */}
          <Card className="md:col-span-2 xl:col-span-5 flex flex-col justify-between">
            <div className="flex items-center justify-between gap-2 pb-4 border-b border-border/70">
              <h2 className="text-base font-semibold text-ink">Daily Progress</h2>
              <SourceTag source={sourceBadge} />
            </div>

            <div className="py-4 flex flex-col sm:flex-row items-center gap-6">
              <DailyHeroRing
                completionRate={today.completionRate}
                completedCount={today.completedCount}
                plannedCount={today.plannedCount}
              />

              <div className="w-full flex-1 space-y-3">
                {CATEGORY_LEGEND.map((item) => {
                  const rate = today.categoryRates?.[item.key];
                  return (
                    <div
                      key={item.key}
                      className="flex items-center justify-between gap-3 text-sm"
                    >
                      <span className="flex items-center gap-2 text-body font-medium min-w-0">
                        <span
                          className={`h-2.5 w-2.5 rounded-full shrink-0 ${item.dotClass}`}
                          aria-hidden="true"
                        />
                        <span className="truncate">{item.label}</span>
                      </span>
                      <span className="font-semibold text-ink shrink-0">
                        {rate === null || rate === undefined
                          ? 'No tasks'
                          : `${Math.round(rate)}%`}
                      </span>
                    </div>
                  );
                })}

                {today.plannedCount === 0 && (
                  <div className="pt-2">
                    <Link
                      to="/app/planner/daily"
                      className="inline-flex items-center justify-center rounded-pill bg-primary-soft px-3.5 py-1.5 text-xs font-semibold text-primary hover:bg-primary hover:text-white transition-colors duration-fast"
                    >
                      Plan today →
                    </Link>
                  </div>
                )}
              </div>
            </div>
          </Card>

          <Card className="xl:col-span-4 flex flex-col justify-between">
            <div className="flex items-center justify-between gap-2 pb-4 border-b border-border/70">
              <h2 className="text-base font-semibold text-ink">Today&apos;s Focus</h2>
              <Link
                to="/app/planner/daily"
                className="text-xs font-semibold text-primary hover:underline"
              >
                Open planner
              </Link>
            </div>

            {today.focusTasks.length === 0 ? (
              <div className="py-4">
                <EmptyState
                  title="No focus tasks today"
                  description="Add your top tasks in Daily Planner to track them here."
                  action={
                    <Link
                      to="/app/planner/daily"
                      className="inline-flex items-center rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
                    >
                      Plan today
                    </Link>
                  }
                />
              </div>
            ) : (
              <ul className="mt-4 space-y-2.5 flex-1">
                {today.focusTasks.map((task) => {
                  const isDone = task.status === 'COMPLETED';
                  return (
                    <li
                      key={task.id}
                      className="flex items-start justify-between gap-3 rounded-xl border border-border/70 bg-canvas/60 px-3 py-2.5"
                    >
                      <label
                        title="Task status updates arrive in Prompt 5 (Planner)"
                        className="flex items-start gap-2.5 min-w-0 cursor-not-allowed"
                      >
                        <input
                          type="checkbox"
                          checked={isDone}
                          disabled
                          aria-label={task.title}
                          className="mt-0.5 h-4 w-4 rounded border-border text-primary disabled:opacity-70"
                        />
                        <span className="min-w-0">
                          <span
                            className={`block text-sm font-medium leading-snug break-words ${
                              isDone ? 'line-through text-muted' : 'text-ink'
                            }`}
                          >
                            {task.title}
                          </span>
                          <span className="mt-1 inline-flex items-center gap-2">
                            <CategoryChip category={task.category} />
                          </span>
                        </span>
                      </label>
                      {task.durationMinutes != null && (
                        <span className="shrink-0 text-xs font-medium text-muted">
                          {task.durationMinutes}m
                        </span>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>

          <Card className="xl:col-span-3 !bg-primary-soft !border-primary/20 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between gap-2 pb-3 border-b border-primary/15">
                <span className="flex items-center gap-2">
                  <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary text-white">
                    <svg
                      className="h-4 w-4"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      aria-hidden="true"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        d="M9 18h6M10 22h4M12 2a7 7 0 0 0-4 12.74V17a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1v-2.26A7 7 0 0 0 12 2z"
                      />
                    </svg>
                  </span>
                  <h2 className="text-base font-semibold text-ink">AI Suggestion</h2>
                </span>
                <div className="flex items-center gap-1.5">
                  <SourceTag source={recommendation.source} />
                  <button
                    type="button"
                    onClick={handleRefreshRecommendation}
                    disabled={aiRefreshing}
                    title="Refresh recommendation"
                    className="rounded-lg px-1.5 py-0.5 text-[11px] font-semibold text-primary hover:bg-white/70 disabled:opacity-50"
                  >
                    {aiRefreshing ? '…' : '↻'}
                  </button>
                </div>
              </div>

              {recommendation.notice && (
                <p className="mt-3 rounded-lg border border-amber-300/70 bg-amber-50 px-2.5 py-1.5 text-[11px] text-amber-900">
                  {recommendation.notice}
                </p>
              )}

              <p className="mt-3 text-sm text-ink leading-relaxed">
                {recommendation.text}
              </p>

              <p className="mt-2 text-[11px] text-muted">
                {recommendation.source === 'AI'
                  ? recommendation.disclaimer || 'AI-generated. Based on your saved records.'
                  : 'Rule-based from your saved records.'}
              </p>
            </div>

            <div className="mt-4 pt-3 border-t border-primary/15 space-y-2">
              {recommendation.proposedTask && (
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={handleProposeTask}
                    disabled={proposingTask}
                    className="rounded-pill bg-primary px-3 py-1 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                  >
                    {proposingTask ? 'Adding...' : '+ Add as task'}
                  </button>
                  <button
                    type="button"
                    onClick={openAiInbox}
                    className="rounded-pill border border-primary/30 bg-surface px-2.5 py-1 text-[11px] font-semibold text-primary hover:bg-white"
                  >
                    Approval Inbox
                  </button>
                </div>
              )}

              <div className="flex items-center justify-between gap-2">
                <Link
                  to={recommendation.actionPath || '/app/planner/daily'}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:text-primary-hover hover:underline"
                >
                  <span>{recommendation.actionLabel || 'View Suggestion'}</span>
                  <span aria-hidden="true">→</span>
                </Link>
                <button
                  type="button"
                  onClick={openAiAgent}
                  className="text-xs font-semibold text-ink hover:text-primary hover:underline"
                >
                  Ask Agent
                </button>
              </div>
            </div>
          </Card>

          {/* ROW 2: Today's Tasks | Coding Streak | GitHub Activity | Project Progress */}
          <Card className="xl:col-span-3 flex flex-col justify-between">
            <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
              <h2 className="text-base font-semibold text-ink">Today&apos;s Tasks</h2>
              <SourceTag source={sourceBadge} />
            </div>

            <div className="mt-4 grid grid-cols-3 gap-2">
              <StatTile
                label="Completed"
                value={today.completedCount}
                className="!p-3 !shadow-none !bg-completed-soft !border-completed/20 [&>div:nth-child(2)]:text-completed"
              />
              <StatTile
                label="Pending"
                value={today.pendingCount}
                className="!p-3 !shadow-none !bg-pending-soft !border-pending/30 [&>div:nth-child(2)]:text-amber-700"
              />
              <StatTile
                label="Skipped"
                value={today.skippedCount}
                className="!p-3 !shadow-none !bg-skipped-soft !border-skipped/20 [&>div:nth-child(2)]:text-skipped"
              />
            </div>

            <p className="mt-3 text-xs text-muted">
              {today.plannedCount === 0
                ? 'No tasks planned yet today.'
                : `${today.plannedCount} planned${
                    today.rescheduledCount > 0
                      ? ` · ${today.rescheduledCount} rescheduled (excluded)`
                      : ''
                  }`}
            </p>
          </Card>

          <Card className="xl:col-span-3 flex flex-col justify-between">
            <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
              <h2 className="text-base font-semibold text-ink">Coding Streak</h2>
              <SourceTag source={codingStreak.dataSource} />
            </div>

            <div className="py-4 flex items-center gap-3">
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-pending-soft text-amber-600">
                <svg
                  className="h-6 w-6"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M12 2c1 4-2 6-2 9a4 4 0 0 0 8 0c0-2.5-1.5-4.5-3-6 .5 2-1 3.5-2 3.5S12 6 12 2z"
                  />
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M8.5 14.5A5.5 5.5 0 1 0 18 13"
                  />
                </svg>
              </span>
              <div className="min-w-0">
                <p className="text-2xl font-bold text-ink leading-tight">
                  {codingStreak.days} {codingStreak.days === 1 ? 'day' : 'days'}
                </p>
                <p className="mt-1 text-xs text-muted leading-relaxed">
                  {codingStreak.message}
                </p>
              </div>
            </div>

            <div className="pt-2 border-t border-border/70">
              <Link
                to="/app/coding"
                className="text-xs font-semibold text-primary hover:underline"
              >
                {codingStreak.days === 0 ? 'Log coding →' : 'Open Coding Activity →'}
              </Link>
            </div>
          </Card>

          {!hideGithubCard && (
            <Card className="xl:col-span-3 flex flex-col justify-between">
              <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
                <div className="flex items-center gap-2 min-w-0">
                  <h2 className="text-base font-semibold text-ink truncate">
                    GitHub Activity
                  </h2>
                  <SourceTag source={dashboard?.demo ? 'DEMO' : 'IMPORTED'} />
                </div>
                <button
                  type="button"
                  onClick={() => setHideGithubCard(true)}
                  aria-label="Hide GitHub activity"
                  title="Hide GitHub activity"
                  className="rounded-lg p-1 text-muted hover:bg-canvas hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  ✕
                </button>
              </div>

              {!hasGithubActivity ? (
                <div className="py-3">
                  <EmptyState
                    title="No GitHub activity yet"
                    description="Connect your GitHub handle in Settings to import events."
                    action={
                      <Link
                        to="/app/settings"
                        className="inline-flex items-center rounded-pill bg-primary px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover"
                      >
                        Connect in Settings
                      </Link>
                    }
                  />
                </div>
              ) : (
                <div className="py-3 overflow-x-auto">
                  <div className="inline-flex items-start gap-2">
                    <div className="grid grid-rows-7 gap-1 text-[10px] text-muted pr-1">
                      {WEEKDAY_ROWS.map((dayLabel, idx) => (
                        <span key={dayLabel} className="h-3 leading-3">
                          {idx % 2 === 0 ? dayLabel.slice(0, 1) : ''}
                        </span>
                      ))}
                    </div>
                    <div
                      className="grid gap-1"
                      style={{
                        gridTemplateColumns: `repeat(${githubWeeks.length}, minmax(0, 1fr))`,
                      }}
                    >
                      {githubWeeks.map((week, wIdx) => (
                        <div key={wIdx} className="grid grid-rows-7 gap-1">
                          {week.days.map((day) => (
                            <span
                              key={day.date}
                              title={
                                day.level < 0
                                  ? ''
                                  : `${day.date}: ${day.count} event(s)`
                              }
                              className={`h-3 w-3 rounded-[3px] ${
                                HEATMAP_LEVEL_CLASSES[day.level] ||
                                HEATMAP_LEVEL_CLASSES[0]
                              }`}
                            />
                          ))}
                        </div>
                      ))}
                    </div>
                  </div>
                  <p className="mt-2.5 text-[11px] text-muted">
                    Last {githubWeeks.length} weeks (Mon–Sun)
                  </p>
                </div>
              )}
            </Card>
          )}

          <Card
            className={`${
              hideGithubCard ? 'xl:col-span-6' : 'xl:col-span-3'
            } flex flex-col justify-between`}
          >
            <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
              <h2 className="text-base font-semibold text-ink">Project Progress</h2>
              {currentProject && <SourceTag source={currentProject.dataSource} />}
            </div>

            {!currentProject ? (
              <div className="py-3">
                <EmptyState
                  title="No active project"
                  description="Add a project and milestones to track real progress."
                  action={
                    <Link
                      to="/app/projects"
                      className="inline-flex items-center rounded-pill bg-primary px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover"
                    >
                      Add project
                    </Link>
                  }
                />
              </div>
            ) : (
              <div className="py-4 space-y-3">
                <div>
                  <p className="text-sm font-semibold text-ink truncate">
                    {currentProject.name}
                  </p>
                  <p className="text-xs text-muted mt-0.5">
                    {currentProject.completedMilestones} of{' '}
                    {currentProject.totalMilestones} milestones completed
                  </p>
                </div>

                {currentProject.progress === null ? (
                  <p className="text-xs font-medium text-muted bg-canvas rounded-xl px-3 py-2">
                    No milestones added yet.
                  </p>
                ) : (
                  <ProgressBar
                    value={currentProject.progress}
                    label="Milestone completion"
                  />
                )}

                <FormulaTooltip text={PROJECT_FORMULA_TEXT} />
              </div>
            )}

            <div className="pt-2 border-t border-border/70">
              <Link
                to={currentProject ? `/app/projects/${currentProject.id}` : '/app/projects'}
                className="text-xs font-semibold text-primary hover:underline"
              >
                {currentProject ? 'Open Project →' : 'View Projects →'}
              </Link>
            </div>
          </Card>

          {/* ROW 3: Monthly Progress | Recent Job Applications | Quick Links */}
          <Card className="xl:col-span-4 flex flex-col justify-between">
            <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
              <h2 className="text-base font-semibold text-ink">Monthly Progress</h2>
              <SourceTag source={sourceBadge} />
            </div>

            <div className="py-4 space-y-3">
              {monthly.completionRate === null ? (
                <EmptyState
                  title="No tasks planned this month"
                  description={`No planned tasks recorded for ${monthly.month} yet.`}
                  action={
                    <Link
                      to="/app/planner/monthly"
                      className="inline-flex items-center rounded-pill bg-primary px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover"
                    >
                      Plan month
                    </Link>
                  }
                />
              ) : (
                <>
                  <ProgressBar
                    value={monthly.completionRate}
                    label={`${monthly.month} completion (${monthly.completedCount}/${monthly.plannedCount})`}
                  />
                  <FormulaTooltip text={DAILY_FORMULA_TEXT} />
                </>
              )}
            </div>

            <div className="pt-2 border-t border-border/70">
              <Link
                to={`/app/reports/${monthly.month}`}
                className="text-xs font-semibold text-primary hover:underline"
              >
                Open Monthly Report →
              </Link>
            </div>
          </Card>

          <Card className="xl:col-span-4 flex flex-col justify-between">
            <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
              <h2 className="text-base font-semibold text-ink">
                Recent Job Applications
              </h2>
              <Link
                to="/app/projects?tab=applications"
                className="text-xs font-semibold text-primary hover:underline"
              >
                View all
              </Link>
            </div>

            {recentApplications.length === 0 ? (
              <div className="py-4">
                <EmptyState
                  title="No applications yet"
                  description="Track your job applications and status updates in one place."
                  action={
                    <Link
                      to="/app/projects?tab=applications"
                      className="inline-flex items-center rounded-pill bg-primary px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover"
                    >
                      Track applications
                    </Link>
                  }
                />
              </div>
            ) : (
              <ul className="mt-4 space-y-2.5 flex-1">
                {recentApplications.map((app) => (
                  <li key={app.id}>
                    <Link
                      to={`/app/projects?tab=applications&jobId=${app.id}`}
                      className="flex items-center justify-between gap-3 rounded-xl border border-border/70 bg-canvas/60 px-3 py-2.5 hover:border-primary/30 transition-colors"
                    >
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-ink truncate">
                          {app.roleTitle}
                        </p>
                        <p className="text-xs text-muted truncate">
                          {app.companyName} · {app.appliedDate}
                        </p>
                      </div>
                      <JobStatusChip status={app.status} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card className="md:col-span-2 xl:col-span-4 flex flex-col justify-between">
            <div className="flex items-center justify-between gap-2 pb-3 border-b border-border/70">
              <h2 className="text-base font-semibold text-ink">Quick Links</h2>
              <Link
                to="/app/settings"
                className="text-xs font-semibold text-primary hover:underline"
              >
                Manage in Settings
              </Link>
            </div>

            <div className="mt-4 flex-1 space-y-3">
              <ProfileQuickLinkPills links={links} layout="grid" />

              {(links.linkedinUrl || links.codingbatUrl) && (
                <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-border/60 text-xs">
                  {links.linkedinUrl && (
                    <a
                      href={links.linkedinUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="rounded-pill border border-border bg-canvas px-3 py-1 font-medium text-ink hover:bg-primary-soft hover:text-primary"
                    >
                      LinkedIn ↗
                    </a>
                  )}
                  {links.codingbatUrl && (
                    <a
                      href={links.codingbatUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="rounded-pill border border-border bg-canvas px-3 py-1 font-medium text-ink hover:bg-primary-soft hover:text-primary"
                    >
                      CodingBat ↗
                    </a>
                  )}
                </div>
              )}
            </div>
          </Card>

          {/* ROW 4: Upcoming Job Follow-ups */}
          <div className="md:col-span-2 xl:col-span-12">
            <UpcomingFollowupsCard
              followups={dashboard?.upcomingFollowups || []}
              onUpdated={loadDashboard}
              onToast={(message, type = 'info') => setToast({ message, type })}
              showViewAllLink
            />
          </div>
        </div>
      )}

      <Toast
        message={toast.message}
        type={toast.type}
        onDismiss={() => setToast({ message: '', type: 'info' })}
      />
    </div>
  );
}
