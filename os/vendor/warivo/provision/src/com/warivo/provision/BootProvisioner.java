package com.warivo.provision;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Makes the Warivo Launcher Device Owner on first boot, so a flashed ROM comes up as a
 * locked head unit without anyone plugging in a cable.
 *
 * <p>Path A does this with {@code adb shell dpm set-device-owner} (see
 * {@code os/build/provision.sh}). That is impossible on a scooter: there is no host to run
 * adb from, and a ROM that needs a laptop before it works is not an appliance. This
 * receiver closes that gap and is the reason the ROM exists at all.
 *
 * <p><b>Why reflection.</b> {@code setActiveAdmin} and {@code setDeviceOwner} are
 * {@code @hide} platform methods with no public equivalent — the public API assumes
 * provisioning happens through adb, NFC or a DPC during setup, none of which apply to a
 * device that ships pre-provisioned. Reflection onto hidden APIs is blocked for ordinary
 * apps, but platform-signed system apps are exempt, which is exactly what this is. If
 * these signatures move in a future AOSP release this class stops working and logs why;
 * it will never crash the boot.
 */
public class BootProvisioner extends BroadcastReceiver {

    private static final String TAG = "WarivoProvision";

    private static final String LAUNCHER_PKG = "com.warivo.os";
    private static final String ADMIN_CLASS = "com.warivo.os.kiosk.AdminReceiver";
    private static final String OWNER_NAME = "Warivo OS";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            provision(context);
        } catch (Throwable t) {
            // Never let provisioning take the boot down with it. A ROM that boots to an
            // unprovisioned launcher is recoverable over adb; one that does not boot is a
            // reflash.
            Log.e(TAG, "provisioning failed", t);
        }
    }

    private void provision(Context context) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) {
            Log.e(TAG, "no DevicePolicyManager");
            return;
        }

        if (dpm.isDeviceOwnerApp(LAUNCHER_PKG)) {
            Log.i(TAG, "already provisioned");
            return;
        }

        // Some other app owning the device is not something to fight over: only one
        // Device Owner can exist, and taking it would need a factory reset anyway.
        ComponentName admin = new ComponentName(LAUNCHER_PKG, ADMIN_CLASS);

        if (!dpm.isAdminActive(admin)) {
            if (!setActiveAdmin(dpm, admin)) {
                Log.e(TAG, "could not activate " + admin.flattenToShortString());
                return;
            }
        }

        if (setDeviceOwner(dpm, admin)) {
            Log.i(TAG, "Warivo Launcher is now Device Owner");
        } else {
            Log.e(TAG, "setDeviceOwner refused; the device may already be provisioned or "
                    + "have accounts configured");
        }
    }

    /** {@code dpm.setActiveAdmin(ComponentName, boolean)} — hidden. */
    private boolean setActiveAdmin(DevicePolicyManager dpm, ComponentName admin) {
        try {
            Method m = DevicePolicyManager.class.getMethod(
                    "setActiveAdmin", ComponentName.class, boolean.class);
            m.invoke(dpm, admin, /* refreshing= */ true);
            return true;
        } catch (NoSuchMethodException e) {
            // Older releases took an extra userId argument.
            try {
                Method m = DevicePolicyManager.class.getMethod(
                        "setActiveAdmin", ComponentName.class, boolean.class, int.class);
                m.invoke(dpm, admin, true, UserHandle.myUserId());
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "setActiveAdmin unavailable", t);
                return false;
            }
        } catch (Throwable t) {
            Log.e(TAG, "setActiveAdmin failed", t);
            return false;
        }
    }

    /** {@code dpm.setDeviceOwner(...)} — hidden, and its signature has changed twice. */
    private boolean setDeviceOwner(DevicePolicyManager dpm, ComponentName admin) {
        // Tried newest first. Each AOSP generation added an argument rather than
        // deprecating the old form, so probing is the only way to stay portable across
        // the branch someone actually syncs.
        Class<?>[][] signatures = {
                { ComponentName.class, String.class, int.class },
                { ComponentName.class, String.class },
                { ComponentName.class },
        };
        Object[][] arguments = {
                { admin, OWNER_NAME, UserHandle.myUserId() },
                { admin, OWNER_NAME },
                { admin },
        };

        for (int i = 0; i < signatures.length; i++) {
            try {
                Method m = DevicePolicyManager.class.getMethod("setDeviceOwner", signatures[i]);
                Object result = m.invoke(dpm, arguments[i]);
                // Returns boolean on every release that has it; treat a null return
                // (void variant) as success, since it threw nothing.
                return !(result instanceof Boolean) || (Boolean) result;
            } catch (NoSuchMethodException ignored) {
                // Try the next shape.
            } catch (Throwable t) {
                Log.e(TAG, "setDeviceOwner threw", t);
                return false;
            }
        }
        Log.e(TAG, "no setDeviceOwner overload matched this platform");
        return false;
    }
}
