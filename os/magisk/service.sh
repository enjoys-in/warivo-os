#!/system/bin/sh
# Warivo head-unit defaults, applied once the framework is up.
#
# These live in the settings provider, not in system properties, so they can't go in a
# system.prop — a late_start service that shells `settings`/`svc` is the systemless way.
# Runs on every boot; every line is idempotent.

# Wait for the framework so `settings` and `svc` exist and take effect.
until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 2
done

# Never show the setup wizard — a scooter flashes and boots straight to the dashboard.
settings put global device_provisioned 1
settings put secure user_setup_complete 1

# Stay awake while charging; the phone is permanently powered from the scooter's 5 V rail.
# Bitmask AC|USB|WIRELESS = 1|2|4 = 7.
settings put global stay_on_while_plugged_in 7

# The radios a head unit needs, on at boot before any app runs.
svc wifi enable
svc bluetooth enable
settings put secure location_mode 3
