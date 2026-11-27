import { useEffect, useState } from 'react';
import Modal from '../Modal';
import { isValidHttpUrl } from '../../utils/urlValidation';
import { PROJECT_ICON_OPTIONS, PROJECT_STATUS_OPTIONS } from './projectMeta';

const SUGGESTED_TECH = ['Java', 'Spring Boot', 'MySQL', 'React', 'Tailwind CSS', 'Docker'];

export default function ProjectFormModal({
  open,
  initialProject,
  saving,
  onClose,
  onSubmit,
}) {
  const [title, setTitle] = useState('');
  const [icon, setIcon] = useState('code');
  const [status, setStatus] = useState('CURRENT');
  const [description, setDescription] = useState('');
  const [techStack, setTechStack] = useState([]);
  const [techInput, setTechInput] = useState('');
  const [liveUrl, setLiveUrl] = useState('');
  const [repoUrl, setRepoUrl] = useState('');
  const [startDate, setStartDate] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    if (initialProject) {
      setTitle(initialProject.title || '');
      setIcon(initialProject.icon || 'code');
      setStatus(initialProject.status || 'PLANNED');
      setDescription(initialProject.description || '');
      setTechStack(initialProject.techStack || []);
      setLiveUrl(initialProject.liveUrl || '');
      setRepoUrl(initialProject.repoUrl || '');
      setStartDate(initialProject.startDate || '');
      setTargetDate(initialProject.targetDate || '');
    } else {
      setTitle('');
      setIcon('code');
      setStatus('CURRENT');
      setDescription('');
      setTechStack([]);
      setLiveUrl('');
      setRepoUrl('');
      setStartDate(new Date().toISOString().slice(0, 10));
      setTargetDate('');
    }
    setTechInput('');
    setError('');
  }, [open, initialProject]);

  const addTechTag = (raw) => {
    const cleaned = raw.trim().replace(/,$/, '');
    if (!cleaned) return;
    if (!techStack.some((t) => t.toLowerCase() === cleaned.toLowerCase())) {
      setTechStack((prev) => [...prev, cleaned]);
    }
    setTechInput('');
  };

  const handleTechKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      addTechTag(techInput);
    }
  };

  const removeTechTag = (tag) => {
    setTechStack((prev) => prev.filter((item) => item !== tag));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim()) {
      setError('Project name is required.');
      return;
    }
    if (liveUrl.trim() && !isValidHttpUrl(liveUrl.trim())) {
      setError('Live Demo URL must start with http:// or https://.');
      return;
    }
    if (repoUrl.trim() && !isValidHttpUrl(repoUrl.trim())) {
      setError('GitHub URL must start with http:// or https://.');
      return;
    }
    if (startDate && targetDate && targetDate < startDate) {
      setError('Target date cannot be earlier than start date.');
      return;
    }

    const finalTech = [...techStack];
    if (techInput.trim()) {
      const extra = techInput.trim().replace(/,$/, '');
      if (extra && !finalTech.some((t) => t.toLowerCase() === extra.toLowerCase())) {
        finalTech.push(extra);
      }
    }

    setError('');
    const ok = await onSubmit({
      title: title.trim(),
      icon,
      status,
      description: description.trim() || null,
      techStack: finalTech,
      liveUrl: liveUrl.trim() || null,
      repoUrl: repoUrl.trim() || null,
      startDate: startDate || null,
      targetDate: targetDate || null,
    });
    if (ok) {
      onClose?.();
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={initialProject ? 'Edit Project' : 'Add Project'}
    >
      <form onSubmit={handleSubmit} className="space-y-4 max-h-[75vh] overflow-y-auto pr-1">
        {error && (
          <div className="rounded-xl border border-skipped/30 bg-skipped-soft px-3.5 py-2.5 text-xs font-medium text-skipped">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div className="sm:col-span-2">
            <label htmlFor="proj-title" className="block text-xs font-semibold text-ink mb-1">
              Project Name <span className="text-skipped">*</span>
            </label>
            <input
              id="proj-title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. PathPilot"
              maxLength={200}
              required
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>

          <div>
            <label htmlFor="proj-status" className="block text-xs font-semibold text-ink mb-1">
              Status
            </label>
            <select
              id="proj-status"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            >
              {PROJECT_STATUS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <span className="block text-xs font-semibold text-ink mb-1.5">Icon Tile</span>
          <div className="flex flex-wrap gap-2">
            {PROJECT_ICON_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setIcon(opt.value)}
                className={`rounded-pill border px-3 py-1 text-xs font-semibold transition-colors ${
                  icon === opt.value
                    ? 'border-primary bg-primary text-white'
                    : 'border-border bg-canvas text-body hover:text-ink'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label htmlFor="proj-desc" className="block text-xs font-semibold text-ink mb-1">
            Description
          </label>
          <textarea
            id="proj-desc"
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="What problem does this project solve and what are you building?"
            className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
          />
        </div>

        <div>
          <label htmlFor="proj-tech" className="block text-xs font-semibold text-ink mb-1">
            Technologies
          </label>
          <div className="flex gap-2">
            <input
              id="proj-tech"
              type="text"
              value={techInput}
              onChange={(e) => setTechInput(e.target.value)}
              onKeyDown={handleTechKeyDown}
              placeholder="Type a tech and press Enter (e.g. Spring Boot)"
              className="flex-1 rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
            <button
              type="button"
              onClick={() => addTechTag(techInput)}
              className="rounded-pill border border-border bg-canvas px-3.5 py-2 text-xs font-semibold text-ink hover:bg-primary-soft hover:text-primary"
            >
              Add
            </button>
          </div>

          {techStack.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {techStack.map((tag) => (
                <span
                  key={tag}
                  className="inline-flex items-center gap-1 rounded-pill bg-primary-soft border border-primary/20 px-2.5 py-0.5 text-xs font-medium text-primary"
                >
                  {tag}
                  <button
                    type="button"
                    onClick={() => removeTechTag(tag)}
                    aria-label={`Remove ${tag}`}
                    className="ml-0.5 hover:text-ink"
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}

          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            <span className="text-[11px] text-muted">Quick add:</span>
            {SUGGESTED_TECH.filter((s) => !techStack.includes(s)).map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => addTechTag(s)}
                className="rounded-pill border border-border bg-surface px-2 py-0.5 text-[11px] text-muted hover:text-ink hover:bg-canvas"
              >
                + {s}
              </button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="proj-live" className="block text-xs font-semibold text-ink mb-1">
              Live Demo URL
            </label>
            <input
              id="proj-live"
              type="url"
              value={liveUrl}
              onChange={(e) => setLiveUrl(e.target.value)}
              placeholder="https://my-app.vercel.app"
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>

          <div>
            <label htmlFor="proj-repo" className="block text-xs font-semibold text-ink mb-1">
              GitHub Repository URL
            </label>
            <input
              id="proj-repo"
              type="url"
              value={repoUrl}
              onChange={(e) => setRepoUrl(e.target.value)}
              placeholder="https://github.com/user/repo"
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="proj-start" className="block text-xs font-semibold text-ink mb-1">
              Start Date
            </label>
            <input
              id="proj-start"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>

          <div>
            <label htmlFor="proj-target" className="block text-xs font-semibold text-ink mb-1">
              Target Date
            </label>
            <input
              id="proj-target"
              type="date"
              value={targetDate}
              onChange={(e) => setTargetDate(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
        </div>

        <div className="pt-3 border-t border-border flex justify-end gap-2">
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
            {saving ? 'Saving...' : initialProject ? 'Save Changes' : 'Create Project'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
