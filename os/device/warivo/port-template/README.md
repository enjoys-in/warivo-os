# device/warivo/port-template

A `BoardConfig.mk` skeleton for the day a Treble GSI is not enough and Warivo needs a real
per-device port.

**Nothing builds this directory.** No `AndroidProducts.mk` points at it. The GSI target in
[../gsi/](../gsi/) deliberately ships no board config, because `PRODUCT_DEVICE` resolves to
AOSP's own `generic_arm64` board and a second directory of that name collides with it.

## When you would need this

A GSI covers screen, touch, Wi-Fi, Bluetooth and GPS — everything a dashboard uses. Move to
a port only if the GSI turns out to break one of those on the specific handset. Camera,
fingerprint and VoLTE breaking do not count; a dashboard does not use them.

## What a port needs beyond this file

1. The **device tree** for that phone — `android_device_<oem>_<codename>` from LineageOS.
2. The **kernel source** — and then
   [../../kernel/configs/warivo.fragment](../../kernel/configs/warivo.fragment) merged onto
   its defconfig.
3. **Vendor blobs**, extracted from the phone or a stock image.
4. A product makefile inheriting LineageOS rather than AOSP, since this is the path where
   LineageOS's device support is the whole point.

[../../manifests/warivo.xml](../../manifests/warivo.xml) has the `repo` entries for 1–3,
left as placeholders because every one of them names a phone that has not been chosen yet.

Take these values from the device tree. Guessing them produces a build that completes and
then does not boot.
