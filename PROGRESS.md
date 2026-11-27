# PathPilot — Build Progress Checklist

> Legend: `[ ]` Todo | `[x] Done (YYYY-MM-DD)`  
> Update this file at the completion of each step/test gate.

---

## Phase 1: Foundation, Repo & Database Schema
- [x] Create `pathpilot/` repository folder structure with `.gitkeep` placeholders (2026-11-26)
- [x] Write `PROJECT_CONTEXT.md` and `PROGRESS.md` (2026-11-26)
- [x] Confirm LLM API direction (Google Gemini API via backend) & GitHub repository target (2026-11-26)
- [x] Create root/backend/frontend `.gitignore` files and `.env.example` templates (2026-11-26)
- [x] Document ERD (`docs/erd.md`) for all 18 tables (2026-11-26)
- [x] Initialize Spring Boot backend (`pom.xml`, `application.yml`, `application-local.yml`, `AppProperties`, `SecurityConfig`, `GlobalExceptionHandler`, Swagger UI, `/api/health`) (2026-11-26)
- [x] Create Flyway migration `V1__init.sql` for all 18 tables (UTC timestamps, `user_id` foreign keys, `(user_id, date)` indexes) (2026-11-26)
- [x] Initialize React + Vite + Tailwind frontend (`tokens.css`, `tailwind.config.js`, `app.js`, `client.js`, `AppShell`, `Sidebar`, `TopBar`, `PageHeader`, reusable components, placeholder routes, and `/api/health` card) (2026-11-26)
- [ ] **Phase 1 Test Gate**: Install local prerequisites (if missing), create `pathpilot` MySQL DB, run `mvn spring-boot:run` + `npm run dev`, and verify all acceptance checks + first GitHub push

## Phase 2: Authentication, User Profiles & Entry Screens
- [x] Backend password hashing with BCrypt (`users` + `user_profiles` with default `Asia/Kolkata` time zone + `V2__add_user_phone.sql`) (2026-11-26)
- [x] Backend registration, login, logout, and current-user endpoints (`/api/auth/*`) with Bean Validation and `UserDto` (2026-11-26)
- [x] Stateless JWT (`httpOnly` cookie) security filter + `CurrentUser.id()` helper ensuring `user_id` comes strictly from the security context (2026-11-26)
- [x] CORS configuration (known origins only), local-only Swagger profile guard, and per-IP rate limiting (429) on login & register (2026-11-26)
- [x] Frontend `AuthContext`, `apiFetch` (`credentials: 'include'`), and `Sidebar` real user display + logout (2026-11-26)
- [x] `/login` page (Login & Register side by side with a quote in the centre, client-side validation, wired to real auth API) (2026-11-26)
- [x] `/welcome` page (dark navy `#0B1220` intro animation, one-time `sessionStorage` flag, Skip + Esc/Enter, `prefers-reduced-motion`) (2026-11-26)
- [x] Auth-protected route wrappers (`ProtectedRoute` & `PublicOnlyRoute`), backend unit/integration tests, and `docs/api.md` (2026-11-26)
- [ ] **Phase 2 Test Gate**: Verify `mvn test`, register in browser -> MySQL `$2` BCrypt hash check, wrong password + 429 rate limit, `/app/dashboard` auth redirect, and Postman collection

## Phase 3: Core Architecture, Theme & Reusable UI Shell
- [x] Implement `ProgressCalculator` class (pure Java calculation contract + `ProgressCalculatorTest` unit tests) (2026-11-26)
- [x] Implement data source badge component (`MANUAL`, `IMPORTED`, `CALCULATED`, `AI`, `DEMO`, `RULE`) (2026-11-26)
- [x] Implement `"how is this calculated?"` tooltip component (2026-11-26)
- [x] Build circular progress ring component (`ProgressRing` + Dashboard empty-ring state) (2026-11-26)
- [x] Build responsive sidebar & updated `TopBar` (search UI, bell unread dot, user chip dropdown) (2026-11-26)
- [x] Build `/app/dashboard` (`DashboardPage`, `GET /api/progress/dashboard`, `DemoDataSeeder` under `demo` profile, responsive 12-col grid) (2026-11-26)
- [ ] **Phase 3 Test Gate**: Verify `mvn test`, new-account empty states vs `demo` profile seeded math, and responsive 360px/tablet/desktop layout

## Phase 4: Planner (Daily, Weekly, Monthly) & Dashboard
- [x] Backend CRUD APIs for `tasks`, `daily_logs`, `learning_records`, and `record_history` + `V4__planner_daily_fields.sql` (all scoped by `CurrentUser.id()`, soft-delete, carry-forward, reschedule link) (2026-11-26)
- [x] Support `"unrecorded"` vs. confirmed rest day vs. recorded day logic (`POST /api/daily-logs/{date}/rest-day`) (2026-11-26)
- [x] Sidebar Planner dropdown (smooth animation, chevron rotation, light-blue active child, filled-blue parent, auto-open) & `PlannerChooser` (`/app/planner`) (2026-11-26)
- [x] `/app/planner/daily` page (date controls, month calendar strip, Step 1 Chooser, task list + modal + 3-dot menu, Meal reminder card, Daily notes & Learned today, task history modal) (2026-11-26)
- [x] `/app/planner/weekly` page (`WeeklyPlannerPage`, goals, Mon–Sun day tabs, task list, summary tiles, collapsible weekly review) (2026-11-26)
- [x] `/app/planner/monthly` page (`MonthlyPlannerPage`, month calendar grid, monthly goals with `CALCULATED`/`MANUAL` badges, 4 overview rings + prev-month change chips, summary & milestones) (2026-11-26)
- [x] `/app/dashboard` page using `ProgressCalculator` and real saved records (2026-11-26)
- [x] Phase 4C Reminders & Notifications: `reminder_at_utc` calculation (`ZoneId` + lead time), `GET/PUT /api/notifications/preferences`, `GET /api/reminders/upcoming`, `useReminders` hook, TopBar bell list & unread count, Daily Planner `"Set Reminder"`, and `/app/settings` Notifications UI (2026-11-26)
- [ ] **Phase 4B/4C Test Gate**: Verify weekly/monthly planner math, 2-min breakfast reminder toast + browser notification, permission denied flow, and closed-tab honesty note

## Later / Future Enhancements
- [ ] Future: Web Push with a Service Worker and push service for background reminder delivery when PathPilot tabs are closed

## Phase 5: Coding Activity & GitHub Integration
- [x] Backend CRUD for `coding_activities` (manual logging + LeetCode/CodingBat profile links, `MANUAL` badge, `record_history` audit trail, `V7__coding_and_github_fields.sql`) (2026-11-26)
- [x] Backend `connected_accounts` (AES-GCM encrypted optional token) & official GitHub REST API v2022-11-28 sync into `github_events` (`IMPORTED` badge, `@Scheduled` twice-daily sync, `(user_id, external_event_id)` de-duplication, rate-limit handling) (2026-11-26)
- [x] `/app/coding` ("Developer Activity") page with Mon–Sun heatmap (`GitHub imported | Manual practice | Combined`), day drawer, External Profiles + repos card, Recharts activity summary, honest streaks, neutral inactive periods, rule banner, and practice table (2026-11-26)
- [ ] **Phase 5A Test Gate**: Verify empty states + "Log your first practice", log 3 practice entries on different days and verify heatmap/streak/summary/drawer, sync a public GitHub username twice without duplicates, verify rate-limit handling, and confirm no LeetCode/CodingBat import claims

## Phase 6: Projects & Job Applications
- [x] Backend CRUD for `projects`, `project_milestones`, `project_worklogs`, and `project_media` (`V8__projects_and_profile_settings.sql`, strict `CurrentUser.id()` filtering, status history in `record_history`, safe local screenshot upload outside web root) (2026-11-27)
- [x] Milestone-based project progress in `ProgressCalculator` (returns `null` when zero milestones, feeds Project cards and Dashboard Project Progress card) (2026-11-27)
- [x] Profile Links & Settings (`Portfolio`, `GitHub` username/URL, `LeetCode`, optional `LinkedIn` & `CodingBat` with `https://` validation, time zone dropdown, skills with Beginner/Working/Comfortable levels, target roles) + prominent 3 link pills on Dashboard & Projects page (2026-11-27)
- [x] `/app/projects` page (Page 4A: stacked `My Projects` + `Job Applications` placeholder on desktop, tabs on mobile, responsive cards, detail drawer with Overview/Milestones/Work Log/Media, work logs synced to Coding day drawer & Monthly progress) (2026-11-27)
- [x] **Phase 5B / Page 4A Test Gate**: Ran `mvn test` (51/51 passed) and `npm run build` (passed) (2026-11-27)
- [x] Backend CRUD for `job_applications`, `job_status_history`, and `job_followups` (`V9__job_applications_and_followups.sql`, strict `CurrentUser.id()` filtering, plain-text JD sanitization, date/URL validation, follow-up reminders in `ReminderService`, Monthly Planner `JOB_APPLICATIONS` goal count, Dashboard `Recent Job Applications` + `Upcoming Follow-ups`) (2026-11-27)
- [x] Full `Job Applications` section on `/app/projects` (`stats` strip, search/status/date filters, `View All` / pagination, inline status dropdown writing `job_status_history`, detail drawer with `Details` + Interview Prep Checklist / `Timeline` / `Notes` / disabled `"Analyse with AI"`, `Upcoming Follow-ups` card with amber overdue highlighting) (2026-11-27)
- [ ] **Phase 6 / Page 4B Test Gate**: Run `mvn test` and `npm run build`, add 3 applications with different statuses -> verify stats, dashboard, and timeline timestamps, set a follow-up for tomorrow -> verify Upcoming Follow-ups & reminder, and verify Monthly `JOB_APPLICATIONS` goal count matches applied rows in month

## Phase 7: Monthly Reports & Spring Scheduling
- [ ] Backend `monthly_reports` generation using `ProgressCalculator` (`CALCULATED` badge)
- [ ] Spring Scheduling job for periodic report snapshots / reminders respecting `notification_preferences`
- [ ] `/app/reports/:yyyy-MM` page (matching user wireframe) with Recharts charts

## Phase 8: AI Career Assistant (Proposal-Only)
- [x] Documented LLM provider comparison & implemented swappable `LlmClient` interface + `GeminiLlmClient` (`gemini-3.5-flash-lite`, `LLM_API_KEY` read strictly server-side, timeout/backoff/quota handling) + `docs/ai.md` (2026-11-27)
- [x] Sliding-window per-user rate limit on `/api/ai/**` (`AiRateLimitFilter`) + per-user response caching (`AiCacheService`) + strict schema/grounding retry & rule fallback (`AiStructuredExecutor`) (2026-11-27)
- [x] Daily Recommendation (`RuleRecommendationEngine` + `GET /api/ai/recommendation`), Dashboard AI Suggestion card (`AI`/`RULE` `SourceTag`, disclaimer, `"Add as task"` -> Approval Inbox), and global TopBar Approval Inbox + Agent modal (2026-11-27)
- [x] Job Description Analysis (`POST/GET /api/ai/jd-analysis`, `V10` persistence on `job_applications`, strict profile/project grounding, `"Analyse with AI"` tab in `JobDetailDrawer`, `"Turn checklist into tasks"` batch proposals) (2026-11-27)
- [x] Monthly Summary AI (`POST /api/ai/monthly-summary/{yyyy-MM}`, `MonthlySummaryGrounder` numeric verification, `V10` persistence on `monthly_reports`, interactive summary panel in `MonthlyPlannerPage`) (2026-11-27)
- [x] Bounded Agent Mode (`POST /api/ai/agent`, max 4 tool calls, `AgentToolRegistry` blocking `userId` arguments with `403`, `proposeTask` proposal-only) + unit/integration test suite mocking `LlmClient` (2026-11-27)
- [ ] **Phase 7 (AI Layer) Test Gate**: Run `mvn test` and `npm run build`, test recommendation/JD analysis/monthly summary/agent with & without `LLM_API_KEY`, verify Approval Inbox creates real tasks only after approval, and review interview explanation of `"how the AI workflow works"`

## Phase 9: Settings, Testing, Security Audit & Polish
- [ ] `/app/settings` page (profile, time zone, connected accounts, notification preferences)
- [ ] JUnit 5 + Mockito unit/slice tests (especially `ProgressCalculator`, auth/user isolation, proposal approval)
- [ ] Postman collection and Swagger UI verification across all endpoints
- [ ] Security audit (no leaked keys, `user_id` isolation on every query, rate limits, CORS, validation)
- [ ] Responsive & accessibility check across desktop, tablet, and mobile
