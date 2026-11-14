#!/usr/bin/env bash
# Create the first admin. Safe to run more than once: it exits when an account
# already exists. The password is chosen by the person running it.
set -euo pipefail

API="${MM_API_URL:-http://127.0.0.1:8787}"

echo "Waiting for the API at $API ..."
for _ in $(seq 1 60); do
  if curl -fsS "$API/api/health" >/dev/null 2>&1; then break; fi
  sleep 1
done
if ! curl -fsS "$API/api/health" >/dev/null 2>&1; then
  echo "API is not reachable. Start it with 'make api' (or 'make dev') first." >&2
  exit 1
fi

STATUS=$(curl -fsS "$API/api/auth/status")
if [[ "$STATUS" != *'"needsSetup":true'* ]]; then
  echo "An account already exists; nothing to do."
  exit 0
fi

EMAIL="${MM_ADMIN_EMAIL:-}"
NAME="${MM_ADMIN_NAME:-}"
PASSWORD="${MM_ADMIN_PASSWORD:-}"

if [[ -z "$EMAIL" ]]; then
  read -r -p "Admin email: " EMAIL
fi
if [[ -z "$NAME" ]]; then
  read -r -p "Display name: " NAME
fi
if [[ -z "$PASSWORD" ]]; then
  read -r -s -p "Password (8+ characters): " PASSWORD
  echo
  read -r -s -p "Repeat password: " CONFIRM
  echo
  if [[ "$PASSWORD" != "$CONFIRM" ]]; then
    echo "Passwords do not match." >&2
    exit 1
  fi
fi

python3 - "$API" "$EMAIL" "$NAME" "$PASSWORD" <<'PY'
import json, sys, urllib.error, urllib.request

api, email, name, password = sys.argv[1:5]
body = json.dumps({"email": email, "name": name, "password": password}).encode()
request = urllib.request.Request(
    f"{api}/api/auth/setup",
    data=body,
    headers={"content-type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(request) as response:
        data = json.load(response)
except urllib.error.HTTPError as error:
    print(f"Setup failed: {error.code} {error.read().decode()}", file=sys.stderr)
    sys.exit(1)
print(f"Admin created: {data['user']['email']}")
PY

echo "Sign in at the web app and change the password from Profile if needed."
