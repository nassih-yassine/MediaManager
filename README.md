# MediaManager

Local library for media you download: music, video, pictures, books. Paste a
link, choose what to keep — audio with its cover art, or the full video — and it
lands in a plain folder tree on disk.

- Persistent player bar: play/pause, next/prev, seek, volume, shuffle, repeat,
  and a drag-to-reorder queue that survives navigation.
- Resume where you stopped: positions are saved per item (even across reloads).
- Playlists that hold any mix of items, with drag reordering.
- Galleries that group pictures into a browsable grid.
- Four themes (Slate, Paper, Ocean, Moss) in light and dark.
- Books open in your native reader; the cover is the file's first page.
- Multi-select in the library for bulk queue / playlist / delete.

- **Rust API** (`crates/api`): axum + SQLite (WAL), durable download queue,
  yt-dlp/ffmpeg for media, streaming file serving with byte ranges.
- **Next.js web** (`apps/web`): App Router, TypeScript, Tailwind v4. Proxies
  `/api`, `/files` and `/thumbs` to the API, so one origin in the browser.

The spec lives in `docs/superpowers/specs/2026-11-14-media-manager-spec.md`.

## Requirements

- Node 20+ and npm
- Rust 1.85+
- `yt-dlp` and `ffmpeg` on `PATH` (for downloads and thumbnails)

## Run

```sh
make install     # npm install + cargo fetch
make dev         # API on :8787, web on :3000
```

Open <http://localhost:3000>.

Run only the API: `make api`. Run a separate worker: `make worker`.

## Checks

```sh
make test        # cargo test + vitest
make check       # cargo check + tsc --noEmit
make lint        # eslint
```

## Configuration

All settings are environment variables; see `.env.example`. Defaults put the
library at `~/MediaManager/library`.
