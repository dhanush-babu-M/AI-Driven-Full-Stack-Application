const API_BASE_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(message, status, code, fieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export async function apiFetch(path, options = {}) {
  const url = `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
  const response = await fetch(url, {
    credentials: 'include',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  const contentType = response.headers.get('content-type') || '';
  const data = contentType.includes('application/json') ? await response.json() : null;

  if (!response.ok) {
    throw new ApiError(
      data?.message || `Request failed with status ${response.status}`,
      data?.status || response.status,
      data?.code || 'HTTP_ERROR',
      data?.fieldErrors || {}
    );
  }

  return data;
}

export function fetchHealth() {
  return apiFetch('/api/health');
}

export function registerUser(payload) {
  return apiFetch('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function loginUser(payload) {
  return apiFetch('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function logoutUser() {
  return apiFetch('/api/auth/logout', {
    method: 'POST',
  });
}

export function fetchCurrentUser() {
  return apiFetch('/api/auth/me');
}

export function fetchDashboard(dateStr) {
  const query = dateStr ? `?date=${encodeURIComponent(dateStr)}` : '';
  return apiFetch(`/api/progress/dashboard${query}`);
}

export function fetchTasksByDate(dateStr) {
  return apiFetch(`/api/tasks?date=${encodeURIComponent(dateStr)}`);
}

export function fetchUnfinishedBefore(dateStr) {
  return apiFetch(`/api/tasks/unfinished-before?date=${encodeURIComponent(dateStr)}`);
}

export function fetchMonthSummary(monthStr) {
  return apiFetch(`/api/tasks/month-summary?month=${encodeURIComponent(monthStr)}`);
}

export function createTask(payload) {
  return apiFetch('/api/tasks', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateTask(id, payload) {
  return apiFetch(`/api/tasks/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function updateTaskStatus(id, payload) {
  return apiFetch(`/api/tasks/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function updateTaskReminder(id, payload) {
  return apiFetch(`/api/tasks/${id}/reminder`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function deleteTask(id) {
  return apiFetch(`/api/tasks/${id}`, {
    method: 'DELETE',
  });
}

export function carryForwardTasks(payload) {
  return apiFetch('/api/tasks/carry-forward', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchTaskHistory(id) {
  return apiFetch(`/api/tasks/${id}/history`);
}

export function fetchDailyLog(dateStr) {
  return apiFetch(`/api/daily-logs/${encodeURIComponent(dateStr)}`);
}

export function saveDailyNote(dateStr, reflectionNote) {
  return apiFetch(`/api/daily-logs/${encodeURIComponent(dateStr)}`, {
    method: 'PUT',
    body: JSON.stringify({ reflectionNote }),
  });
}

export function confirmRestDay(dateStr, note = '') {
  return apiFetch(`/api/daily-logs/${encodeURIComponent(dateStr)}/rest-day`, {
    method: 'POST',
    body: JSON.stringify({ note }),
  });
}

export function createLearningRecord(payload) {
  return apiFetch('/api/learning-records', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchGoals(scope, startDate) {
  const params = new URLSearchParams({ scope });
  if (startDate) params.set('start', startDate);
  return apiFetch(`/api/goals?${params.toString()}`);
}

export function createGoal(payload) {
  return apiFetch('/api/goals', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateGoal(id, payload) {
  return apiFetch(`/api/goals/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function deleteGoal(id) {
  return apiFetch(`/api/goals/${id}`, {
    method: 'DELETE',
  });
}

export function carryForwardGoals(payload) {
  return apiFetch('/api/goals/carry-forward', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchWeeklyProgress(startDate) {
  const query = startDate ? `?start=${encodeURIComponent(startDate)}` : '';
  return apiFetch(`/api/progress/weekly${query}`);
}

export function fetchMonthlyProgress(monthStr) {
  const query = monthStr ? `?month=${encodeURIComponent(monthStr)}` : '';
  return apiFetch(`/api/progress/monthly${query}`);
}

export function fetchWeeklyReview(startDate) {
  const query = startDate ? `?start=${encodeURIComponent(startDate)}` : '';
  return apiFetch(`/api/planner/weekly-review${query}`);
}

export function saveWeeklyReview(payload) {
  return apiFetch('/api/planner/weekly-review', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchNotificationPreferences() {
  return apiFetch('/api/notifications/preferences');
}

export function updateNotificationPreferences(payload) {
  return apiFetch('/api/notifications/preferences', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function fetchUpcomingReminders(hours = 24) {
  return apiFetch(`/api/reminders/upcoming?hours=${encodeURIComponent(hours)}`);
}

export function fetchCodingActivities(params = {}) {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, v);
  });
  const suffix = qs.toString() ? `?${qs.toString()}` : '';
  return apiFetch(`/api/coding${suffix}`);
}

export function createCodingActivity(payload) {
  return apiFetch('/api/coding', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateCodingActivity(id, payload) {
  return apiFetch(`/api/coding/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function deleteCodingActivity(id) {
  return apiFetch(`/api/coding/${id}`, {
    method: 'DELETE',
  });
}

export function fetchCodingHistory(id) {
  return apiFetch(`/api/coding/${id}/history`);
}

export function fetchCodingHeatmap({ from, to, source = 'combined' } = {}) {
  const qs = new URLSearchParams({ source });
  if (from) qs.set('from', from);
  if (to) qs.set('to', to);
  return apiFetch(`/api/coding/heatmap?${qs.toString()}`);
}

export function fetchCodingDayDetails(dateStr) {
  return apiFetch(`/api/coding/day/${encodeURIComponent(dateStr)}`);
}

export function fetchCodingStreak({ from, to } = {}) {
  const qs = new URLSearchParams();
  if (from) qs.set('from', from);
  if (to) qs.set('to', to);
  const suffix = qs.toString() ? `?${qs.toString()}` : '';
  return apiFetch(`/api/coding/streak${suffix}`);
}

export function connectGitHub(payload) {
  return apiFetch('/api/github/connect', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchGitHubStatus() {
  return apiFetch('/api/github/status');
}

export function syncGitHub() {
  return apiFetch('/api/github/sync', {
    method: 'POST',
  });
}

export function fetchGitHubRepos() {
  return apiFetch('/api/github/repos');
}

export function fetchGitHubEvents({ from, to } = {}) {
  const qs = new URLSearchParams();
  if (from) qs.set('from', from);
  if (to) qs.set('to', to);
  const suffix = qs.toString() ? `?${qs.toString()}` : '';
  return apiFetch(`/api/github/events${suffix}`);
}

export function fetchProfileSettings() {
  return apiFetch('/api/profile');
}

export function updateProfileSettings(payload) {
  return apiFetch('/api/profile', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function fetchProfileLinks() {
  return apiFetch('/api/profile/links');
}

export function updateProfileLinks(payload) {
  return apiFetch('/api/profile/links', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function fetchProjects() {
  return apiFetch('/api/projects');
}

export function fetchProjectDetail(projectId) {
  return apiFetch(`/api/projects/${projectId}`);
}

export function createProject(payload) {
  return apiFetch('/api/projects', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateProject(projectId, payload) {
  return apiFetch(`/api/projects/${projectId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function updateProjectStatus(projectId, payload) {
  return apiFetch(`/api/projects/${projectId}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function deleteProject(projectId) {
  return apiFetch(`/api/projects/${projectId}`, {
    method: 'DELETE',
  });
}

export function createProjectMilestone(projectId, payload) {
  return apiFetch(`/api/projects/${projectId}/milestones`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateProjectMilestone(projectId, milestoneId, payload) {
  return apiFetch(`/api/projects/${projectId}/milestones/${milestoneId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function toggleProjectMilestone(projectId, milestoneId, payload) {
  return apiFetch(`/api/projects/${projectId}/milestones/${milestoneId}/complete`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function deleteProjectMilestone(projectId, milestoneId) {
  return apiFetch(`/api/projects/${projectId}/milestones/${milestoneId}`, {
    method: 'DELETE',
  });
}

export function createProjectWorklog(projectId, payload) {
  return apiFetch(`/api/projects/${projectId}/worklogs`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateProjectWorklog(projectId, worklogId, payload) {
  return apiFetch(`/api/projects/${projectId}/worklogs/${worklogId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function deleteProjectWorklog(projectId, worklogId) {
  return apiFetch(`/api/projects/${projectId}/worklogs/${worklogId}`, {
    method: 'DELETE',
  });
}

export async function uploadProjectScreenshot(projectId, file, caption = '') {
  const formData = new FormData();
  formData.append('file', file);
  if (caption && caption.trim()) {
    formData.append('caption', caption.trim());
  }

  const response = await fetch(`${API_BASE_URL}/api/projects/${projectId}/media/upload`, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });

  const contentType = response.headers.get('content-type') || '';
  const data = contentType.includes('application/json') ? await response.json() : null;
  if (!response.ok) {
    throw new ApiError(
      data?.message || `Upload failed with status ${response.status}`,
      data?.status || response.status,
      data?.code || 'HTTP_ERROR',
      data?.fieldErrors || {}
    );
  }
  return data;
}

export function addProjectVideoLink(projectId, payload) {
  return apiFetch(`/api/projects/${projectId}/media/video`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function deleteProjectMedia(projectId, mediaId) {
  return apiFetch(`/api/projects/${projectId}/media/${mediaId}`, {
    method: 'DELETE',
  });
}

export function getProjectMediaFileUrl(fileUrl) {
  if (!fileUrl) return '';
  if (fileUrl.startsWith('http://') || fileUrl.startsWith('https://')) {
    return fileUrl;
  }
  return `${API_BASE_URL}${fileUrl.startsWith('/') ? fileUrl : `/${fileUrl}`}`;
}

// Job Applications
export function fetchJobs(params = {}) {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, v);
  });
  const suffix = qs.toString() ? `?${qs.toString()}` : '';
  return apiFetch(`/api/jobs${suffix}`);
}

export function fetchJobStats() {
  return apiFetch('/api/jobs/stats');
}

export function fetchJobDetail(id) {
  return apiFetch(`/api/jobs/${id}`);
}

export function createJob(payload) {
  return apiFetch('/api/jobs', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateJob(id, payload) {
  return apiFetch(`/api/jobs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function updateJobStatus(id, payload) {
  return apiFetch(`/api/jobs/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function updateJobChecklist(id, items) {
  return apiFetch(`/api/jobs/${id}/checklist`, {
    method: 'PUT',
    body: JSON.stringify({ items }),
  });
}

export function deleteJob(id) {
  return apiFetch(`/api/jobs/${id}`, {
    method: 'DELETE',
  });
}

export function fetchUpcomingFollowups() {
  return apiFetch('/api/jobs/followups/upcoming');
}

export function markFollowupDone(followupId) {
  return apiFetch(`/api/jobs/followups/${followupId}/done`, {
    method: 'PATCH',
  });
}

export function rescheduleFollowup(followupId, payload) {
  return apiFetch(`/api/jobs/followups/${followupId}/reschedule`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export { API_BASE_URL };
