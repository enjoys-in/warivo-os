#!/usr/bin/env bash
# Build the Zephyr Warivo node firmware inside the official Zephyr build container.
#
# No local Zephyr SDK or west install — everything runs in the image. Docker Desktop (or a
# Docker daemon) must be running.
#
#   firmware/warivo-node-zephyr/build-docker.sh [board] [zephyr-ref]
#
# Defaults: board esp32c6_devkitc, Zephyr v3.7.0 (LTS). The west workspace (Zephyr source +
# modules) is cached in .docker-ws/ so only the first run pays the ~2-3 GB download.
set -euo pipefail

# Git-Bash rewrites container-side paths like /ws into C:/Program Files/Git/ws; stop it.
export MSYS_NO_PATHCONV=1

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Zephyr's hardware-model-v2 board target for the ESP32-C6 dev board: the high-power
# RISC-V core (hpcore) is the one that runs the app + BLE host.
board="${1:-esp32c6_devkitc/esp32c6/hpcore}"
zref="${2:-main}"
img="zephyrprojectrtos/zephyr-build:latest"
# The Zephyr workspace lives in a Docker VOLUME, not a Windows bind mount: Docker Desktop's
# bind mounts are far too slow for the thousands of small-file ops a Zephyr build does. Only
# the app source (read) and a small output dir (the resulting binary) are bind-mounted.
vol="warivo-zephyr-ws"
out="$here/out"
mkdir -p "$out"
docker volume create "$vol" >/dev/null
# A fresh named volume's mount point is root-owned, but the build image runs as uid 1000;
# hand the top dir over so it can create the workspace. Harmless on an existing volume.
docker run --rm -v "$vol:/ws" alpine chown 1000:1000 /ws >/dev/null 2>&1 || true

# Docker Desktop on Git-Bash wants a drive-letter path with forward slashes (F:/...); a
# no-op on a real Linux host where cygpath does not exist.
winmount() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

echo "==> board=$board  zephyr=$zref  image=$img"
docker run --rm \
  -e ZEPHYR_REF="$zref" -e BOARD="$board" \
  -v "$vol:/ws" \
  -v "$(winmount "$here"):/app:ro" \
  -v "$(winmount "$out"):/out" \
  -w /ws \
  "$img" bash -c '
    set -e
    if [ ! -d /ws/zephyr ]; then
      echo "== fresh workspace: clone + update + blobs =="
      git clone --depth 1 --branch "$ZEPHYR_REF" https://github.com/zephyrproject-rtos/zephyr /ws/zephyr
      west init -l /ws/zephyr
      west update --narrow -o=--depth=1
      west blobs fetch hal_espressif
    else
      echo "== existing workspace: skipping clone/update =="
    fi
    west zephyr-export
    echo "== build ($BOARD) =="
    west build -p auto -b "$BOARD" /app -d /ws/build
    echo "== artifacts =="
    cp /ws/build/zephyr/zephyr.bin /ws/build/zephyr/zephyr.elf /out/
    ls -la /out/zephyr.bin /out/zephyr.elf
  '
echo "==> firmware: $out/zephyr.bin"
