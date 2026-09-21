# Warivo OS — the ROM (Path B)

**Recommendation: build on AOSP, via LineageOS, and ship it first as a Treble GSI — not
from the Linux kernel, and not as a per-device port.**

This document is the decision and its reasoning, the prerequisites, the honest blockers,
and the build steps. The device-independent part of the work is scaffolded in
[../os/](../os/).

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

So: **AOSP.** Specifically **LineageOS**, because it already carries the device trees,
kernel forks and vendor-blob extraction that raw AOSP does not, and it boots on consumer
phones without GApps.

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
- [ ] **~400 GB free SSD** (250 GB source + ~150 GB build output) and **16 GB RAM
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

Everything else — gauges, kiosk, panels — is the same APK as Path A. See
[../os/README.md](../os/README.md) for the scaffolded product config and overlays.

## 5. Build steps

```bash
# 0. Host setup (Ubuntu 22.04)
sudo apt install -y git-core gnupg flex bison build-essential zip curl zlib1g-dev \
  libc6-dev-i386 x11proto-core-dev libx11-dev lib32z1-dev libgl1-mesa-dev \
  libxml2-utils xsltproc unzip fontconfig python3 openjdk-11-jdk
mkdir -p ~/bin && curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
chmod a+x ~/bin/repo && export PATH=~/bin:$PATH

# 1. Sync LineageOS (~250 GB, hours). Pick the branch matching the target Android version.
mkdir -p ~/lineage && cd ~/lineage
repo init -u https://github.com/LineageOS/android.git -b lineage-21.0 --git-lfs
repo sync -c -j"$(nproc)" --force-sync --no-clone-bundle --no-tags

# 2. Drop in the Warivo product config and overlays
cp -r /path/to/warivo-os/os/vendor/warivo vendor/warivo
#    ...and the launcher APK it expects:
#    vendor/warivo/prebuilt/WarivoLauncher/WarivoLauncher.apk

# 3. Build the GSI
source build/envsetup.sh
lunch warivo_arm64-userdebug        # defined by os/vendor/warivo/AndroidProducts.mk
mka systemimage                     # -> out/target/product/generic_arm64/system.img

# 4. Flash it (device-specific; A/B vs A-only differs)
adb reboot bootloader
fastboot flashing unlock            # WIPES the device
fastboot delete-logical-partition product      # A/B devices often need the room
fastboot flash system system.img
fastboot -w                         # wipe userdata
fastboot reboot
```

Then verify, in this order: it boots → the dashboard is home → WiFi/BT/GPS are on →
`Warivo-Node` connects → audio reaches the speaker.

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
- **Android version:** the launcher targets `minSdk 28`, so a LineageOS 21 (Android 14)
  GSI works too and is better maintained than a 9/10 build. Worth choosing deliberately
  rather than defaulting to the phone's stock version.
- **Device Owner at first boot:** the clean options are a preinstalled privileged app with
  permissions granted by default, or keeping the single `adb dpm` command after flashing.
  The latter is one command and zero ROM complexity; start there.
