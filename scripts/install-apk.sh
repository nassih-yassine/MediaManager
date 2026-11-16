#!/usr/bin/env bash
# Installs an APK only when the phone actually has room for it.
#
# A failed install leaves the user staring at "INSTALL_FAILED_INSUFFICIENT_STORAGE"
# with no idea how much was missing. This checks first and says the numbers.
#
#   scripts/install-apk.sh path/to/app.apk [serial]
#
# Android unmounts and rewrites the package on `install`, so the APK bytes plus
# temporary extraction are both live at once. The headroom below covers that.
set -euo pipefail

APK="${1:?usage: install-apk.sh <apk> [serial]}"
SERIAL="${2:-${ANDROID_SERIAL:-}}"
ADB="${ADB:-adb}"
HEADROOM_MB="${HEADROOM_MB:-150}"

[ -f "$APK" ] || { echo "No such APK: $APK" >&2; exit 1; }

human() { awk -v b="$1" 'BEGIN { split("B KB MB GB", u, " "); i=1
  while (b >= 1024 && i < 4) { b /= 1024; i++ }
  printf (i == 1 ? "%d %s" : "%.0f %s"), b, u[i] }'; }

size=$(stat -c%s "$APK")
adb_args=()
[ -n "$SERIAL" ] && adb_args=(-s "$SERIAL")

avail_kb=$($ADB "${adb_args[@]}" shell df -k /data 2>/dev/null | awk 'NR==2 {print $4}' | tr -d '\r')
if ! [[ "$avail_kb" =~ ^[0-9]+$ ]]; then
  echo "Could not read free space from the device. Is it connected and authorised?" >&2
  exit 1
fi
avail=$((avail_kb * 1024))

# APK + 20 % (re-signed copy while installing) + fixed headroom.
need=$(( size + size / 5 + HEADROOM_MB * 1024 * 1024 ))

echo "APK      $(human "$size")"
echo "Free     $(human "$avail")"
echo "Needed   $(human "$need")  (APK + 20% + ${HEADROOM_MB} MB headroom)"

if [ "$avail" -lt "$need" ]; then
  echo >&2
  echo "Not enough space to install. Free at least $(human $((need - avail))) more," >&2
  echo "or pass HEADROOM_MB=0 to try anyway." >&2
  exit 2
fi

exec "$ADB" "${adb_args[@]}" install -r "$APK"
