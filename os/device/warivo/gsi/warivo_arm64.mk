# Warivo OS — arm64 Treble GSI product.
#
# aosp_arm64 *is* the GSI in Android 10 and later: it already inherits the generic system
# chain and sets the board to generic_arm64. Inheriting generic_system.mk as well (which an
# earlier version of this file did) double-applies the same config and is how you get
# duplicate-package and conflicting-property errors deep in a build.
#
# Built from AOSP, not LineageOS — see docs/ROM_BUILD.md §2. A GSI needs no device tree,
# which is the only thing LineageOS was being brought in for.
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)
$(call inherit-product, device/warivo/gsi/device.mk)

# No BoardConfig.mk here on purpose. PRODUCT_DEVICE below resolves to AOSP's own
# generic_arm64 board; shipping a second board directory with that name collides with it.
# The template for a real device port lives in device/warivo/port-template/.

PRODUCT_NAME := warivo_arm64
PRODUCT_DEVICE := generic_arm64
PRODUCT_BRAND := Warivo
PRODUCT_MODEL := Warivo OS
PRODUCT_MANUFACTURER := warivo

# --- What Warivo removes ---------------------------------------------------
# Removing Launcher3/Trebuchet is the important one: with no other CATEGORY_HOME activity
# present, the system cannot show a launcher chooser and has nothing to fall back to. That,
# plus no GApps, is what turns a phone into an appliance rather than a phone with our app
# on it. Provision is the setup wizard, so the ROM boots straight to the dashboard.
#
# NOT removed, and worth stating so nobody "tidies" them away:
#   LatinIME     — the Search panel has a text field. No keyboard, no search.
#   WebViewGoogle / webview — the Search panel *is* a WebView.
#   PackageInstaller — needed to update the launcher without reflashing.
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
    WallpaperPicker

