# os/ — the Warivo OS ROM scaffold

Device-independent AOSP/LineageOS configuration for **Path B**. Drop it in at
`vendor/warivo/` in a synced LineageOS tree and `lunch warivo_arm64-userdebug`.

Read [../docs/ROM_BUILD.md](../docs/ROM_BUILD.md) first — it holds the decision (AOSP via
LineageOS, shipped as a Treble GSI rather than a per-device port), the hard prerequisites
and the build steps.

> **Status: scaffold, not a working build.** Nothing here has been fed to a real AOSP tree.
> It is the shape of the product config, written so the device-independent decisions are
> reviewable before anyone spends a day syncing 250 GB. Expect to correct framework
> overlay key names against whichever branch you sync.

## Layout

```
os/vendor/warivo/
├── AndroidProducts.mk          registers warivo_arm64 with lunch
├── warivo_arm64.mk             the product: GSI base, our app, removals, branding
├── prebuilt/
│   └── WarivoLauncher/
│       ├── Android.bp          imports the APK as a privileged system app
│       └── WarivoLauncher.apk  ← you add this (Path A's release build)
└── overlay/frameworks/base/
    ├── core/res/…/config.xml                   framework trims
    └── packages/SettingsProvider/…/defaults.xml first-boot settings
```

## What it does, and the one thing to watch

`warivo_arm64.mk` adds `WarivoLauncher` and **removes** `Launcher3`/`Trebuchet`, the setup
wizard (`Provision`), and the phone apps a scooter has no use for. With no other
`CATEGORY_HOME` activity present, the system cannot show a launcher chooser and has
nothing to fall back to — that, plus no GApps, is what turns a phone into an appliance.

`defaults.xml` is the more important of the two overlays: it puts WiFi, Bluetooth and GPS
**on before any app runs**. On stock Android the launcher has to ask at runtime and can
only re-enable them after the fact, as Device Owner.

**The watch-out:** framework `config.xml` keys are renamed between AOSP releases, and an
unknown name is **silently ignored**. That failure mode looks exactly like "the overlay
isn't working". Check every key in `config.xml` against
`frameworks/base/core/res/res/values/config.xml` in the tree you actually synced.

## Building the APK this expects

```bash
cd ../android/warivo-launcher
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk \
   ../../os/vendor/warivo/prebuilt/WarivoLauncher/WarivoLauncher.apk
```

`Android.bp` signs it with `certificate: "platform"`, so the ROM's platform key replaces
whatever Gradle signed it with — the APK does not need its own release keystore here.
