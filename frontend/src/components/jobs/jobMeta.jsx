export const JOB_STATUS_OPTIONS = [
  { value: 'INTERESTED', label: 'Interested' },
  { value: 'TO_APPLY', label: 'To Apply' },
  { value: 'APPLIED', label: 'Applied' },
  { value: 'FOLLOW_UP_NEEDED', label: 'Follow-up Needed' },
  { value: 'ASSESSMENT', label: 'Assessment' },
  { value: 'INTERVIEW', label: 'Interview' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'OFFER_RECEIVED', label: 'Offer Received' },
];

export const JOB_STATUS_META = {
  INTERESTED: {
    label: 'Interested',
    classes: 'bg-slate-100 text-slate-700 border-slate-200',
    dotClass: 'bg-slate-400',
  },
  TO_APPLY: {
    label: 'To Apply',
    classes: 'bg-sky-50 text-sky-700 border-sky-200',
    dotClass: 'bg-sky-500',
  },
  APPLIED: {
    label: 'Applied',
    classes: 'bg-primary-soft text-primary border-primary/25',
    dotClass: 'bg-primary',
  },
  FOLLOW_UP_NEEDED: {
    label: 'Follow-up Needed',
    classes: 'bg-pending-soft text-amber-800 border-pending/30',
    dotClass: 'bg-pending',
  },
  ASSESSMENT: {
    label: 'Assessment',
    classes: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    dotClass: 'bg-indigo-500',
  },
  INTERVIEW: {
    label: 'Interview',
    classes: 'bg-teal-50 text-teal-700 border-teal-200',
    dotClass: 'bg-teal-500',
  },
  REJECTED: {
    label: 'Rejected',
    classes: 'bg-skipped-soft text-skipped border-skipped/25',
    dotClass: 'bg-skipped',
  },
  OFFER_RECEIVED: {
    label: 'Offer Received',
    classes: 'bg-completed-soft text-completed border-completed/30',
    dotClass: 'bg-completed',
  },
};

export const JOB_SOURCE_OPTIONS = [
  { value: 'LINKEDIN', label: 'LinkedIn' },
  { value: 'NAUKRI', label: 'Naukri' },
  { value: 'COMPANY_SITE', label: 'Company Site' },
  { value: 'REFERRAL', label: 'Referral' },
  { value: 'OTHER', label: 'Other' },
];

export function getJobStatusMeta(status) {
  const key = String(status || 'APPLIED').toUpperCase();
  return JOB_STATUS_META[key] || JOB_STATUS_META.APPLIED;
}

export function getJobSourceLabel(source) {
  const found = JOB_SOURCE_OPTIONS.find((s) => s.value === String(source || '').toUpperCase());
  return found ? found.label : 'Other';
}

export function JobStatusChip({ status }) {
  const meta = getJobStatusMeta(status);
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-pill border px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap ${meta.classes}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${meta.dotClass}`} aria-hidden="true" />
      {meta.label}
    </span>
  );
}
