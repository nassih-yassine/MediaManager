# Deploy MediaManager on a VM (Oracle Free Tier or any VPS)

Goal: run `api` + `web` (and optional Caddy TLS) as containers, deployed and
updated by GitHub Actions over SSH.

## 0. Architecture you get

```
GitHub Actions                    VM
─────────────                     ──
release.yml builds multi-arch     docker/podman compose:
  ghcr.io/<repo>/api:tag            api   (SQLite + /library volume)
  ghcr.io/<repo>/web:tag            web   (serves the UI, proxies /api)
deploy.yml SSHes in and:            caddy (optional TLS profile)
  git checkout <tag>
  compose pull && up -d
```

The web image bakes `MM_API_URL=http://api:8787` at build time; the compose
network resolves `api`, so no per-host rewrite is needed.

## 1. Provision the VM

### Oracle Cloud Free Tier (ARM)

1. Create an **Ampere A1** instance, Ubuntu 22.04 or 24.04 (ARM shape). The
   free 4 OCPU / 24 GB is ample.
2. In the VCN, open ingress rules: **22** (SSH), **80** (HTTP), **443** (HTTPS).
   Restrict 22 to your IP if you want.
3. Ubuntu's `ufw` is usually inactive; if you enable it, allow the same ports.

### Any VPS / “instabox”

Any Ubuntu/Debian VM with ≥1 GB RAM works. The images are multi-arch, so x86
and ARM both run.

## 2. Install the runtime (Docker or Podman)

Docker (recommended on a fresh VM):

```sh
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER && newgrp docker
```

Podman (rootless, what you already have locally):

```sh
sudo apt-get update && sudo apt-get install -y podman podman-compose
```

## 3. Clone and configure

```sh
git clone <your-repo> /opt/mediamanager
cd /opt/mediamanager
cp deploy/.env.example .env
chmod 600 .env
```

Edit `.env`:

| Key | Value |
|---|---|
| `IMAGE_PREFIX` | `ghcr.io/<github-user>/<repo>` |
| `MM_WEB_ORIGIN` | `https://media.example.com` (or `http://<vm-ip>:3001` for a test) |
| `MM_COOKIE_SECURE` | `1` only behind HTTPS, else `0` |
| `PUBLIC_HOST` | your domain for Caddy |
| `MM_ADMIN_EMAIL` / `MM_ADMIN_PASSWORD` | first admin, used only while no users exist |

```sh
mkdir -p library && sudo chown -R 1000:1000 library   # API runs as uid 1000
docker login ghcr.io                                  # or: podman login ghcr.io
```

## 4. First deploy (manual, before CI)

Pull the images and start:

```sh
# Docker
docker compose -f compose.yaml up -d
# Podman
podman compose -f compose.yaml -f compose.podman.yaml up -d
```

Check:

```sh
curl -fsS http://127.0.0.1:3001/api/health
curl -fsS http://127.0.0.1:3001/login
```

## 5. TLS

No domain? Use Tailscale and keep HTTP:

```sh
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
```

Then reach `http://<tailscale-ip>:3001`.

With a domain, point its DNS at the VM and start Caddy:

```sh
# Docker
docker compose -f compose.yaml --profile tls up -d
# Podman
podman compose -f compose.yaml -f compose.podman.yaml --profile tls up -d
```

Caddy fetches a Let's Encrypt cert automatically for `PUBLIC_HOST`.

## 6. Wire CI/CD

1. Push the repo to GitHub.
2. Repo → **Settings → Secrets and variables → Actions**, add:
   - `DEPLOY_HOST` — VM address
   - `DEPLOY_USER` — SSH user (e.g. `ubuntu` or `opc`)
   - `DEPLOY_SSH_KEY` — private key (contents of `~/.ssh/id_ed25519`)
   - `DEPLOY_PATH` — `/opt/mediamanager` (optional)
3. On the VM, add the matching public key to `~/.ssh/authorized_keys`.
4. Tag and push a release:

```sh
git tag v0.1.0 && git push origin v0.1.0
```

5. GitHub **Release** → edit the draft → **Publish release**. The `deploy`
   workflow SSHes in, checks out the tag, and runs `compose pull && up -d`.

## 7. Operations

- **Update:** publish a new release (or run the deploy workflow manually with a tag).
- **Backup:** the whole state is `./library` (SQLite + media). A nightly cron:
  ```sh
  0 3 * * * tar -czf /backup/mm-$(date +\%F).tgz -C /opt/mediamanager library
  ```
- **Logs:** `docker compose -f compose.yaml logs -f api web` (or `podman compose ...`).
- **First admin:** set `MM_ADMIN_EMAIL/PASSWORD` in `.env` before first start, or
  run the UI `/setup`. Local interactive `make setup` is not used on the server.

## 8. Troubleshooting

- Login loops on HTTP: `MM_COOKIE_SECURE=1` with an `http://` origin. Set it `0`.
- `api` fails on the volume: it runs as uid 1000; `sudo chown -R 1000:1000 library`.
- Image not found / wrong arch: use `ghcr.io/<repo>/api:<tag>` and ensure the
  release workflow ran (it builds amd64 + arm64).
