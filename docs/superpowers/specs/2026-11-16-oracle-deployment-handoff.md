# Oracle VM deployment — handoff spec

Continues `2026-11-15-vm-deployment.md`. That file defines intent, goals and
acceptance criteria; this one records where the work actually stands and the
next steps in order.

## Where things stand (verified 2026-11-15)

| Item | State |
| --- | --- |
| `main` | `ad0ba84` — versions bumped to 0.1.3 (api crate, tauri, both package.json) |
| Tag | `v0.1.3` pushed on `ad0ba84` |
| Release run `43674535225` | Desktop bundles **success** (`MediaManager_0.1.3_amd64.AppImage/.deb`, `-1.x86_64.rpm`); multi-arch images **in progress** |
| Releases | `v0.1.3` **draft**, `v0.1.2` draft and stale |
| Deploy secrets | **none** — `DEPLOY_HOST` / `DEPLOY_USER` / `DEPLOY_SSH_KEY` unset, so publishing would fail `deploy.yml` |
| DNS | `yassinenassih.duckdns.org` → `41.143.20.95` (home router, port 22 closed). Not the VM. |
| Branch protection | Impossible: private repo on GitHub Free returns `403` (needs Pro or a public repo) |
| npm audit (web) | 0 critical, 0 high; 6 moderate `minimatch` DoS inside ESLint tooling (minimatch 11 breaks ESLint) |
| cargo audit (api) | `libsqlite3-sys 0.30.1` medium — SQLx 0.9 pins `<0.38`; `rsa 0.9.10` in lock, not in the tree |
| cargo audit (desktop) | `glib 0.18.5` unsound + `proc-macro-error` unmaintained — blocked by Tauri's GTK 0.18 stack |

Images are already published to GHCR by the release job as
`ghcr.io/nassih-yassine/mediamanager/{api,web}:0.1.3`.

## Tomorrow, in order

1. **Create the VM** — Oracle Cloud Always Free, `VM.Standard.A1.Flex` (ARM),
   Ubuntu 24.04, 2 OCPU / 12 GB is a valid free shape. Reserve the public IP,
   open ingress 22, 80, 443 (and 3001 only if testing without TLS). *~45 min*
2. **Point DNS** — set the DuckDNS A record to the new public IP and delete any
   AAAA records (an IPv6 record that does not route is the current failure mode).
   *~10 min*
3. **Bootstrap the host** — follow `docs/deploy-vm.md`: install the container
   engine, create `/opt/mediamanager`, `chown 1000:1000` the library dir, write
   `.env` with `IMAGE_PREFIX`, `MM_ADMIN_EMAIL`, `MM_ADMIN_PASSWORD`,
   `MM_WEB_ORIGIN`, `MM_COOKIE_SECURE`. *~30 min*
4. **Add repo secrets** — `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`
   (optional `DEPLOY_PATH`); the key must be able to run `docker compose` on the
   host. *~10 min*
5. **Publish the release** — publish the `v0.1.3` draft, or run Deploy with
   `workflow_dispatch` and tag `v0.1.3`. *~20 min*
6. **Verify and record** — walk the acceptance criteria below, log in on
   `https://<host>`, upload one picture, play a downloaded item, restart the
   stack and confirm the library survives. *~20 min*

Total: **~2 h 15 min**, most of it waiting on Oracle and the first image pull.

## Acceptance criteria

Carried over from `2026-11-15-vm-deployment.md`, current status in brackets.

- [x] `release.yml` builds and pushes `api` and `web` for amd64 + arm64
      [desktop job passed; images published by the same run]
- [ ] Following `docs/deploy-vm.md` on a fresh Ubuntu VM brings up a working login
- [ ] `MM_ADMIN_EMAIL`/`MM_ADMIN_PASSWORD` creates the first admin on first boot
- [ ] Publishing a release updates the running containers and preserves `./library`
- [ ] The deploy workflow works when the server runs either Docker or Podman
- [ ] Caddy profile terminates TLS for a real domain

## Image size backlog (measured 2026-11-15)

`mm-web:local` is **1.42 GB**; the base `node:22-bookworm-slim` is 232 MB.

| Path | Size |
| --- | --- |
| `/app/node_modules` (includes dev deps) | 507 MB |
| `/app/.next` | 158 MB |
| `/root/.npm` (build cache) | 115 MB |

Cheap, safe wins in `apps/web/Dockerfile`, in this order:

1. `RUN npm prune --omit=dev` after `npm run build` — drops TypeScript, ESLint,
   Vitest, jsdom, Testing Library and their trees (~200 MB).
2. `RUN rm -rf /root/.npm` — the npm cache never matters at runtime (115 MB).
3. Remove `.next/cache` if a build leaves one behind.
4. Optional: adopt `output: "standalone"` and a multi-stage copy; only worth it
   if a ~150 MB image is a goal.

`mm-api:local` is **665 MB**, of which only 13.9 MB is the binary. ffmpeg and
nodejs are required. **`python3` is probably not** — the yt-dlp release binary is
a self-contained zipapp and nothing in `crates/api/src` shells out to Python.
Verify by removing it from the Dockerfile and running one real YouTube download
on the image before keeping the change.

Then tag `v0.1.4` — or slim first and publish that instead of `v0.1.3`.

## Risks and gotchas for the VM

- **Cookie/transport mismatch:** `MM_COOKIE_SECURE=1` with plain HTTP gives a
  login loop; with TLS off it must be `0`.
- **ARM images:** desktop bundles are amd64 only. The VM only needs the
  containers, so this is fine; do not try to install the `.deb` there.
- **yt-dlp on ARM:** the image downloads the universal binary; confirm an actual
  extraction on the VM, not just `yt-dlp --version`.
- **Library disk:** keep `/library` on the 200 GB free block volume, not the
  boot volume.
- **Backups:** cron `tar` of `/opt/mediamanager/library` plus a copy of
  `mediamanager.db`; not automated yet.
- **Publishing with no secrets** turns `deploy.yml` red. Set secrets first, or
  keep the release as a draft.

## Not in scope tomorrow

- Offline sync client (IndexedDB cache + queue) and the `crates/core` split.
- Branch protection — needs GitHub Pro or a public repo.
- Desktop auto-update.
