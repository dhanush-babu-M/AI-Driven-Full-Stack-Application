import { useEffect, useState } from 'react';
import Modal from '../Modal';

const PLATFORMS = ['LeetCode', 'CodingBat', 'HackerRank', 'Codeforces', 'Other'];
const TOPICS = [
  'Arrays',
  'Strings',
  'HashMap',
  'Recursion',
  'Stack',
  'SQL',
  'Linked List',
  'Trees',
  'Graphs',
  'Dynamic Programming',
  'Binary Search',
  'Other',
];
const DIFFICULTIES = ['Easy', 'Medium', 'Hard'];
const RESULTS = ['Solved', 'Attempted', 'Practiced'];

export default function PracticeFormModal({
  open,
  initialValue,
  defaultDate,
  onClose,
  onSubmit,
  saving,
}) {
  const [problemTitle, setProblemTitle] = useState('');
  const [platform, setPlatform] = useState('LeetCode');
  const [topic, setTopic] = useState('Arrays');
  const [activityDate, setActivityDate] = useState(defaultDate || '');
  const [difficulty, setDifficulty] = useState('Easy');
  const [result, setResult] = useState('Solved');
  const [minutesSpent, setMinutesSpent] = useState('30');
  const [referenceUrl, setReferenceUrl] = useState('');
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (!open) return;
    setErrors({});
    if (initialValue) {
      setProblemTitle(initialValue.problemTitle || '');
      setPlatform(initialValue.platform || 'LeetCode');
      setTopic(initialValue.topic || 'Arrays');
      setActivityDate(initialValue.activityDate || defaultDate || '');
      setDifficulty(initialValue.difficulty || 'Easy');
      setResult(initialValue.result || 'Solved');
      setMinutesSpent(String(initialValue.minutesSpent ?? 0));
      setReferenceUrl(initialValue.referenceUrl || '');
      setNotes(initialValue.notes || '');
    } else {
      setProblemTitle('');
      setPlatform('LeetCode');
      setTopic('Arrays');
      setActivityDate(defaultDate || new Date().toISOString().slice(0, 10));
      setDifficulty('Easy');
      setResult('Solved');
      setMinutesSpent('30');
      setReferenceUrl('');
      setNotes('');
    }
  }, [open, initialValue, defaultDate]);

  const validate = () => {
    const next = {};
    if (!problemTitle.trim()) next.problemTitle = 'Problem title is required.';
    if (!activityDate) next.activityDate = 'Date is required.';
    if (!platform.trim()) next.platform = 'Platform is required.';
    if (!result) next.result = 'Result is required.';
    const mins = Number(minutesSpent);
    if (minutesSpent !== '' && (Number.isNaN(mins) || mins < 0 || mins > 1440)) {
      next.minutesSpent = 'Enter minutes between 0 and 1440.';
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate() || saving) return;
    await onSubmit({
      problemTitle: problemTitle.trim(),
      platform,
      topic,
      activityDate,
      difficulty,
      result,
      problemsSolved: result === 'Solved' ? 1 : 0,
      minutesSpent: minutesSpent === '' ? 0 : Number(minutesSpent),
      referenceUrl: referenceUrl.trim() || null,
      notes: notes.trim() || null,
    });
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={initialValue ? 'Edit Coding Practice' : 'Log Coding Practice'}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className="rounded-pill border border-border px-4 py-2 text-xs font-semibold text-body hover:bg-canvas"
          >
            Cancel
          </button>
          <button
            type="submit"
            form="practice-log-form"
            disabled={saving}
            className="rounded-pill bg-primary px-5 py-2 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
          >
            {saving ? 'Saving...' : initialValue ? 'Save Changes' : 'Save Practice'}
          </button>
        </>
      }
    >
      <form id="practice-log-form" onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="practice-title" className="block text-xs font-semibold text-ink mb-1">
            Problem Title *
          </label>
          <input
            id="practice-title"
            type="text"
            value={problemTitle}
            onChange={(e) => setProblemTitle(e.target.value)}
            placeholder="e.g. Two Sum / Warmup-1 sleepIn"
            className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
          />
          {errors.problemTitle && <p className="mt-1 text-xs text-skipped">{errors.problemTitle}</p>}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="practice-platform" className="block text-xs font-semibold text-ink mb-1">
              Platform *
            </label>
            <select
              id="practice-platform"
              value={platform}
              onChange={(e) => setPlatform(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            >
              {PLATFORMS.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="practice-topic" className="block text-xs font-semibold text-ink mb-1">
              Topic
            </label>
            <select
              id="practice-topic"
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            >
              {TOPICS.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div>
            <label htmlFor="practice-date" className="block text-xs font-semibold text-ink mb-1">
              Date *
            </label>
            <input
              id="practice-date"
              type="date"
              value={activityDate}
              onChange={(e) => setActivityDate(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
            {errors.activityDate && <p className="mt-1 text-xs text-skipped">{errors.activityDate}</p>}
          </div>

          <div>
            <label htmlFor="practice-difficulty" className="block text-xs font-semibold text-ink mb-1">
              Difficulty
            </label>
            <select
              id="practice-difficulty"
              value={difficulty}
              onChange={(e) => setDifficulty(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            >
              {DIFFICULTIES.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="practice-result" className="block text-xs font-semibold text-ink mb-1">
              Result *
            </label>
            <select
              id="practice-result"
              value={result}
              onChange={(e) => setResult(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            >
              {RESULTS.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="practice-minutes" className="block text-xs font-semibold text-ink mb-1">
              Minutes Spent (optional)
            </label>
            <input
              id="practice-minutes"
              type="number"
              min="0"
              max="1440"
              value={minutesSpent}
              onChange={(e) => setMinutesSpent(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
            {errors.minutesSpent && <p className="mt-1 text-xs text-skipped">{errors.minutesSpent}</p>}
          </div>

          <div>
            <label htmlFor="practice-url" className="block text-xs font-semibold text-ink mb-1">
              Problem Link (optional)
            </label>
            <input
              id="practice-url"
              type="url"
              value={referenceUrl}
              onChange={(e) => setReferenceUrl(e.target.value)}
              placeholder="https://leetcode.com/problems/..."
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
        </div>

        <div>
          <label htmlFor="practice-notes" className="block text-xs font-semibold text-ink mb-1">
            Notes — What I learned
          </label>
          <textarea
            id="practice-notes"
            rows={3}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Key idea, edge cases, or time/space complexity takeaway..."
            className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
          />
        </div>
      </form>
    </Modal>
  );
}
