export const TASK_CATEGORIES = [
  { value: 'JAVA_LEARNING', label: 'Java Learning', group: 'Study' },
  { value: 'SPRING_BOOT', label: 'Spring Boot', group: 'Study' },
  { value: 'SQL_MYSQL', label: 'SQL / MySQL', group: 'Study' },
  { value: 'REACT_FRONTEND', label: 'React Frontend', group: 'Study' },
  { value: 'CODING_PRACTICE', label: 'Coding Practice', group: 'Practice' },
  { value: 'PROJECT_DEVELOPMENT', label: 'Project Development', group: 'Project' },
  { value: 'JOB_APPLICATIONS', label: 'Job Applications', group: 'Job Search' },
  { value: 'INTERVIEW_PREP', label: 'Interview Prep', group: 'Job Search' },
  { value: 'MEALS_PERSONAL', label: 'Meals and personal', group: 'Health' },
  { value: 'OTHER', label: 'Other', group: 'Personal' },
];

export const GOAL_CATEGORIES = [
  { value: 'TECHNOLOGIES_TO_LEARN', label: 'Technologies to learn', group: 'Study' },
  { value: 'CODING_PROBLEMS', label: 'Coding problems', group: 'Practice' },
  { value: 'PROJECTS', label: 'Projects', group: 'Project' },
  { value: 'JOB_APPLICATIONS', label: 'Job applications', group: 'Job Search' },
  { value: 'INTERVIEW_PREP', label: 'Interview preparation', group: 'Job Search' },
  { value: 'OTHER', label: 'Other', group: 'Personal' },
];

export const TASK_PRIORITIES = [
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
];

export const TASK_STATUSES = [
  { value: 'PLANNED', label: 'Planned' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'SKIPPED', label: 'Skipped' },
];

export const GROUP_CHIP_STYLES = {
  Study: 'bg-primary-soft text-primary border-primary/20',
  Practice: 'bg-completed-soft text-completed border-completed/25',
  Project: 'bg-pending-soft text-amber-800 border-pending/30',
  'Job Search': 'bg-indigo-50 text-indigo-700 border-indigo-200',
  Health: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  Personal: 'bg-slate-100 text-slate-700 border-border',
};

export function getCategoryMeta(categoryValue) {
  const key = String(categoryValue || '').toUpperCase();
  const found = TASK_CATEGORIES.find((c) => c.value === key);
  if (found) return found;

  // Fallback for Phase 3 demo short names
  if (key === 'LEARNING') return { value: 'JAVA_LEARNING', label: 'Study', group: 'Study' };
  if (key === 'PRACTICE') return { value: 'CODING_PRACTICE', label: 'Practice', group: 'Practice' };
  if (key === 'PROJECTS') return { value: 'PROJECT_DEVELOPMENT', label: 'Project', group: 'Project' };
  if (key === 'APPLICATIONS') return { value: 'JOB_APPLICATIONS', label: 'Job Search', group: 'Job Search' };

  return { value: key || 'OTHER', label: categoryValue || 'Other', group: 'Personal' };
}

export function formatTime12h(timeStr) {
  if (!timeStr) return '';
  const [hStr, mStr] = String(timeStr).split(':');
  const h = Number(hStr);
  const m = Number(mStr);
  if (Number.isNaN(h) || Number.isNaN(m)) return timeStr;
  const period = h >= 12 ? 'PM' : 'AM';
  const hour12 = h % 12 || 12;
  return `${hour12}:${String(m).padStart(2, '0')} ${period}`;
}

export function formatMinutesAsDuration(totalMinutes) {
  const mins = Number(totalMinutes) || 0;
  if (mins <= 0) return '0m';
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  if (h === 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}
