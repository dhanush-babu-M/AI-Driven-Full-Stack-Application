import { useEffect, useState } from 'react';
import { format, parseISO } from 'date-fns';
import {
  addProjectVideoLink,
  createProjectMilestone,
  createProjectWorklog,
  deleteProject,
  deleteProjectMedia,
  deleteProjectMilestone,
  deleteProjectWorklog,
  getProjectMediaFileUrl,
  toggleProjectMilestone,
  updateProjectMilestone,
  updateProjectStatus,
  updateProjectWorklog,
  uploadProjectScreenshot,
} from '../../api/client';
import { isValidHttpUrl } from '../../utils/urlValidation';
import ProgressBar from '../ProgressBar';
import SourceTag from '../SourceTag';
import {
  PROJECT_STATUS_OPTIONS,
  ProjectIconTile,
  ProjectStatusChip,
} from './projectMeta';

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'milestones', label: 'Milestones' },
  { key: 'worklogs', label: 'Work Log' },
  { key: 'media', label: 'Media' },
];

const MAX_FILE_BYTES = 2 * 1024 * 1024;
const ALLOWED_MIME = new Set(['image/png', 'image/jpeg', 'image/jpg', 'image/webp']);

function formatShortDate(dateStr) {
  if (!dateStr) return '—';
  try {
    return format(parseISO(dateStr), 'MMM d, yyyy');
  } catch {
    return dateStr;
  }
}

export default function ProjectDetailDrawer({
  open,
  detail,
  loading,
  error,
  activeTab,
  onTabChange,
  onClose,
  onEditProject,
  onReloadDetail,
  onProjectsMutated,
  onToast,
}) {
  const project = detail?.project || null;
  const milestones = detail?.milestones || [];
  const worklogs = detail?.worklogs || [];
  const media = detail?.media || [];
  const statusHistory = detail?.statusHistory || [];

  // Status quick change
  const [nextStatus, setNextStatus] = useState('PLANNED');
  const [statusNote, setStatusNote] = useState('');
  const [savingStatus, setSavingStatus] = useState(false);
  const [confirmDeleteProject, setConfirmDeleteProject] = useState(false);

  // Milestone form
  const [msTitle, setMsTitle] = useState('');
  const [msDueDate, setMsDueDate] = useState('');
  const [msNotes, setMsNotes] = useState('');
  const [editingMilestoneId, setEditingMilestoneId] = useState(null);
  const [savingMilestone, setSavingMilestone] = useState(false);

  // Worklog form
  const [wlDate, setWlDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [wlMinutes, setWlMinutes] = useState('60');
  const [wlSummary, setWlSummary] = useState('');
  const [editingWorklogId, setEditingWorklogId] = useState(null);
  const [savingWorklog, setSavingWorklog] = useState(false);

  // Media forms
  const [screenshotFile, setScreenshotFile] = useState(null);
  const [screenshotCaption, setScreenshotCaption] = useState('');
  const [uploadingMedia, setUploadingMedia] = useState(false);
  const [videoUrl, setVideoUrl] = useState('');
  const [videoCaption, setVideoCaption] = useState('');
  const [savingVideo, setSavingVideo] = useState(false);

  useEffect(() => {
    if (project) {
      setNextStatus(project.status || 'PLANNED');
      setStatusNote('');
      setConfirmDeleteProject(false);
    }
  }, [project?.id, project?.status]);

  useEffect(() => {
    if (!open) return undefined;
    const handleEsc = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [open, onClose]);

  if (!open) return null;

  const screenshots = media.filter((m) => m.mediaType === 'SCREENSHOT');
  const videoLinks = media.filter((m) => m.mediaType === 'VIDEO_LINK');

  const refreshAll = async () => {
    if (!project) return;
    await Promise.all([onReloadDetail?.(project.id), onProjectsMutated?.()]);
  };

  const handleStatusSave = async (e) => {
    e.preventDefault();
    if (!project || savingStatus) return;
    setSavingStatus(true);
    try {
      await updateProjectStatus(project.id, {
        status: nextStatus,
        note: statusNote.trim() || null,
      });
      setStatusNote('');
      await refreshAll();
      onToast?.('Project status updated.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to update status.', 'error');
    } finally {
      setSavingStatus(false);
    }
  };

  const handleDeleteProject = async () => {
    if (!project) return;
    try {
      await deleteProject(project.id);
      await onProjectsMutated?.();
      onToast?.('Project deleted.', 'success');
      onClose?.();
    } catch (err) {
      onToast?.(err?.message || 'Unable to delete project.', 'error');
    }
  };

  // Milestones
  const resetMilestoneForm = () => {
    setMsTitle('');
    setMsDueDate('');
    setMsNotes('');
    setEditingMilestoneId(null);
  };

  const handleMilestoneSubmit = async (e) => {
    e.preventDefault();
    if (!project || !msTitle.trim() || savingMilestone) return;
    setSavingMilestone(true);
    try {
      if (editingMilestoneId) {
        const existing = milestones.find((m) => m.id === editingMilestoneId);
        await updateProjectMilestone(project.id, editingMilestoneId, {
          title: msTitle.trim(),
          dueDate: msDueDate || null,
          completedDate: existing?.completedDate || null,
          status: existing?.status || 'PLANNED',
          sortOrder: existing?.sortOrder ?? 0,
          notes: msNotes.trim() || null,
        });
        onToast?.('Milestone updated.', 'success');
      } else {
        await createProjectMilestone(project.id, {
          title: msTitle.trim(),
          dueDate: msDueDate || null,
          status: 'PLANNED',
          notes: msNotes.trim() || null,
        });
        onToast?.('Milestone added.', 'success');
      }
      resetMilestoneForm();
      await refreshAll();
    } catch (err) {
      onToast?.(err?.message || 'Unable to save milestone.', 'error');
    } finally {
      setSavingMilestone(false);
    }
  };

  const handleToggleMilestone = async (m) => {
    if (!project) return;
    const willComplete = m.status !== 'COMPLETED';
    try {
      await toggleProjectMilestone(project.id, m.id, {
        completed: willComplete,
        completedDate: willComplete ? new Date().toISOString().slice(0, 10) : null,
      });
      await refreshAll();
    } catch (err) {
      onToast?.(err?.message || 'Unable to update milestone.', 'error');
    }
  };

  const handleCompletedDateChange = async (m, newDate) => {
    if (!project) return;
    try {
      await updateProjectMilestone(project.id, m.id, {
        title: m.title,
        dueDate: m.dueDate,
        completedDate: newDate || null,
        status: 'COMPLETED',
        sortOrder: m.sortOrder,
        notes: m.notes,
      });
      await refreshAll();
      onToast?.('Completed date updated.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to update completed date.', 'error');
    }
  };

  const handleDeleteMilestone = async (milestoneId) => {
    if (!project) return;
    try {
      await deleteProjectMilestone(project.id, milestoneId);
      await refreshAll();
      onToast?.('Milestone deleted.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to delete milestone.', 'error');
    }
  };

  // Work logs
  const resetWorklogForm = () => {
    setWlDate(new Date().toISOString().slice(0, 10));
    setWlMinutes('60');
    setWlSummary('');
    setEditingWorklogId(null);
  };

  const handleWorklogSubmit = async (e) => {
    e.preventDefault();
    if (!project || !wlSummary.trim() || savingWorklog) return;
    setSavingWorklog(true);
    try {
      const payload = {
        workDate: wlDate,
        minutesSpent: Number(wlMinutes) || 0,
        summary: wlSummary.trim(),
      };
      if (editingWorklogId) {
        await updateProjectWorklog(project.id, editingWorklogId, payload);
        onToast?.('Work log updated.', 'success');
      } else {
        await createProjectWorklog(project.id, payload);
        onToast?.('Work log added.', 'success');
      }
      resetWorklogForm();
      await refreshAll();
    } catch (err) {
      onToast?.(err?.message || 'Unable to save work log.', 'error');
    } finally {
      setSavingWorklog(false);
    }
  };

  const handleDeleteWorklog = async (worklogId) => {
    if (!project) return;
    try {
      await deleteProjectWorklog(project.id, worklogId);
      await refreshAll();
      onToast?.('Work log removed.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to delete work log.', 'error');
    }
  };

  // Media
  const handleScreenshotUpload = async (e) => {
    e.preventDefault();
    if (!project || !screenshotFile || uploadingMedia) return;
    if (!ALLOWED_MIME.has(screenshotFile.type)) {
      onToast?.('Only PNG, JPG, or WebP screenshots are allowed.', 'error');
      return;
    }
    if (screenshotFile.size > MAX_FILE_BYTES) {
      onToast?.('Screenshot exceeds the 2 MB maximum size.', 'error');
      return;
    }
    setUploadingMedia(true);
    try {
      await uploadProjectScreenshot(project.id, screenshotFile, screenshotCaption);
      setScreenshotFile(null);
      setScreenshotCaption('');
      await refreshAll();
      onToast?.('Screenshot uploaded safely.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to upload screenshot.', 'error');
    } finally {
      setUploadingMedia(false);
    }
  };

  const handleAddVideo = async (e) => {
    e.preventDefault();
    if (!project || !videoUrl.trim() || savingVideo) return;
    if (!isValidHttpUrl(videoUrl.trim())) {
      onToast?.('Demo video link must start with http:// or https://.', 'error');
      return;
    }
    setSavingVideo(true);
    try {
      await addProjectVideoLink(project.id, {
        videoUrl: videoUrl.trim(),
        caption: videoCaption.trim() || null,
      });
      setVideoUrl('');
      setVideoCaption('');
      await refreshAll();
      onToast?.('Demo video link added.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to add video link.', 'error');
    } finally {
      setSavingVideo(false);
    }
  };

  const handleDeleteMedia = async (mediaId) => {
    if (!project) return;
    try {
      await deleteProjectMedia(project.id, mediaId);
      await refreshAll();
      onToast?.('Media removed.', 'success');
    } catch (err) {
      onToast?.(err?.message || 'Unable to remove media.', 'error');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-ink/40 backdrop-blur-[1px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="project-drawer-title"
      onClick={onClose}
    >
      <aside
        className="h-full w-full max-w-3xl bg-surface border-l border-border shadow-drawer flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-start justify-between gap-4 px-6 py-4 border-b border-border">
          {project ? (
            <div className="flex items-start gap-3 min-w-0">
              <ProjectIconTile icon={project.icon} size="lg" />
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 id="project-drawer-title" className="text-xl font-bold text-ink truncate">
                    {project.title}
                  </h2>
                  <ProjectStatusChip status={project.status} />
                  <SourceTag source={project.dataSource} />
                </div>
                <p className="mt-0.5 text-xs text-muted">
                  Start: {formatShortDate(project.startDate)} · Target: {formatShortDate(project.targetDate)}
                </p>
              </div>
            </div>
          ) : (
            <h2 id="project-drawer-title" className="text-lg font-bold text-ink">
              Project Details
            </h2>
          )}

          <button
            type="button"
            onClick={onClose}
            aria-label="Close project details"
            className="rounded-lg p-1.5 text-muted hover:text-ink hover:bg-canvas"
          >
            ✕
          </button>
        </div>

        {/* Tabs */}
        {project && !error && (
          <div className="px-6 pt-3 border-b border-border flex flex-wrap gap-1.5">
            {TABS.map((tab) => {
              const count =
                tab.key === 'milestones'
                  ? milestones.length
                  : tab.key === 'worklogs'
                  ? worklogs.length
                  : tab.key === 'media'
                  ? media.length
                  : null;
              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => onTabChange?.(tab.key)}
                  className={`px-3.5 py-2 text-xs font-semibold rounded-t-xl border-b-2 transition-colors ${
                    activeTab === tab.key
                      ? 'border-primary text-primary bg-primary-soft/60'
                      : 'border-transparent text-muted hover:text-ink'
                  }`}
                >
                  {tab.label}
                  {count !== null ? ` (${count})` : ''}
                </button>
              );
            })}
          </div>
        )}

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6 text-sm">
          {loading ? (
            <p className="text-xs text-muted">Loading project details...</p>
          ) : error ? (
            <div className="rounded-card border border-skipped/30 bg-skipped-soft p-6 text-center space-y-3">
              <p className="text-sm font-semibold text-skipped">{error}</p>
              <button
                type="button"
                onClick={onClose}
                className="rounded-pill border border-border bg-surface px-4 py-1.5 text-xs font-semibold text-ink"
              >
                Back to Projects
              </button>
            </div>
          ) : !project ? null : (
            <>
              {/* OVERVIEW TAB */}
              {activeTab === 'overview' && (
                <div className="space-y-6">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-wrap gap-2">
                      {isValidHttpUrl(project.liveUrl) ? (
                        <a
                          href={project.liveUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover"
                        >
                          Open Live Demo ↗
                        </a>
                      ) : (
                        <span
                          aria-disabled="true"
                          className="rounded-pill border border-border bg-slate-100 px-4 py-1.5 text-xs font-medium text-slate-400 cursor-not-allowed"
                        >
                          Live Demo (not set)
                        </span>
                      )}

                      {isValidHttpUrl(project.repoUrl) ? (
                        <a
                          href={project.repoUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="rounded-pill border border-border bg-surface px-4 py-1.5 text-xs font-semibold text-ink hover:bg-canvas"
                        >
                          Open GitHub ↗
                        </a>
                      ) : (
                        <span
                          aria-disabled="true"
                          className="rounded-pill border border-border bg-slate-100 px-4 py-1.5 text-xs font-medium text-slate-400 cursor-not-allowed"
                        >
                          GitHub (not set)
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => onEditProject?.(project)}
                        className="rounded-pill border border-border bg-surface px-3.5 py-1.5 text-xs font-semibold text-ink hover:bg-canvas"
                      >
                        Edit Project
                      </button>
                      {!confirmDeleteProject ? (
                        <button
                          type="button"
                          onClick={() => setConfirmDeleteProject(true)}
                          className="rounded-pill border border-skipped/30 px-3.5 py-1.5 text-xs font-semibold text-skipped hover:bg-skipped-soft"
                        >
                          Delete
                        </button>
                      ) : (
                        <div className="flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={handleDeleteProject}
                            className="rounded-pill bg-skipped px-3 py-1.5 text-xs font-semibold text-white"
                          >
                            Confirm Delete
                          </button>
                          <button
                            type="button"
                            onClick={() => setConfirmDeleteProject(false)}
                            className="rounded-pill border border-border px-2.5 py-1.5 text-xs text-muted"
                          >
                            Cancel
                          </button>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Description */}
                  <div className="rounded-xl border border-border bg-canvas/50 p-4 space-y-2">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                      Description
                    </h3>
                    <p className="text-sm text-body whitespace-pre-wrap leading-relaxed">
                      {project.description || 'No description added yet.'}
                    </p>
                  </div>

                  {/* Technologies */}
                  <div className="space-y-2">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                      Technologies
                    </h3>
                    {(project.techStack || []).length === 0 ? (
                      <p className="text-xs text-muted">No technologies added yet.</p>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        {project.techStack.map((tech) => (
                          <span
                            key={tech}
                            className="rounded-pill border border-border bg-canvas px-3 py-1 text-xs font-semibold text-ink"
                          >
                            {tech}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Milestone Progress Summary */}
                  <div className="rounded-xl border border-border p-4 space-y-2">
                    <div className="flex items-center justify-between">
                      <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                        Milestone Progress
                      </h3>
                      <button
                        type="button"
                        onClick={() => onTabChange?.('milestones')}
                        className="text-xs font-semibold text-primary hover:underline"
                      >
                        Manage Milestones →
                      </button>
                    </div>
                    {project.totalMilestones > 0 && project.progress !== null ? (
                      <ProgressBar
                        value={project.progress}
                        label={`${project.completedMilestones} of ${project.totalMilestones} milestones completed`}
                      />
                    ) : (
                      <p className="text-xs text-muted">
                        No milestones yet — add planned milestones to measure real progress.
                      </p>
                    )}
                  </div>

                  {/* Status Change with History */}
                  <form
                    onSubmit={handleStatusSave}
                    className="rounded-xl border border-border bg-canvas/50 p-4 space-y-3"
                  >
                    <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                      Update Status (History Kept)
                    </h3>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                      <select
                        value={nextStatus}
                        onChange={(e) => setNextStatus(e.target.value)}
                        aria-label="Project status"
                        className="rounded-xl border border-border bg-surface px-3 py-2 text-xs font-semibold text-ink focus:outline-none focus:border-primary"
                      >
                        {PROJECT_STATUS_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                      <input
                        type="text"
                        value={statusNote}
                        onChange={(e) => setStatusNote(e.target.value)}
                        placeholder="Optional note (e.g. Deployed v1)"
                        className="sm:col-span-2 rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                      />
                    </div>
                    <div className="flex justify-end">
                      <button
                        type="submit"
                        disabled={savingStatus}
                        className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                      >
                        {savingStatus ? 'Updating...' : 'Save Status Change'}
                      </button>
                    </div>
                  </form>

                  {/* Status History */}
                  <div className="space-y-2.5">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                      Status &amp; Audit History
                    </h3>
                    {statusHistory.length === 0 ? (
                      <p className="text-xs text-muted">No history recorded yet.</p>
                    ) : (
                      <ul className="space-y-2">
                        {statusHistory.map((item) => (
                          <li
                            key={item.id}
                            className="rounded-xl border border-border/80 bg-surface px-3.5 py-2.5 text-xs flex items-start justify-between gap-3"
                          >
                            <div>
                              <span className="font-semibold text-ink">{item.actionType}</span>
                              <p className="text-body mt-0.5">{item.changeSummary}</p>
                            </div>
                            <span className="shrink-0 text-[11px] text-muted">
                              {formatShortDate(item.recordDate)}
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </div>
              )}

              {/* MILESTONES TAB */}
              {activeTab === 'milestones' && (
                <div className="space-y-5">
                  <div className="rounded-xl border border-border bg-canvas/60 p-4 space-y-2">
                    {project.totalMilestones > 0 && project.progress !== null ? (
                      <ProgressBar
                        value={project.progress}
                        label={`Project Progress (${project.completedMilestones}/${project.totalMilestones} completed)`}
                      />
                    ) : (
                      <p className="text-xs font-medium text-muted">
                        No milestones yet. Add your first planned milestone below.
                      </p>
                    )}
                    <p className="text-[11px] text-muted">
                      Formula: completed milestones / planned milestones × 100. Feeds the project card and Dashboard Project Progress card.
                    </p>
                  </div>

                  {/* Add / Edit Milestone Form */}
                  <form
                    onSubmit={handleMilestoneSubmit}
                    className="rounded-xl border border-border p-4 space-y-3 bg-surface"
                  >
                    <div className="flex items-center justify-between">
                      <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                        {editingMilestoneId ? 'Edit Milestone' : 'Add Milestone'}
                      </h3>
                      {editingMilestoneId && (
                        <button
                          type="button"
                          onClick={resetMilestoneForm}
                          className="text-xs text-muted hover:text-ink"
                        >
                          Cancel edit
                        </button>
                      )}
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                      <input
                        type="text"
                        value={msTitle}
                        onChange={(e) => setMsTitle(e.target.value)}
                        placeholder="Milestone title (e.g. JWT auth & Flyway schema)"
                        required
                        className="sm:col-span-2 rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                      />
                      <input
                        type="date"
                        value={msDueDate}
                        onChange={(e) => setMsDueDate(e.target.value)}
                        aria-label="Planned due date"
                        className="rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                      />
                    </div>
                    <input
                      type="text"
                      value={msNotes}
                      onChange={(e) => setMsNotes(e.target.value)}
                      placeholder="Optional acceptance notes"
                      className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                    />
                    <div className="flex justify-end">
                      <button
                        type="submit"
                        disabled={savingMilestone}
                        className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                      >
                        {savingMilestone
                          ? 'Saving...'
                          : editingMilestoneId
                          ? 'Update Milestone'
                          : '+ Add Milestone'}
                      </button>
                    </div>
                  </form>

                  {/* Milestone List */}
                  {milestones.length === 0 ? (
                    <p className="rounded-xl border border-dashed border-border p-6 text-center text-xs text-muted">
                      No milestones yet. Add one above to start tracking completion.
                    </p>
                  ) : (
                    <ul className="space-y-2.5">
                      {milestones.map((m) => {
                        const isDone = m.status === 'COMPLETED';
                        return (
                          <li
                            key={m.id}
                            className="rounded-xl border border-border bg-canvas/40 p-3.5 space-y-2"
                          >
                            <div className="flex items-start justify-between gap-3">
                              <label className="flex items-start gap-2.5 cursor-pointer min-w-0">
                                <input
                                  type="checkbox"
                                  checked={isDone}
                                  onChange={() => handleToggleMilestone(m)}
                                  className="mt-0.5 h-4 w-4 rounded border-border text-primary"
                                />
                                <div className="min-w-0">
                                  <span
                                    className={`block text-sm font-semibold ${
                                      isDone ? 'line-through text-muted' : 'text-ink'
                                    }`}
                                  >
                                    {m.title}
                                  </span>
                                  {m.notes && (
                                    <p className="text-xs text-body mt-0.5">{m.notes}</p>
                                  )}
                                </div>
                              </label>

                              <span
                                className={`shrink-0 rounded-pill border px-2.5 py-0.5 text-[11px] font-semibold ${
                                  isDone
                                    ? 'border-completed/25 bg-completed-soft text-completed'
                                    : 'border-slate-200 bg-slate-100 text-slate-700'
                                }`}
                              >
                                {isDone ? 'Completed' : 'Planned'}
                              </span>
                            </div>

                            <div className="flex flex-wrap items-center justify-between gap-2 pl-6 text-xs text-muted">
                              <div className="flex flex-wrap items-center gap-3">
                                <span>Planned: {formatShortDate(m.dueDate)}</span>
                                {isDone && (
                                  <label className="inline-flex items-center gap-1.5">
                                    <span>Completed:</span>
                                    <input
                                      type="date"
                                      value={m.completedDate || ''}
                                      onChange={(e) => handleCompletedDateChange(m, e.target.value)}
                                      className="rounded-lg border border-border bg-surface px-2 py-0.5 text-xs text-ink"
                                    />
                                  </label>
                                )}
                              </div>

                              <div className="flex items-center gap-2">
                                <button
                                  type="button"
                                  onClick={() => {
                                    setEditingMilestoneId(m.id);
                                    setMsTitle(m.title || '');
                                    setMsDueDate(m.dueDate || '');
                                    setMsNotes(m.notes || '');
                                  }}
                                  className="font-semibold text-primary hover:underline"
                                >
                                  Edit
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleDeleteMilestone(m.id)}
                                  className="font-semibold text-skipped hover:underline"
                                >
                                  Delete
                                </button>
                              </div>
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              )}

              {/* WORK LOG TAB */}
              {activeTab === 'worklogs' && (
                <div className="space-y-5">
                  <div className="rounded-xl border border-primary/20 bg-primary-soft px-4 py-3 text-xs text-ink">
                    Entries logged here are saved newest-first and automatically appear in the{' '}
                    <strong>Coding Activity day drawer</strong> and <strong>Monthly Report</strong>.
                  </div>

                  <form
                    onSubmit={handleWorklogSubmit}
                    className="rounded-xl border border-border p-4 space-y-3 bg-surface"
                  >
                    <div className="flex items-center justify-between">
                      <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                        {editingWorklogId ? 'Edit Work Log' : 'Add Work Log Entry'}
                      </h3>
                      {editingWorklogId && (
                        <button
                          type="button"
                          onClick={resetWorklogForm}
                          className="text-xs text-muted hover:text-ink"
                        >
                          Cancel edit
                        </button>
                      )}
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div>
                        <label htmlFor="wl-date" className="block text-[11px] font-semibold text-ink mb-1">
                          Date
                        </label>
                        <input
                          id="wl-date"
                          type="date"
                          value={wlDate}
                          onChange={(e) => setWlDate(e.target.value)}
                          required
                          className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                        />
                      </div>
                      <div>
                        <label htmlFor="wl-mins" className="block text-[11px] font-semibold text-ink mb-1">
                          Minutes Spent
                        </label>
                        <input
                          id="wl-mins"
                          type="number"
                          min="0"
                          max="1440"
                          value={wlMinutes}
                          onChange={(e) => setWlMinutes(e.target.value)}
                          className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                        />
                      </div>
                    </div>

                    <div>
                      <label htmlFor="wl-summary" className="block text-[11px] font-semibold text-ink mb-1">
                        What I built that day <span className="text-skipped">*</span>
                      </label>
                      <textarea
                        id="wl-summary"
                        rows={2}
                        value={wlSummary}
                        onChange={(e) => setWlSummary(e.target.value)}
                        placeholder="e.g. Implemented ProjectDetailDrawer with milestone progress & safe screenshot upload"
                        required
                        className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                      />
                    </div>

                    <div className="flex justify-end">
                      <button
                        type="submit"
                        disabled={savingWorklog}
                        className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-60"
                      >
                        {savingWorklog
                          ? 'Saving...'
                          : editingWorklogId
                          ? 'Update Entry'
                          : '+ Add Work Log'}
                      </button>
                    </div>
                  </form>

                  {worklogs.length === 0 ? (
                    <p className="rounded-xl border border-dashed border-border p-6 text-center text-xs text-muted">
                      No work log entries yet. Document what you built today above.
                    </p>
                  ) : (
                    <ul className="space-y-2.5">
                      {worklogs.map((w) => (
                        <li
                          key={w.id}
                          className="rounded-xl border border-border bg-canvas/40 p-3.5 space-y-1.5"
                        >
                          <div className="flex items-center justify-between gap-2">
                            <div className="flex items-center gap-2">
                              <span className="text-xs font-bold text-ink">
                                {formatShortDate(w.workDate)}
                              </span>
                              <span className="rounded-pill border border-border bg-surface px-2 py-0.5 text-[11px] text-muted">
                                {w.minutesSpent} min
                              </span>
                              <SourceTag source={w.dataSource || 'MANUAL'} />
                            </div>
                            <div className="flex items-center gap-2 text-xs">
                              <button
                                type="button"
                                onClick={() => {
                                  setEditingWorklogId(w.id);
                                  setWlDate(w.workDate);
                                  setWlMinutes(String(w.minutesSpent ?? 0));
                                  setWlSummary(w.summary || '');
                                }}
                                className="font-semibold text-primary hover:underline"
                              >
                                Edit
                              </button>
                              <button
                                type="button"
                                onClick={() => handleDeleteWorklog(w.id)}
                                className="font-semibold text-skipped hover:underline"
                              >
                                Delete
                              </button>
                            </div>
                          </div>
                          <p className="text-xs text-body whitespace-pre-wrap">{w.summary}</p>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}

              {/* MEDIA TAB */}
              {activeTab === 'media' && (
                <div className="space-y-6">
                  {/* Storage choice explanation */}
                  <div className="rounded-xl border border-primary/20 bg-primary-soft p-3.5 text-xs text-ink space-y-1">
                    <p className="font-semibold">Safe Local File Storage (Free)</p>
                    <p className="text-body leading-relaxed">
                      Screenshots (PNG, JPG, WebP; max 2 MB each; up to 6 per project) are stored on disk{' '}
                      <strong>outside the web root</strong> with random UUID filenames. Every upload is validated
                      server-side by file size and image magic bytes, and served only to the authenticated owner.
                    </p>
                  </div>

                  {/* Screenshot Upload Form */}
                  <form
                    onSubmit={handleScreenshotUpload}
                    className="rounded-xl border border-border p-4 space-y-3 bg-surface"
                  >
                    <div className="flex items-center justify-between">
                      <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                        Upload Screenshot ({screenshots.length}/6)
                      </h3>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <input
                        type="file"
                        accept="image/png,image/jpeg,image/webp"
                        disabled={screenshots.length >= 6}
                        onChange={(e) => setScreenshotFile(e.target.files?.[0] || null)}
                        className="text-xs text-body file:mr-3 file:rounded-pill file:border-0 file:bg-primary-soft file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-primary hover:file:bg-primary hover:file:text-white"
                      />
                      <input
                        type="text"
                        value={screenshotCaption}
                        onChange={(e) => setScreenshotCaption(e.target.value)}
                        placeholder="Optional caption (e.g. Dashboard view)"
                        disabled={screenshots.length >= 6}
                        className="rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                      />
                    </div>

                    <div className="flex justify-end">
                      <button
                        type="submit"
                        disabled={!screenshotFile || uploadingMedia || screenshots.length >= 6}
                        className="rounded-pill bg-primary px-4 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover disabled:opacity-50"
                      >
                        {uploadingMedia ? 'Uploading...' : 'Upload Screenshot'}
                      </button>
                    </div>
                  </form>

                  {/* Screenshot Grid */}
                  {screenshots.length > 0 && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {screenshots.map((item) => (
                        <div
                          key={item.id}
                          className="rounded-xl border border-border overflow-hidden bg-canvas/40 flex flex-col justify-between"
                        >
                          <img
                            src={getProjectMediaFileUrl(item.fileUrl)}
                            alt={item.caption || item.originalFilename || 'Project screenshot'}
                            className="h-44 w-full object-cover bg-slate-100"
                          />
                          <div className="p-3 flex items-center justify-between gap-2 text-xs">
                            <div className="min-w-0">
                              <p className="font-semibold text-ink truncate">
                                {item.caption || item.originalFilename || 'Screenshot'}
                              </p>
                              {item.fileSizeBytes && (
                                <p className="text-[11px] text-muted">
                                  {(item.fileSizeBytes / 1024).toFixed(0)} KB
                                </p>
                              )}
                            </div>
                            <button
                              type="button"
                              onClick={() => handleDeleteMedia(item.id)}
                              className="shrink-0 font-semibold text-skipped hover:underline"
                            >
                              Delete
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Demo Video Link Form */}
                  <form
                    onSubmit={handleAddVideo}
                    className="rounded-xl border border-border p-4 space-y-3 bg-surface"
                  >
                    <h3 className="text-xs font-bold uppercase tracking-wider text-muted">
                      Add Demo Video Link
                    </h3>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <input
                        type="url"
                        value={videoUrl}
                        onChange={(e) => setVideoUrl(e.target.value)}
                        placeholder="https://www.loom.com/share/..."
                        required
                        className="rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                      />
                      <input
                        type="text"
                        value={videoCaption}
                        onChange={(e) => setVideoCaption(e.target.value)}
                        placeholder="Caption (e.g. 2-min walkthrough)"
                        className="rounded-xl border border-border bg-surface px-3 py-2 text-xs text-ink focus:outline-none focus:border-primary"
                      />
                    </div>
                    <div className="flex justify-end">
                      <button
                        type="submit"
                        disabled={savingVideo}
                        className="rounded-pill border border-primary bg-primary-soft px-4 py-1.5 text-xs font-semibold text-primary hover:bg-primary hover:text-white disabled:opacity-50"
                      >
                        {savingVideo ? 'Adding...' : '+ Add Demo Video'}
                      </button>
                    </div>
                  </form>

                  {/* Demo Video List */}
                  {videoLinks.length > 0 && (
                    <ul className="space-y-2">
                      {videoLinks.map((vid) => (
                        <li
                          key={vid.id}
                          className="rounded-xl border border-border bg-canvas/40 px-3.5 py-2.5 flex items-center justify-between gap-3 text-xs"
                        >
                          <div className="min-w-0">
                            <p className="font-semibold text-ink truncate">
                              {vid.caption || 'Demo Video'}
                            </p>
                            <a
                              href={vid.videoUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-primary hover:underline truncate block"
                            >
                              {vid.videoUrl} ↗
                            </a>
                          </div>
                          <button
                            type="button"
                            onClick={() => handleDeleteMedia(vid.id)}
                            className="shrink-0 font-semibold text-skipped hover:underline"
                          >
                            Delete
                          </button>
                        </li>
                      ))}
                    </ul>
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
