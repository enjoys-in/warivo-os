#!/sbin/sh
# Warivo OS Magisk module installer.
#
# The Warivo boot animation is copied into system/media and system/product/media by
# os/build/build-magisk.sh before packaging. Magisk auto-mounts the module's system/ over
# /system (and system/product over /product), so both bootanimation locations phones
# actually read from are covered without editing the real partitions.

ui_print "- Warivo OS head unit"

if [ ! -f "$MODPATH/system/media/bootanimation.zip" ]; then
  ui_print "! bootanimation.zip is missing from the module"
  ui_print "! build it first:  os/build/build-bootanimation.sh"
  abort "  aborting — nothing to install"
fi

ui_print "- Boot animation, radios-on and no-setup-wizard defaults installed"
ui_print "- Next: run os/build/provision.sh once over adb for the single-app kiosk lock"

set_perm_recursive "$MODPATH" 0 0 0755 0644
