# Registers the Warivo GSI product with lunch. Lives at device/warivo/gsi/ in an AOSP tree.
PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/warivo_arm64.mk

COMMON_LUNCH_CHOICES := \
    warivo_arm64-userdebug \
    warivo_arm64-user
