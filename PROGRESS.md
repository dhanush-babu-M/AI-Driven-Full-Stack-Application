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
- [ ] **Phase 4B Test Gate**: Verify weekly tasks hand math, week/month period isolation, Dashboard vs Monthly Planner exact match, and 360px mobile view

## Phase 5: Coding Activity & GitHub Integration
- [ ] Backend CRUD for `coding_activities` (manual logging + LeetCode/CodingBat profile links, `MANUAL` badge)
- [ ] Backend `connected_accounts` & official GitHub REST API sync into `github_events` (`IMPORTED` badge)
- [ ] `/app/coding` page (matching user wireframe) with Recharts visualizations

## Phase 6: Projects & Job Applications
- [ ] Backend CRUD for `projects`, `project_milestones`, and `project_worklogs`
- [ ] Milestone-based project progress in `ProgressCalculator`
- [ ] Backend CRUD for `job_applications` and automatic `job_status_history` tracking
- [ ] `/app/projects` page with `Projects` | `Job Applications` tabs (matching user wireframe)

## Phase 7: Monthly Reports & Spring Scheduling
- [ ] Backend `monthly_reports` generation using `ProgressCalculator` (`CALCULATED` badge)
- [ ] Spring Scheduling job for periodic report snapshots / reminders respecting `notification_preferences`
- [ ] `/app/reports/:yyyy-MM` page (matching user wireframe) with Recharts charts

## Phase 8: AI Career Assistant (Proposal-Only)
- [ ] Java backend LLM client (API key stored only in server `.env`, never frontend)
- [ ] Rate limiting on all AI endpoints
- [ ] `ai_proposals` entity, service, and endpoints (AI can only PROPOSE; never mutates user records directly)
- [ ] UI workflow to inspect, approve, or reject `AI`-badged proposals (with `record_history` audit trail)

## Phase 9: Settings, Testing, Security Audit & Polish
- [ ] `/app/settings` page (profile, time zone, connected accounts, notification preferences)
- [ ] JUnit 5 + Mockito unit/slice tests (especially `ProgressCalculator`, auth/user isolation, proposal approval)
- [ ] Postman collection and Swagger UI verification across all endpoints
- [ ] Security audit (no leaked keys, `user_id` isolation on every query, rate limits, CORS, validation)
- [ ] Responsive & accessibility check across desktop, tablet, and mobile
