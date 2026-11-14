# MediaManager — Specification

Date: 2026-11-14
Status: awaiting approval (supersedes `2026-11-14-media-manager-design.md`)

## 1. Intent

A single-user, local media manager for *downloaded* content: music, video,
pictures, books. Paste a URL, choose what you want back (audio-only with cover art,
or the full video), and it lands in a clean, browsable library on disk. No accounts,
no cloud. **Built to survive heavy use**: many concurrent downloads, large files, a
100k-item library, and stays responsive under all of it — on one machine.

## 2. Goals

- One paste-a-URL flow; audio-only vs full video is an explicit choice at submit time.
- Music keeps its thumbnail: sidecar JPG for the UI, embedded cover art as best-effort.
- Browse, search, play, open-in-file-manager, delete.
- Persistent playback: a player bar that survives navigation, a playback queue, and
  OS media keys.
- Organize with ordered playlists that can hold any kind.
- Books open in the native reader; the first PDF page (or EPUB cover) is the thumbnail.
- Clean UI: quiet surfaces, semantic tokens, four themes × light/dark.
- Durable: a crashed process or power cut loses no job and no finished file.
- Throughput: bounded but configurable concurrency, streaming end-to-end, no
  whole-file buffering.

## 3. Non-goals (v1)

- Indexing media already on disk (explicitly deferred).
- Multi-user, accounts, internet exposure, TLS.
- Subscriptions, channel following, scheduled downloads.
- Subtitles, transcoding, book format conversion, OCR.
- Metadata editing beyond title; no tagging system yet.
- Server-synced "now playing": playback state is per-browser, not per-account.
- **Multi-machine / distributed scale.** Single host, one SQLite file, N worker
  processes on that host. Clustering (Postgres/Redis queue, sharded storage) is a
  deliberate future boundary, not v1.

## 4. Anti-goals

- **Losing files.** SQLite is an index, never the source of truth. Delete asks
  whether the file on disk goes too, and says so.
- **Silent failure.** Every job ends `done`/`failed`/`cancelled` with a readable
  error; a failed job never leaves a partial file visible in the library.
- **Blocking the reader.** No write path may block reads longer than a short
  transaction. WAL mode is the mechanism.

## 5. Scale target (what "heavy load" means here)

| dimension | target |
|---|---|
| library items | 100,000+ |
| concurrent downloads | up to `MM_MAX_CONCURRENT` (default 8), queue unbounded above that |
| file size | streamed, GB-scale; upload/download capped at `MM_MAX_DOWNLOAD_MB` |
| search | sub-50ms on 100k titles via FTS5 |
| listing | keyset pagination, no OFFSET beyond page 1 |
| API | rate-limited, always returns within a few ms except file serving |

## 6. Architecture

```
apps/web   Next.js 15 (App Router, TS strict, Tailwind v4)          :3000
   |  /api/*, /files/* proxied via next.config rewrite (dev)
crates/api Rust axum 0.8 + tokio + sqlx/SQLite + tower-http         :8787
   |  one binary `mm`, roles: `mm serve` (API) | `mm worker` (jobs)
   |  both roles share the same SQLite file (WAL: many readers, one writer)
   |
   +-- library/   sharded by kind + id prefix (see §8)
   +-- .incoming/ partial downloads per job
   +-- yt-dlp     music + video (external binary)
   +-- reqwest    pictures, books, direct file URLs
   +-- ffmpeg     thumbnail conversion, cover-art embedding fallback
```

The durable job queue (SQLite table) is the seam that lets one process do everything
or split API/worker. v1 ships the combined mode as default, with `--role` kept behind
it so scaling to several workers is a config change, not a rewrite.

## 7. Data model

SQLite. On open, apply:

```sql
PRAGMA journal_mode = WAL;        -- readers never block the single writer
PRAGMA synchronous  = NORMAL;     -- durability vs throughput; WAL still survives crash
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;       -- writer contention yields, not SQLITE_BUSY
PRAGMA wal_autocheckpoint = 1000;
PRAGMA journal_size_limit = 67108864;
```

Thumbnails and media are files on disk, never BLOBs. `items` carries an integer
`rowid` (for FTS5 and compact ordering) plus a text UUID `id` (for URLs), so neither
purpose is compromised.

```sql
CREATE TABLE items (
  rowid       INTEGER PRIMARY KEY AUTOINCREMENT,
  id          TEXT NOT NULL UNIQUE,           -- uuid v7 (time-sortable), in URLs
  kind        TEXT NOT NULL CHECK(kind IN ('music','video','picture','book')),
  title       TEXT NOT NULL,
  source_url  TEXT,
  path        TEXT NOT NULL UNIQUE,           -- relative to library root
  thumb_path  TEXT,
  mime        TEXT NOT NULL,
  bytes       INTEGER NOT NULL,
  duration_ms INTEGER,
  width       INTEGER,
  height      INTEGER,
  sha256      TEXT,                           -- set on demand, not at import
  position_ms INTEGER NOT NULL DEFAULT 0,     -- resume point
  last_played_at INTEGER,
  created_at  INTEGER NOT NULL,               -- unix ms
  updated_at  INTEGER NOT NULL
);
CREATE INDEX idx_items_kind_created ON items(kind, created_at DESC, rowid DESC);
CREATE INDEX idx_items_title        ON items(title);

CREATE TABLE jobs (
  id            TEXT PRIMARY KEY,             -- uuid v7
  dedupe_key    TEXT NOT NULL,                -- hash(url|kind|mode)
  url           TEXT NOT NULL,
  kind          TEXT NOT NULL,
  mode          TEXT NOT NULL CHECK(mode IN ('audio+thumb','full')),
  status        TEXT NOT NULL DEFAULT 'queued'
                  CHECK(status IN ('queued','running','done','failed','cancelled')),
  stage         TEXT,                         -- fetching|converting|thumbnail|finalizing
  progress      REAL NOT NULL DEFAULT 0,      -- 0..1
  error         TEXT,
  item_id       TEXT REFERENCES items(id),
  priority      INTEGER NOT NULL DEFAULT 0,   -- higher runs first
  attempts      INTEGER NOT NULL DEFAULT 0,
  max_attempts  INTEGER NOT NULL DEFAULT 3,
  scheduled_at  INTEGER NOT NULL,             -- unix ms
  claimed_by    TEXT,
  claimed_at    INTEGER,
  heartbeat_at  INTEGER,
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL
);
CREATE INDEX idx_jobs_dequeue ON jobs(status, priority DESC, scheduled_at, created_at);
CREATE INDEX idx_jobs_dedupe  ON jobs(dedupe_key);
CREATE INDEX idx_jobs_item    ON jobs(item_id);

-- Playlists (migration 0002). Items are removed with the playlist or the item.
CREATE TABLE playlists (
  id TEXT PRIMARY KEY, name TEXT NOT NULL,
  kind TEXT NOT NULL DEFAULT 'playlist',   -- playlist | gallery
  cover_path TEXT,                          -- custom cover under .thumbs/covers/
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE TABLE playlist_items (
  playlist_id TEXT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
  item_id     TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  position    INTEGER NOT NULL,
  added_at    INTEGER NOT NULL,
  PRIMARY KEY (playlist_id, item_id)
);
CREATE INDEX idx_playlist_items_order ON playlist_items(playlist_id, position);

CREATE VIRTUAL TABLE items_fts USING fts5(
  title, content='items', content_rowid='rowid'
);
-- AFTER INSERT/UPDATE/DELETE triggers keep items_fts in sync with items.
```

`content=` external-content FTS means the FTS index stores only the search terms and
reads the canonical title from `items`, so there is a single source of truth.

## 8. Storage layout (sharded)

100k files in one directory degrades on most filesystems, so shard by id prefix:

```
<MM_LIBRARY_DIR>/
  .mm.db
  .incoming/<job-id>/...          partial work, never served
  music/ab/<id>.mp3
  video/7c/<id>.mp4
  picture/0e/<id>.jpg
  book/   d4/<id>.pdf
  .thumbs/ab/<id>.jpg
```

`<id>` is the item UUID; `<prefix>` is `id[0..2]`. Every filesystem path is derived
from a DB row, joined under the library root, then canonicalised and checked with a
`starts_with` guard before any read/write — path traversal is rejected at the boundary,
not sprinkled through handlers.

## 9. Job queue & worker (the load-bearing part)

Jobs are rows, not in-memory tasks, so they survive restarts and several workers can
pull them. Workers are tokio tasks (one per concurrency slot), each running a
claim → execute → finalize loop.

**Claim** is a single atomic statement — safe across many workers because SQLite
allows one writer and the claim is one write:

```sql
UPDATE jobs
SET status = 'running', claimed_by = :worker, claimed_at = :now,
    heartbeat_at = :now, attempts = attempts + 1, updated_at = :now
WHERE id = (
  SELECT id FROM jobs
  WHERE status = 'queued'
    AND scheduled_at <= :now
    AND attempts < max_attempts
  ORDER BY priority DESC, created_at
  LIMIT 1
)
RETURNING *;
```

**Lease / crash recovery.** A worker heartbeats (`UPDATE jobs SET heartbeat_at=:now`)
every 30s while the download runs. A reaper loop (every 15s) finds `status='running'
AND heartbeat_at < now - 60s` — a crashed worker — and re-queues the job for another
attempt, restoring any partial work to `.incoming`. `attempts` caps retries; exhausted
jobs go `failed` with a permanent error.

**Concurrency & backpressure.**
- `MM_MAX_CONCURRENT` (default 8) bounds active downloads per host, enforced by a
  `Semaphore` in-process and by the claim loop polling only while slots are free.
- Queue depth is capped (`MM_MAX_QUEUED`, default 500): submitting beyond it returns
  `429` with a retry hint instead of growing unbounded.
- Retries are exponential backoff (`scheduled_at = now + min(2^attempts, 300)s`).

**Idempotent submit.** `dedupe_key = hash(url|kind|mode)`; if an active job (queued
or running) shares it, the API returns `409` with that job's id rather than queueing a
duplicate. A finished duplicate is allowed (re-download is a real operation).

**Finalize order** (crash-safe): write to `.incoming/<id>` → fsync → atomic rename
into the sharded library dir → insert `items` row → set job `done` + `item_id`. A
crash between rename and insert leaves an orphan file; the startup sweep (§11)
re-adopts or removes it.

## 10. Download pipeline

**music** — `yt-dlp -x --audio-format mp3 --audio-quality 0 --embed-metadata
--write-thumbnail --convert-thumbnails jpg --js-runtimes node
--newline --progress-template <tpl> -o <dest>`

**video** — `yt-dlp -f bv*+ba/b --merge-output-format mp4 --write-thumbnail
--convert-thumbnails jpg --js-runtimes node --newline --progress-template <tpl>`

**picture / book** — direct HTTP GET via reqwest, streamed to disk, size-capped, or a
multipart upload from the UI. No yt-dlp.

Progress is parsed line-by-line from yt-dlp's `--progress-template` (machine-readable
fields), written to `jobs.progress` and `stage` throttled to once per second — the
frontend polls, so DB writes are the only progress channel and survive a page reload.

**Verified gotcha (yt-dlp v2026.10.26, this machine):** only `deno` is enabled by
default and deno is absent. Every invocation must pass `--js-runtimes node` or YouTube
extraction fails. Sidecar JPG is required (UI depends on it); `--embed-thumbnail` runs
as a separate best-effort pass so a missing mutagen/ffmpeg never fails the job.

## 11. Reliability

- **Graceful shutdown:** SIGTERM → stop accepting new jobs, cancel in-flight yt-dlp
  children, flush `jobs` state, close pool. Aborted work stays in `.incoming` and is
  re-queued by the reaper on next start.
- **Startup sweep:** requeue only jobs whose lease expired (`heartbeat_at` older than
  `MM_LEASE_S`) so starting a second worker never steals a live worker's job; clear
  `.incoming` for terminal jobs. Migrations run via `sqlx::migrate!`.
- **Connection strategy:** a single dedicated writer path for job claims/finalize, a
  small reader pool for listing/search. WAL means reads proceed during writes; the
  `busy_timeout` absorbs the single-writer queue.

## 12. API

```
POST   /api/jobs              {url, kind, mode, collectionId?} -> {id}  409 dup, 429 full
POST   /api/jobs/clear        {scope:"active"|"finished"|"all"}
GET    /api/jobs              ?active=1|status=&limit=
GET    /api/jobs/:id
DELETE /api/jobs/:id          cancel queued/running
POST   /api/uploads           multipart, {kind}                  size-capped
GET    /api/items             ?kind=&q=&limit=&cursor=&sort=&ungrouped=  keyset pagination
GET    /api/items/:id
PATCH  /api/items/:id         {title}
DELETE /api/items/:id?delete_file=true
POST   /api/items/:id/open    launch the OS default app (books)
POST   /api/items/:id/progress {positionMs}                     resume point
GET    /api/playlists         summaries with count + cover  ?kind=playlist|gallery
POST   /api/playlists         {name, kind?}
GET    /api/playlists/:id     playlist + ordered items
PATCH  /api/playlists/:id     {name}
DELETE /api/playlists/:id
POST   /api/playlists/:id/items            {itemIds}
DELETE /api/playlists/:id/items/:itemId
PUT    /api/playlists/:id/order            {itemIds}
PUT    /api/playlists/:id/cover            {itemId}
POST   /api/playlists/:id/cover/upload     multipart
DELETE /api/playlists/:id/cover
GET    /api/health            {status, db:"ok|degraded", queue_depth, active}
GET    /api/events            Server-Sent Events (item/collection changes)
GET    /api/metrics           Prometheus text
GET    /files/:id/:filename   Range-capable, streamed from disk
```

- **Playback is client-side.** The web app owns one `<video>` element mounted in the
  root layout, queue and volume in `localStorage`, and a Media Session handler for OS
  keys. No server "now playing" row: two tabs would fight over it.
- **Resume:** `items.position_ms` is written every ~8s while playing, on pause, and on
  tab close (`keepalive`); it resets to 0 on completion. Starting an item seeks there
  when the stored point is 3s+ and at least 5s from the end.
- **Names vs values:** the stored kind is `music`; the UI labels it **Audio**
  (`lib/kind.ts`). Music is always downloaded as `audio+thumb`; full video is the
  separate Video kind, not a mode toggle.
- **Sorting:** `sort=newest|oldest|title|largest|duration`. Keyset cursors apply to
  `newest`/`oldest` only.
- **Keyboard:** Space play/pause, ←/→ seek, N/P skip, M mute, Q queue, `/` search.
- **Playlists are ordered and server-side.** Positions are integers; reorder writes
  the client's full ordered id list in one transaction. Duplicates are prevented by
  the composite primary key.
- **Native open** — `POST /api/items/:id/open` resolves the stored path under the
  library root and spawns `MM_OPENER` (default `xdg-open`) detached. Loopback-only,
  so this is the user's own machine launching their own file.

- **Keyset pagination** — `?cursor=<opaque>` encodes `(created_at, rowid)` of the last
  row; the server does `WHERE (created_at,rowid) < (:ts,:id) ORDER BY created_at
  DESC, rowid DESC LIMIT :n`. No `OFFSET` on large libraries.
- **Search** — `?q=` hits `items_fts` (`MATCH`, `ORDER BY rank`) falling back to
  `LIKE` on small result sets.
- **Rate limiting** — `tower_governor`, per-IP token bucket; writes tighter than
  reads. Returns `429` with `Retry-After`.
- Errors are `{"error":"...","code":"..."}`; stack traces go to tracing, never to the
  client.

## 12.1 Playback, queue and organization (UI)

- **Player bar** (fixed, root layout): thumb, title, prev/play/next, seek, time,
  volume/mute, shuffle, repeat off/all/one, queue drawer. One media element for the
  whole app; video shows in a floating panel above the bar and can go fullscreen.
- **Queue** is the play order, not the download queue. Actions everywhere: Play,
  Play next, Add to queue. The drawer reorders by drag and removes rows. State is
  persisted; a reload restores it paused (autoplay policy).
- **Playlists** (`/playlists`, `/playlists/:id`): create, rename, delete, play all,
  drag to reorder, remove entries. Adding is done from the library's multi-select bar
  or an item page. Deleting an item cascades out of playlists.
- **Galleries** live inside the Library's **Pictures** tab: gallery cards on top,
  loose (ungrouped) pictures below. `/galleries/:id` is the detail grid; `/galleries`
  redirects to `/?kind=picture`. Galleries reuse the playlist tables with
  `kind='gallery'`, accept only pictures, and are added from picture cards.
- **Adding from a collection:** a playlist or gallery page has an "Add items" /
  "Add pictures" dialog that searches the library and appends a selection. The Add
  media form can also target a playlist/gallery up front; downloads attach on
  completion (`jobs.collection_id`), uploads attach immediately.
- **Covers:** a playlist/gallery can use any image already in it or an uploaded image;
  stored under `.thumbs/covers/<collection-id>.jpg`. Missing cover falls back to the
  first item's thumbnail.
- **Live updates:** the API streams `item.*` and `collection.changed` over SSE
  (`/api/events`); views refresh without a reload. A streaming Next route proxies it
  because the generic rewrite buffers event streams.
- **Pictures default:** `All pictures` is a system gallery created at startup; new
  pictures are filed into it automatically, so the Pictures tab is never empty by
  default. Loose pictures remain possible when removed from every gallery.
- **Queue controls:** the play queue has a clear action plus always-visible row
  removal; the download queue can be cleared/cancelled from the Add page.
- **Themes**: every component uses semantic tokens (`--canvas`, `--panel`, `--line`,
  `--ink`, `--muted`, `--accent`, `--accent-ink`, `--danger`) defined in
  `apps/web/src/app/globals.css`. Four families (Slate, Paper, Ocean, Moss) × dark
  and light, chosen in the sidebar and persisted in `localStorage`; the inline
  bootstrap script applies the attributes before paint.
- **Books**: `POST /open` launches the native reader; `/read/:id` auto-opens once and
  shows the cover. PDF covers come from page 1 (`pdftoppm`, `magick` fallback); EPUB
  covers are extracted from the archive (`unzip`, cover-named image preferred).

## 13. Observability

`tracing` with a span per job (job id, kind, mode, url) and per request. Prometheus
metrics: `queue_depth`, `jobs_active`, `jobs_total{status}`, `bytes_downloaded_total`,
`bytes_served_total`, `request_duration_seconds`, `item_count`. `/api/health` splits
liveness (process up) from readiness (DB writable, reaper and worker loops alive).

## 14. Security

Localhost-only default (`127.0.0.1`), no auth. Uploads and downloads are size-capped
before the body is read. Content-type and extension are validated and the user's
chosen `kind` wins regardless of sniffed type. No shell interpolation: yt-dlp/ffmpeg
are spawned with an argv array, never a shell string. Path traversal rejected via
canonicalised `starts_with` under the library root. CORS locked to `MM_WEB_ORIGIN`.

## 15. Configuration

| var | default |
|---|---|
| `MM_LIBRARY_DIR` | `~/MediaManager/library` |
| `MM_DB_PATH` | `<library>/.mm.db` |
| `MM_BIND` | `127.0.0.1:8787` |
| `MM_WEB_ORIGIN` | `http://localhost:3000` |
| `MM_MAX_CONCURRENT` | `8` |
| `MM_MAX_QUEUED` | `500` |
| `MM_MAX_DOWNLOAD_MB` | `2048` |
| `MM_HEARTBEAT_S` / `MM_LEASE_S` | `30` / `60` |
| `MM_ROLE` | `combined` \| `api` \| `worker` |
| `MM_OPENER` | `xdg-open` (books "open in app") |

## 16. Testing & verification

- **Unit** — filename slugging, yt-dlp argv construction (snapshot), path-traversal
  guard, progress-template parser.
- **Integration** — SQLite in-memory: queue claim/lease/reap state machine, keyset
  pagination over a seeded 10k rows, FTS search correctness, finalize ordering.
- **Load** — a seed command generates N synthetic items + queued jobs; a load script
  (k6 or Rust bench) asserts: search <50ms at 100k items, listing stays flat through
  deep pages, W readers + 1 writer run without `SQLITE_BUSY`, queue reaps a killed
  worker's job. Results are a documented threshold, not a perf regression gate in CI.
- **Smoke (manual, documented in AGENTS.md)** — one music URL, one video URL, one
  image URL, one small PDF; confirm seek works, thumbnail exists, `delete_file`
  removes the file, and a job survives a process restart mid-download.

## 17. Approaches considered

1. **Next.js API routes own the DB; Rust only shells out to yt-dlp.** Rejected: two
   languages writing one schema, job state in Node memory, no durable queue.
2. **Rust serves an exported static Next bundle.** Rejected: loses App Router dev
   ergonomics; no benefit that offsets it.
3. **Next.js + Rust API over HTTP, durable SQLite queue. (chosen)** Presentation vs
   library split cleanly at JSON + Range file serving; the queue table gives
   durability and N-worker scaling on one host.

## 18. Repo layout

```
apps/web/                 Next.js app (src/app, src/components, src/lib)
crates/api/               axum binary `mm` + migrations/
docs/superpowers/specs/   this spec + plan
Makefile                  dev, api, worker, web, test, lint, fmt, check, seed, load
AGENTS.md                 commands, gotchas, smoke test steps
```

## 19. Risks / open items

- YouTube extraction drifts with site changes; yt-dlp must stay current and failures
  surface as job errors, not crashes.
- SQLite single-writer is the ceiling on write throughput. Measured at the load target
  it is ample; if it ever isn't, the queue moves to Postgres/Redis (§3 boundary).
- Embedded cover art in MP3 is finicky across yt-dlp/ffmpeg versions; it is explicitly
  best-effort and never blocks a music job.

## 20. Left to the builder

- Exact Tailwind token values and component primitives (confirm with a screenshot
  before styling the whole grid).
- Whether production serves `/files` through the Next server or directly from Rust;
  dev uses the rewrite.
- Kickoff order: library shell → add/job flow → player → search.
