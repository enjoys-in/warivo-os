# Warivo OS — device-level packages and copies.
#
# Split from warivo_arm64.mk on purpose: that file is the *product* (what Warivo is), this
# one is the *device* (what the hardware needs). A device port replaces this file and
# keeps the product one.

# The launcher, as a privileged system app. See vendor/warivo/prebuilt/.
PRODUCT_PACKAGES += \
    WarivoLauncher

# Branding. Render the animation first:
#   os/build/build-bootanimation.sh
# Android requires the zip be STORED, not deflated, or it silently does not play.
PRODUCT_COPY_FILES += \
    vendor/warivo/prebuilt/bootanimation.zip:$(TARGET_COPY_OUT_SYSTEM)/media/bootanimation.zip

# Framework and Settings defaults: radios on before any app runs, no keyguard, no
# rotation, stay awake while charging.
DEVICE_PACKAGE_OVERLAYS += vendor/warivo/overlay

# A head unit is landscape and permanently powered.
PRODUCT_PROPERTY_OVERRIDES += \
    ro.warivo.version=0.1 \
    ro.warivo.codename=NOVA-S \
    persist.sys.rotation.enabled=false \
    ro.config.low_ram=false

# No Play Services anywhere, deliberately: no Play Store, no account setup, no update
# nagging on a scooter. Search is the launcher's own Google-only WebView.
