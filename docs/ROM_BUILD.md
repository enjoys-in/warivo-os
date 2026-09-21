# Warivo OS — the ROM (Path B)

**Recommendation: build a Treble GSI from AOSP — not from the Linux kernel, and not as a
per-device port.**

This document is the decision and its reasoning, the prerequisites, the honest blockers,
and the build steps. The source tree itself is [../os/](../os/) — kernel config layer,
device and product definitions, overlays, and build scripts.

---

## 1. Why AOSP and not "from the Linux kernel"

Both options start from the Linux kernel — Android *is* a Linux kernel plus Android's
userspace. The real question is whether we keep Android's userspace or write our own.

Everything the Warivo Launcher already does is an Android framework service:

| What we use | Android API | If we dropped Android |
| --- | --- | --- |
| Kiosk lock, radio policy | `DevicePolicyManager`, Lock Task | Write our own session/policy layer |
| Scooter link | `BluetoothLeScanner` / `BluetoothGatt` | Drive BlueZ over D-Bus by hand |
| Music to the speaker | A2DP via `MediaPlayer` + audio routing | PipeWire/PulseAudio + BlueZ A2DP setup |
| Map, search | MapLibre Android, `WebView` | Port a renderer, embed a browser engine |
| The whole UI | Kotlin + Jetpack Compose | Rewrite in Qt/GTK/Flutter-embedded |
| Local music | `MediaStore` | Write a media indexer |

Going "from the Linux kernel" means **throwing away the launcher and rewriting all of
that**. It is a years-long project for one scooter dashboard, and it buys nothing a rider
would notice. Android already is an embedded Linux appliance OS with the exact services we
need; the job is to strip it down, not to rebuild it.

So: **AOSP** — and for a GSI, plain AOSP rather than LineageOS.

An earlier draft of this document said LineageOS, on the grounds that it carries device
trees, kernel forks and vendor-blob extraction that raw AOSP does not. That reasoning is
sound for a **device port** and irrelevant to a **GSI**, which uses none of those things.
LineageOS also has no GSI product to lunch; the GSI *is* AOSP's `aosp_arm64` target, and
has been since Android 10. Syncing LineageOS to build a GSI means roughly 80 GB more
source for nothing.

LineageOS becomes the right tree the moment this stops being a GSI — see
`os/device/warivo/port-template/`.

> A raw-Linux appliance only makes sense if the head unit stopped being a phone — e.g. a
> Raspberry Pi with a touchscreen bolted to the scooter. That is a different product, and
> the ESP32 node and its BLE protocol would carry over unchanged. Worth keeping in mind as
> a hardware option; it is not a ROM for this phone.

## 2. Why a GSI first, not a device port

A **GSI** (Generic System Image) is one `system.img` that boots on any
**Project-Treble** device. Every phone shipped with Android 9 or later is Treble-compliant,
which includes the target phone.

| | Full device port | **GSI (recommended)** |
| --- | --- | --- |
| Needs a device tree + vendor blobs | Yes, per phone | No |
| Needs a maintained LineageOS device | Yes | No |
| Source to sync | LineageOS, ~280 GB | AOSP, ~200 GB |
| Build time (first) | 3–8 h | 2–5 h |
| Works on a second phone later | Another full port | Same image |
| Camera/fingerprint/VoLTE quirks | You fix them | May be broken — **we do not care** |

That last row is why a GSI fits this project precisely: a scooter dashboard needs screen,
touch, WiFi, Bluetooth and GPS. It does not need the camera, the fingerprint reader, or
VoLTE — the things GSIs typically break.

**Plan: GSI first.** Only do a full device port if the GSI turns out to break a radio we
actually need on that exact phone.

## 3. Hard prerequisites

Check all of these **before** starting. Each one is a hard stop.

- [ ] **A Linux build host.** AOSP dropped macOS support; it does not build on darwin.
      Ubuntu 22.04 is the safe choice — bare metal, a VM, or a cloud box.
- [ ] **~300 GB free SSD** (~200 GB source + ~100 GB build output) and **16 GB RAM
      minimum**, 32–64 GB if you want it to finish this decade. Build time scales with
      cores; a 4-core laptop is an overnight job.
- [ ] **The phone's exact model and codename.** Everything downstream depends on it.
- [ ] **An unlockable bootloader.** `fastboot flashing unlock` must succeed. Many Indian
      carrier/OEM variants (and all Huawei since 2018) cannot be unlocked at all — if this
      fails, Path B is over for that handset and Path A remains the product.
- [ ] **Treble confirmed:** `adb shell getprop ro.treble.enabled` → `true`, and note
      `ro.product.cpu.abi` (almost certainly `arm64-v8a`) and whether the device is
      A/B or A-only (`adb shell getprop ro.boot.slot_suffix` — empty means A-only).
- [ ] **A full backup.** Flashing a GSI wipes userdata.

## 4. What Warivo actually changes in the ROM

The ROM is not where the product lives — the launcher is. The ROM only removes friction
the kiosk cannot:

| Change | Why it needs a ROM |
| --- | --- |
| Warivo Launcher preinstalled as the **only** home | Removes Launcher3/Trebuchet entirely, so there is no launcher chooser and nothing to fall back to |
| **No GApps** | No Play Store, no account setup, no updates nagging a scooter |
| **No setup wizard** | Flash → boots straight to the dashboard, no onboarding |
| WiFi / BT / GPS **on by default** | `SettingsProvider` defaults, so they are on at first boot before any app runs |
| **Stay awake while charging** | The phone is permanently powered from the scooter's 5 V rail |
| Warivo **boot animation** | `/system/media/bootanimation.zip` needs system partition access |
| **Device Owner with no cable** | Only a platform-signed system app can self-provision; `adb dpm` is impossible on a scooter |

Everything else — gauges, kiosk, panels — is the same APK as Path A. See
[../os/README.md](../os/README.md) for the scaffolded product config and overlays.

## 5. Build steps

```bash
# Host setup (Ubuntu 22.04)
sudo apt install -y git-core gnupg flex bison build-essential zip curl zlib1g-dev \
  libc6-dev-i386 x11proto-core-dev libx11-dev lib32z1-dev libgl1-mesa-dev \
  libxml2-utils xsltproc unzip fontconfig python3 openjdk-11-jdk ffmpeg
mkdir -p ~/bin && curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
chmod a+x ~/bin/repo && export PATH=~/bin:$PATH

# 1. The launcher APK the ROM bakes in
cd launcher && ./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk \
   ../os/vendor/warivo/prebuilt/WarivoLauncher/WarivoLauncher.apk
cd ..

# 2. Optional branding
os/build/build-bootanimation.sh 1080 1920 30

# 3. Sync AOSP and build the GSI (~200 GB sync, hours)
os/build/build-rom.sh                 # --no-sync to rebuild later
```

`build-rom.sh` front-loads every hard stop rather than failing hours in: Linux only (AOSP
dropped macOS support — this is not a flag), `repo` and a JDK present, ~400 GB free, the
launcher APK in place. It warns below 16 GB RAM, where Soong thrashes or gets OOM-killed.
It grafts `os/device/warivo` and `os/vendor/warivo` into the synced tree by copying, not
symlinking, because Soong follows symlinks inconsistently and the failure surfaces as a
baffling "file not found" inside a generated ninja file.

Output: `out/target/product/generic_arm64/system.img`.

### Flashing

Device-specific, and A/B differs from A-only. Broadly:

```bash
adb reboot bootloader
fastboot flashing unlock            # WIPES the device
fastboot delete-logical-partition product   # A/B devices often need the room
fastboot flash system system.img
fastboot -w                         # wipe userdata
fastboot reboot
```

Then verify, in this order: it boots → the dashboard is home → WiFi/BT/GPS are on →
`Warivo-Node` connects → audio reaches the speaker.

### The kernel

A GSI uses the phone's **stock kernel**, so there is no kernel build in the flow above.
`os/kernel/configs/warivo.fragment` exists for a full device port, and for the one thing
Warivo wants from the kernel that stock phone kernels often omit: `CONFIG_USB_ACM` and the
CP210x/CH341 drivers, which allow a wired USB-OTG link to the ESP32-C6 as a diagnostic
fallback when BLE misbehaves. See [../os/kernel/README.md](../os/kernel/README.md).

## 6. Realistic cost

| Stage | Effort |
| --- | --- |
| Host setup + first sync | Half a day, mostly waiting |
| First successful GSI build | 3–8 h of machine time; a day or two of fixing |
| Warivo overlays + no-GApps + boot animation | 1–2 days |
| Flash, debug, reflash | 1–3 days |

**Call it a week of real work for someone who has not done it before**, and it delivers a
boot animation, no setup wizard, and radios-on. Path A's kiosk already delivers the other
95%. Ship Path A first — that has not changed.

## 7. Open decisions

- **Which phone?** Nothing here can start without the model, codename and unlock status.
- **Android version:** the launcher targets `minSdk 28`, so an Android 14 GSI works and is
  better maintained than a 9/10 build. Worth choosing deliberately rather than defaulting
  to the phone's stock version. Pin an AOSP release tag; `master` moves under a build.
- ~~**Device Owner at first boot**~~ — **done.** `os/vendor/warivo/provision/` is a
  platform-signed system app that makes the launcher Device Owner at
  `LOCKED_BOOT_COMPLETED`, so a flashed ROM comes up locked with no cable. See
  [../os/README.md](../os/README.md) for why it needs reflection and the three conditions
  that must all hold for its permissions to land.
