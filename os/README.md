# os/ — Warivo OS ROM source

The **OS half** of Warivo: a Linux kernel plus Android's userspace, configured to boot
straight into the Warivo Launcher and nothing else. This is Path B.

The launcher itself lives in [../launcher/](../launcher/) and is the same APK either way —
Path A runs it as a kiosk on stock Android, Path B bakes it in as the only home.

Read [../docs/ROM_BUILD.md](../docs/ROM_BUILD.md) first for *why* this is AOSP-based and
shipped as a Treble GSI rather than a per-device port.

> **Status: source tree, never built.** Nothing here has been fed to a real AOSP tree.
> It is written so the device-independent decisions are reviewable before anyone spends a
> day syncing 250 GB, and so the build is one command when there is a Linux host and a
> phone. Expect to correct framework overlay key names against whichever branch you sync.

---

## The tree

```
os/
├── kernel/                     the Linux-kernel layer
│   ├── configs/warivo.fragment   config deltas merged onto the device defconfig
│   └── README.md
├── device/warivo/generic_arm64/  the board + product definition
│   ├── BoardConfig.mk            arch, Treble/VNDK; device-port lines left commented
│   ├── device.mk                 packages, branding copies, overlays, properties
│   ├── warivo_arm64.mk           the product: GSI base + what Warivo removes
│   └── AndroidProducts.mk        registers warivo_arm64 with lunch
├── vendor/warivo/                overlays and prebuilts
│   ├── overlay/frameworks/base/  framework + SettingsProvider defaults
│   └── prebuilt/WarivoLauncher/  ← you add WarivoLauncher.apk here
├── manifests/warivo.xml          repo local manifest (device port only)
├── build/
│   ├── build-rom.sh              sync, graft, build a GSI
│   └── build-bootanimation.sh    branding/ → bootanimation.zip
└── README.md                     you are here
```

`device/` is *what the hardware needs*; `vendor/` is *what we add on top*. That is the
AOSP convention and it means a device port replaces `device/` and keeps `vendor/`
untouched.

## Build

```bash
# 1. Build the launcher APK the ROM expects
cd ../launcher && ./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk \
   ../os/vendor/warivo/prebuilt/WarivoLauncher/WarivoLauncher.apk

# 2. Optional: render the boot animation
../os/build/build-bootanimation.sh 1080 1920 30

# 3. Sync LineageOS and build the GSI  (Linux only, ~400 GB, hours)
../os/build/build-rom.sh
```

`build-rom.sh` refuses to start rather than failing hours in: it checks you are on Linux
(AOSP dropped macOS years ago — this is not a flag you can pass), that `repo` and a JDK
exist, that there is ~400 GB free, and that the launcher APK is present. It warns below
16 GB RAM, where Soong thrashes or gets OOM-killed.

Output: `out/target/product/generic_arm64/system.img`. Flashing is device-specific; see
[../docs/ROM_BUILD.md](../docs/ROM_BUILD.md) §5.

## What this actually changes

The ROM is not where the product lives — the launcher is. The ROM only removes friction
the kiosk cannot:

| Change | Why it needs a ROM |
| --- | --- |
| Launcher preinstalled as the **only** home | `Launcher3`/`Trebuchet` are removed, so no launcher chooser and nothing to fall back to |
| **No GApps** | No Play Store, no account setup, no update nagging on a scooter |
| **No setup wizard** | `Provision` removed: flash, boot, dashboard |
| WiFi/BT/GPS **on at first boot** | `SettingsProvider` defaults land before any app runs |
| **Stay awake while charging** | Permanently powered from the scooter's 5 V rail |
| Warivo **boot animation** | `/system/media/` needs system-partition access |

Three packages are deliberately **kept**, and it is worth knowing why before anyone tidies
them away: `LatinIME` (the Search panel has a text field — no keyboard, no search), the
WebView implementation (the Search panel *is* a WebView), and `PackageInstaller` (so the
launcher can be updated without reflashing).

## Two things that will bite

- **Framework `config.xml` keys are renamed between AOSP releases, and an unknown key is
  silently ignored.** That failure mode looks exactly like "the overlay isn't working".
  Check every key in `vendor/warivo/overlay/.../config.xml` against
  `frameworks/base/core/res/res/values/config.xml` in the tree you actually synced.
- **`bootanimation.zip` must be STORED, not deflated** (`zip -0`). A compressed zip gives
  no error and no animation. `build-bootanimation.sh` gets this right; hand-zipping
  usually does not.

## The kernel layer

`kernel/configs/warivo.fragment` is a *fragment* merged onto the target device's own
defconfig, not a replacement — a phone's kernel config is mostly SoC drivers, clock trees
and panel timings we have no business rewriting.

**A GSI build uses the phone's stock kernel and needs none of it.** It matters for a full
device port, and for one Warivo-specific addition: `CONFIG_USB_ACM` plus the CP210x/CH341
drivers, which let the phone reach the ESP32-C6 over USB-OTG. BLE is the design — it is
how the phone keeps its own internet and its Bluetooth speaker at the same time — but a
wired path is an excellent diagnostic when BLE misbehaves on a specific handset, and stock
phone kernels often omit those drivers.

See [kernel/README.md](kernel/README.md).

## No SELinux policy here, on purpose

Warivo adds no native daemons, no new HALs and no new device nodes — the launcher is an
ordinary (privileged) Android app. There is therefore nothing to write policy for, and an
empty `sepolicy/` directory would only imply otherwise. A device port may need policy, but
that comes from the device tree, not from us.
