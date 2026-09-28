#!/usr/bin/env bash
# Path A — provision a phone as the Warivo head unit with nothing but adb.
#
# Installs the launcher, makes it Device Owner, grants its runtime permissions, verifies.
# No ROM, no root, no cable left attached afterwards. Safe to re-run.
#
# Device Owner can only be granted on a phone with no accounts and no existing owner, and
# `dpm set-device-owner` reports all of its failure reasons as one unhelpful line — so this
# checks the three real causes before it tries.
#
#   os/build/provision.sh [path/to/app.apk]      # APK optional; found automatically
set -euo pipefail

PKG="com.warivo.os"
ADMIN="$PKG/.kiosk.AdminReceiver"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"

ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || {
  echo "adb not found — add platform-tools to PATH or set \$ADB"; exit 1; }

# APK: explicit arg > release build > debug build.
APK="${1:-}"
if [ -z "$APK" ]; then
  for c in \
    "$root/launcher/app/build/outputs/apk/release/app-release.apk" \
    "$root/launcher/app/build/outputs/apk/debug/app-debug.apk"; do
    [ -f "$c" ] && { APK="$c"; break; }
  done
fi
[ -n "${APK:-}" ] && [ -f "$APK" ] || {
  echo "launcher APK not found — build it first:  (cd launcher && ./gradlew assembleDebug)"; exit 1; }
echo "==> APK: $APK"

# Exactly one device, actually in 'device' state (not unauthorized/offline).
online="$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
[ "$online" = "1" ] || {
  echo "need exactly one device in 'device' state (saw ${online}). Check: \`$ADB devices\`"; exit 1; }

echo "==> pre-flight (the three reasons dpm set-device-owner fails)"
fail=0
if "$ADB" shell dumpsys account 2>/dev/null | grep -qE 'Account \{'; then
  echo "  x an account is signed in — remove all accounts, or factory reset"; fail=1
fi
owners="$("$ADB" shell dpm list-owners 2>/dev/null || true)"
if printf '%s' "$owners" | grep -qiE 'owner'; then
  echo "  x a device/profile owner is already set:"; printf '%s\n' "$owners" | sed 's/^/      /'; fail=1
fi
extra_users="$("$ADB" shell pm list users 2>/dev/null | grep -c 'UserInfo' || true)"
if [ "${extra_users:-1}" -gt 1 ]; then
  echo "  x a secondary/work profile exists — remove it or factory reset"; fail=1
fi
if [ "$fail" = "1" ]; then
  echo "A factory reset with NO Wi-Fi and NO accounts clears all three. Fix, then re-run."
  exit 1
fi

echo "==> installing (with runtime permissions)"
"$ADB" install -r -g "$APK"

echo "==> granting Device Owner"
"$ADB" shell dpm set-device-owner "$ADMIN"

echo "==> granting runtime permissions (BLE scan needs location; music needs storage)"
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION READ_EXTERNAL_STORAGE; do
  "$ADB" shell pm grant "$PKG" "android.permission.$p" >/dev/null 2>&1 || true
done

echo "==> verifying"
"$ADB" shell dpm list-owners

cat <<EOF

Done. Open the launcher, then turn on 'Lock to the dashboard on boot' in Setup.
The way back out is in the same panel: 'Unlock now' (this session) or 'Release phone'
(undoes Device Owner for good — needs another factory reset to re-grant).
EOF
