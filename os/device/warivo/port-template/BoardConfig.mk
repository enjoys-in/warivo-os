# Warivo OS — BoardConfig TEMPLATE for a full device port.
#
# NOTHING BUILDS THIS FILE. It is not referenced by any AndroidProducts.mk, and that is
# deliberate: the GSI target (device/warivo/gsi/) must not ship a board config at all,
# because PRODUCT_DEVICE resolves to AOSP's own generic_arm64 board and a second directory
# of that name collides with it. Pinning board values is also exactly what stops a GSI
# being generic.
#
# Use this only when moving from a GSI to a real device port:
#   1. copy to device/<oem>/<codename>/BoardConfig.mk
#   2. fill in the DEVICE PORT section from the LineageOS device tree for that phone
#   3. add a matching AndroidProducts.mk and lineage_<codename>.mk
#
# See docs/ROM_BUILD.md §2 for why a GSI comes first.

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
