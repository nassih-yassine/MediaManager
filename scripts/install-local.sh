#!/usr/bin/env bash
# Install MediaManager for the current user on Linux:
#   - API + production web as systemd --user services
#   - Tauri desktop binary and launcher entry
# No sudo. Run after `cargo build --release` and `npm run build`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="$HOME/.local/bin"
UNITS="$HOME/.config/systemd/user"
APPS="$HOME/.local/share/applications"
ICONS="$HOME/.local/share/icons/hicolor/512x512/apps"
NODE="$(command -v node || true)"

fail() { echo "error: $*" >&2; exit 1; }

[[ -x "$ROOT/crates/api/target/release/mm" ]] || fail "run: cargo build --release --manifest-path crates/api/Cargo.toml"
[[ -f "$ROOT/apps/desktop/src-tauri/target/release/mm-desktop" ]] || fail "run: (cd apps/desktop && npm run build)"
[[ -f "$ROOT/apps/web/.next/BUILD_ID" ]] || fail "run: (cd apps/web && npm run build)"
[[ -n "$NODE" ]] || fail "node is not on PATH"

mkdir -p "$BIN" "$UNITS" "$APPS" "$ICONS"
install -m 755 "$ROOT/crates/api/target/release/mm" "$BIN/mm"
install -m 755 "$ROOT/apps/desktop/src-tauri/target/release/mm-desktop" "$BIN/mm-desktop"
install -m 644 "$ROOT/apps/web/public/logo.png" "$ICONS/mediamanager.png"

cat > "$UNITS/mm-api.service" <<EOF
[Unit]
Description=MediaManager API
After=network.target

[Service]
Type=simple
WorkingDirectory=$ROOT
ExecStart=$BIN/mm
Environment=MM_WEB_ORIGIN=http://localhost:3001
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
EOF

cat > "$UNITS/mm-web.service" <<EOF
[Unit]
Description=MediaManager web
After=network.target mm-api.service
Wants=mm-api.service

[Service]
Type=simple
WorkingDirectory=$ROOT/apps/web
ExecStart=$NODE $ROOT/apps/web/node_modules/next/dist/bin/next start -p 3001
Environment=NODE_ENV=production
Environment=PORT=3001
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
EOF

cat > "$APPS/mediamanager.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=MediaManager
Comment=Local media library
Exec=$BIN/mm-desktop
Icon=mediamanager
StartupWMClass=mm-desktop
Terminal=false
Categories=AudioVideo;Player;
EOF

systemctl --user daemon-reload
systemctl --user enable --now mm-api.service mm-web.service
gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" >/dev/null 2>&1 || true
update-desktop-database "$APPS" >/dev/null 2>&1 || true

echo "Installed."
echo "  services: systemctl --user status mm-api mm-web"
echo "  web:      http://localhost:3001"
echo "  desktop:  start it from the app launcher (MediaManager)"
