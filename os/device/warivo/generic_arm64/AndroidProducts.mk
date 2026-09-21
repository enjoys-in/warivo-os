# Registers the Warivo products with lunch. Placed at vendor/warivo/ in a LineageOS tree.
PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/warivo_arm64.mk

COMMON_LUNCH_CHOICES := \
    warivo_arm64-userdebug \
    warivo_arm64-user
