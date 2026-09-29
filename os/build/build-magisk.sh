#!/usr/bin/env bash
# Package the Warivo OS Magisk module — the no-ROM route to "restart -> Warivo OS -> a
# single full-screen app, no Android UI".
#
# It applies the two ROM-level things systemlessly, on any rooted (Magisk) phone:
#   * the Warivo boot animation  -> boot says "Warivo OS", not the OEM/Android animation
#   * head-unit defaults         -> radios on, stay awake while charging, no setup wizard
# The single-app kiosk lock itself is Device Owner + Lock Task, granted once over adb by
# os/build/provision.sh — that part needs no root and no ROM either.
#
#   os/build/build-bootanimation.sh    # first, if bootanimation.zip isn't built yet
#   os/build/build-magisk.sh           # -> os/warivo-os-magisk.zip
#
# Flash it in the Magisk app: Modules -> Install from storage -> pick the zip -> reboot.
#
# Note: the very first OEM bootloader logo (and any "unlocked bootloader" warning) is drawn
# before Android starts and cannot be changed from here — that is a device-specific splash
# partition, and the warning is often enforced by the bootloader.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
mod="$root/os/magisk"
boot="$root/os/vendor/warivo/prebuilt/bootanimation.zip"
out="$root/os/warivo-os-magisk.zip"

[ -f "$mod/module.prop" ] || { echo "missing $mod/module.prop"; exit 1; }
if [ ! -f "$boot" ]; then
  echo "bootanimation.zip not found — build it first:  os/build/build-bootanimation.sh"
  exit 1
fi

pick_py() { for c in py python3 python; do command -v "$c" >/dev/null 2>&1 && { echo "$c"; return; }; done; }
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Module skeleton (scripts + prop), then the boot animation in both locations phones read
# from: /system/media on older/AOSP-ish trees, /product/media on most modern devices.
cp "$mod/module.prop" "$mod/customize.sh" "$mod/service.sh" "$work/"
mkdir -p "$work/system/media" "$work/system/product/media"
cp "$boot" "$work/system/media/bootanimation.zip"
cp "$boot" "$work/system/product/media/bootanimation.zip"

rm -f "$out"
echo "==> packaging -> $out"
if command -v zip >/dev/null 2>&1; then
  ( cd "$work" && zip -q -r -X "$out" module.prop customize.sh service.sh system )
else
  # No zip binary (bare Windows shell): pack with Python's zipfile (deflate is fine here —
  # unlike bootanimation.zip, a Magisk module is not required to be STORED).
  py="$(pick_py)"
  [ -n "$py" ] || { echo "need either 'zip' or Python to package"; exit 1; }
  "$py" - "$(winpath "$work")" "$(winpath "$out")" <<'PY'
import os, sys, zipfile
src, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for dirpath, _, files in os.walk(src):
        for f in files:
            full = os.path.join(dirpath, f)
            z.write(full, os.path.relpath(full, src).replace(os.sep, "/"))
PY
fi

echo "==> done: $out  ($(du -h "$out" | cut -f1))"
cat <<'EOF'

Install:
  1. Root the phone with Magisk (patch its boot.img, or flash Magisk in recovery).
  2. Magisk app -> Modules -> Install from storage -> pick warivo-os-magisk.zip -> reboot.
     Reboot now shows the Warivo boot animation, radios on, no setup wizard.
  3. One-time, over adb, for the single-app lock:  os/build/provision.sh
     Then in the launcher's Setup: 'Lock to the dashboard on boot'.
EOF
