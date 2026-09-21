# os/ — Warivo OS ROM source

The **OS half** of Warivo: a Linux kernel plus Android's userspace, configured to boot
straight into the Warivo Launcher and nothing else. This is Path B.

The launcher itself lives in [../launcher/](../launcher/) and is the same APK either way —
Path A runs it as a kiosk on stock Android, Path B bakes it in as the only home.

Read [../docs/ROM_BUILD.md](../docs/ROM_BUILD.md) first for *why* this is a Treble GSI
rather than a per-device port.

**Built from AOSP, not LineageOS.** LineageOS has no GSI product — the GSI *is* AOSP's
`aosp_arm64` target, and has been since Android 10. LineageOS was only ever in the plan for
the device trees a GSI does not use, so syncing it here means ~80 GB more source for
nothing. It becomes the right tree the day this turns into a per-device port.

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
├── device/warivo/
│   ├── gsi/                      the GSI product — this is what builds
│   │   ├── AndroidProducts.mk      registers warivo_arm64 with lunch
│   │   ├── warivo_arm64.mk         inherits aosp_arm64 + what Warivo removes
│   │   └── device.mk               packages, branding copies, overlays, properties
│   └── port-template/            BoardConfig skeleton for a future device port
│                                   (nothing builds it — see its README)
├── vendor/warivo/                overlays, prebuilts, and the provisioner
│   ├── overlay/frameworks/base/  framework + SettingsProvider defaults
│   ├── provision/                first-boot Device Owner app (platform-signed)
│   ├── etc/                      privileged permission allowlist
│   └── prebuilt/WarivoLauncher/  ← you add WarivoLauncher.apk here
├── manifests/warivo.xml          repo local manifest (device port only)
├── build/
│   ├── provision.sh              Path A: install + Device Owner + permissions (no ROM needed)
│   ├── build-rom.sh              sync, graft, build a GSI
│   ├── build-bootanimation.sh    branding/ → bootanimation.zip
│   └── flash-gsi.sh              flash the GSI, with the checks that save a bootloop
└── README.md                     you are here
```

`device/` is *what the hardware needs*; `vendor/` is *what we add on top*. That is the
AOSP convention and it means a device port replaces `device/` and keeps `vendor/`
untouched.

**The GSI target ships no `BoardConfig.mk`, deliberately.** `PRODUCT_DEVICE` resolves to
AOSP's own `generic_arm64` board, and a second directory of that name collides with it —
pinning board values is also precisely what would stop a GSI being generic. The skeleton
for a real port is in `device/warivo/port-template/`, referenced by nothing.

## Provision a phone without building anything

Path A needs no ROM at all, and `provision.sh` is the whole of it — it runs today, against
a stock phone, with nothing but `adb`:

```bash
cd launcher && ./gradlew assembleDebug && cd ..
os/build/provision.sh
```

It installs the APK, grants Device Owner, grants the runtime permissions so the first
launch is already working, and verifies the result. Every step is checked *before* it is
attempted, because `dpm set-device-owner` fails for three reasons and reports none of
them: an account is still signed in, the device was already provisioned, or it is a managed
profile. The script names all three and tells you the reset that clears them. Safe to
re-run.

## Build

```bash
# 1. Build the launcher APK the ROM expects
cd ../launcher && ./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk \
   ../os/vendor/warivo/prebuilt/WarivoLauncher/WarivoLauncher.apk

# 2. Optional: render the boot animation
../os/build/build-bootanimation.sh 1080 1920 30

# 3. Sync AOSP and build the GSI  (Linux only, ~300 GB, hours)
../os/build/build-rom.sh
```

`build-rom.sh` refuses to start rather than failing hours in: it checks you are on Linux
(AOSP dropped macOS years ago — this is not a flag you can pass), that `repo` and a JDK
exist, that there is ~400 GB free, and that the launcher APK is present. It warns below
16 GB RAM, where Soong thrashes or gets OOM-killed.

Output: `out/target/product/generic_arm64/system.img`. Then:

```bash
os/build/flash-gsi.sh
```

**It wipes the phone** — unlocking the bootloader erases userdata by design, so there is no
non-destructive path. It reads `ro.treble.enabled`, the ABI and the A/B layout off the
device first and refuses rather than flashing a GSI that cannot boot, which is a far
cheaper way to learn that than a bootloop. It asks you to type the model name to confirm.

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

## First-boot provisioning — why there is an app for it

Path A grants Device Owner with `adb shell dpm set-device-owner`. **There is no adb on a
scooter.** A ROM that needs a laptop plugged in before it becomes a head unit is not an
appliance, and that gap is most of the reason Path B exists at all.

`vendor/warivo/provision/` is a ~150-line platform-signed system app that runs at
`LOCKED_BOOT_COMPLETED`, notices there is no Device Owner, and makes the launcher one. It
then does nothing for the rest of the device's life.

It calls `setActiveAdmin` and `setDeviceOwner` **by reflection**, because both are
`@hide`. There is no public equivalent: the public API assumes provisioning happens through
adb, NFC or a DPC during setup, none of which apply to a device that ships already
provisioned. Hidden-API reflection is blocked for ordinary apps but platform-signed system
apps are exempt — which is what this is, and why it cannot live in the launcher APK that
also has to run as a normal app on stock Android.

`setDeviceOwner`'s signature has changed twice across AOSP releases (each generation added
an argument rather than deprecating the old form), so the app probes newest-first and logs
which one matched. If a future release moves them again it stops working and says so in
logcat; it can never take the boot down, because the whole receiver is wrapped — an
unprovisioned launcher is recoverable over adb, a device that does not boot is a reflash.

**Three things must all be true or it silently has no permissions:**

1. `certificate: "platform"` in `Android.bp`,
2. `privileged: true`, so it installs to `/system/priv-app`,
3. an entry in `etc/privapp-permissions-warivo.xml`, copied to
   `/system/etc/permissions/`.

Since Android 8 a priv-app receives **none** of its `signature|privileged` permissions
without being allowlisted, and nothing obvious reports the omission.

## No SELinux policy here, on purpose

Warivo adds no native daemons, no new HALs and no new device nodes — the launcher and the
provisioner are ordinary (privileged) Android apps running in existing domains. There is
therefore nothing to write policy for, and an empty `sepolicy/` directory would only imply
otherwise. A device port may need policy, but that comes from the device tree, not from us.

If a `userdebug` build does log an SELinux denial for `com.warivo.provision`, the
`priv_app` domain is where it belongs; do not put it in `system_app`.
