#!/usr/bin/env bash
# Build a Warivo OS Treble GSI from AOSP.
#
# It refuses to start rather than failing hours in: Linux host, `repo` + a JDK, ~400 GB
# free, and the launcher APK present. The first run downloads ~200 GB of AOSP source.
# Output: out/target/product/generic_arm64/system.img  ->  flash with flash-gsi.sh.
#
#   os/build/build-rom.sh
#   AOSP_BRANCH=android-14.0.0_r67  AOSP_DIR=~/aosp-warivo  os/build/build-rom.sh
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"

AOSP_BRANCH="${AOSP_BRANCH:-android-14.0.0_r67}"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp-warivo}"
LUNCH="${LUNCH:-warivo_arm64-userdebug}"

die() { echo "ERROR: $*" >&2; exit 1; }

# --- pre-flight: fail fast, not hours in ---------------------------------------------
[ "$(uname -s)" = "Linux" ] || die "AOSP builds on Linux only (this is $(uname -s)). Use a Linux host or WSL2."
command -v repo >/dev/null 2>&1 || die "'repo' not found — see source.android.com/setup/develop#installing-repo"
command -v java >/dev/null 2>&1 || die "a JDK is required (OpenJDK 17+ for Android 13+)."

parent="$(dirname "$AOSP_DIR")"; mkdir -p "$parent"
avail_kb="$(df -Pk "$parent" | awk 'NR==2{print $4}')"
[ "${avail_kb:-0}" -ge $((400*1024*1024)) ] \
  || die "need ~400 GB free at $parent (have ~$((avail_kb/1024/1024)) GB)."

ram_gb="$(awk '/MemTotal/{printf "%d",$2/1024/1024}' /proc/meminfo 2>/dev/null || echo 0)"
[ "${ram_gb:-0}" -ge 16 ] || echo "WARNING: ${ram_gb} GB RAM — Soong thrashes or is OOM-killed below 16 GB."

# The ROM bakes in the Path A launcher APK unchanged.
APK_SRC="$root/launcher/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK_SRC" ] || APK_SRC="$root/launcher/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK_SRC" ] || die "build the launcher first:  (cd launcher && ./gradlew assembleRelease)"

echo "==> AOSP $AOSP_BRANCH  ->  $AOSP_DIR"
cd "$AOSP_DIR"
if [ ! -d .repo ]; then
  repo init -u https://android.googlesource.com/platform/manifest -b "$AOSP_BRANCH" --depth=1
fi
mkdir -p .repo/local_manifests
cp "$root/os/manifests/warivo.xml" .repo/local_manifests/warivo.xml

echo "==> repo sync (the big download)"
repo sync -c -j"$(nproc)" --no-clone-bundle --no-tags

# Graft the Warivo tree in. Symlinks keep edits in this repo rather than the AOSP checkout.
echo "==> grafting device/warivo + vendor/warivo"
rm -rf device/warivo vendor/warivo
ln -s "$root/os/device/warivo" device/warivo
ln -s "$root/os/vendor/warivo" vendor/warivo

echo "==> staging the launcher prebuilt"
mkdir -p vendor/warivo/prebuilt/WarivoLauncher
cp "$APK_SRC" vendor/warivo/prebuilt/WarivoLauncher/WarivoLauncher.apk

if [ ! -f vendor/warivo/prebuilt/bootanimation.zip ]; then
  echo "==> no bootanimation.zip yet — rendering it"
  "$root/os/build/build-bootanimation.sh" || echo "  (boot animation skipped; ROM still builds)"
fi

echo "==> building $LUNCH  (hours)"
set +u; source build/envsetup.sh; set -u
lunch "$LUNCH"
m -j"$(nproc)"

IMG="$AOSP_DIR/out/target/product/generic_arm64/system.img"
[ -f "$IMG" ] || die "build finished but $IMG is missing."
echo "==> done: $IMG"
echo "Flash it:  os/build/flash-gsi.sh \"$IMG\""
