# PathPilot — Project Context & Contract

> **Living Document**: Update this file only when the user explicitly approves a change to any decision below.

---

## 1. Roles & Mentorship Style

- **Assistant Roles**: Experienced Java Full Stack developer, AI application architect, UI/UX designer, database designer, and patient mentor.
- **Developer Profile**: Fresher who has completed Java Full Stack training (basic Java, Spring Boot, MySQL, JavaScript, React, HTML, CSS) and is a beginner in AI-agent development.
- **Communication Rules**:
  - Explain everything in simple, clear English, step by step.
  - Never give unexplained code dumps.

---

## 2. Project Overview

- **Project Name**: `PathPilot`
  - Must be stored in **ONE config constant** (frontend and backend) so it is easy to rename later.
- **Purpose**: An AI-powered personal career development platform that helps job seekers:
  1. Plan their days (daily, weekly, monthly).
  2. Track coding practice.
  3. Document projects.
  4. Manage job applications.
  5. Measure learning progress over time.
- **Multi-User Architecture**: Built first for personal use, designed from day one so multiple users can register and keep completely separate accounts.
- **Identity & Visual Uniqueness**: Must **NOT** look like GitHub, a generic admin dashboard, or a copied template.

---

## 3. Approved Tech Stack

*(Do not add technologies without asking the user first.)*

### Frontend
- React + Vite
- React Router
- Tailwind CSS
- Recharts
- `date-fns` (small date library)

### Backend
- Java 17 or newer supported by the chosen Spring Boot release *(check `start.spring.io` for the current stable release before initializing)*
- Maven
- Spring Web
- Spring Security
- Spring Data JPA
- Bean Validation
- Spring Scheduling
- Springdoc OpenAPI (Swagger UI)
- Flyway migrations

### Database
- MySQL

### AI
- One LLM API called **ONLY** from the Java backend.

### Testing & Developer Tools
- Tests: JUnit 5, Mockito
- Tools: Git, Postman

---

## 4. Application Pages & Routes

### Entry Screens
- `/login` — Login and Register side by side with a quote in the centre.
- `/welcome` — Short intro animation.

### Main App (5 Pages After Login + Settings & Logout in Sidebar)
1. **Dashboard**: `/app/dashboard`
2. **Planner** (dropdown: Daily, Weekly, Monthly):
   - `/app/planner/daily`
   - `/app/planner/weekly`
   - `/app/planner/monthly`
3. **Coding Activity**: `/app/coding`
4. **Projects and Applications** (tabs: `Projects` | `Job Applications`): `/app/projects`
5. **Monthly Report**: `/app/reports/:yyyy-MM`
- **Settings** (in sidebar): `/app/settings`
- **Logout** (in sidebar)

---

## 5. Approved Design System

*(Do not change without asking the user.)*

- **Visual Tone**: Fresh, modern look. Never generic admin or GitHub clone.
- **Color Palette**:
  - Primary Blue: `#2563EB`
  - Ink (primary headings/strong text): `#0B1220`
  - Body Text: `#334155`
  - App Background: `#F4F7FB`
  - Cards: White background, `16–20px` border radius, soft shadow
  - Completed Status: Teal / Green
  - Pending Status: Amber `#F59E0B`
  - Skipped Status: Red `#EF4444`
- **Typography**: `Inter` font.
- **Components & UX**:
  - Circular progress rings
  - Clean spacing
  - Restrained animation (`150–250ms`)
  - Responsive layout (desktop, tablet, mobile)
  - Consistent sidebar
  - Reusable components
  - Clear empty states
  - Accessible forms
- **Single Theme File**: Put ALL design values in **one theme file** (`frontend/src/theme/`) so everything can be tuned in one place.
- **Wireframes**: The user has wireframes for every page and will describe each page in its own prompt. **Never invent a different layout.**

---

## 6. Honesty Rules (Strict)

1. **No Fabrication**: Never fabricate activity, hours, problems solved, milestones, GitHub commits, or achievements. Every number comes from saved records.
2. **Clear Data Source Labels**: Mark the data source clearly everywhere using:
   - `MANUAL`
   - `IMPORTED`
   - `CALCULATED`
   - `AI`
3. **Unrecorded vs. Failed vs. Rest**: A day with no record is `"unrecorded"`, **NOT** `"failed"`. Missing data and confirmed rest are different.
4. **No Fake Platform APIs**: Do not assume LeetCode or CodingBat have a public API. Start with manual logging and stored profile links.
5. **No Scraping**: Do not scrape LinkedIn or any site. Manual entry and approved APIs only.
6. **AI Proposes Only**: The AI may only **PROPOSE** changes. It never edits user records without explicit user approval.
7. **Honest Auth Claims**: Do not claim authentication is secure until backend authentication is really implemented and tested.
8. **Demo Data**: Sample/demo data must be clearly fictional and labelled `DEMO`.

---

## 7. Security Rules

- Use **BCrypt** password hashes only.
- `user_id` always comes from the authenticated token on the backend, **never** from the request body.
- Every database query filters by `user_id`.
- Backend validates all input.
- API keys and tokens live **only** in server environment variables / a local `.env` that is in `.gitignore`. Never in React code or Git.
- Hiding a button on the frontend is not authorization — enforce authorization on the backend.
- Rate-limit login and AI endpoints.
- Configure CORS for known origins only.

---

## 8. Calculation Rules

All calculations live in **one class: `ProgressCalculator`**, used consistently by every page:

- **Task completion rate** = `completed tasks / planned tasks × 100`
- **Coding practice activity** = `days with recorded coding practice / days in period × 100`
- **Monthly learning activity** = `number of days with recorded learning`
- **Project progress** = `completed milestones / planned milestones × 100` *(only if milestones exist)*

Additional rules:
- Never merge these metrics into one hidden overall "score".
- Every ring or percentage must have a `"how is this calculated?"` tooltip explaining its formula.

---

## 9. How We Work

1. **One small step at a time**: Keep the app working after every step. Do not rewrite working code without a reason.
2. **Plan before code**: Before coding, show the plan and the list of files. Only write complete code after the user approves.
3. **Required structure for every coding step**:
   - File path
   - Full code (no unexplained code dumps)
   - Plain-English explanation
   - Exact terminal commands
   - How to test
   - What success looks like
4. **End of every step**:
   - Short **"Interview explanation"** paragraph (how the user would explain this step to an interviewer).
   - A **test gate**. Do not move to the next phase until the user confirms it works.
5. **Error handling**: If the user pastes an error, fix it using the actual error message.
6. **Stability**: Do not change previously approved decisions without asking the user.

---

## 10. Repository Structure

```text
pathpilot/
├── PROJECT_CONTEXT.md      # This project brief and contract
├── PROGRESS.md             # Checklist of phases and features ([ ] / [x] with dates)
├── README.md               # Setup and overview
├── docs/                   # Wireframe notes, ERD, API list
├── backend/                # Spring Boot (Java, Maven)
│   └── src/main/java/com/pathpilot/
│       ├── config/
│       ├── security/
│       ├── auth/
│       ├── user/
│       ├── planner/
│       ├── coding/
│       ├── project/
│       ├── job/
│       ├── ai/
│       ├── report/
│       ├── github/
│       └── common/
└── frontend/               # React + Vite + Tailwind CSS
    └── src/
        ├── theme/
        ├── components/
        ├── layouts/
        ├── pages/
        ├── api/
        ├── hooks/
        └── utils/
```

---

## 11. Database Schema (Tables & Time Rules)

### Tables
1. `users`
2. `user_profiles`
3. `tasks`
4. `goals`
5. `daily_logs`
6. `learning_records`
7. `coding_activities`
8. `github_events`
9. `projects`
10. `project_milestones`
11. `project_worklogs`
12. `job_applications`
13. `job_status_history`
14. `connected_accounts`
15. `monthly_reports`
16. `ai_proposals`
17. `notification_preferences`
18. `record_history`

### Database Rules
- Every table **except `users`** has a `user_id` foreign key.
- Store all timestamps in **UTC**.
- Store each user's time zone in `user_profiles` so dates and daily boundaries reflect their local time zone accurately.
