# VM Deployment and CI/CD — spec

## Intent

Deploy and update MediaManager on a cheap VPS (Oracle Cloud Free Tier ARM or
any host) using containers, with GitHub Actions building images and driving
deploys over SSH. A maintainer should be able to bring up the stack, publish a
release, and have it reach the server without logging in.

## Who and why

- **Who:** a single operator (the library owner) running a private instance.
- **Why:** run the app away from the dev machine, with repeatable upgrades and
  automated tests/builds rather than hand-rolled deploys.
- **Success:** one `git tag && git push` plus publishing the release updates the
  running server; the library survives upgrades and restarts.

## Goals

- Multi-arch container images (`linux/amd64`, `linux/arm64`) published to
  GitHub Container Registry on every `v*` tag.
- A `compose.yaml` stack (`api`, `web`, optional `caddy`) that works with both
  Docker Compose and Podman.
- SSH deploy that checks out the tagged commit and runs `compose pull && up -d`.
- First-run admin via `MM_ADMIN_EMAIL`/`MM_ADMIN_PASSWORD`, with `/setup` as a
  fallback.
- One source of truth for configuration: a server-side `.env`.

## Non-goals

- Horizontal scaling, zero-downtime rolling with multiple replicas, or a
  managed database. Single node, one SQLite file.
- Auto-provisioning the VM from scratch (Terraform/Pulumi/cloud-init are out of
  scope; a documented manual bootstrap is the deliverable).
- Desktop auto-update.
- Exposing the API directly; the web container is the public surface.

## Constraints

- The `web` image bakes `MM_API_URL` at build time (`next.config.ts` rewrites),
  so it must be built with `--build-arg MM_API_URL=http://api:8787`.
- The `api` image runs as uid 1000 and owns the `/library` bind mount; the host
  directory must be `chown 1000:1000`.
- Oracle Free Tier is ARM64: images **must** be multi-arch or the deploy pulls
  nothing.
- Cookies must be `Secure` only behind TLS; `MM_COOKIE_SECURE` and
  `MM_WEB_ORIGIN` must match the transport or login breaks.
- `yt-dlp` in the API image needs Python; ffmpeg, node, poppler and unzip are
  runtime deps (already in `crates/api/Dockerfile`).

## Approach

Chosen: **registry-published multi-arch images + SSH compose deploy.** A
server-side local build (git pull + `make images`) is the fallback, not the
default, because it is slow on ARM and requires the full Rust toolchain.

Alternatives considered:
1. **Build on the server** — simple, but slow and needs toolchain on every box.
2. **Kubernetes/k3s** — more moving parts than a single private instance needs.
3. **Bare metal (systemd unit)** — no isolation or easy rollback.

## Decisions

- Images: `ghcr.io/<owner>/<repo>/api` and `/web`, tagged `<tag>` + `latest`.
- Deploy trigger: `release.published` (after the image build job) or manual
  `workflow_dispatch` with a tag.
- Engine detection: prefer `docker compose`, fall back to `podman compose`.
- TLS is optional and off by default; Caddy is a compose `profile`.
- Backup is out of band (a documented cron tar of `./library`), not in CI.

## Files

- `.github/workflows/ci.yml` — lint/tests/build on push and PR.
- `.github/workflows/release.yml` — desktop bundles + multi-arch images on `v*`.
- `.github/workflows/deploy.yml` — SSH compose deploy on publish/manual.
- `compose.yaml` / `compose.podman.yaml` — the stack (Docker + Podman).
- `crates/api/Dockerfile`, `apps/web/Dockerfile` — runtime images.
- `deploy/.env.example`, `deploy/Caddyfile`, `docs/deploy-vm.md` — server config.

## Acceptance criteria

- [ ] `release.yml` builds and pushes `api` and `web` for amd64 + arm64.
- [ ] On a fresh Ubuntu VM (ARM or x86), following `docs/deploy-vm.md` brings up
      `http://<host>:3001` with a working login.
- [ ] `MM_ADMIN_EMAIL`/`MM_ADMIN_PASSWORD` creates the first admin on first boot.
- [ ] Publishing a release updates the running containers and preserves `./library`.
- [ ] The deploy workflow works when the server runs either Docker or Podman.
- [ ] Caddy profile terminates TLS for a real domain.

## Out of scope (future)

- Terraform/cloud-init provisioning, backups in CI, DB migrations guard/rollback,
  multiple replicas, observability (metrics dashboards), secrets rotation.
