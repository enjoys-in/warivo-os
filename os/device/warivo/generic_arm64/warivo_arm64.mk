# Warivo OS — arm64 Treble GSI product.
#
# Inherits AOSP's generic system image target, so this builds one system.img that boots on
# any Treble device rather than needing a per-phone device tree. See docs/ROM_BUILD.md §2.
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)
$(call inherit-product, device/warivo/generic_arm64/device.mk)

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

