# os/kernel — the Linux-kernel layer

Warivo OS is a Linux kernel plus Android's userspace. This directory holds the **kernel
side**: the config deltas Warivo needs. It deliberately does **not** vendor a copy of the
kernel source — that is hundreds of megabytes that `repo sync` fetches for the target
device, and a stale fork is worse than no fork.

## Files

| File | What it is |
| --- | --- |
| `configs/warivo.fragment` | Config options merged on top of the device's own defconfig |

## Applying the fragment

```bash
cd kernel/<vendor>/<soc>
ARCH=arm64 scripts/kconfig/merge_config.sh \
    arch/arm64/configs/<device>_defconfig \
    ../../../os/kernel/configs/warivo.fragment
```

A fragment rather than a whole defconfig, because a phone's kernel config is mostly SoC
drivers, clock trees and panel timings that we have no business rewriting. We add to it;
we do not replace it.

## When this matters

**A Treble GSI build uses the phone's stock kernel and needs none of this.** That is the
recommended first target (see [../../docs/ROM_BUILD.md](../../docs/ROM_BUILD.md) §2), so
expect to build a working ROM before you ever touch a kernel.

It becomes relevant for two things:

1. **A full device port**, where you are building the boot image as well as the system
   image.
2. **The USB fallback to the node.** `CONFIG_USB_ACM` and the CP210x/CH341 drivers let the
   phone talk to the ESP32-C6 over USB-OTG. BLE is the design (the phone keeps its own
   internet and its Bluetooth speaker that way), but a wired path is an excellent
   diagnostic when BLE misbehaves on a specific handset, and stock phone kernels often
   omit those drivers.
