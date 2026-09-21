# Warivo OS — arm64 Treble GSI product.
#
# Inherits AOSP's generic system image target, so this builds one system.img that boots on
# any Treble device rather than needing a per-phone device tree. See docs/ROM_BUILD.md §2.
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)

PRODUCT_NAME := warivo_arm64
PRODUCT_DEVICE := generic_arm64
PRODUCT_BRAND := Warivo
PRODUCT_MODEL := Warivo OS
PRODUCT_MANUFACTURER := warivo

# --- The launcher, as the only home ---------------------------------------
# WarivoLauncher is added here; Launcher3/Trebuchet is removed below so the system has no
# other CATEGORY_HOME activity to fall back to and never shows a launcher chooser.
PRODUCT_PACKAGES += \
    WarivoLauncher

# Anything a scooter dashboard has no use for. Removing them is what makes this an
# appliance rather than a phone with our app on it.
PRODUCT_PACKAGES_REMOVE := \
    Launcher3 \
    Launcher3QuickStep \
    QuickStep \
    Trebuchet \
    Provision \
    Camera2 \
    Gallery2 \
    Calendar \
    Contacts \
    Dialer \
    Messaging \
    Email \
    QuickSearchBox \
    WallpaperPicker \
    LatinIME

# --- Framework and settings defaults --------------------------------------
# Radios on at first boot, before any app runs. See overlay/.
DEVICE_PACKAGE_OVERLAYS += vendor/warivo/overlay

# --- Branding --------------------------------------------------------------
# Render branding/boot-animation.html to numbered PNG frames first; see
# branding/README.md for the ffmpeg + zip -0 recipe. Android requires the zip be STORED,
# not deflated, or the animation silently does not play.
PRODUCT_COPY_FILES += \
    vendor/warivo/prebuilt/bootanimation.zip:$(TARGET_COPY_OUT_SYSTEM)/media/bootanimation.zip

PRODUCT_PROPERTY_OVERRIDES += \
    ro.warivo.version=0.1 \
    ro.warivo.codename=NOVA-S

# No GApps, deliberately: no Play Store, no account setup, no update nagging on a scooter.
# Search is the launcher's own Google-only WebView (see the Search panel).
