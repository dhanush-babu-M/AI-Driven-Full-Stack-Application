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

export { API_BASE_URL };
