# MediaManager structure and sync

## Layout

```
crates/
  api/          Rust axum server + SQLite + worker (current)
apps/
  web/          Next.js client (current, reference UI)
  desktop/      Tauri 2 native window around the same UI
  mobile/       Expo/PWA client (planned)
packages/
  api-client/   generated typed client (planned from OpenAPI)
docs/
```

The server stays the single source of truth. Desktop and mobile are clients,
not forks of the backend. Do not add a second database per platform.

## Sync model (server-authoritative)

- The server appends every metadata write to `sync_changes`
  (`seq`, `owner_id`, `entity`, `entity_id`, `op`, `payload`, `created_at`).
  Triggers log items, playlists and playlist membership, so handlers cannot
  forget to publish a change.
- Clients pull `GET /api/sync/changes?since=<seq>` with their stored cursor.
  The returned `cursor` is the new high-water mark.
- Clients cache metadata locally (IndexedDB in the webview/PWA; SQLite in a
  future native mobile client). Media bytes are never bulk-synced: `/files`
  streams on demand with HTTP Range.
- Offline edits are queued with an idempotency key and replayed in order;
  conflicts are last-write-wins per entity using `updated_at`, with the server
  sequence deciding ties. Creation endpoints that need server ids stay
  online-only until an idempotent create API exists.
- Deletions are tombstones (`op=delete`) so a device that was offline does not
  resurrect rows.
- Per-device state (play queue, current volume, playback position on that
  device) does not sync through this log.

## Desktop

`apps/desktop` runs a Tauri 2 window that loads the same web UI from a running
MediaManager server (`MM_DESKTOP_URL`, default `http://localhost:3001`). This
keeps one design and one data layer. Bundling the Next standalone server into
the app is the packaging step; it is not required to try the desktop shell.

Run:

```sh
make dev                 # API + web in one terminal
cd apps/desktop
npm install
npm run dev              # native window
```

## Mobile

`apps/mobile` is a **standalone Kotlin app**, not a client of the server and not a
PWA. It has its own Room database and its own library under app-private storage, no
accounts, and no network call except the download itself. Downloads use the bundled
yt-dlp + ffmpeg via `youtubedl-android`, so Audio is mp3 with cover art and Video is
mp4 at the height set in Settings.

The server stays the source of truth only for the web and desktop clients. Do not
add sync, login, or an API client to the Android app without reversing this on
purpose: the point is that it works on a phone with nothing else running.

The Storage screen is the app's own answer to "what is eating my phone": library
total, device free/used from `StatFs`, a breakdown by kind, thumbnails, temp, and
every item ranked by size with a delete that reports the exact bytes freed.
