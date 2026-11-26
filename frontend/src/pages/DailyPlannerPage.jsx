import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { addDays, endOfMonth, format, parseISO, startOfMonth } from 'date-fns';
import {
  carryForwardTasks,
  confirmRestDay,
  createLearningRecord,
  createTask,
  deleteTask,
  fetchDailyLog,
  fetchMonthSummary,
  fetchTaskHistory,
  fetchTasksByDate,
  fetchUnfinishedBefore,
  saveDailyNote,
  updateTask,
  updateTaskReminder,
  updateTaskStatus,
} from '../api/client';
import Card from '../components/Card';
import CategoryChip from '../components/CategoryChip';
import EmptyState from '../components/EmptyState';
import Modal from '../components/Modal';
import PageHeader from '../components/PageHeader';
import SourceTag from '../components/SourceTag';
import StatusChip from '../components/StatusChip';
import Toast from '../components/Toast';
import { useAuth } from '../context/AuthContext';
import { notifyRemindersChanged } from '../hooks/useReminders';
import {
  TASK_CATEGORIES,
  TASK_PRIORITIES,
  TASK_STATUSES,
  formatMinutesAsDuration,
  formatTime12h,
} from '../utils/taskCategories';

function getTodayIsoInTimeZone(timeZone) {
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
    return `${y}-${m}-${d}`;
  } catch {
    return format(new Date(), 'yyyy-MM-dd');
  }
}

function makeEmptyForm(taskDate) {
  return {
    title: '',
    description: '',
    category: 'JAVA_LEARNING',
    taskDate,
    startTime: '',
    endTime: '',
    priority: 'MEDIUM',
    status: 'PLANNED',
    notes: '',
  };
}

export default function DailyPlannerPage() {
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const queryDate = searchParams.get('date');

  const [selectedDate, setSelectedDate] = useState(() =>
    queryDate || getTodayIsoInTimeZone(user?.timeZone)
  );

  useEffect(() => {
    if (queryDate && /^\d{4}-\d{2}-\d{2}$/.test(queryDate)) {
      setSelectedDate(queryDate);
    }
  }, [queryDate]);

  const [tasks, setTasks] = useState([]);
  const [unfinishedTasks, setUnfinishedTasks] = useState([]);
  const [monthSummaries, setMonthSummaries] = useState([]);
  const [dailyLog, setDailyLog] = useState({
    date: selectedDate,
    dayStatus: 'UNRECORDED',
    reflectionNote: '',
    learningRecords: [],
    totalLearningMinutes: 0,
  });

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [toast, setToast] = useState({ message: '', type: 'info' });

  // Step 1 Chooser state
  const [forceChooser, setForceChooser] = useState(false);
  const [chooserMode, setChooserMode] = useState(''); // '' | 'continue' | 'carry'
  const [selectedCarryIds, setSelectedCarryIds] = useState([]);
  const [carryingForward, setCarryingForward] = useState(false);

  // Task Modal (Add / Edit)
  const [taskModalOpen, setTaskModalOpen] = useState(false);
  const [editingTask, setEditingTask] = useState(null);
  const [formValues, setFormValues] = useState(() => makeEmptyForm(selectedDate));
  const [formErrors, setFormErrors] = useState({});
  const [savingTask, setSavingTask] = useState(false);

  // Row Menu / Reschedule / History Modals
  const [openMenuTaskId, setOpenMenuTaskId] = useState(null);
  const [rescheduleTarget, setRescheduleTarget] = useState(null);
  const [rescheduleDate, setRescheduleDate] = useState('');
  const [historyModal, setHistoryModal] = useState({
    open: false,
    taskTitle: '',
    items: [],
    loading: false,
  });

  // Daily Notes & Learning Records
  const [noteDraft, setNoteDraft] = useState('');
  const [savingNote, setSavingNote] = useState(false);
  const [topicInput, setTopicInput] = useState('');
  const [minutesInput, setMinutesInput] = useState('30');
  const [savingLearning, setSavingLearning] = useState(false);

  const selectedMonth = selectedDate.slice(0, 7);

  const loadDayData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [dayTasks, logData, summaryData, prevUnfinished] = await Promise.all([
        fetchTasksByDate(selectedDate),
        fetchDailyLog(selectedDate),
        fetchMonthSummary(selectedMonth),
        fetchUnfinishedBefore(selectedDate),
      ]);
      setTasks(dayTasks || []);
      setDailyLog(logData);
      setNoteDraft(logData?.reflectionNote || '');
      setMonthSummaries(summaryData || []);
      setUnfinishedTasks(prevUnfinished || []);
      setSelectedCarryIds([]);
    } catch (err) {
      setError(err?.message || 'Unable to load daily planner data.');
    } finally {
      setLoading(false);
    }
  }, [selectedDate, selectedMonth]);

  useEffect(() => {
    loadDayData();
  }, [loadDayData]);

  useEffect(() => {
    setForceChooser(false);
    setChooserMode('');
    setOpenMenuTaskId(null);
  }, [selectedDate]);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
  };

  const shiftDay = (delta) => {
    const next = addDays(parseISO(selectedDate), delta);
    setSelectedDate(format(next, 'yyyy-MM-dd'));
  };

  // Calendar strip days for current month
  const calendarDays = useMemo(() => {
    const first = startOfMonth(parseISO(selectedDate));
    const last = endOfMonth(first);
    const summaryMap = new Map(monthSummaries.map((s) => [s.date, s]));
    const days = [];
    for (let d = first; d <= last; d = addDays(d, 1)) {
      const iso = format(d, 'yyyy-MM-dd');
      days.push({
        iso,
        dayNumber: format(d, 'd'),
        weekday: format(d, 'EEE'),
        summary: summaryMap.get(iso) || null,
      });
    }
    return days;
  }, [selectedDate, monthSummaries]);

  // Continue from previous activity: group unfinished tasks from most recent earlier day
  const lastRecordedPreviousTasks = useMemo(() => {
    if (unfinishedTasks.length === 0) return { date: null, items: [] };
    const latestDate = unfinishedTasks[0].taskDate;
    return {
      date: latestDate,
      items: unfinishedTasks.filter((t) => t.taskDate === latestDate),
    };
  }, [unfinishedTasks]);

  const nextMealTask = useMemo(() => {
    const mealTasks = tasks.filter((t) => t.category === 'MEALS_PERSONAL');
    return (
      mealTasks.find((t) => t.status === 'PLANNED' || t.status === 'PENDING') ||
      mealTasks[0] ||
      null
    );
  }, [tasks]);

  const showStepOneChooser =
    forceChooser || (!loading && tasks.length === 0 && dailyLog.dayStatus !== 'REST_DAY');

  const openAddModal = () => {
    setEditingTask(null);
    setFormValues(makeEmptyForm(selectedDate));
    setFormErrors({});
    setTaskModalOpen(true);
  };

  const openEditModal = (task) => {
    setOpenMenuTaskId(null);
    setEditingTask(task);
    setFormValues({
      title: task.title || '',
      description: task.description || '',
      category: task.category || 'JAVA_LEARNING',
      taskDate: task.taskDate || selectedDate,
      startTime: task.startTime ? task.startTime.slice(0, 5) : '',
      endTime: task.endTime ? task.endTime.slice(0, 5) : '',
      priority: task.priority || 'MEDIUM',
      status: task.status === 'RESCHEDULED' ? 'PLANNED' : task.status || 'PLANNED',
      notes: task.notes || '',
    });
    setFormErrors({});
    setTaskModalOpen(true);
  };

  const validateTaskForm = () => {
    const errs = {};
    if (!formValues.title.trim()) {
      errs.title = 'Task title is required.';
    }
    if (!formValues.taskDate) {
      errs.taskDate = 'Task date is required.';
    }
    if (formValues.startTime && formValues.endTime && formValues.endTime <= formValues.startTime) {
      errs.endTime = 'End time must be after start time.';
    }
    return errs;
  };

  const handleTaskSubmit = async (e) => {
    e.preventDefault();
    if (savingTask) return;

    const errs = validateTaskForm();
    setFormErrors(errs);
    if (Object.keys(errs).length > 0) return;

    const payload = {
      title: formValues.title.trim(),
      description: formValues.description.trim() || null,
      category: formValues.category,
      taskDate: formValues.taskDate,
      startTime: formValues.startTime || null,
      endTime: formValues.endTime || null,
      priority: formValues.priority,
      status: formValues.status,
      notes: formValues.notes.trim() || null,
    };

    setSavingTask(true);
    try {
      if (editingTask) {
        await updateTask(editingTask.id, payload);
        showToast('Task updated and change logged in history.');
      } else {
        await createTask(payload);
        showToast('Task added to Daily Planner.');
      }
      setTaskModalOpen(false);
      setForceChooser(false);
      notifyRemindersChanged();
      await loadDayData();
    } catch (err) {
      if (err?.fieldErrors && Object.keys(err.fieldErrors).length > 0) {
        setFormErrors(err.fieldErrors);
      } else {
        setFormErrors({ general: err?.message || 'Unable to save task.' });
      }
    } finally {
      setSavingTask(false);
    }
  };

  const handleStatusChange = async (task, nextStatus, extra = {}) => {
    setOpenMenuTaskId(null);
    try {
      await updateTaskStatus(task.id, { status: nextStatus, ...extra });
      showToast(`Marked "${task.title}" as ${nextStatus.toLowerCase()}.`);
      notifyRemindersChanged();
      await loadDayData();
    } catch (err) {
      showToast(err?.message || 'Could not update task status.', 'error');
    }
  };

  const handleDeleteTask = async (task) => {
    setOpenMenuTaskId(null);
    try {
      await deleteTask(task.id);
      showToast(`Removed "${task.title}" (kept in audit history).`);
      notifyRemindersChanged();
      await loadDayData();
    } catch (err) {
      showToast(err?.message || 'Could not delete task.', 'error');
    }
  };

  const handleRescheduleSubmit = async (e) => {
    e.preventDefault();
    if (!rescheduleTarget || !rescheduleDate) return;
    try {
      await updateTaskStatus(rescheduleTarget.id, {
        status: 'RESCHEDULED',
        rescheduleToDate: rescheduleDate,
      });
      setRescheduleTarget(null);
      showToast(`Rescheduled to ${rescheduleDate}. Original kept as RESCHEDULED.`);
      await loadDayData();
    } catch (err) {
      showToast(err?.message || 'Could not reschedule task.', 'error');
    }
  };

  const handleCarryForwardSubmit = async (idsToCarry) => {
    if (!idsToCarry.length || carryingForward) return;
    setCarryingForward(true);
    try {
      await carryForwardTasks({ taskIds: idsToCarry, targetDate: selectedDate });
      setForceChooser(false);
      setChooserMode('');
      showToast(`Carried forward ${idsToCarry.length} task(s) to ${selectedDate}.`);
      await loadDayData();
    } catch (err) {
      showToast(err?.message || 'Could not carry forward tasks.', 'error');
    } finally {
      setCarryingForward(false);
    }
  };

  const handleToggleMealReminder = async (task) => {
    try {
      const nextEnabled = !task.reminderEnabled;
      await updateTaskReminder(task.id, {
        enabled: nextEnabled,
        reminderTime: task.startTime || null,
      });
      showToast(
        nextEnabled
          ? 'Reminder saved for open-tab alerts.'
          : 'Reminder turned off.'
      );
      notifyRemindersChanged();
      await loadDayData();
    } catch (err) {
      showToast(err?.message || 'Could not save reminder choice.', 'error');
    }
  };

  const handleOpenHistory = async (task) => {
    setOpenMenuTaskId(null);
    setHistoryModal({ open: true, taskTitle: task.title, items: [], loading: true });
    try {
      const items = await fetchTaskHistory(task.id);
      setHistoryModal({ open: true, taskTitle: task.title, items: items || [], loading: false });
    } catch (err) {
      setHistoryModal({ open: false, taskTitle: '', items: [], loading: false });
      showToast(err?.message || 'Could not load task history.', 'error');
    }
  };

  const handleSaveDailyNote = async (e) => {
    e.preventDefault();
    if (savingNote) return;
    setSavingNote(true);
    try {
      const updated = await saveDailyNote(selectedDate, noteDraft);
      setDailyLog(updated);
      showToast('Daily reflection note saved.');
    } catch (err) {
      showToast(err?.message || 'Could not save note.', 'error');
    } finally {
      setSavingNote(false);
    }
  };

  const handleConfirmRestDay = async () => {
    try {
      const updated = await confirmRestDay(selectedDate, noteDraft);
      setDailyLog(updated);
      setForceChooser(false);
      showToast('Rest day confirmed. Rest days are never marked as failures.');
      await loadDayData();
    } catch (err) {
      showToast(err?.message || 'Could not confirm rest day.', 'error');
    }
  };

  const handleAddLearningRecord = async (e) => {
    e.preventDefault();
    if (!topicInput.trim() || savingLearning) return;
    const mins = Number(minutesInput);
    if (!mins || mins < 1) {
      showToast('Minutes spent must be at least 1.', 'error');
      return;
    }
    setSavingLearning(true);
    try {
      await createLearningRecord({
        recordDate: selectedDate,
        topic: topicInput.trim(),
        minutesSpent: mins,
      });
      setTopicInput('');
      setMinutesInput('30');
      showToast('Learning topic added.');
      await loadDayData();
    } catch (err) {
      showToast(err?.message || 'Could not save learning topic.', 'error');
    } finally {
      setSavingLearning(false);
    }
  };

  const toggleCarryCheckbox = (id) => {
    setSelectedCarryIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Daily Planner"
        breadcrumb="Planner > Daily Planner"
        rightSlot={
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex items-center rounded-pill border border-border bg-surface p-1 shadow-sm">
              <button
                type="button"
                onClick={() => shiftDay(-1)}
                aria-label="Previous day"
                className="rounded-pill px-2.5 py-1 text-sm font-semibold text-body hover:bg-canvas hover:text-ink"
              >
                ‹
              </button>
              <label className="inline-flex items-center gap-1.5 px-2 text-xs sm:text-sm font-semibold text-ink cursor-pointer">
                <span className="sr-only">Pick date</span>
                <input
                  type="date"
                  value={selectedDate}
                  onChange={(e) => e.target.value && setSelectedDate(e.target.value)}
                  aria-label="Select planner date"
                  className="bg-transparent text-xs sm:text-sm font-semibold text-ink focus:outline-none cursor-pointer"
                />
              </label>
              <button
                type="button"
                onClick={() => shiftDay(1)}
                aria-label="Next day"
                className="rounded-pill px-2.5 py-1 text-sm font-semibold text-body hover:bg-canvas hover:text-ink"
              >
                ›
              </button>
            </div>

            <button
              type="button"
              onClick={() => setForceChooser((prev) => !prev)}
              className="rounded-pill border border-border bg-surface px-3.5 py-2 text-xs sm:text-sm font-medium text-primary hover:bg-primary-soft transition-colors duration-fast"
            >
              {showStepOneChooser ? 'Hide plan mode' : 'Change plan mode'}
            </button>

            <button
              type="button"
              onClick={openAddModal}
              className="rounded-pill bg-primary px-4 py-2 text-xs sm:text-sm font-semibold text-white hover:bg-primary-hover shadow-sm transition-colors duration-fast"
            >
              + Add Task
            </button>
          </div>
        }
      />

      {error && (
        <div role="alert" className="rounded-card border border-skipped/30 bg-skipped-soft p-4 text-sm text-skipped">
          {error}
        </div>
      )}

      {/* MONTH CALENDAR STRIP */}
      <Card padding="p-4">
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold text-ink">
              {format(parseISO(selectedDate), 'MMMM yyyy')}
            </h2>
            {dailyLog.dayStatus === 'REST_DAY' && (
              <span className="rounded-pill bg-completed-soft px-2.5 py-0.5 text-xs font-semibold text-completed border border-completed/20">
                Rest Day Confirmed
              </span>
            )}
          </div>
          <div className="flex items-center gap-3 text-[11px] text-muted">
            <span className="inline-flex items-center gap-1">
              <span className="h-2 w-2 rounded-full bg-completed" /> Completed
            </span>
            <span className="inline-flex items-center gap-1">
              <span className="h-2 w-2 rounded-full bg-pending" /> Pending
            </span>
            <span className="inline-flex items-center gap-1">
              <span className="h-2 w-2 rounded-full bg-skipped" /> Skipped
            </span>
          </div>
        </div>

        <div className="flex gap-1.5 overflow-x-auto pb-1">
          {calendarDays.map((day) => {
            const isSelected = day.iso === selectedDate;
            const s = day.summary;
            return (
              <button
                key={day.iso}
                type="button"
                onClick={() => setSelectedDate(day.iso)}
                className={`flex min-w-[44px] flex-col items-center rounded-xl border px-2 py-2 text-xs transition-colors duration-fast shrink-0 ${
                  isSelected
                    ? 'bg-primary text-white border-primary shadow-sm'
                    : 'bg-canvas/70 text-body border-border/70 hover:bg-primary-soft hover:text-ink'
                }`}
              >
                <span className={`text-[10px] ${isSelected ? 'text-blue-100' : 'text-muted'}`}>
                  {day.weekday}
                </span>
                <span className="font-bold text-sm mt-0.5">{day.dayNumber}</span>
                <span className="mt-1 flex h-2 items-center gap-0.5">
                  {s?.completedCount > 0 && <span className="h-1.5 w-1.5 rounded-full bg-completed" />}
                  {s?.pendingCount > 0 && <span className="h-1.5 w-1.5 rounded-full bg-pending" />}
                  {s?.skippedCount > 0 && <span className="h-1.5 w-1.5 rounded-full bg-skipped" />}
                  {s?.dayStatus === 'REST_DAY' && !s.completedCount && !s.pendingCount && (
                    <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
                  )}
                </span>
              </button>
            );
          })}
        </div>
      </Card>

      {/* STEP 1 CHOOSER */}
      {showStepOneChooser && (
        <Card>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-4 border-b border-border/70">
            <div>
              <h2 className="text-base font-bold text-ink">
                Choose how you want to plan your day
              </h2>
              <p className="text-xs text-muted mt-0.5">
                Start fresh, carry forward unfinished work, or confirm a rest day for {selectedDate}.
              </p>
            </div>
            {dailyLog.dayStatus !== 'REST_DAY' && (
              <button
                type="button"
                onClick={handleConfirmRestDay}
                className="self-start rounded-pill border border-border bg-canvas px-3.5 py-1.5 text-xs font-semibold text-body hover:bg-completed-soft hover:text-completed transition-colors duration-fast"
              >
                Confirm Rest Day
              </button>
            )}
          </div>

          <div className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-4">
            <button
              type="button"
              onClick={() => {
                setChooserMode(chooserMode === 'continue' ? '' : 'continue');
                setSelectedCarryIds(lastRecordedPreviousTasks.items.map((t) => t.id));
              }}
              className={`text-left rounded-card border p-4 transition-all duration-fast ${
                chooserMode === 'continue'
                  ? 'border-primary bg-primary-soft/60'
                  : 'border-border bg-canvas/50 hover:border-primary/40 hover:bg-surface'
              }`}
            >
              <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-primary-soft text-primary font-bold">
                1
              </span>
              <h3 className="mt-3 text-sm font-bold text-ink">
                Continue from previous activity
              </h3>
              <p className="mt-1 text-xs text-muted">
                {lastRecordedPreviousTasks.date
                  ? `${lastRecordedPreviousTasks.items.length} unfinished task(s) from ${lastRecordedPreviousTasks.date}.`
                  : 'No unfinished tasks found from a previous day.'}
              </p>
            </button>

            <button
              type="button"
              onClick={() => {
                setChooserMode(chooserMode === 'carry' ? '' : 'carry');
                setSelectedCarryIds([]);
              }}
              className={`text-left rounded-card border p-4 transition-all duration-fast ${
                chooserMode === 'carry'
                  ? 'border-primary bg-primary-soft/60'
                  : 'border-border bg-canvas/50 hover:border-primary/40 hover:bg-surface'
              }`}
            >
              <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-completed-soft text-completed font-bold">
                2
              </span>
              <h3 className="mt-3 text-sm font-bold text-ink">
                Carry forward unfinished tasks
              </h3>
              <p className="mt-1 text-xs text-muted">
                {unfinishedTasks.length > 0
                  ? `Pick from ${unfinishedTasks.length} earlier unfinished task(s).`
                  : 'No earlier unfinished tasks available.'}
              </p>
            </button>

            <button
              type="button"
              onClick={openAddModal}
              className="text-left rounded-card border border-border bg-canvas/50 p-4 hover:border-primary/40 hover:bg-surface transition-all duration-fast"
            >
              <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-pending-soft text-amber-700 font-bold">
                3
              </span>
              <h3 className="mt-3 text-sm font-bold text-ink">Create new tasks</h3>
              <p className="mt-1 text-xs text-muted">
                Add study blocks, coding practice, meals, or applications for {selectedDate}.
              </p>
            </button>
          </div>

          {/* Checklist drawer inside Step 1 Chooser for Continue / Carry Forward */}
          {(chooserMode === 'continue' || chooserMode === 'carry') && (
            <div className="mt-4 rounded-xl border border-border bg-canvas/70 p-4 space-y-3">
              {(chooserMode === 'continue'
                ? lastRecordedPreviousTasks.items
                : unfinishedTasks
              ).length === 0 ? (
                <p className="text-xs text-muted">
                  No unfinished tasks from earlier days are available to carry forward.
                </p>
              ) : (
                <>
                  <ul className="space-y-2 max-h-56 overflow-y-auto">
                    {(chooserMode === 'continue'
                      ? lastRecordedPreviousTasks.items
                      : unfinishedTasks
                    ).map((item) => (
                      <li
                        key={item.id}
                        className="flex items-center justify-between gap-3 rounded-xl bg-surface border border-border/70 px-3 py-2"
                      >
                        <label className="flex items-center gap-2.5 min-w-0 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={selectedCarryIds.includes(item.id)}
                            onChange={() => toggleCarryCheckbox(item.id)}
                            className="h-4 w-4 rounded border-border text-primary"
                          />
                          <span className="text-sm font-medium text-ink truncate">
                            {item.title}
                          </span>
                        </label>
                        <div className="flex items-center gap-2 shrink-0">
                          <span className="text-[11px] text-muted">{item.taskDate}</span>
                          <CategoryChip category={item.category} />
                        </div>
                      </li>
                    ))}
                  </ul>
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      disabled={selectedCarryIds.length === 0 || carryingForward}
                      onClick={() => handleCarryForwardSubmit(selectedCarryIds)}
                      className="rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
                    >
                      {carryingForward
                        ? 'Carrying forward...'
                        : `Carry forward (${selectedCarryIds.length})`}
                    </button>
                  </div>
                </>
              )}
            </div>
          )}
        </Card>
      )}

      {/* MAIN GRID: Tasks List (left 8 cols) + Meal Reminder & Notes (right 4 cols) */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6 items-start">
        {/* TODAY'S TASKS CARD */}
        <Card className="xl:col-span-8">
          <div className="flex items-center justify-between gap-2 pb-4 border-b border-border/70">
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-ink">
                Tasks for {format(parseISO(selectedDate), 'd MMM yyyy')}
              </h2>
              <SourceTag source="MANUAL" />
            </div>
            <span className="text-xs font-medium text-muted">{tasks.length} total</span>
          </div>

          {loading ? (
            <div className="py-8 text-sm text-muted">Loading tasks...</div>
          ) : tasks.length === 0 ? (
            <div className="py-6">
              <EmptyState
                title={
                  dailyLog.dayStatus === 'REST_DAY'
                    ? 'Confirmed rest day'
                    : 'No tasks for this day yet'
                }
                description={
                  dailyLog.dayStatus === 'REST_DAY'
                    ? 'Enjoy your rest day. You can still add a task anytime if plans change.'
                    : 'Click "+ Add Task" or choose a planning mode above to get started.'
                }
                action={
                  <button
                    type="button"
                    onClick={openAddModal}
                    className="rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
                  >
                    + Add Task
                  </button>
                }
              />
            </div>
          ) : (
            <ul className="mt-4 space-y-3">
              {tasks.map((task) => {
                const isCompleted = task.status === 'COMPLETED';
                const isRescheduled = task.status === 'RESCHEDULED';
                const timeRange =
                  task.startTime && task.endTime
                    ? `${formatTime12h(task.startTime)} – ${formatTime12h(task.endTime)}`
                    : task.startTime
                      ? formatTime12h(task.startTime)
                      : 'Flexible time';

                return (
                  <li
                    key={task.id}
                    className="relative flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-card border border-border/80 bg-canvas/50 p-3.5 hover:bg-surface transition-colors duration-fast"
                  >
                    <div className="flex items-start gap-3 min-w-0 flex-1">
                      <input
                        type="checkbox"
                        checked={isCompleted}
                        disabled={isRescheduled}
                        onChange={() =>
                          handleStatusChange(task, isCompleted ? 'PENDING' : 'COMPLETED')
                        }
                        aria-label={`Mark ${task.title} as ${isCompleted ? 'pending' : 'completed'}`}
                        className="mt-1 h-4 w-4 rounded border-border text-primary focus:ring-primary"
                      />

                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span
                            className={`text-sm font-semibold break-words ${
                              isCompleted
                                ? 'line-through text-muted'
                                : isRescheduled
                                  ? 'text-muted'
                                  : 'text-ink'
                            }`}
                          >
                            {task.title}
                          </span>
                          <CategoryChip category={task.category} />
                          <StatusChip status={task.status} label={task.status} />
                        </div>

                        <div className="mt-1.5 flex flex-wrap items-center gap-3 text-xs text-muted">
                          <span className="inline-flex items-center gap-1">
                            <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                              <circle cx="12" cy="12" r="9" />
                              <path strokeLinecap="round" d="M12 7v5l3 2" />
                            </svg>
                            {timeRange}
                          </span>
                          {task.reminderEnabled && (
                            <span className="inline-flex items-center gap-1 text-primary font-medium">
                              <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                              </svg>
                              Reminder set
                            </span>
                          )}
                          <span>Priority: {task.priority}</span>
                          {task.carriedFromTaskId && (
                            <span className="text-primary font-medium">Carried forward</span>
                          )}
                          {task.rescheduledToDate && (
                            <span className="text-amber-700 font-medium">
                              Moved to {task.rescheduledToDate}
                            </span>
                          )}
                          <button
                            type="button"
                            onClick={() => handleOpenHistory(task)}
                            className="text-primary font-medium hover:underline"
                          >
                            View history
                          </button>
                          {task.startTime && !isRescheduled && (
                            <button
                              type="button"
                              onClick={() => handleToggleMealReminder(task)}
                              className="text-emerald-700 font-medium hover:underline"
                            >
                              {task.reminderEnabled ? 'Clear Reminder' : 'Set Reminder'}
                            </button>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Three-dot action menu */}
                    <div className="relative self-end sm:self-center shrink-0">
                      <button
                        type="button"
                        onClick={() =>
                          setOpenMenuTaskId((prev) => (prev === task.id ? null : task.id))
                        }
                        aria-label={`Actions for ${task.title}`}
                        aria-expanded={openMenuTaskId === task.id}
                        className="rounded-lg border border-border bg-surface px-2.5 py-1 text-sm font-bold text-body hover:bg-canvas hover:text-ink"
                      >
                        ⋮
                      </button>

                      {openMenuTaskId === task.id && (
                        <div
                          role="menu"
                          className="absolute right-0 mt-1.5 w-44 rounded-card border border-border bg-surface py-1.5 shadow-card-hover z-20 text-xs"
                        >
                          <button
                            type="button"
                            role="menuitem"
                            onClick={() => openEditModal(task)}
                            className="w-full text-left px-3.5 py-2 text-body hover:bg-canvas hover:text-ink"
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            role="menuitem"
                            onClick={() => handleStatusChange(task, 'PENDING')}
                            className="w-full text-left px-3.5 py-2 text-body hover:bg-canvas hover:text-ink"
                          >
                            Mark Pending
                          </button>
                          <button
                            type="button"
                            role="menuitem"
                            onClick={() => handleStatusChange(task, 'SKIPPED')}
                            className="w-full text-left px-3.5 py-2 text-body hover:bg-canvas hover:text-ink"
                          >
                            Skip
                          </button>
                          {!isRescheduled && (
                            <button
                              type="button"
                              role="menuitem"
                              onClick={() => {
                                setOpenMenuTaskId(null);
                                setRescheduleTarget(task);
                                setRescheduleDate(
                                  format(addDays(parseISO(task.taskDate), 1), 'yyyy-MM-dd')
                                );
                              }}
                              className="w-full text-left px-3.5 py-2 text-body hover:bg-canvas hover:text-ink"
                            >
                              Reschedule
                            </button>
                          )}
                          <button
                            type="button"
                            role="menuitem"
                            onClick={() => handleDeleteTask(task)}
                            className="w-full text-left px-3.5 py-2 text-skipped hover:bg-skipped-soft font-medium"
                          >
                            Delete
                          </button>
                        </div>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>

        {/* RIGHT SIDEBAR CARDS: Meal Reminder + Daily Notes & Learning */}
        <div className="xl:col-span-4 space-y-6">
          {/* MEAL AND FOOD REMINDER CARD */}
          <Card className="!bg-emerald-50/70 !border-emerald-200/80">
            <div className="flex items-center justify-between gap-2 pb-3 border-b border-emerald-200/70">
              <div className="flex items-center gap-2">
                <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-600 text-white">
                  <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                    <path strokeLinecap="round" strokeLinejoin="round" d="M13.73 21a2 2 0 0 1-3.46 0" />
                  </svg>
                </span>
                <h2 className="text-base font-bold text-ink">Meal &amp; Food Reminder</h2>
              </div>
            </div>

            {!nextMealTask ? (
              <p className="mt-3 text-xs text-body leading-relaxed">
                No Meals / Personal task scheduled for {selectedDate}. Add one (e.g. &ldquo;Eat breakfast - puri&rdquo;) to set a reminder.
              </p>
            ) : (
              <div className="mt-3 space-y-3">
                <div>
                  <p className="text-sm font-semibold text-ink">{nextMealTask.title}</p>
                  <p className="text-xs text-muted mt-0.5">
                    {nextMealTask.startTime
                      ? `${formatTime12h(nextMealTask.startTime)}${
                          nextMealTask.endTime ? ` – ${formatTime12h(nextMealTask.endTime)}` : ''
                        }`
                      : 'No time set'}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => handleToggleMealReminder(nextMealTask)}
                  className="rounded-pill bg-emerald-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-emerald-700 transition-colors duration-fast"
                >
                  {nextMealTask.reminderEnabled ? 'Reminder Saved ✓' : 'Set Reminder'}
                </button>
              </div>
            )}
          </Card>

          {/* DAILY NOTES & LEARNED TODAY */}
          <Card>
            <h2 className="text-base font-bold text-ink pb-3 border-b border-border/70">
              Daily Notes &amp; Learning
            </h2>

            <form onSubmit={handleSaveDailyNote} className="mt-4 space-y-2.5">
              <label htmlFor="daily-reflection-note" className="block text-xs font-semibold text-ink">
                What did you learn today? How was your progress?
              </label>
              <textarea
                id="daily-reflection-note"
                rows={3}
                value={noteDraft}
                onChange={(e) => setNoteDraft(e.target.value)}
                placeholder="Write honest notes about what clicked, blockers, or rest..."
                className="w-full rounded-xl border border-border bg-surface p-3 text-sm text-ink placeholder:text-slate-400 focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
              />
              <div className="flex justify-end">
                <button
                  type="submit"
                  disabled={savingNote}
                  className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                >
                  {savingNote ? 'Saving...' : 'Save Note'}
                </button>
              </div>
            </form>

            <div className="mt-5 pt-4 border-t border-border/70 space-y-3">
              <div className="flex items-center justify-between gap-2">
                <h3 className="text-sm font-bold text-ink">Learned today</h3>
                <span className="rounded-pill bg-primary-soft px-2.5 py-0.5 text-xs font-semibold text-primary">
                  Total: {formatMinutesAsDuration(dailyLog.totalLearningMinutes)}
                </span>
              </div>

              {dailyLog.learningRecords?.length === 0 ? (
                <p className="text-xs text-muted">
                  No learning topics logged for {selectedDate} yet.
                </p>
              ) : (
                <ul className="space-y-2">
                  {dailyLog.learningRecords.map((rec) => (
                    <li
                      key={rec.id}
                      className="flex items-center justify-between gap-2 rounded-xl bg-canvas px-3 py-2 text-xs"
                    >
                      <span className="font-medium text-ink truncate">{rec.topic}</span>
                      <span className="shrink-0 font-semibold text-primary">
                        {formatMinutesAsDuration(rec.minutesSpent)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}

              <form onSubmit={handleAddLearningRecord} className="flex gap-2 pt-1">
                <input
                  type="text"
                  value={topicInput}
                  onChange={(e) => setTopicInput(e.target.value)}
                  placeholder="Topic learned..."
                  aria-label="Topic learned"
                  className="flex-1 min-w-0 rounded-xl border border-border bg-surface px-3 py-1.5 text-xs text-ink focus:outline-none focus:border-primary"
                />
                <input
                  type="number"
                  min={1}
                  max={1440}
                  value={minutesInput}
                  onChange={(e) => setMinutesInput(e.target.value)}
                  aria-label="Minutes spent"
                  className="w-16 rounded-xl border border-border bg-surface px-2 py-1.5 text-xs text-ink focus:outline-none focus:border-primary"
                />
                <button
                  type="submit"
                  disabled={savingLearning}
                  className="rounded-pill bg-ink px-3 py-1.5 text-xs font-semibold text-white hover:bg-slate-800 shrink-0"
                >
                  Add
                </button>
              </form>
            </div>
          </Card>
        </div>
      </div>

      {/* ADD / EDIT TASK MODAL */}
      <Modal
        open={taskModalOpen}
        title={editingTask ? 'Edit Task' : 'Add Task'}
        onClose={() => setTaskModalOpen(false)}
      >
        <form onSubmit={handleTaskSubmit} noValidate className="space-y-3.5">
          {formErrors.general && (
            <p role="alert" className="rounded-xl bg-skipped-soft p-2.5 text-xs font-medium text-skipped">
              {formErrors.general}
            </p>
          )}

          <div>
            <label htmlFor="task-title" className="block text-xs font-semibold text-ink mb-1">
              Title *
            </label>
            <input
              id="task-title"
              type="text"
              value={formValues.title}
              onChange={(e) => setFormValues((p) => ({ ...p, title: e.target.value }))}
              placeholder="e.g. Eat breakfast - puri"
              aria-invalid={Boolean(formErrors.title)}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
            {formErrors.title && (
              <p role="alert" className="mt-1 text-xs font-medium text-red-700">{formErrors.title}</p>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="task-category" className="block text-xs font-semibold text-ink mb-1">
                Category
              </label>
              <select
                id="task-category"
                value={formValues.category}
                onChange={(e) => setFormValues((p) => ({ ...p, category: e.target.value }))}
                className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink bg-surface focus:outline-none focus:border-primary"
              >
                {TASK_CATEGORIES.map((cat) => (
                  <option key={cat.value} value={cat.value}>
                    {cat.label} ({cat.group})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="task-date" className="block text-xs font-semibold text-ink mb-1">
                Date
              </label>
              <input
                id="task-date"
                type="date"
                value={formValues.taskDate}
                onChange={(e) => setFormValues((p) => ({ ...p, taskDate: e.target.value }))}
                className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="task-start" className="block text-xs font-semibold text-ink mb-1">
                Start Time
              </label>
              <input
                id="task-start"
                type="time"
                value={formValues.startTime}
                onChange={(e) => setFormValues((p) => ({ ...p, startTime: e.target.value }))}
                className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
              />
            </div>
            <div>
              <label htmlFor="task-end" className="block text-xs font-semibold text-ink mb-1">
                End Time
              </label>
              <input
                id="task-end"
                type="time"
                value={formValues.endTime}
                onChange={(e) => setFormValues((p) => ({ ...p, endTime: e.target.value }))}
                aria-invalid={Boolean(formErrors.endTime)}
                className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
              />
              {formErrors.endTime && (
                <p role="alert" className="mt-1 text-xs font-medium text-red-700">{formErrors.endTime}</p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="task-priority" className="block text-xs font-semibold text-ink mb-1">
                Priority
              </label>
              <select
                id="task-priority"
                value={formValues.priority}
                onChange={(e) => setFormValues((p) => ({ ...p, priority: e.target.value }))}
                className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink bg-surface focus:outline-none focus:border-primary"
              >
                {TASK_PRIORITIES.map((p) => (
                  <option key={p.value} value={p.value}>{p.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="task-status" className="block text-xs font-semibold text-ink mb-1">
                Status
              </label>
              <select
                id="task-status"
                value={formValues.status}
                onChange={(e) => setFormValues((p) => ({ ...p, status: e.target.value }))}
                className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink bg-surface focus:outline-none focus:border-primary"
              >
                {TASK_STATUSES.map((s) => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label htmlFor="task-description" className="block text-xs font-semibold text-ink mb-1">
              Description
            </label>
            <input
              id="task-description"
              type="text"
              value={formValues.description}
              onChange={(e) => setFormValues((p) => ({ ...p, description: e.target.value }))}
              placeholder="Optional short detail"
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>

          <div>
            <label htmlFor="task-notes" className="block text-xs font-semibold text-ink mb-1">
              Notes (optional)
            </label>
            <textarea
              id="task-notes"
              rows={2}
              value={formValues.notes}
              onChange={(e) => setFormValues((p) => ({ ...p, notes: e.target.value }))}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>

          <div className="pt-2 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setTaskModalOpen(false)}
              className="rounded-pill border border-border px-4 py-2 text-xs font-semibold text-body hover:bg-canvas"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={savingTask}
              className="rounded-pill bg-primary px-5 py-2 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
            >
              {savingTask ? 'Saving...' : editingTask ? 'Save Changes' : 'Create Task'}
            </button>
          </div>
        </form>
      </Modal>

      {/* RESCHEDULE MODAL */}
      <Modal
        open={Boolean(rescheduleTarget)}
        title="Reschedule Task"
        onClose={() => setRescheduleTarget(null)}
      >
        <form onSubmit={handleRescheduleSubmit} className="space-y-4">
          <p className="text-xs text-muted">
            Rescheduling creates a linked copy on the new date and keeps{' '}
            <strong className="text-ink">&ldquo;{rescheduleTarget?.title}&rdquo;</strong> on{' '}
            {rescheduleTarget?.taskDate} as <strong className="text-ink">RESCHEDULED</strong>.
          </p>
          <div>
            <label htmlFor="reschedule-date" className="block text-xs font-semibold text-ink mb-1">
              New Date
            </label>
            <input
              id="reschedule-date"
              type="date"
              required
              value={rescheduleDate}
              onChange={(e) => setRescheduleDate(e.target.value)}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setRescheduleTarget(null)}
              className="rounded-pill border border-border px-4 py-2 text-xs font-semibold text-body hover:bg-canvas"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="rounded-pill bg-primary px-4 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
            >
              Confirm Reschedule
            </button>
          </div>
        </form>
      </Modal>

      {/* HISTORY MODAL */}
      <Modal
        open={historyModal.open}
        title={`Audit History — ${historyModal.taskTitle}`}
        onClose={() => setHistoryModal({ open: false, taskTitle: '', items: [], loading: false })}
      >
        {historyModal.loading ? (
          <p className="text-xs text-muted">Loading history...</p>
        ) : historyModal.items.length === 0 ? (
          <p className="text-xs text-muted">No changes recorded yet.</p>
        ) : (
          <ul className="space-y-2.5 max-h-72 overflow-y-auto">
            {historyModal.items.map((item) => (
              <li key={item.id} className="rounded-xl border border-border/70 bg-canvas p-3 text-xs">
                <div className="flex items-center justify-between gap-2 font-semibold text-ink">
                  <span>{item.actionType}</span>
                  <span className="text-[11px] text-muted">
                    {new Date(item.createdAtUtc).toLocaleString()}
                  </span>
                </div>
                <p className="mt-1 text-body break-words">{item.changeSummary}</p>
              </li>
            ))}
          </ul>
        )}
      </Modal>

      <Toast
        message={toast.message}
        type={toast.type}
        onDismiss={() => setToast({ message: '', type: 'info' })}
      />
    </div>
  );
}
