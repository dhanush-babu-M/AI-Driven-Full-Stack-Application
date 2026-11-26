# PathPilot REST API Documentation

Base URL (local): `http://localhost:8080`

## Authentication Mechanism (Option A)

- **Stateless JWT in an `httpOnly` Cookie**: On `POST /api/auth/login`, the backend signs a short-lived HS256 JWT (default 30 minutes via `JWT_EXPIRATION_MINUTES`) and sets the `pathpilot_token` cookie (`HttpOnly`, `SameSite=Lax`, `Secure` controlled by `JWT_COOKIE_SECURE`).
- Frontend requests use `credentials: 'include'` so the browser attaches the cookie automatically without exposing the token to JavaScript (`localStorage` is never used).
- **Server-derived identity**: Protected endpoints read the user ID strictly from `CurrentUser.id()` (Spring `SecurityContext`), never from the request body or query params. Every future user-owned repository query must filter by `CurrentUser.id()` (for example `findAllByUserId(CurrentUser.id())`).
- **Swagger UI**: Available at `/swagger-ui.html` **only** when the `local` Spring profile is active.

---

## Standard Error Response

All error responses (`400`, `401`, `409`, `429`, `500`) return:

```json
{
  "timestamp": "2026-11-26T13:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Input validation failed.",
  "fieldErrors": {
    "email": "Enter a valid email address."
  }
}
```

---

## Public Endpoints

### 1. Health Check
- **Method & Path:** `GET /api/health`
- **Auth:** Public
- **Response (`200 OK`):**
  ```json
  {
    "status": "UP",
    "appName": "PathPilot",
    "time": "2026-11-26T13:00:00Z"
  }
  ```

### 2. Register User
- **Method & Path:** `POST /api/auth/register`
- **Auth:** Public
- **Rate Limit:** 5 attempts per 60 seconds per IP (`429 Too Many Requests` on 6th rapid request)
- **Request Body:**
  ```json
  {
    "fullName": "Alex Johnson",
    "phone": "9876543210",
    "email": "alex@example.com",
    "password": "Password123",
    "confirmPassword": "Password123"
  }
  ```
- **Validation & Behavior:**
  - `fullName`: required, 2–150 characters.
  - `phone`: required, digits only, 10–15 digits, unique.
  - `email`: required, valid email format, normalized lowercase, unique.
  - `password`: minimum 8 characters with at least 1 letter and 1 number; hashed with BCrypt before saving.
  - `confirmPassword`: must match `password`.
  - Automatically creates a `user_profiles` row with default `time_zone = "Asia/Kolkata"`.
- **Response (`201 Created`):**
  ```json
  {
    "id": 1,
    "fullName": "Alex Johnson",
    "email": "alex@example.com",
    "phone": "9876543210",
    "role": "USER",
    "timeZone": "Asia/Kolkata",
    "createdAtUtc": "2026-11-26T13:00:00Z"
  }
  ```

### 3. Login
- **Method & Path:** `POST /api/auth/login`
- **Auth:** Public
- **Rate Limit:** 5 attempts per 60 seconds per IP (`429 Too Many Requests` on 6th rapid request)
- **Request Body (`identifier` accepts email OR phone):**
  ```json
  {
    "identifier": "alex@example.com",
    "password": "Password123"
  }
  ```
- **Response (`200 OK`):**
  - Sets `Set-Cookie: pathpilot_token=<jwt>; Path=/; Max-Age=1800; HttpOnly; SameSite=Lax`
  - Body: `UserDto` (same JSON fields as registration response).
- **Error (`401 Unauthorized`):** Returns generic `"Invalid credentials"` for both unknown user and wrong password.

### 4. Logout
- **Method & Path:** `POST /api/auth/logout`
- **Auth:** Public (idempotent cookie clear)
- **Response (`200 OK`):** Clears `pathpilot_token` (`Max-Age=0`) and returns `{"message": "Logged out successfully."}`.

---

## Protected Endpoints (Require Valid `pathpilot_token` Cookie)

### 5. Current Authenticated User
- **Method & Path:** `GET /api/auth/me`
- **Auth:** Required (`401 Unauthorized` if missing/invalid/expired cookie)
- **Response (`200 OK`):** `UserDto` for `CurrentUser.id()`.

### 6. Dashboard Progress Summary
- **Method & Path:** `GET /api/progress/dashboard?date=YYYY-MM-DD` (`date` optional, defaults to today in the user's profile `timeZone`)
- **Auth:** Required (`401 Unauthorized` if missing/invalid cookie)
- **Behavior:** Reads all records filtered strictly by `CurrentUser.id()`, computes completion rates via `ProgressCalculator` (returning `null` when nothing is planned, excluding `RESCHEDULED` tasks from both sides), computes the honest coding streak, builds 12 Mon–Sun `githubWeeks`, and generates a deterministic `RULE`-sourced recommendation.

---

## Daily Planner Endpoints (All Require Auth & Filter by `CurrentUser.id()`)

- `GET /api/tasks?date=YYYY-MM-DD` — Lists active (non-soft-deleted) tasks for the given date ordered by priority, start time, id.
- `GET /api/tasks/unfinished-before?date=YYYY-MM-DD` — Lists `PLANNED` / `PENDING` tasks from earlier dates for Step 1 Chooser / carry-forward.
- `GET /api/tasks/month-summary?month=YYYY-MM` — Returns per-day Completed/Pending/Skipped counts and `day_status` for the month calendar strip.
- `POST /api/tasks` — Creates a task (validates title, `endTime > startTime`, 10 allowed `TaskCategory` values, `TaskPriority`, `TaskStatus`).
- `PUT /api/tasks/{id}` — Updates a task and appends old/new values to `record_history`.
- `PATCH /api/tasks/{id}/status` — Updates status (`PLANNED`, `COMPLETED`, `PENDING`, `SKIPPED`, `RESCHEDULED`); `RESCHEDULED` creates a linked copy on `rescheduleToDate` and keeps the original record.
- `PATCH /api/tasks/{id}/reminder` — Saves reminder flag and time on the task.
- `DELETE /api/tasks/{id}` — Soft-deletes a task (`deleted_at_utc`) and records `DELETED` in `record_history`.
- `POST /api/tasks/carry-forward` — Copies chosen `PLANNED`/`PENDING` tasks to `targetDate` (`carried_from_task_id`) and marks originals `RESCHEDULED`.
- `GET /api/tasks/{id}/history` — Returns `record_history` audit entries for the user's task (`404` if not owned by caller).
- `GET /api/daily-logs/{date}` & `PUT /api/daily-logs/{date}` — Reads/saves `"What I learned today"` reflection note + `learning_records`.
- `POST /api/daily-logs/{date}/rest-day` — Confirms a rest day (`day_status = REST_DAY`).
- `GET /api/learning-records?date=YYYY-MM-DD` & `POST /api/learning-records` — Lists/creates learning topic entries with minutes spent.

---

## Weekly & Monthly Planner Endpoints (All Require Auth & Filter by `CurrentUser.id()`)

- `GET /api/goals?scope=WEEK|MONTH&start=YYYY-MM-DD` — Lists goals for that Monday-to-Sunday week or calendar month. `done_count` is `CALCULATED` from linked tasks or period logs (`coding_activities`, `job_applications`, `project_milestones`) when available, otherwise `MANUAL`.
- `POST /api/goals`, `PUT /api/goals/{id}`, `DELETE /api/goals/{id}` (soft delete), `POST /api/goals/carry-forward` (creates copy in new period and marks original `RESCHEDULED`).
- `GET /api/planner/weekly-review?start=YYYY-MM-DD` & `POST /api/planner/weekly-review` — Reads/saves per-week review notes (`whatWentWell`, `carryForwardNotes`).
- `GET /api/progress/weekly?start=YYYY-MM-DD` — Weekly completion metrics, category rates, per-day counts (Mon–Sun), and tasks.
- `GET /api/progress/monthly?month=YYYY-MM` — Monthly completion metrics (same `ProgressCalculator` math as Dashboard), category rates, change vs previous month (`null` if previous month has no data), day summaries, and dated milestones.

---

## Reminders & Notification Preferences (In-App Option A)

- `GET /api/notifications/preferences` & `PUT /api/notifications/preferences` — Reads/updates `browserEnabled`, `leadMinutes` (`0..120`, e.g. `0, 5, 10, 30`), `quietHoursStart`, and `quietHoursEnd`. Updating `leadMinutes` recalculates `reminder_at_utc` for active reminder-enabled tasks.
- `GET /api/reminders/upcoming?hours=24` — Returns active reminder-enabled tasks due from the start of the user's local today through the next `hours` hours, including `inQuietHours`.

---

## Coding Activity, Profile Links & GitHub Sync (All Require Auth & Filter by `CurrentUser.id()`)

- `GET /api/coding?from=&to=&platform=&topic=&result=&page=0&size=10` — Lists non-soft-deleted manual coding practice entries in the date range with platform/topic/result filtering and pagination.
- `POST /api/coding` — Logs a manual coding practice entry (`MANUAL` source, Bean Validation) and writes a `CREATE` audit row to `record_history`.
- `PUT /api/coding/{id}` — Updates a manual coding practice entry owned by `CurrentUser.id()` and writes an `UPDATE` audit row to `record_history`.
- `DELETE /api/coding/{id}` — Soft-deletes a manual coding practice entry (`deleted_at_utc`) and preserves audit history in `record_history`.
- `GET /api/coding/{id}/history` — Returns `record_history` entries for that coding activity.
- `GET /api/coding/heatmap?from=&to=&source=github|manual|combined` — Returns per-day counts separated by source (`githubCount`, `githubCommitCount`, `manualCount`, `manualSolvedCount`, `combinedCategory`) so imported and manual activity are never silently blended.
- `GET /api/coding/day/{date}` — Returns imported `github_events`, manual `coding_activities`, and `learning_records` for the `"Activity on <date>"` drawer.
- `GET /api/coding/streak?from=&to=` — Computes attempted/solved totals, honest current & longest streak (`ProgressCalculator`), practice days %, neutral `"no record"` inactive periods, Recharts chart series, and a deterministic `RULE` encouragement banner.
- `GET /api/profile/links` & `PUT /api/profile/links` — Reads/updates `githubUsername`, `leetcodeUrl`, `codingbatUrl`, `portfolioUrl`, and `linkedinUrl` on `user_profiles` (LeetCode/CodingBat are bookmark links only; never scraped).
- `POST /api/github/connect` — Verifies a GitHub username via `GET https://api.github.com/users/{username}`, optionally encrypts a personal access token (AES-GCM, never returned to the browser), saves `connected_accounts` + `user_profiles.github_username`, and runs an initial sync.
- `GET /api/github/status` — Returns connection status, username, `hasToken` boolean (never the token), `lastSyncedAtUtc`, `lastSyncStatus`, `lastSyncError`, and `storedEventsCount`.
- `POST /api/github/sync` — Fetches up to 300 public events from the last 30 days via GitHub REST API (`ETag` / `If-None-Match` conditional request support) and de-duplicates by `(user_id, external_event_id)`. Also runs twice daily (`02:00` and `14:00`) via `@Scheduled`. Returns `429` with a friendly reset message if rate-limited.
- `GET /api/github/repos` — Returns up to 10 recently pushed public repositories (`name`, `fullName`, `language`, `pushedAtUtc`, `htmlUrl`, `stars`) from GitHub REST API.
- `GET /api/github/events?from=&to=` — Lists stored `github_events` (`IMPORTED` / `DEMO`) for the user in the requested date range.



