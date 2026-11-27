import { useEffect, useState } from 'react';
import Modal from '../Modal';
import { isValidHttpUrl } from '../../utils/urlValidation';
import { JOB_SOURCE_OPTIONS, JOB_STATUS_OPTIONS } from './jobMeta';

export default function JobFormModal({
  open,
  initialJob,
  saving,
  onClose,
  onSubmit,
}) {
  const [companyName, setCompanyName] = useState('');
  const [roleTitle, setRoleTitle] = useState('');
  const [status, setStatus] = useState('APPLIED');
  const [appliedDate, setAppliedDate] = useState('');
  const [source, setSource] = useState('LINKEDIN');
  const [location, setLocation] = useState('');
  const [jobUrl, setJobUrl] = useState('');
  const [jobDescription, setJobDescription] = useState('');
  const [followupDate, setFollowupDate] = useState('');
  const [interviewDate, setInterviewDate] = useState('');
  const [recruiterNotes, setRecruiterNotes] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    if (initialJob) {
      setCompanyName(initialJob.companyName || '');
      setRoleTitle(initialJob.roleTitle || '');
      setStatus(initialJob.status || 'APPLIED');
      setAppliedDate(initialJob.appliedDate || new Date().toISOString().slice(0, 10));
      setSource(initialJob.source || 'OTHER');
      setLocation(initialJob.location || '');
      setJobUrl(initialJob.jobUrl || '');
      setJobDescription(initialJob.jobDescription || '');
      setFollowupDate(initialJob.followupDate || '');
      setInterviewDate(initialJob.interviewDate || '');
      setRecruiterNotes(initialJob.recruiterNotes || '');
      setNotes(initialJob.notes || '');
    } else {
      setCompanyName('');
      setRoleTitle('');
      setStatus('APPLIED');
      setAppliedDate(new Date().toISOString().slice(0, 10));
      setSource('LINKEDIN');
      setLocation('');
      setJobUrl('');
      setJobDescription('');
      setFollowupDate('');
      setInterviewDate('');
      setRecruiterNotes('');
      setNotes('');
    }
    setError('');
  }, [open, initialJob]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!companyName.trim()) {
      setError('Company name is required.');
      return;
    }
    if (!roleTitle.trim()) {
      setError('Job title is required.');
      return;
    }
    if (!appliedDate) {
      setError('Date applied is required.');
      return;
    }
    if (jobUrl.trim() && !isValidHttpUrl(jobUrl.trim())) {
      setError('Job link must start with http:// or https://.');
      return;
    }
    if (followupDate && followupDate < appliedDate) {
      setError('Follow-up date cannot be before date applied.');
      return;
    }
    if (interviewDate && interviewDate < appliedDate) {
      setError('Interview date cannot be before date applied.');
      return;
    }

    setError('');
    const ok = await onSubmit({
      companyName: companyName.trim(),
      roleTitle: roleTitle.trim(),
      status,
      appliedDate,
      source,
      location: location.trim() || null,
      jobUrl: jobUrl.trim() || null,
      jobDescription: jobDescription.trim() || null,
      followupDate: followupDate || null,
      interviewDate: interviewDate || null,
      recruiterNotes: recruiterNotes.trim() || null,
      notes: notes.trim() || null,
    });
    if (ok) {
      onClose?.();
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={initialJob ? 'Edit Job Application' : 'Add Job Application'}
    >
      <form onSubmit={handleSubmit} className="space-y-3.5 max-h-[75vh] overflow-y-auto pr-1">
        {error && (
          <div className="rounded-xl border border-skipped/30 bg-skipped-soft px-3.5 py-2.5 text-xs font-medium text-skipped">
            {error}
          </div>
        )}

        <p className="text-[11px] text-muted">
          Manual entry only — PathPilot never scrapes LinkedIn, Naukri, or external job boards.
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="job-company" className="block text-xs font-semibold text-ink mb-1">
              Company Name <span className="text-skipped">*</span>
            </label>
            <input
              id="job-company"
              type="text"
              value={companyName}
              onChange={(e) => setCompanyName(e.target.value)}
              placeholder="e.g. Acme Tech"
              required
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>

          <div>
            <label htmlFor="job-role" className="block text-xs font-semibold text-ink mb-1">
              Job Title <span className="text-skipped">*</span>
            </label>
            <input
              id="job-role"
              type="text"
              value={roleTitle}
              onChange={(e) => setRoleTitle(e.target.value)}
              placeholder="e.g. Junior Java Developer"
              required
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div>
            <label htmlFor="job-status" className="block text-xs font-semibold text-ink mb-1">
              Status
            </label>
            <select
              id="job-status"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            >
              {JOB_STATUS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="job-source" className="block text-xs font-semibold text-ink mb-1">
              Source
            </label>
            <select
              id="job-source"
              value={source}
              onChange={(e) => setSource(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            >
              {JOB_SOURCE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="job-applied" className="block text-xs font-semibold text-ink mb-1">
              Date Applied <span className="text-skipped">*</span>
            </label>
            <input
              id="job-applied"
              type="date"
              value={appliedDate}
              onChange={(e) => setAppliedDate(e.target.value)}
              required
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="job-location" className="block text-xs font-semibold text-ink mb-1">
              Location
            </label>
            <input
              id="job-location"
              type="text"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="e.g. Bengaluru / Remote"
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>

          <div>
            <label htmlFor="job-url" className="block text-xs font-semibold text-ink mb-1">
              Job Posting Link
            </label>
            <input
              id="job-url"
              type="url"
              value={jobUrl}
              onChange={(e) => setJobUrl(e.target.value)}
              placeholder="https://company.com/careers/..."
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label htmlFor="job-followup" className="block text-xs font-semibold text-ink mb-1">
              Follow-up Date (creates reminder)
            </label>
            <input
              id="job-followup"
              type="date"
              value={followupDate}
              onChange={(e) => setFollowupDate(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>

          <div>
            <label htmlFor="job-interview" className="block text-xs font-semibold text-ink mb-1">
              Interview Date
            </label>
            <input
              id="job-interview"
              type="date"
              value={interviewDate}
              onChange={(e) => setInterviewDate(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
            />
          </div>
        </div>

        <div>
          <label htmlFor="job-jd" className="block text-xs font-semibold text-ink mb-1">
            Job Description (paste plain text)
          </label>
          <textarea
            id="job-jd"
            rows={3}
            value={jobDescription}
            onChange={(e) => setJobDescription(e.target.value)}
            placeholder="Paste the job description text here (stored safely as plain text)..."
            className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
          />
        </div>

        <div>
          <label htmlFor="job-recruiter" className="block text-xs font-semibold text-ink mb-1">
            Recruiter / Contact Notes
          </label>
          <input
            id="job-recruiter"
            type="text"
            value={recruiterNotes}
            onChange={(e) => setRecruiterNotes(e.target.value)}
            placeholder="e.g. Priya (Talent Partner) - priya@company.com"
            className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
          />
        </div>

        <div>
          <label htmlFor="job-notes" className="block text-xs font-semibold text-ink mb-1">
            Personal Notes
          </label>
          <textarea
            id="job-notes"
            rows={2}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Any personal thoughts, referral details, or salary range..."
            className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:border-primary"
          />
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
            {saving ? 'Saving...' : initialJob ? 'Save Changes' : 'Add Application'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
