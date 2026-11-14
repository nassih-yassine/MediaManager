# Profile and Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Account-level profile settings (name, password, theme), bug/feature reports, and persisted notifications with an admin inbox and broadcast announcements.

**Architecture:** Migration `0010` adds `theme`/`theme_mode`, `reports`, `notifications`. A `notifications.rs` helper writes rows; routes/worker emit `notification.created` over the existing owner-filtered SSE. The app layout mounts a notification provider that feeds a sidebar bell and `/notifications`. `/profile` hosts settings and the report form; Admin gets Users/Reports/Announce tabs.

**Tech Stack:** Rust 1.85, axum 0.8, SQLite/sqlx, Next.js 15 client components, Tailwind v4, SSE.

**Spec:** `docs/superpowers/specs/2026-11-14-profile-notifications-design.md`

## Global Constraints

- Every new table row is owner-scoped; another user's notification/report is `404`, never `403`.
- Theme families: `slate`, `paper`, `ocean`, `moss`; modes: `light`, `dark`.
- Report kinds: `bug`, `feature`; status: `open`, `resolved`.
- Notification kinds: `report_new`, `report_resolved`, `announcement`, `job_done`, `job_failed`.
- SSE payloads stay `{type,id,collectionId,ownerId}`; new events use `type:"notification.created"`.
- No `<select>` or native controls; use `SelectMenu`, `Button` patterns, portaled popovers.
- Bug fixes/features are test-first: Rust tests in-module, Vitest next to code.
- `next build` must work with the backend down.

## Review Focus

- A report submitted while zero admins are enabled: the request still succeeds; no notification is lost for the next admin? (accepted: no recipients, report stays in inbox).
- Two admins resolve the same report: the reporter gets exactly one `report_resolved`.
- A job that fails twice then succeeds: no `job_failed` notifications from the retries.
- An announcement to a user created mid-fan-out: either receives it fully or not at all (single transaction).
- A theme PATCH failing (offline): localStorage still changes, UI does not revert or block.

---

### Task 1: Schema and notification helper

**Files:**
- Create: `crates/api/migrations/0010_profile_notifications.sql`
- Create: `crates/api/src/notifications.rs`
- Modify: `crates/api/src/main.rs` (module declaration)
- Test: `crates/api/src/media.rs` test helpers reused; tests live in `notifications.rs`

**Interfaces:**
- Produces:
  - `notifications::create(pool, user_id: &str, kind: &str, title: &str, body: Option<&str>, href: Option<&str>) -> anyhow::Result<String>` (returns id)
  - `notifications::notify_admins(pool, kind, title, body, href) -> anyhow::Result<Vec<String>>`
  - `notifications::notify_all_users(pool, kind, title, body, href) -> anyhow::Result<Vec<String>>`
  - `notifications::unread_count(pool, user_id) -> anyhow::Result<i64>`

- [ ] **Step 1: Write the migration** exactly as the spec's data model (three ALTER/creates + four indexes).

- [ ] **Step 2: Write the failing tests** in `notifications.rs`: create one row and read it back; `notify_admins` reaches enabled admins only (disabled admin and normal user excluded); `notify_all_users` reaches enabled users only; `unread_count` counts unread for one owner only.

- [ ] **Step 3: Run** `cargo test --manifest-path crates/api/Cargo.toml notifications::` and confirm failure (`module not found`).

- [ ] **Step 4: Implement** `notifications.rs` with a small `Notification` row struct and the four functions. Fan-out uses one transaction; each insert gets `uuid::Uuid::now_v7()` and `db::now_ms()`.

- [ ] **Step 5: Run the tests, then the full Rust suite.** Expected: all pass.

- [ ] **Step 6: Commit** `feat(api): notifications schema and helper`.

### Task 2: Profile API and theme sync

**Files:**
- Modify: `crates/api/src/routes.rs` (profile handlers, router)
- Modify: `crates/api/src/auth.rs` (theme fields on `User` + select lists)
- Test: `crates/api/src/routes.rs` `#[cfg(test)]`
- Modify: `apps/web/src/lib/types.ts`, `apps/web/src/lib/auth.tsx`, `apps/web/src/components/ThemePicker.tsx`
- Test: `apps/web/src/lib/auth.test.tsx`

**Interfaces:**
- Consumes: `AuthUser`.
- Produces:
  - `GET /api/profile -> {user, theme, themeMode}`
  - `PATCH /api/profile {name?, theme?, themeMode?} -> same`
  - `auth::User` gains `theme: String`, `theme_mode: String`; every `SELECT ... users` list includes them.
  - Frontend `useAuth()` gains `preferences: {theme, themeMode} | null` and `savePreferences(partial)`.

- [ ] **Step 1: Write failing route tests**: owner can change theme to `ocean`/`dark`; invalid theme or mode → 400 `invalid_preference`; changing name updates `/api/auth/me`; another user's profile is unaffected.

- [ ] **Step 2: Run the focused tests** and confirm failure.

- [ ] **Step 3: Implement** migration-backed fields in `User`, profile handlers, router entries.

- [ ] **Step 4: Run Rust tests**; then `npx tsc --noEmit` in `apps/web`.

- [ ] **Step 5: Write the failing Vitest** for auth: a successful `/api/profile` response sets `preferences`; `savePreferences` PATCHes and keeps state on failure.

- [ ] **Step 6: Implement** provider preference state + `ThemePicker` persistence (PATCH, ignoring errors). On provider load, apply the server theme and write `mm.appearance`.

- [ ] **Step 7: Run** `npx vitest run src/lib/auth.test.tsx` and the full suite; commit `feat: account-level theme preferences`.

### Task 3: Reports API

**Files:**
- Modify: `crates/api/src/routes.rs`
- Modify: `crates/api/src/models.rs` (report DTO)
- Test: `crates/api/src/routes.rs` tests

**Interfaces:**
- Consumes: `notifications::{create, notify_admins}`; `AuthUser`; `AdminUser`.
- Produces:
  - `POST /api/reports {kind,title,body}` → `201 {report:{id,kind,title,body,status,createdAt}}`
  - `GET /api/reports` → `{reports}` (own, newest first)
  - `GET /api/admin/reports?status=` → `{reports}` incl. `userName`, `userEmail`
  - `PATCH /api/admin/reports/{id} {status}` → `204`; resolving inserts one `report_resolved`.

- [ ] **Step 1: Write failing tests**: user creates a report → admins get `report_new`; empty/short title or body → 400 `invalid_report`; user list excludes other users' reports; admin list includes reporter; resolving twice produces one notification; non-admin admin route → 403.

- [ ] **Step 2: Run focused tests** (fail).

- [ ] **Step 3: Implement** handlers with the input bounds from the spec; resolving checks `status='open'` before updating so the second resolve is a no-op.

- [ ] **Step 4: Emit** `notification.created` for each id returned by the helper; run Rust tests; commit `feat(api): reports inbox`.

### Task 4: Announcements API

**Files:**
- Modify: `crates/api/src/routes.rs`
- Test: `crates/api/src/routes.rs` tests

**Interfaces:**
- `POST /api/admin/announcements {title,body}` → `201 {created}`; title 3–120, body 3–2000.
- Consumes `notifications::notify_all_users`.

- [ ] **Step 1: Failing test**: admin announcement creates one row per enabled user (disabled excluded), each with `kind="announcement"` and the same title/body; non-admin → 403; empty input → 400 `invalid_announcement`.
- [ ] **Step 2: Run, implement, emit `notification.created` for each, run tests.**
- [ ] **Step 3: Commit** `feat(api): admin announcements`.

### Task 5: Notifications API, provider, bell, list

**Files:**
- Modify: `crates/api/src/routes.rs`
- Create: `apps/web/src/lib/notifications.tsx`
- Create: `apps/web/src/components/NotificationBell.tsx`
- Create: `apps/web/src/app/(app)/notifications/page.tsx`
- Modify: `apps/web/src/app/(app)/layout.tsx`, `apps/web/src/components/Sidebar.tsx`
- Test: `apps/web/src/lib/notifications.test.tsx`, `apps/web/src/components/NotificationBell.test.tsx`

**Interfaces:**
- `GET /api/notifications?limit=` → `{notifications, unread}`; `PATCH /api/notifications/{id}/read`; `POST /api/notifications/read-all`; `GET /api/notifications/unread` → `{unread}`.
- `useNotifications()` → `{items, unread, loading, refresh, markRead(id), markAllRead()}`.
- Bell uses `aria-label="Notifications"` and shows `unread`; flyout items are buttons that call `markRead` and follow `href` via `router.push`.

- [ ] **Step 1: Failing Rust tests**: list is owner-scoped; mark-read of another user's id → 404 and leaves it unread; read-all returns the caller's updated count only; unread count matches.
- [ ] **Step 2: Implement and run Rust tests.**
- [ ] **Step 3: Failing Vitest** for the provider: refresh loads items/unread, a `notification.created` `mm:realtime` event refreshes, markAllRead clears the badge; bell renders the count and opens the flyout.
- [ ] **Step 4: Implement** provider + bell + `/notifications`; mount provider inside `PlayerProvider` in the app layout; place the bell in the sidebar header and desktop account block.
- [ ] **Step 5: Run** Vitest + `tsc`; commit `feat(web): notification bell and inbox`.

### Task 6: Profile page

**Files:**
- Create: `apps/web/src/app/(app)/profile/page.tsx`
- Modify: `apps/web/src/components/Sidebar.tsx` (Profile link)
- Test: `apps/web/src/app/(app)/profile/page.test.tsx`

**Interfaces:**
- Consumes `/api/profile`, `/api/auth/password`, `/api/reports`, `useAuth`, `ThemePicker`.

- [ ] **Step 1: Failing component tests**: name save calls `PATCH /api/profile`; password form requires both fields and reports server errors; report form submits kind/title/body and appends the returned report; own reports render status.
- [ ] **Step 2: Implement** the page in sections (Account, Password, Appearance, Reports) with the existing form/button styles.
- [ ] **Step 3: Run Vitest + tsc; commit** `feat(web): profile page with reports`.

### Task 7: Admin tabs, worker notifications, docs and E2E

**Files:**
- Modify: `apps/web/src/app/(app)/admin/page.tsx` (tabs: Users/Reports/Announce)
- Modify: `crates/api/src/worker.rs` (done/failed notifications)
- Test: `crates/api/src/worker.rs` tests + `apps/web/src/app/(app)/admin/page.test.tsx`
- Modify: `AGENTS.md`, `README.md`, `apps/web/e2e/smoke.mjs`

**Interfaces:**
- Worker consumes `notifications::create`; done href `/watch/{item_id}`, failed href `/add`.
- Admin page fetches `/api/admin/reports` and POSTs `/api/admin/announcements`.

- [ ] **Step 1: Failing tests**: worker helper `notify_job_outcome` inserts `job_done` once for a done job and `job_failed` only when `attempts >= max_attempts`; admin page renders reports and resolves one, and sends an announcement.
- [ ] **Step 2: Implement** worker notifications at the existing outcome branches and the two admin tabs.
- [ ] **Step 3: Extend E2E**: as the admin, open `/profile`, send a feature report, open Admin → Reports, resolve it, and assert a `report_resolved` notification appears in the bell (a second seeded user is unnecessary: admin can report and resolve their own).
- [ ] **Step 4: Docs**: AGENTS (notification kinds, owner-scoped reports, theme sync, `/profile`), README bullets, test counts.
- [ ] **Step 5: Run** `make test`, `make lint`, `npx tsc --noEmit`, E2E, then stop dev, `npm run build`, restart dev. Commit `feat: profile, reports, and notifications`.

## Self-Review

- Spec coverage: profile, themes, reports, announcements, notifications, worker outcomes, bell-only (no banner) — Tasks 1–7.
- Type consistency: `theme`/`themeMode`, `report_new`/`report_resolved`/`announcement`/`job_done`/`job_failed`, `notification.created` used consistently.
- Tests named for the Review Focus cases: duplicate resolve, retry failure, disabled recipients, owner scoping, failed theme PATCH.
- No native `<select>`; popovers portaled.
