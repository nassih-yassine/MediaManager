# MediaManager

Local library for media you download: music, video, pictures, books. Paste a
link, choose what to keep — audio with its cover art, or the full video — and it
lands in a plain folder tree on disk.

- Local accounts: email + password, private libraries per user, and an admin panel
  for creating users and resetting passwords.
- Profile page: display name, password, account-level theme, and bug/feature reports
  that land in an admin inbox.
- Notification bell: report updates, admin announcements, and finished or failed
  downloads; unread badge plus a full `/notifications` list.
- Persistent player bar: play/pause, next/prev, seek, volume, shuffle, repeat,
  and a drag-to-reorder queue that survives navigation.
- Resume where you stopped: positions are saved per item (even across reloads).
- Playlists that hold any mix of items, with drag reordering.
- Galleries tab: galleries on top, loose pictures below; create a gallery inline.
- Full-screen gallery viewer: keyboard arrows, thumbnail strip, swipe on touch.
- Select mode inside a gallery: move pictures to another gallery, remove them
  from it, or delete the files.
- Upload pictures directly from a gallery, or drop them onto its grid.
- Add to a collection from its own page, or pick a target while adding media.
- Live updates: new items, deletes and playlist changes appear without a reload.
- `All pictures` default gallery (cannot be deleted); upload several pictures at once.
- Deleting a gallery asks whether its pictures move to `All pictures` or are
  deleted from disk.
- The Library `All` tab lists collections and hides individual pictures so the
  grid stays gallery-first.
- Custom playlist/gallery covers from an item or an upload.
- Clear the play queue and the download activity explicitly.
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

Desktop (native window, same UI):

```sh
make dev                    # keep the web + API running
cd apps/desktop && npm install
npm run dev
```

The window points at `http://localhost:3001` (override with `MM_DESKTOP_URL`).

Open <http://localhost:3000>. First run shows **Create the admin account**; existing
media is adopted by that account. Set `MM_ADMIN_EMAIL`/`MM_ADMIN_PASSWORD` to create
it at startup instead. Additional users are created in **Admin**. To run E2E against
an account: `MM_E2E_EMAIL=you@example.com MM_E2E_PASSWORD=... make e2e`.

Run only the API: `make api`. Run a separate worker: `make worker`.

## Checks

```sh
make test        # cargo test + vitest
make check       # cargo check + tsc --noEmit
make lint        # eslint
```

## CI/CD

GitHub Actions:

- **CI** (`.github/workflows/ci.yml`) on pushes and PRs: Rust fmt + tests; web
  typecheck, lint, Vitest and production build; a browser smoke test with a
  seeded track; and a Docker build of both images.
- **Release** (`.github/workflows/release.yml`) on `v*` tags: desktop
  AppImage/deb/rpm via Tauri attached to a draft GitHub release, plus
  `ghcr.io/<repo>/api` and `/web` images tagged with the release and `latest`.
- **Deploy** (`.github/workflows/deploy.yml`): after a successful release, or
  manually with a tag, SSHes to the server and runs
  `docker compose -f compose.yaml pull && up -d`.

Server bootstrap (once):

```sh
git clone <repo> /opt/mediamanager && cd /opt/mediamanager
cp deploy/.env.example .env && chmod 600 .env   # set IMAGE_PREFIX, origin, admin
mkdir -p library && sudo chown -R 1000:1000 library
docker login ghcr.io                          # read:packages token
```

Optional TLS: point a hostname at the server, set `PUBLIC_HOST` and
`MM_WEB_ORIGIN=https://...`, then
`docker compose --profile tls up -d`.

Repository secrets: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`,
`DEPLOY_PATH` (optional, default `/opt/mediamanager`). The deploy script uses
`docker compose` when Docker is present, otherwise `podman compose`.

Run the container stack locally with Podman:

```sh
make images
cp deploy/.env.example .env    # set IMAGE_PREFIX=localhost, MM_WEB_ORIGIN=http://localhost:3001, MM_COOKIE_SECURE=0
podman compose -f compose.yaml -f compose.podman.yaml up -d
```

Then open <http://localhost:3001>. The Podman override maps the library volume
with `userns_mode: keep-id`, so files stay owned by your user.

## Configuration

All settings are environment variables; see `.env.example`. Defaults put the
library at `~/MediaManager/library`.
