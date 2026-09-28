#!/usr/bin/env bash
# Flash a Warivo GSI (system.img) onto a Treble device via userspace fastboot (fastbootd).
#
# This WIPES the phone: unlocking the bootloader erases userdata by design, so there is no
# non-destructive path. It reads the device's Treble / ABI / A-B state first and refuses to
# flash a GSI that cannot boot — a much cheaper way to learn that than a bootloop.
#
#   os/build/flash-gsi.sh <system.img>
set -euo pipefail

IMG="${1:-}"
[ -n "$IMG" ] && [ -f "$IMG" ] || { echo "usage: flash-gsi.sh <system.img>"; exit 1; }

ADB="${ADB:-adb}"; FASTBOOT="${FASTBOOT:-fastboot}"
command -v "$FASTBOOT" >/dev/null 2>&1 || { echo "fastboot not found — install platform-tools"; exit 1; }

die() { echo "ERROR: $*" >&2; exit 1; }

model=""
if command -v "$ADB" >/dev/null 2>&1 && "$ADB" get-state 2>/dev/null | grep -q device; then
  treble="$("$ADB" shell getprop ro.treble.enabled 2>/dev/null | tr -d '\r')"
  abilist="$("$ADB" shell getprop ro.product.cpu.abilist 2>/dev/null | tr -d '\r')"
  ab="$("$ADB" shell getprop ro.build.ab_update 2>/dev/null | tr -d '\r')"
  model="$("$ADB" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  echo "device: ${model:-?}   treble=${treble:-?}  ab=${ab:-?}  abis=${abilist:-?}"
  [ "$treble" = "true" ] || die "device is not Project-Treble enabled; a GSI will not boot."
  case "$abilist" in *arm64*) : ;; *) die "GSI is arm64; device abilist is '${abilist:-unknown}'." ;; esac
else
  echo "note: device not visible over adb — skipping capability checks (flashing blind)."
fi

printf '\nThis ERASES the phone (bootloader unlock wipes userdata).\n'
printf 'Type the model name to confirm [%s]: ' "${model:-model}"
read -r ans
[ -n "$ans" ] && [ "$ans" = "${model:-$ans}" ] || die "confirmation did not match; aborting."

echo "==> rebooting to bootloader"
"$ADB" reboot bootloader 2>/dev/null || true
sleep 3
"$FASTBOOT" devices

# Unlock if needed (no-op if already unlocked). THIS is the wipe.
"$FASTBOOT" flashing unlock 2>/dev/null || "$FASTBOOT" oem unlock 2>/dev/null || true

echo "==> entering fastbootd to flash the dynamic 'system' partition"
"$FASTBOOT" reboot fastboot
sleep 3
"$FASTBOOT" flash system "$IMG"
"$FASTBOOT" -w || true        # a GSI expects no stale userdata

echo "==> rebooting"
"$FASTBOOT" reboot
echo "Done. First boot is slow; it should land on the Warivo boot animation, then the PIN."
