import { useEffect, useState } from 'react';
import { format, parseISO } from 'date-fns';
import {
  analyzeJobDescription,
  createAiTaskProposalsBatch,
  deleteJob,
  fetchSavedJdAnalysis,
  updateJobChecklist,
  updateJobStatus,
} from '../../api/client';
import { notifyAiInboxChanged, openAiInbox } from '../../hooks/useAiInbox';
import { notifyRemindersChanged } from '../../hooks/useReminders';
import { isValidHttpUrl } from '../../utils/urlValidation';
import SourceTag from '../SourceTag';
import {
  getJobSourceLabel,
  JOB_STATUS_OPTIONS,
  JobStatusChip,
} from './jobMeta';

const TABS = [
  { key: 'details', label: 'Details' },
  { key: 'timeline', label: 'Timeline' },
  { key: 'notes', label: 'Notes' },
  { key: 'ai', label: 'AI Analysis' },
];

const QUESTION_GROUPS = [
  { key: 'java', label: 'Java' },
  { key: 'springBoot', label: 'Spring Boot' },
  { key: 'sql', label: 'SQL' },
  { key: 'react', label: 'React' },
  { key: 'behavioural', label: 'Behavioural' },
];

const QUICK_PREP_TEMPLATES = [
  'Revise OOPS',
  'Review Spring Boot basics',
  'Practice SQL & Joins',
  'Research company & product',
];

function formatDate(dateStr) {
  if (!dateStr) return '—';
  try {
    return format(parseISO(dateStr), 'MMM d, yyyy');
  } catch {
    return dateStr;
  }
}

function formatTimestamp(iso) {
  if (!iso) return '';
  try {
    return format(parseISO(iso), 'MMM d, yyyy · h:mm a');
  } catch {
    return iso;
  }
}

export default function JobDetailDrawer({
  open,
  job,
  loading,
  error,
  onClose,
  onEditJob,
  onReloadJob,
  onJobsMutated,
  onToast,
}) {
  const [activeTab, setActiveTab] = useState('details');
  const [statusNote, setStatusNote] = useState('');
  const [nextStatus, setNextStatus] = useState('APPLIED');
  const [savingStatus, setSavingStatus] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Interview prep checklist
  const [checklistInput, setChecklistInput] = useState('');
  const [editingItemId, setEditingItemId] = useState(null);
  const [editingItemText, setEditingItemText] = useState('');
  const [savingChecklist, setSavingChecklist] = useState(false);

  // AI JD analysis
  const [aiAnalysis, setAiAnalysis] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState('');
  const [customJdText, setCustomJdText] = useState('');
  const [queueingBatch, setQueueingBatch] = useState(false);

  useEffect(() => {
    if (job) {
      setNextStatus(job.status || 'APPLIED');
      setStatusNote('');
      setConfirmDelete(false);
      setCustomJdText(job.jobDescription || '');
      setAiError('');
      setAiAnalysis(null);
      if (job.id) {
        fetchSavedJdAnalysis(job.id)
          .then((saved) => {
            if (saved) setAiAnalysis(saved);
          })
          .catch(() => {});
      }
    }
  }, [job?.id, job?.status]);

  useEffect(() => {
    if (!open) return undefined;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  const checklist = job?.prepChecklist || [];
  const history = job?.statusHistory || [];

  const refreshBoth = async () => {
    if (!job) return;
    await Promise.all([onReloadJob?.(job.id), onJobsMutated?.()]);
  };

  const handleStatusSave = async (e) => {
    e.preventDefault();
    if (!job || savingStatus) return;
    setSavingStatus(true);
    try {
      await updateJobStatus(job.id, {
        status: nextStatus,
        note: statusNote.trim() || null,
      });
      setStatusNote('');
      await refreshBoth();
      onToast?.('Application status updated.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to update status.', 'error');
    } finally {
      setSavingStatus(false);
    }
  };

  const handleDelete = async () => {
    if (!job) return;
    try {
      await deleteJob(job.id);
      notifyRemindersChanged();
      await onJobsMutated?.();
      onToast?.('Application deleted.', 'success');
      onClose?.();
    } catch (err) {
      onToast?.(err?.message || 'Unable to delete application.', 'error');
    }
  };

  const saveChecklist = async (nextItems) => {
    if (!job || savingChecklist) return;
    setSavingChecklist(true);
    try {
      await updateJobChecklist(job.id, nextItems);
      await onReloadJob?.(job.id);
    } catch (err) {
      onToast?.(err?.message || 'Unable to save checklist.', 'error');
    } finally {
      setSavingChecklist(false);
    }
  };

  const addChecklistItem = async (rawText) => {
    const text = rawText.trim();
    if (!text) return;
    const next = [
      ...checklist,
      { id: `${Date.now()}-${Math.random().toString(16).slice(2)}`, text, done: false },
    ];
    setChecklistInput('');
    await saveChecklist(next);
  };

  const toggleChecklistItem = async (id) => {
    const next = checklist.map((item) =>
      item.id === id ? { ...item, done: !item.done } : item
    );
    await saveChecklist(next);
  };

  const commitChecklistEdit = async (id) => {
    const trimmed = editingItemText.trim();
    if (!trimmed) return;
    const next = checklist.map((item) =>
      item.id === id ? { ...item, text: trimmed } : item
    );
    setEditingItemId(null);
    setEditingItemText('');
    await saveChecklist(next);
  };

  const removeChecklistItem = async (id) => {
    const next = checklist.filter((item) => item.id !== id);
    await saveChecklist(next);
  };

  const handleAnalyseWithAi = async () => {
    if (!job?.id) return;
    setActiveTab('ai');
    setAiLoading(true);
    setAiError('');
    try {
      const text = (customJdText || job.jobDescription || '').trim();
      const res = await analyzeJobDescription({
        jobApplicationId: job.id,
        jobDescription: text || undefined,
      });
      setAiAnalysis(res);
      onToast?.('Job description analysed (suggestions labelled below).', 'success');
    } catch (err) {
      setAiError(err?.message || 'Unable to analyse job description.');
    } finally {
      setAiLoading(false);
    }
  };

  const handleTurnChecklistIntoTasks = async () => {
    const items = aiAnalysis?.preparationChecklist || [];
    if (items.length === 0) return;
    setQueueingBatch(true);
    try {
      const todayIso = new Date().toISOString().slice(0, 10);
      await createAiTaskProposalsBatch(
        items.slice(0, 8).map((step) => ({
          title: step,
          description: `Interview prep suggested for ${job.companyName} — ${job.roleTitle}`,
          category: 'JOB_APPLICATIONS',
          taskDate: todayIso,
          priority: 'MEDIUM',
        }))
      );
      notifyAiInboxChanged();
      onToast?.(`${Math.min(items.length, 8)} prep tasks sent to Approval Inbox.`, 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to queue prep tasks.', 'error');
    } finally {
      setQueueingBatch(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-ink/40 backdrop-blur-[1px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="job-drawer-title"
      onClick={onClose}
    >
      <aside
        className="h-full w-full max-w-2xl bg-surface border-l border-border shadow-drawer flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-start justify-between gap-4 px-6 py-4 border-b border-border">
          {job ? (
            <div className="min-w-0 space-y-1">
              <div className="flex flex-wrap items-center gap-2">
                <h2 id="job-drawer-title" className="text-xl font-bold text-ink truncate">
                  {job.companyName}
                </h2>
                <JobStatusChip status={job.status} />
                <SourceTag source={job.dataSource || 'MANUAL'} />
              </div>
              <p className="text-sm font-medium text-body">{job.roleTitle}</p>
              <p className="text-xs text-muted">
                Applied {formatDate(job.appliedDate)} · {getJobSourceLabel(job.source)}
                {job.location ? ` · ${job.location}` : ''}
              </p>
            </div>
          ) : (
            <h2 id="job-drawer-title" className="text-lg font-bold text-ink">
              Application Details
            </h2>
          )}

          <button
            type="button"
            onClick={onClose}
            aria-label="Close application drawer"
            className="rounded-lg p-1.5 text-muted hover:text-ink hover:bg-canvas"
          >
            ✕
          </button>
        </div>

        {/* Tabs + Analyse with AI */}
        {job && !error && (
          <div className="px-6 pt-3 border-b border-border flex flex-wrap items-center justify-between gap-2">
            <div className="flex gap-1.5">
              {TABS.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setActiveTab(tab.key)}
                  className={`px-3.5 py-2 text-xs font-semibold rounded-t-xl border-b-2 transition-colors ${
                    activeTab === tab.key
                      ? 'border-primary text-primary bg-primary-soft/60'
                      : 'border-transparent text-muted hover:text-ink'
                  }`}
                >
                  {tab.label}
                  {tab.key === 'timeline' ? ` (${history.length})` : ''}
                </button>
              ))}
            </div>

            <button
              type="button"
              onClick={handleAnalyseWithAi}
              disabled={aiLoading}
              className="mb-1.5 inline-flex items-center gap-1.5 rounded-pill bg-primary px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
            >
              <span aria-hidden="true">✨</span>
              <span>{aiLoading ? 'Analysing...' : 'Analyse with AI'}</span>
            </button>
          </div>
        )}

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6 text-sm">
          {loading ? (
            <p className="text-xs text-muted">Loading application details...</p>
          ) : error ? (
            <div className="rounded-card border border-skipped/30 bg-skipped-soft p-6 text-center space-y-3">
              <p className="text-sm font-semibold text-skipped">{error}</p>
              <button
                type="button"
                onClick={onClose}
                className="rounded-pill border border-border bg-surface px-4 py-1.5 text-xs font-semibold text-ink"
              >
                Back to Applications
              </button>
            </div>
          ) : !job ? null : (
            <>
              {/* DETAILS TAB */}
              {activeTab === 'details' && (
                <div className="space-y-6">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      {isValidHttpUrl(job.jobUrl) ? (
                        <a
                          href={job.jobUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="rounded-pill border border-primary/25 bg-primary-soft px-3.5 py-1.5 text-xs font-semibold text-primary hover:bg-primary hover:text-white"
                        >
                          Open Job Posting ↗
                        </a>
                      ) : (
                        <span
                          aria-disabled="true"
                          className="rounded-pill border border-border bg-slate-100 px-3.5 py-1.5 text-xs font-medium text-slate-400 cursor-not-allowed"
                        >
                          No posting link
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => onEditJob?.(job)}
                        className="rounded-pill border border-border bg-surface px-3.5 py-1.5 text-xs font-semibold text-ink hover:bg-canvas"
                      >
                        Edit
                      </button>
                      {!confirmDelete ? (
                        <button
                          type="button"
                          onClick={() => setConfirmDelete(true)}
                          className="rounded-pill border border-skipped/30 px-3.5 py-1.5 text-xs font-semibold text-skipped hover:bg-skipped-soft"
                        >
                          Delete
                        </button>
                      ) : (
                        <div className="flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={handleDelete}
                            className="rounded-pill bg-skipped px-3 py-1.5 text-xs font-semibold text-white"
                          >
                            Confirm Delete
                          </button>
                          <button
                            type="button"
                            onClick={() => setConfirmDelete(false)}
                            className="rounded-pill border border-border px-2.5 py-1.5 text-xs text-muted"
                          >
                            Cancel
                          </button>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Key Dates */}
                  <dl className="grid grid-cols-1 sm:grid-cols-3 gap-3 rounded-xl border border-border bg-canvas/50 p-4 text-xs">
                    <div>
                      <dt className="font-medium text-muted">Date Applied</dt>
                      <dd className="mt-1 font-semibold text-ink">{formatDate(job.appliedDate)}</dd>
                    </div>
                    <div>
                      <dt className="font-medium text-muted">Follow-up Date</dt>
                      <dd className="mt-1 font-semibold text-ink">{formatDate(job.followupDate)}</dd>
                    </div>
                    <div>
                      <dt className="font-medium text-muted">Interview Date</dt>
                      <dd className="mt-1 font-semibold text-ink">{formatDate(job.interviewDate)}</dd>
                    </div>
                  </dl>

                  {/* Quick Status Update */}
                  <form
                    onSubmit={handleStatusSave}
                    className="rounded-xl border border-border bg-canvas/50 p-4 space-y-3"
                  >
                    <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                      Update Status
                    </h3>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                      <select
                        value={nextStatus}
                        onChange={(e) => setNextStatus(e.target.value)}
                        aria-label="Application status"
                        className="rounded-xl border border-border bg-surface px-3 py-2 text-xs font-semibold text-ink"
                      >
                        {JOB_STATUS_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                      <input
                        type="text"
                        value={statusNote}
                        onChange={(e) => setStatusNote(e.target.value)}
                        placeholder="Optional timeline note (e.g. HR screen scheduled)"
                        className="sm:col-span-2 rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink"
                      />
                    </div>
                    <div className="flex justify-end">
                      <button
                        type="submit"
                        disabled={savingStatus}
                        className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                      >
                        {savingStatus ? 'Updating...' : 'Save Status'}
                      </button>
                    </div>
                  </form>

                  {/* Interview Prep Checklist */}
                  <div className="rounded-xl border border-border p-4 space-y-3">
                    <div className="flex items-center justify-between gap-2">
                      <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                        Interview Prep Checklist ({checklist.filter((c) => c.done).length}/{checklist.length})
                      </h3>
                    </div>

                    <div className="flex gap-2">
                      <input
                        type="text"
                        value={checklistInput}
                        onChange={(e) => setChecklistInput(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            addChecklistItem(checklistInput);
                          }
                        }}
                        placeholder="Add prep item (e.g. Revise OOPS)"
                        className="flex-1 rounded-xl border border-border bg-surface px-3 py-1.5 text-xs text-ink"
                      />
                      <button
                        type="button"
                        disabled={!checklistInput.trim() || savingChecklist}
                        onClick={() => addChecklistItem(checklistInput)}
                        className="rounded-pill bg-primary px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
                      >
                        Add
                      </button>
                    </div>

                    <div className="flex flex-wrap gap-1.5">
                      {QUICK_PREP_TEMPLATES.filter(
                        (tpl) => !checklist.some((c) => c.text.toLowerCase() === tpl.toLowerCase())
                      ).map((tpl) => (
                        <button
                          key={tpl}
                          type="button"
                          onClick={() => addChecklistItem(tpl)}
                          className="rounded-pill border border-border bg-canvas px-2.5 py-0.5 text-[11px] text-muted hover:text-ink"
                        >
                          + {tpl}
                        </button>
                      ))}
                    </div>

                    {checklist.length === 0 ? (
                      <p className="text-xs text-muted">
                        No prep items yet. Add topics to review before assessments or interviews.
                      </p>
                    ) : (
                      <ul className="space-y-2">
                        {checklist.map((item) => (
                          <li
                            key={item.id}
                            className="flex items-center justify-between gap-2 rounded-xl border border-border/70 bg-canvas/50 px-3 py-2 text-xs"
                          >
                            {editingItemId === item.id ? (
                              <div className="flex flex-1 items-center gap-2">
                                <input
                                  type="text"
                                  value={editingItemText}
                                  onChange={(e) => setEditingItemText(e.target.value)}
                                  className="flex-1 rounded-lg border border-border bg-surface px-2 py-1 text-xs"
                                />
                                <button
                                  type="button"
                                  onClick={() => commitChecklistEdit(item.id)}
                                  className="font-semibold text-primary hover:underline"
                                >
                                  Save
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setEditingItemId(null)}
                                  className="text-muted hover:text-ink"
                                >
                                  Cancel
                                </button>
                              </div>
                            ) : (
                              <>
                                <label className="flex items-center gap-2 cursor-pointer min-w-0 flex-1">
                                  <input
                                    type="checkbox"
                                    checked={item.done}
                                    onChange={() => toggleChecklistItem(item.id)}
                                    className="h-4 w-4 rounded border-border text-primary"
                                  />
                                  <span
                                    className={`truncate ${
                                      item.done ? 'line-through text-muted' : 'text-ink font-medium'
                                    }`}
                                  >
                                    {item.text}
                                  </span>
                                </label>
                                <div className="flex items-center gap-2 shrink-0">
                                  <button
                                    type="button"
                                    onClick={() => {
                                      setEditingItemId(item.id);
                                      setEditingItemText(item.text);
                                    }}
                                    className="text-primary font-semibold hover:underline"
                                  >
                                    Edit
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => removeChecklistItem(item.id)}
                                    className="text-skipped font-semibold hover:underline"
                                  >
                                    Remove
                                  </button>
                                </div>
                              </>
                            )}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>

                  {/* Sanitized Plain-Text Job Description */}
                  <div className="rounded-xl border border-border bg-canvas/50 p-4 space-y-2">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                      Job Description
                    </h3>
                    <p className="text-xs text-body whitespace-pre-wrap leading-relaxed">
                      {job.jobDescription || 'No job description pasted yet.'}
                    </p>
                  </div>
                </div>
              )}

              {/* TIMELINE TAB */}
              {activeTab === 'timeline' && (
                <div className="space-y-3">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                    Status History
                  </h3>
                  {history.length === 0 ? (
                    <p className="text-xs text-muted">No status changes recorded yet.</p>
                  ) : (
                    <ul className="space-y-2.5">
                      {history.map((entry) => (
                        <li
                          key={entry.id}
                          className="rounded-xl border border-border bg-canvas/40 p-3.5 space-y-1.5"
                        >
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <div className="flex flex-wrap items-center gap-2 text-xs">
                              {entry.previousStatus ? (
                                <>
                                  <JobStatusChip status={entry.previousStatus} />
                                  <span aria-hidden="true" className="text-muted">→</span>
                                </>
                              ) : (
                                <span className="text-xs font-semibold text-muted">Initial:</span>
                              )}
                              <JobStatusChip status={entry.newStatus} />
                            </div>
                            <span className="text-[11px] text-muted">
                              {formatTimestamp(entry.changedAtUtc)}
                            </span>
                          </div>
                          {entry.note && <p className="text-xs text-body">{entry.note}</p>}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}

              {/* NOTES TAB */}
              {activeTab === 'notes' && (
                <div className="space-y-4">
                  <div className="flex justify-end">
                    <button
                      type="button"
                      onClick={() => onEditJob?.(job)}
                      className="rounded-pill border border-border bg-surface px-3.5 py-1.5 text-xs font-semibold text-ink hover:bg-canvas"
                    >
                      Edit Notes
                    </button>
                  </div>

                  <div className="rounded-xl border border-border bg-canvas/50 p-4 space-y-1.5">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                      Recruiter / Contact Notes
                    </h3>
                    <p className="text-xs text-body whitespace-pre-wrap">
                      {job.recruiterNotes || 'No recruiter or contact notes recorded.'}
                    </p>
                  </div>

                  <div className="rounded-xl border border-border bg-canvas/50 p-4 space-y-1.5">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                      Personal Notes
                    </h3>
                    <p className="text-xs text-body whitespace-pre-wrap">
                      {job.notes || 'No personal notes recorded.'}
                    </p>
                  </div>
                </div>
              )}

              {/* AI ANALYSIS TAB */}
              {activeTab === 'ai' && (
                <div className="space-y-4">
                  <div className="rounded-xl border border-border bg-canvas/50 p-4 space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <label htmlFor="jd-paste-input" className="text-xs font-bold uppercase tracking-wider text-muted">
                        Job Description (plain text, sanitized on server)
                      </label>
                      <button
                        type="button"
                        onClick={handleAnalyseWithAi}
                        disabled={aiLoading}
                        className="rounded-pill bg-primary px-3.5 py-1 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                      >
                        {aiLoading ? 'Analysing...' : aiAnalysis ? 'Re-run Analysis' : 'Run Analysis'}
                      </button>
                    </div>
                    <textarea
                      id="jd-paste-input"
                      rows={4}
                      value={customJdText}
                      onChange={(e) => setCustomJdText(e.target.value)}
                      maxLength={12000}
                      placeholder="Paste the job description here to compare against your Settings skills and your saved projects..."
                      className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                    />
                  </div>

                  {aiError && (
                    <div className="rounded-xl border border-skipped/30 bg-skipped-soft p-3 text-xs font-medium text-skipped">
                      {aiError}
                    </div>
                  )}

                  {!aiAnalysis && !aiLoading ? (
                    <div className="rounded-xl border border-dashed border-border bg-canvas/40 p-6 text-center space-y-2">
                      <p className="text-sm font-semibold text-ink">No saved AI analysis yet</p>
                      <p className="text-xs text-muted">
                        Click &ldquo;Analyse with AI&rdquo; to compare the JD against your Settings skills and saved projects.
                      </p>
                    </div>
                  ) : aiAnalysis && (
                    <div className="space-y-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <SourceTag source={aiAnalysis.source || 'RULE'} />
                          <span className="text-[11px] text-muted">
                            {aiAnalysis.disclaimer || 'AI-generated. Based on your saved records.'}
                          </span>
                        </div>
                        <span className="text-[11px] text-muted">
                          Suggestions only · Verified against Settings &amp; Projects
                        </span>
                      </div>

                      {aiAnalysis.notice && (
                        <p className="rounded-xl border border-amber-300/70 bg-amber-50 px-3 py-2 text-xs text-amber-900">
                          {aiAnalysis.notice}
                        </p>
                      )}

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="rounded-xl border border-border bg-canvas/50 p-3 space-y-1.5">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-muted">Required Skills</h4>
                          <div className="flex flex-wrap gap-1.5">
                            {(aiAnalysis.requiredSkills || []).length === 0 ? (
                              <span className="text-xs text-muted">None extracted</span>
                            ) : (
                              aiAnalysis.requiredSkills.map((s) => (
                                <span key={s} className="rounded-pill bg-surface border border-border px-2.5 py-0.5 text-xs font-medium text-ink">
                                  {s}
                                </span>
                              ))
                            )}
                          </div>
                        </div>

                        <div className="rounded-xl border border-border bg-canvas/50 p-3 space-y-1.5">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-muted">Nice to Have</h4>
                          <div className="flex flex-wrap gap-1.5">
                            {(aiAnalysis.niceToHave || []).length === 0 ? (
                              <span className="text-xs text-muted">None listed</span>
                            ) : (
                              aiAnalysis.niceToHave.map((s) => (
                                <span key={s} className="rounded-pill bg-surface border border-border px-2.5 py-0.5 text-xs text-body">
                                  {s}
                                </span>
                              ))
                            )}
                          </div>
                        </div>

                        <div className="rounded-xl border border-completed/30 bg-completed-soft/50 p-3 space-y-1.5">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-completed">Matched (Profile)</h4>
                          <div className="flex flex-wrap gap-1.5">
                            {(aiAnalysis.matchedSkills || []).length === 0 ? (
                              <span className="text-xs text-muted">No matching skills in Settings yet</span>
                            ) : (
                              aiAnalysis.matchedSkills.map((s) => (
                                <span key={s} className="rounded-pill bg-surface border border-completed/30 px-2.5 py-0.5 text-xs font-semibold text-completed">
                                  {s}
                                </span>
                              ))
                            )}
                          </div>
                        </div>

                        <div className="rounded-xl border border-amber-300/60 bg-amber-50/70 p-3 space-y-1.5">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-amber-800">Weak Areas (Beginner) &amp; Gaps</h4>
                          <div className="flex flex-wrap gap-1.5">
                            {(aiAnalysis.weakAreas || []).map((s) => (
                              <span key={`weak-${s}`} className="rounded-pill bg-surface border border-amber-300 px-2.5 py-0.5 text-xs font-medium text-amber-900">
                                Beginner: {s}
                              </span>
                            ))}
                            {(aiAnalysis.gaps || []).map((s) => (
                              <span key={`gap-${s}`} className="rounded-pill bg-surface border border-border px-2.5 py-0.5 text-xs font-medium text-ink">
                                Gap: {s}
                              </span>
                            ))}
                            {(aiAnalysis.weakAreas || []).length === 0 && (aiAnalysis.gaps || []).length === 0 && (
                              <span className="text-xs text-muted">No gaps detected</span>
                            )}
                          </div>
                        </div>
                      </div>

                      <div className="rounded-xl border border-border bg-canvas/50 p-3.5 space-y-1.5">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-muted">
                          Recommended Projects to Highlight (Your Projects)
                        </h4>
                        {(aiAnalysis.recommendedProjectsToShow || []).length === 0 ? (
                          <p className="text-xs text-muted">Add projects in My Projects to receive project suggestions.</p>
                        ) : (
                          <ul className="list-disc pl-4 space-y-1 text-xs text-body">
                            {aiAnalysis.recommendedProjectsToShow.map((p) => (
                              <li key={p}>{p}</li>
                            ))}
                          </ul>
                        )}
                      </div>

                      <div className="rounded-xl border border-border bg-canvas/50 p-3.5 space-y-2">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-muted">
                            Suggested Preparation Checklist
                          </h4>
                          <div className="flex items-center gap-2">
                            {(aiAnalysis.preparationChecklist || []).length > 0 && (
                              <button
                                type="button"
                                onClick={handleTurnChecklistIntoTasks}
                                disabled={queueingBatch}
                                className="rounded-pill bg-primary px-3 py-1 text-[11px] font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                              >
                                {queueingBatch ? 'Queueing...' : 'Turn checklist into tasks'}
                              </button>
                            )}
                            <button
                              type="button"
                              onClick={openAiInbox}
                              className="rounded-pill border border-border bg-surface px-2.5 py-1 text-[11px] font-semibold text-ink hover:bg-canvas"
                            >
                              Approval Inbox
                            </button>
                          </div>
                        </div>
                        <ul className="list-disc pl-4 space-y-1 text-xs text-body">
                          {(aiAnalysis.preparationChecklist || []).map((step) => (
                            <li key={step}>{step}</li>
                          ))}
                        </ul>
                      </div>

                      {aiAnalysis.interviewQuestions && (
                        <div className="rounded-xl border border-border bg-canvas/50 p-3.5 space-y-2.5">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-muted">
                            Suggested Interview Questions
                          </h4>
                          {QUESTION_GROUPS.map((group) => {
                            const qs = aiAnalysis.interviewQuestions[group.key] || [];
                            if (qs.length === 0) return null;
                            return (
                              <div key={group.key} className="space-y-1">
                                <p className="text-xs font-semibold text-ink">{group.label}</p>
                                <ul className="list-disc pl-4 space-y-0.5 text-xs text-body">
                                  {qs.map((q) => (
                                    <li key={q}>{q}</li>
                                  ))}
                                </ul>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </aside>
    </div>
  );
}
