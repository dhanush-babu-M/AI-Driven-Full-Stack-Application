import { useEffect, useState } from 'react';
import { createTask } from '../api/client';
import {
  TASK_CATEGORIES,
  TASK_PRIORITIES,
  TASK_STATUSES,
} from '../utils/taskCategories';
import Modal from './Modal';

export default function TaskFormModal({ open, defaultDate, onClose, onSaved }) {
  const [form, setForm] = useState({
    title: '',
    description: '',
    category: 'JAVA_LEARNING',
    taskDate: defaultDate || '',
    startTime: '',
    endTime: '',
    priority: 'MEDIUM',
    status: 'PLANNED',
    notes: '',
  });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setForm({
        title: '',
        description: '',
        category: 'JAVA_LEARNING',
        taskDate: defaultDate || '',
        startTime: '',
        endTime: '',
        priority: 'MEDIUM',
        status: 'PLANNED',
        notes: '',
      });
      setErrors({});
    }
  }, [open, defaultDate]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (saving) return;

    const nextErrs = {};
    if (!form.title.trim()) nextErrs.title = 'Task title is required.';
    if (!form.taskDate) nextErrs.taskDate = 'Task date is required.';
    if (form.startTime && form.endTime && form.endTime <= form.startTime) {
      nextErrs.endTime = 'End time must be after start time.';
    }
    setErrors(nextErrs);
    if (Object.keys(nextErrs).length > 0) return;

    setSaving(true);
    try {
      await createTask({
        title: form.title.trim(),
        description: form.description.trim() || null,
        category: form.category,
        taskDate: form.taskDate,
        startTime: form.startTime || null,
        endTime: form.endTime || null,
        priority: form.priority,
        status: form.status,
        notes: form.notes.trim() || null,
      });
      onClose?.();
      await onSaved?.();
    } catch (err) {
      if (err?.fieldErrors && Object.keys(err.fieldErrors).length > 0) {
        setErrors(err.fieldErrors);
      } else {
        setErrors({ general: err?.message || 'Unable to save task.' });
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal open={open} title="Add Task" onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate className="space-y-3.5">
        {errors.general && (
          <p role="alert" className="rounded-xl bg-skipped-soft p-2.5 text-xs font-medium text-skipped">
            {errors.general}
          </p>
        )}

        <div>
          <label htmlFor="modal-task-title" className="block text-xs font-semibold text-ink mb-1">
            Title *
          </label>
          <input
            id="modal-task-title"
            type="text"
            value={form.title}
            onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
            placeholder="What needs to get done?"
            className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
          />
          {errors.title && <p role="alert" className="mt-1 text-xs text-red-700">{errors.title}</p>}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="modal-task-category" className="block text-xs font-semibold text-ink mb-1">
              Category
            </label>
            <select
              id="modal-task-category"
              value={form.category}
              onChange={(e) => setForm((p) => ({ ...p, category: e.target.value }))}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink bg-surface focus:outline-none focus:border-primary"
            >
              {TASK_CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label} ({c.group})
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="modal-task-date" className="block text-xs font-semibold text-ink mb-1">
              Date
            </label>
            <input
              id="modal-task-date"
              type="date"
              value={form.taskDate}
              onChange={(e) => setForm((p) => ({ ...p, taskDate: e.target.value }))}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="modal-task-start" className="block text-xs font-semibold text-ink mb-1">
              Start Time
            </label>
            <input
              id="modal-task-start"
              type="time"
              value={form.startTime}
              onChange={(e) => setForm((p) => ({ ...p, startTime: e.target.value }))}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
          <div>
            <label htmlFor="modal-task-end" className="block text-xs font-semibold text-ink mb-1">
              End Time
            </label>
            <input
              id="modal-task-end"
              type="time"
              value={form.endTime}
              onChange={(e) => setForm((p) => ({ ...p, endTime: e.target.value }))}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
            {errors.endTime && <p role="alert" className="mt-1 text-xs text-red-700">{errors.endTime}</p>}
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="modal-task-priority" className="block text-xs font-semibold text-ink mb-1">
              Priority
            </label>
            <select
              id="modal-task-priority"
              value={form.priority}
              onChange={(e) => setForm((p) => ({ ...p, priority: e.target.value }))}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink bg-surface focus:outline-none focus:border-primary"
            >
              {TASK_PRIORITIES.map((p) => (
                <option key={p.value} value={p.value}>{p.label}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="modal-task-status" className="block text-xs font-semibold text-ink mb-1">
              Status
            </label>
            <select
              id="modal-task-status"
              value={form.status}
              onChange={(e) => setForm((p) => ({ ...p, status: e.target.value }))}
              className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink bg-surface focus:outline-none focus:border-primary"
            >
              {TASK_STATUSES.map((s) => (
                <option key={s.value} value={s.value}>{s.label}</option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label htmlFor="modal-task-notes" className="block text-xs font-semibold text-ink mb-1">
            Notes (optional)
          </label>
          <textarea
            id="modal-task-notes"
            rows={2}
            value={form.notes}
            onChange={(e) => setForm((p) => ({ ...p, notes: e.target.value }))}
            className="w-full rounded-xl border border-border px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
          />
        </div>

        <div className="pt-2 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-pill border border-border px-4 py-2 text-xs font-semibold text-body hover:bg-canvas"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={saving}
            className="rounded-pill bg-primary px-5 py-2 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
          >
            {saving ? 'Saving...' : 'Create Task'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
