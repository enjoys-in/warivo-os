#!/usr/bin/env bash
# Package the Warivo boot animation into bootanimation.zip.
#
# Android requires the archive be STORED, not deflated — a compressed bootanimation.zip
# gives no error and simply never plays. This renders the frames first if there are none
# (Chrome-free, via warivo_bootanim.py), writes desc.txt, and packages STORED.
#
#   os/build/build-bootanimation.sh [width] [height] [fps]      # defaults 1920 1080 30
#   FORCE_RENDER=1 os/build/build-bootanimation.sh              # re-render frames
set -euo pipefail

W="${1:-1920}"; H="${2:-1080}"; FPS="${3:-30}"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
anim="$root/branding/bootanimation"
out="$root/os/vendor/warivo/prebuilt/bootanimation.zip"

pick_py() { for c in py python3 python; do command -v "$c" >/dev/null 2>&1 && { echo "$c"; return; }; done; }

# On MSYS/Git-Bash the Windows Python needs Windows paths; a no-op on Linux (no cygpath).
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

have_frames() { compgen -G "$anim/part1/"'*.png' >/dev/null 2>&1; }

if [ "${FORCE_RENDER:-0}" = "1" ] || ! have_frames; then
  py="$(pick_py)"
  [ -n "$py" ] || { echo "need Python + Pillow to render frames (py -m pip install pillow)"; exit 1; }
  echo "==> rendering frames  ${W}x${H} @ ${FPS}fps"
  "$py" "$(winpath "$here/warivo_bootanim.py")" --root "$(winpath "$root")" \
        --out "$(winpath "$anim")" --width "$W" --height "$H" --fps "$FPS"
else
  echo "==> using existing frames in $anim/part{0,1}"
fi

# desc.txt: "<w> <h> <fps>", part0 plays once, part1 loops forever until boot completes.
printf '%s %s %s\np 1 0 part0\np 0 0 part1\n' "$W" "$H" "$FPS" > "$anim/desc.txt"

mkdir -p "$(dirname "$out")"
rm -f "$out"
echo "==> packaging STORED -> $out"
if command -v zip >/dev/null 2>&1; then
  ( cd "$anim" && zip -0 -q -r -X "$out" desc.txt part0 part1 )
else
  # No zip binary (e.g. bare Windows): build a STORED archive with Python's zipfile.
  py="$(pick_py)"
  [ -n "$py" ] || { echo "need either 'zip' or Python to package"; exit 1; }
  "$py" - "$(winpath "$anim")" "$(winpath "$out")" <<'PY'
import os, sys, zipfile
anim, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, "w", zipfile.ZIP_STORED) as z:
    z.write(os.path.join(anim, "desc.txt"), "desc.txt")
    for part in ("part0", "part1"):
        d = os.path.join(anim, part)
        for name in sorted(os.listdir(d)):
            if name.lower().endswith(".png"):
                z.write(os.path.join(d, name), f"{part}/{name}")
PY
fi

# A deflated archive is the classic "boot animation does nothing" bug; catch it here.
if command -v unzip >/dev/null 2>&1; then
  if unzip -vl "$out" | grep -qiE '\bDefl'; then
    echo "ERROR: archive contains deflated entries — Android needs STORED"; exit 1
  fi
fi

n0=$(find "$anim/part0" -name '*.png' | wc -l | tr -d ' ')
n1=$(find "$anim/part1" -name '*.png' | wc -l | tr -d ' ')
echo "==> done: $out  ($(du -h "$out" | cut -f1), ${n0} intro + ${n1} loop frames)"
echo "    The ROM copies it to /system/media/bootanimation.zip (see device/warivo/gsi/device.mk)."
