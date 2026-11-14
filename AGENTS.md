# AGENTS.md

Local, single-user media manager for downloaded media. Next.js UI + Rust API +
SQLite. Read `docs/superpowers/specs/2026-11-14-media-manager-spec.md` before
touching the data model, job queue, or download pipeline.

## Commands (verified on this machine)

```sh
make install    # npm install + cargo fetch
make dev        # API :8787 + web :3000
make api        # API only
make worker     # worker only (MM_ROLE=worker)
make web        # Next dev server only
make test       # cargo test (38) + vitest (96)
make test-web   # vitest only
make e2e        # real Chromium smoke test; needs `make dev` running
make check      # cargo check + tsc --noEmit
make lint       # eslint
cd apps/web && npm run build   # works with the backend down
```

Single Rust test from the repo root:

```sh
cargo test --manifest-path crates/api/Cargo.toml <name>
```

Single frontend test:

```sh
cd apps/web && npx vitest run src/lib/player-utils.test.ts
```

Cargo always runs via `--manifest-path` so the CWD stays at the repo root and
`dotenvy` picks up the root `.env`.

## Architecture rules that are not obvious from the tree

- **Rust owns SQLite and the library files.** The Next app must never write the
  DB or compose filesystem paths. All data goes through `/api`.
- Next rewrites `/api`, `/files`, `/thumbs` to `:8787` (`apps/web/next.config.ts`).
  Use relative URLs in the client; never hardcode `localhost:8787`.
- Pages are client components that fetch in `useEffect`. This is deliberate:
  `next build` must not require a running backend.
- **Jobs are rows, not in-memory tasks.** Claim is a single
  `UPDATE ... RETURNING`; a 30s heartbeat keeps the lease alive; the reaper
  re-queues `running` rows whose `heartbeat_at` is older than `MM_LEASE_S`.
  Don't reintroduce in-process job state as the source of truth.
- SQLite runs in WAL with one writer. Keep write transactions short and never
  hold one across a download.
- FTS5 is created at runtime (`db::ensure_fts`). If it is unavailable, search
  falls back to `LIKE`. Don't assume `MATCH` exists. Its update trigger is scoped to
  `UPDATE OF title`, so progress writes must not reindex the library.
- Storage is sharded: `<kind>/<id[0:2]>/<id>.<ext>`, thumbs in
  `.thumbs/<prefix>/<id>.jpg`. Build paths only through `media::safe_join`.
- Finalize order matters: write `.incoming/<job-id>/` → rename into the library
  → INSERT `items` → mark the job `done`. Keep it.
- `/files/*` and `/thumbs/*` are `ServeDir` mounts. That is what gives video
  seeking (HTTP Range). Don't replace them with handlers that buffer the file.
- Downloads and uploads stream to disk and are capped by `MM_MAX_DOWNLOAD_MB`.
- No sqlx compile-time macros: queries are runtime strings, so there is no
  `DATABASE_URL` / `cargo sqlx prepare` step.
- **Naming:** the stored kind is `music` (DB, files, jobs, URLs). The UI calls it
  **Audio** via `lib/kind.ts`. Do not rename the DB value; it would break files and
  jobs.
- `GET /api/items` accepts `sort=newest|oldest|title|largest|duration`. Cursor
  pagination is only valid for `newest`/`oldest`; other sorts return one page.

## Player, queue, playlists, themes

- **One media element only.** `PlayerProvider` (`src/lib/player.tsx`) mounts a single
  `<video>` in the root layout and keeps it alive across navigation. Rendering a
  second element for audio causes double playback; the existing one is hidden by its
  container when the current item is music.
- Playback state lives in the browser (`localStorage` key `mm.player.v1`), not in
  SQLite. The download queue (`jobs`) and the play queue are different things.
- **Resume points live in SQLite** (`items.position_ms`): saved every ~8s, on pause,
  on end (reset to 0) and on `beforeunload`. `resumeTarget()` decides whether to seek
  (3s minimum, 5s from the end). Don't remove the keepalive save.
- Startup recovery must only requeue **expired leases**. A blanket
  `running -> queued` steals jobs from live workers; there is a regression test.
- The queue drawer reorders via native HTML5 drag events. Playlist reorder persists
  by sending the full ordered `itemIds` to `PUT /api/playlists/:id/order`.
- Playlist positions are integers; duplicates are prevented by the composite PK.
  Deleting an item cascades it out of playlists (FK), and the client also calls
  `player.removeItem(id)` so the queue does not keep a dead entry.
- **Galleries are playlists with `kind='gallery'`**, not a second table. The API
  rejects non-picture items; the UI uses the same `PlaylistMenu` with
  `kind="gallery"` on picture cards. Pictures and galleries share the Library's
  **Galleries tab** (galleries on top, `ungrouped=1` pictures below); `/galleries`
  redirects to `/?kind=picture`, while `/galleries/:id` is the grid detail page.
- **Adding from inside a collection:** `AddItemsDialog` searches the library and
  appends via `POST /api/playlists/:id/items`. The Add media form can target a
  collection up front: uploads attach client-side, downloads carry
  `jobs.collection_id` and the worker attaches after finalize.
- `GET /api/items/:id/playlists?kind=` powers membership checkmarks. Keep the picker
  in sync with mutations via the `mm:playlists-changed` window event.
- **Live updates use SSE.** The API broadcasts JSON on `/api/events`; the browser
  subscribes once via `useRealtime` (`src/lib/realtime.ts`). The Next rewrite buffers
  SSE, so `src/app/api/events/route.ts` proxies the stream explicitly — do not remove
  it or route `/api/events` through the generic rewrite. Events: `item.created`,
  `item.updated`, `item.deleted`, `collection.changed`; each is also re-dispatched as
  the `mm:realtime` window event for tests/inspection.
- **Default gallery:** `ensure_default_gallery` creates `All pictures` at startup and
  backfills existing pictures. Uploads and finished picture downloads are attached to
  it automatically. Music/video/book are never placed there. It is reported as
  `isDefault: true`; `DELETE /api/playlists/:id` on it returns
  `400 default_gallery`, and the UI hides Delete for it.
- **Gallery delete asks keep-or-delete:** `DELETE /api/playlists/:id?mode=move`
  (default) re-attaches the gallery's pictures to the default gallery;
  `mode=delete_items` deletes the files and rows first. The UI uses
  `DeleteCollectionDialog`; plain playlists only ever detach.
- **Gallery lightbox and selection:** clicking a tile opens `GalleryLightbox` (portal,
  arrows/Escape, thumbnail strip, swipe). Header `Select` toggles a tile-selection bar:
  move (append to another gallery, then remove from this one), remove membership, or
  delete files. Inside the default gallery only "Delete pictures" is offered, since
  move/remove would fight the backfill invariant.
- **Library `All` is collection-first:** `GET /api/items?hide_pictures=1` hides
  standalone pictures so the grid shows galleries; the Galleries tab is the place
  where loose pictures appear (`ungrouped=1`).
- **Clearing the download queue:** `POST /api/jobs/clear {scope:"active"|"finished"|"all"}`.
  Active jobs are marked cancelled (workers discard the partial item on completion);
  finished/failed rows are deleted. The Add page exposes this behind a confirm.
- **Multi-file upload:** the Add form accepts several files for pictures/books and
  uploads them sequentially, attaching each to the selected collection.
- **Menus must be portaled.** Popovers inside cards/sidebars get clipped by
  `overflow-hidden`/`overflow-y-auto` (this already happened). `PlaylistMenu`,
  `ThemePicker`, and `SelectMenu` render into `document.body` with fixed coordinates;
  keep new popovers out of scroll containers. Native `<select>`/search widgets are not
  used; `SelectMenu` and `BarSlider` carry the design. Buttons get a pointer cursor
  globally (Tailwind v4 no longer does this).
- **Themes are account settings and tokens.** The picker lives on `/profile` (not the
  sidebar). `src/app/globals.css` defines
  `--canvas`, `--panel`, `--panel-2`, `--line`, `--line-strong`, `--ink`, `--muted`,
  `--accent`, `--accent-ink`, `--danger` for four families × light/dark. To add a
  theme: two CSS blocks plus a swatch in `ThemePicker.tsx`. `data-theme`/`data-mode`
  are set by the inline script in `layout.tsx` before paint.
- Typography is Space Grotesk (installed locally) with JetBrains Mono for numbers.
  No webfont download; keep it that way so builds work offline.
- Keyboard: Space play/pause, ←/→ seek 5s, N/P next/prev, M mute, Q queue drawer,
  `/` focus search. These are wired in `PlayerBar.tsx` and `Library.tsx`. Escape
  leaves library/gallery selection mode (both bars also have an explicit Cancel).

## Desktop and structure

- Target shape is documented in `docs/architecture.md`: Rust server is the source
  of truth; `apps/web` and `apps/desktop` are clients.
- `apps/desktop` is a Tauri 2 window that loads the running web UI at
  `MM_DESKTOP_URL` (default `http://localhost:3001`). It is a client, not a second
  backend; run `make dev` first. Icons come from `apps/web/public/logo.png`.
- The app mark is `apps/web/public/logo.svg` (also the favicon); keep the PNG in
  sync when the SVG changes (`rsvg-convert -w 512 -h 512`).
- Sync is server-authoritative: `sync_changes` is append-only and owner-scoped,
  clients pull `GET /api/sync/changes?since=<seq>`, and media bytes stream on
  demand instead of syncing. Never make a client-local DB the source of truth.

## Sync foundation

- `sync_changes` is append-only and owner-scoped; the cursor is `seq`. Triggers log
  `items`, `playlists`, and `playlist_items` writes, so new handlers do not need to
  write to it. Clients pull `GET /api/sync/changes?since=<seq>&limit=<n>`.
  Media bytes are not synced; they stream on demand.

## Books

- `POST /api/items/:id/open` spawns `MM_OPENER` (default `xdg-open`) detached.
  `/read/:id` auto-opens a book once; pictures stay in the browser.
- Covers: PDF page 1 via `pdftoppm` (fallback `magick`), EPUB via `unzip` picking a
  cover-named image. Missing tools or a coverless EPUB leave the item without a
  thumbnail — that is not an error.
- EPUB preview is intentionally absent; the native reader is the feature.

## Gotchas that have already cost time

- **Never run `next build` while `next dev` is running.** Both write `.next`; the
  build replaces dev chunks, the open tab 404s `main-app.js` /
  `app-pages-internals.js`, hydration never runs, and the UI sits on "Loading…"
  forever. Stop dev, build, then start dev again. This has already bitten once.
- If a page is stuck on "Loading…", first check the Network tab for 404 chunks and
  restart the dev server; the API being down renders an error banner instead.
- `pkill -f target/debug/mm` matches the calling shell and kills it. Use
  `pgrep -x mm` and `kill <pid>`.

- **yt-dlp JS runtime:** only `deno` is enabled by default and deno is not
  installed here. Every invocation passes `--no-js-runtimes --js-runtimes node`
  or YouTube extraction fails.
- Title/duration/dimensions come from yt-dlp's `--write-info-json` output, not
  stdout parsing. Only progress comes from `--progress-template`.
- The sidecar JPG is the required cover artifact. `--embed-thumbnail` is not
  reliable, so cover embedding runs as a separate best-effort `ffmpeg` pass.
  That pass changes the file size — update `items.bytes` after it.
- `ffmpeg` writing a single image needs `-update 1`, otherwise it warns about
  image sequence patterns.
- Permanent download errors (video unavailable, 404/403, private) fail the job
  immediately; everything else retries 3x with exponential backoff.
- **Deleting a downloaded item** used to fail with `FOREIGN KEY constraint failed`
  because `jobs.item_id` had no delete action. Migration `0003` rebuilds `jobs` with
  `ON DELETE SET NULL`. Regression test: `deletes_an_item_that_has_a_job`.
- Bug fixes go test-first: backend tests live in the module (`#[cfg(test)]`), frontend
  tests are Vitest files next to the code (`*.test.ts`). A fix without a reproducing
  test is incomplete.
- **E2E** (`apps/web/e2e/smoke.mjs`, `make e2e`) drives real Chromium over CDP: item
  render, no horizontal overflow, playback, queue removal, theme persistence, and
  delete. It uploads a temporary `E2E DELETE ME` picture to the real library and
  removes it again. Requires web on :3001 and API on :8787.
- The E2E Chromium is launched with `--password-store=basic` / `--use-mock-keychain`.
  Without those, headless runs trigger the desktop keyring unlock prompt.
- Component tests use Testing Library + jsdom. `src/test/setup.ts` polyfills
  `localStorage` because Node 26's global one is unavailable by default.
- The Add form has **no audio/video mode toggle**: music is always `audio+thumb`;
  choosing Video is how you get the full file. Don't re-add the toggle.
- Port 3000 is sometimes already held by another local dev server; Next falls
  back to 3001 and the app still works, because `/api` and `/files` are proxied
  server-side. Check the port in the Next banner before assuming 3000.
- Deleting an item without `?delete_file=true` leaves the file on disk. The UI
  always passes `true`; think before changing that default.
- `pdftoppm` and `unzip` are runtime dependencies for book covers. They are present
  here; absent ones degrade to "no cover", not a failed upload.
- Opening `/read/:id` for a book launches the native reader on purpose. Do not
  "fix" that by removing the effect; it is the requested behavior.

## API contract

- DTOs are camelCase; DB columns are snake_case.
- `GET /api/items?kind=&q=&limit=&cursor=` uses keyset pagination
  (`created_at,rowid`), no `OFFSET`.
- Errors are `{ "error": "...", "code": "..." }`.
- Every content request needs the `mm_session` cookie; anonymous gets `401`, another
  user's row/file gets `404` (never `403`). `/api/health`, `/api/auth/status`,
  `/api/auth/setup`, `/api/auth/login` and `/api/auth/logout` are the only public
  routes. CORS is locked to `MM_WEB_ORIGIN`; state changes also reject foreign Origins.

## Auth and ownership

- `auth.rs` owns Argon2id hashes, hashed session tokens, the `AuthUser`/`AdminUser`
  extractors, the login limiter, and bootstrap/adoption. Cookie is
  `HttpOnly; SameSite=Lax; Max-Age=MM_SESSION_DAYS`.
- **Every query is owner-scoped.** `items`, `playlists` and `jobs` carry `owner_id`.
  When adding a handler, take `AuthUser` and add `AND owner_id = ?` to both reads and
  writes; the two-user regression test is `other_users_items_and_files_are_invisible`.
- **No `ServeDir` for library or thumbs.** `/files/{*path}` and `/thumbs/{*path}` look
  up the row, compare owners, then delegate to `ServeFile` so HTTP Range (seeking)
  still works. Do not replace this with a buffered handler.
- The first admin comes from `/setup` or `MM_ADMIN_EMAIL`/`MM_ADMIN_PASSWORD`.
  `auth::adopt_legacy` assigns all pre-auth rows (`owner_id IS NULL`) to the only
  admin and marks the old `All pictures` gallery as its default. Existing media must
  never become invisible on upgrade.
- Default galleries are **per owner** (`ensure_default_gallery(pool, owner_id)`), and
  `AppState` no longer holds a global default gallery. Do not reintroduce one.
- SSE payloads carry `ownerId`; `sse_events` forwards only that owner's events. The
  Next proxy must forward the `cookie` header or live updates 401.
- Admin invariants live in the handlers: no self role/disable/delete, always one
  enabled admin, and user deletion requires `deleteContent=true` (cancels jobs, then
  removes items/files/playlists).
- Frontend: `AuthProvider` + `(app)/layout.tsx` `AuthGate`; `/login` and `/setup` are
  outside the shell. A `401` from `api()` emits `mm:unauthorized`, and logout clears
  `mm.player.v1` so the next account does not inherit the queue.
- E2E needs an existing account: `MM_E2E_EMAIL=... MM_E2E_PASSWORD=... make e2e`.

## Profile, reports, notifications

- **Themes live on the account** (`users.theme`, `users.theme_mode`). `/api/profile`
  reads/writes them; `AuthProvider` applies them and keeps `mm.appearance` as the
  first-paint cache. `ThemePicker` PATCHes when signed in and never blocks on failure.
- **`/profile`** owns display name, password, appearance, and the user's reports.
  Reports have `kind=bug|feature`, `status=open|resolved`, 3–120 char titles and
  10–4000 char bodies (`invalid_report` otherwise).
- **Reports notify every enabled admin** (`report_new`); resolving notifies the
  reporter exactly once (`report_resolved`). The update uses
  `WHERE id=? AND status='open'` so a second resolve is a no-op.
- **Announcements** (`POST /api/admin/announcements`) fan out one `announcement`
  notification per enabled user in a transaction. There is no banner; the bell is
  the delivery surface by request.
- **Download outcomes** write `job_done`/`job_failed` for the job owner. Only the
  final failure notifies; retries stay silent.
- Notification kinds are free text in SQLite; keep the four known names above.
  `notifications.rs` owns create/fan-out/unread; routes/worker emit
  `notification.created` with the recipient's owner id so the SSE filter works.
- All notification/report endpoints are owner-scoped; another user's id is `404`.
  `read-all` only touches the caller. Admin report lists join the reporter's
  name/email and are `AdminUser`-only.
- Frontend: `NotificationProvider` feeds the sidebar `NotificationBell` (portaled)
  and `/notifications`; `notification.created` refreshes the list and badge.

## CI/CD

- `.github/workflows/ci.yml`: Rust fmt/tests, web tsc/lint/Vitest/build, a real
  Chromium smoke test (bootstraps `MM_ADMIN_*`, seeds a 60s MP3, runs the web on
  :3001), and Docker builds of both images.
- `.github/workflows/release.yml`: on `v*` tags, Tauri desktop bundles plus
  `ghcr.io/<repo>/api` and `/web` images. The web image bakes `MM_API_URL` at
  build time because `next.config.ts` resolves rewrites during `next build`.
- `.github/workflows/deploy.yml`: SSH to the host and run
  `docker compose -f compose.yaml pull && up -d`. Secrets: `DEPLOY_HOST`,
  `DEPLOY_USER`, `DEPLOY_SSH_KEY`, optional `DEPLOY_PATH`.
- `compose.yaml` runs `api` (uid 1000, `/library` volume, ffmpeg/yt-dlp/node in
  the image) and `web`. Server `.env` supplies `IMAGE_PREFIX`, admin bootstrap,
  `MM_WEB_ORIGIN` and `MM_COOKIE_SECURE=1`; TLS is the optional Caddy profile.
- E2E requires a seeded playable item in CI; a fresh DB alone fails the resume
  test. Do not remove the seed step without replacing it.
- Docker is not installed on this development machine; Podman is (`make images`,
  `podman compose -f compose.yaml -f compose.podman.yaml`). Validate workflows with
  `python3 -c "import yaml; yaml.safe_load(open(...))"` and keep the Dockerfiles
  in sync with real build paths.

## Smoke test after changes

1. `make dev`, open `http://localhost:3000` (or the port Next reports), sign in (or
   complete `/setup` on a fresh database).
2. Upload a picture on `/add` → thumbnail appears in the grid.
3. Request the file with `Range: bytes=0-99` → expect `206`.
4. Add a YouTube URL as music → `.mp3` + JPG appear in the library, cover is
   embedded (`ffprobe` shows an `mjpeg` stream).
5. Delete with `delete_file=true` → both DB row and file are gone.
6. Upload a PDF as a book → cover is page 1; `Open in app` launches the reader.
   Re-upload it at `/read/:id` to check the auto-open path.
7. Click a card's play button, navigate to `/playlists` → audio keeps playing in
   the bar; open the queue drawer and reorder.
8. Create a playlist, select items in the library, add them, drag to reorder.
9. Switch theme on `/profile`, reload (or sign in on another browser) → theme and mode persist.
