# Profile and notifications — design

## Intent

Give every account a home for its settings and a way to talk to the admin:
a profile page, account-level themes, a bug/feature report form, and a
persistent notification system that carries reports, announcements, and
download outcomes to the right person.

## Goals

- `/profile`: display name, password change, theme + mode, report form, and
  the user's own reports with status.
- Themes are account settings (SQLite), not only browser state; `localStorage`
  remains the first-paint cache.
- Persistent notifications with an unread bell in the sidebar and a full
  `/notifications` list. Mark one/all read; links to the target.
- Notification sources:
  - `report_new` → every enabled admin when a user submits a report.
  - `report_resolved` → the reporter when an admin resolves it.
  - `announcement` → every enabled user when an admin broadcasts.
  - `job_done` / `job_failed` → the job owner when a download finishes or
    finally fails.
- Admin gets a Reports inbox (open/resolved) and an announcement composer.
- Fire-and-forget: notifications survive restarts and appear on any page via
  SSE, with no banner/modal.

## Non-goals

- Email/SMTP, web push, mobile push.
- Per-type notification preferences or muting.
- Report threads, replies, attachments.
- Rich-text announcements; plain title + body.

## Anti-goals

- Losing a notification on reload or when the user is on another page.
- Any user reading another user's notifications or reports.
- A theme from a previous account flashing for the next one.
- Duplicate `job_done`/`job_failed` notifications after a worker retry.

## Data model (migration `0010_profile_notifications.sql`)

- `users.theme TEXT NOT NULL DEFAULT 'slate'`
- `users.theme_mode TEXT NOT NULL DEFAULT 'dark'`
- `reports(id TEXT PK, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT CHECK(kind IN ('bug','feature')), title TEXT NOT NULL,
  body TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'open'
  CHECK(status IN ('open','resolved')), created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL, resolved_at INTEGER, resolved_by TEXT)`
- `notifications(id TEXT PK, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE
  CASCADE, kind TEXT NOT NULL, title TEXT NOT NULL, body TEXT, href TEXT,
  read_at INTEGER, created_at INTEGER NOT NULL)`
- Indexes: `reports(status, created_at DESC)`, `reports(user_id, created_at DESC)`,
  `notifications(user_id, created_at DESC)`, `notifications(user_id, read_at)`.

`kind` on notifications is free text so future sources do not need a migration;
the known values are the four above.

## API

Public to signed-in users:

- `GET /api/profile` → `{user, theme, themeMode}`.
- `PATCH /api/profile {name?, theme?, themeMode?}` — theme whitelisted to the
  four families, mode to `light|dark`; returns the same shape.
- `POST /api/reports {kind, title, body}` → `201 {report}`; creates `report_new`
  notifications for all enabled admins.
- `GET /api/reports` → `{reports: [...]}` (own only, newest first).
- `GET /api/notifications?limit=` → `{notifications, unread}` (own only).
- `PATCH /api/notifications/{id}/read` → `204` (404 if not owner).
- `POST /api/notifications/read-all` → `{updated}`.
- `GET /api/notifications/unread` → `{unread}` (cheap badge poll fallback).

Admin only:

- `GET /api/admin/reports?status=open|resolved` → `{reports}` incl. reporter name/email.
- `PATCH /api/admin/reports/{id} {status}` → updates; resolving creates
  `report_resolved` for the reporter exactly once.
- `POST /api/admin/announcements {title, body}` → creates one `announcement`
  per enabled user; returns `{created}`.

SSE: `notification.created` carries `{id, kind, ownerId}`; the existing owner
filter forwards it only to that user. `href` is computed at read time from the
stored row, so the badge/toast can link.

## Backend shape

- `notifications.rs` (new): `create(pool, user_id, kind, title, body, href)`
  inserts a row and returns it; `notify_admins(pool, ...)`;
  `notify_all_users(pool, ...)`; `unread_count`.
- The emit stays in routes/worker because it needs `AppState.events`:
  insert → `state.emit("notification.created", &id, None, Some(owner_id))`.
- Worker: after a job reaches `done`, notify owner with href `/watch/{item}`;
  on final failure (`attempts >= max_attempts`), notify with the truncated
  error. Retries do not notify.
- Reports/announcement endpoints use `AuthUser` / `AdminUser` and the same
  owner-scoped pattern as everything else.

## Frontend shape

- `src/lib/notifications.tsx`: `NotificationProvider` (mounted in the app
  layout) owns `items`, `unread`, `refresh`, `markRead`, `markAllRead`;
  subscribes to `useRealtime` for `notification.created` and refreshes.
- `src/components/NotificationBell.tsx`: sidebar bell + badge, portaled flyout
  with the latest 8, "Mark all read", "View all". Reuses the popover pattern.
- `/notifications`: full list, unread styling, click marks read then follows
  `href`; empty state.
- `/profile`: sections — Account (name), Password, Appearance (ThemePicker),
  Reports (form + own list). Submitting calls the APIs and updates local state.
- Theme sync: on auth load, `GET /api/profile` applies theme/mode and writes
  `mm.appearance`; `ThemePicker` keeps writing localStorage immediately and
  PATCHes `/api/profile` when signed in. A failed PATCH must not block the UI.
- `/admin`: three tabs — Users (existing), Reports, Announce. Reports lists
  reporter, kind, body, status, and a Resolve button; Announce is a title/body
  form with a "sent to N users" confirmation.

## Error handling

- Report title 3–120 chars, body 10–4000; API returns `code:"invalid_report"`.
- Unknown themes/modes return `400 invalid_preference`.
- Notification endpoints always scope by owner; another user's id is `404`.
- Announcement/report fan-out uses one transaction for the recipient rows; a
  notification insert failure fails the request rather than silently dropping.

## Testing

- Rust: profile theme whitelist; report notifies admins; resolve notifies the
  reporter once; announcement reaches every enabled user only; notifications
  are owner-scoped and `read-all` touches only the caller; worker done/failed
  notification helper.
- Vitest: bell unread + mark-all, profile report form, theme persistence call,
  admin reports resolve, notification link click.
- E2E: submit a report as the user, see it in Admin, resolve it, see the
  reporter's notification; finish a fixture download? (existing E2E covers
  uploads, so job notifications are unit-tested instead).
