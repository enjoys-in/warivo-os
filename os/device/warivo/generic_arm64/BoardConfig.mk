# Warivo OS — board config for the arm64 Treble GSI target.
#
# A GSI is deliberately board-agnostic: it boots on any Project-Treble device using that
# device's own kernel, bootloader and vendor partition. So almost everything a normal
# BoardConfig would declare (partition sizes, panel, SoC, sepolicy) is inherited from the
# AOSP generic target and must NOT be pinned here — pinning it is what stops a GSI being
# generic.
#
# For a full device port, copy this file to device/<oem>/<codename>/ and fill in the
# "DEVICE PORT ONLY" section from the LineageOS device tree for that phone.

TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_VARIANT := generic
TARGET_CPU_ABI2 :=

# 32-bit ABI kept for app compatibility: plenty of ARM32-only APKs still exist, and a
# head unit that cannot run them for no reason is a worse head unit.
TARGET_2ND_ARCH := arm
TARGET_2ND_ARCH_VARIANT := armv8-a
TARGET_2ND_CPU_ABI := armeabi-v7a
TARGET_2ND_CPU_ABI2 := armeabi
TARGET_2ND_CPU_VARIANT := generic

TARGET_USES_64_BIT_BINDER := true

# Treble: build against the current VNDK so the system image stays independent of the
# vendor partition it lands on.
BOARD_VNDK_VERSION := current
PRODUCT_TARGET_VNDK_VERSION := current

# The GSI ships system-as-root; the device's own boot image is left alone.
BOARD_BUILD_SYSTEM_ROOT_IMAGE := false

# ---------------------------------------------------------------------------
# DEVICE PORT ONLY — leave every line below commented for a GSI build.
#
# These come from the target phone's LineageOS device tree; inventing them is how you get
# a build that completes and then does not boot. Take them, do not guess them.
# ---------------------------------------------------------------------------
# TARGET_BOARD_PLATFORM := <soc>
# TARGET_KERNEL_SOURCE := kernel/<vendor>/<soc>
# TARGET_KERNEL_CONFIG := <device>_defconfig
# BOARD_KERNEL_CMDLINE := <from the stock boot image>
# BOARD_KERNEL_BASE := 0x...
# BOARD_KERNEL_PAGESIZE := 4096
# BOARD_BOOTIMAGE_PARTITION_SIZE := ...
# BOARD_SYSTEMIMAGE_PARTITION_SIZE := ...
# BOARD_USERDATAIMAGE_PARTITION_SIZE := ...
# BOARD_FLASH_BLOCK_SIZE := ...
# TARGET_USERIMAGES_USE_EXT4 := true
# TARGET_USERIMAGES_USE_F2FS := true
# BOARD_SEPOLICY_DIRS += device/<oem>/<codename>/sepolicy
