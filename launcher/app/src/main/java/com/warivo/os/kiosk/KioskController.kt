@file:Suppress("DEPRECATION", "MissingPermission")

package com.warivo.os.kiosk

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.warivo.os.MainActivity

/**
 * Everything that turns the launcher into an appliance: Lock Task, the persistent HOME
 * binding, radios-always-on, and the escape hatch back out.
 *
 * All of it needs Device Owner, which can only be granted on a factory-reset phone with
 * no accounts. Without it every call here is a no-op and the app still runs as an
 * ordinary, exitable app — that is the intended development mode.
 */
class KioskController(private val context: Context) {

    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val admin = ComponentName(context, AdminReceiver::class.java)

    val isDeviceOwner: Boolean
        get() = runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)

    val isAdminActive: Boolean
        get() = runCatching { dpm.isAdminActive(admin) }.getOrDefault(false)

    // ---- one-time provisioning policies ---------------------------------------

    /**
     * Idempotent. Safe to call on every boot; each step is guarded because a few of
     * these APIs throw rather than return false when the OEM disallows them.
     */
    fun applyOwnerPolicies() {
        if (!isDeviceOwner) return

        step("lockTaskPackages") { dpm.setLockTaskPackages(admin, arrayOf(context.packageName)) }

        // LOCK_TASK_FEATURE_NONE: no home, no recents, no status bar, no power menu.
        step("lockTaskFeatures") {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }

        // Make us HOME permanently, so the system never shows a launcher chooser.
        step("persistentHome") {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin, filter, ComponentName(context, MainActivity::class.java)
            )
        }

        step("uninstallBlocked") { dpm.setUninstallBlocked(admin, context.packageName, true) }
        step("statusBarDisabled") { dpm.setStatusBarDisabled(admin, true) }
        step("keyguardDisabled") { dpm.setKeyguardDisabled(admin, true) }

        // Permanently powered from the scooter's 5 V rail: never sleep while charging.
        step("stayOn") {
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "7")
        }

        // Stop the rider (or a thief) from undoing any of this.
        RESTRICTIONS.forEach { restriction ->
            step("restrict:$restriction") { dpm.addUserRestriction(admin, restriction) }
        }

        // Strip the phone to the appliance: hide the stock launcher, the app stores and the
        // Google/consumer apps a head unit never opens. Reversed by releaseDevice().
        setBloatHidden(true)

        grantOwnPermissions()
    }

    /**
     * As Device Owner we can grant our own runtime permissions silently, so a rider
     * never sees a permission dialog on a locked-down phone.
     */
    private fun grantOwnPermissions() {
        if (!isDeviceOwner) return
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        permissions.forEach { permission ->
            step("grant:$permission") {
                dpm.setPermissionGrantState(
                    admin,
                    context.packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            }
        }
    }

    /**
     * Hide (or restore) the stock launcher, the app stores and the Google/consumer apps a
     * head unit never opens. setApplicationHidden disables and hides each for this user
     * WITHOUT uninstalling it, so the escape hatch can put everything back. Absent packages,
     * and any the platform refuses to hide, are skipped — the list is a superset across OEMs.
     *
     * Deliberately never touches SystemUI, Settings, the keyboard, WebView, PackageInstaller
     * or Play Services: the framework or the launcher itself depends on them.
     */
    private fun setBloatHidden(hidden: Boolean) {
        if (!isDeviceOwner) return
        BLOAT_PACKAGES.forEach { pkg ->
            step("${if (hidden) "hide" else "show"}:$pkg") {
                dpm.setApplicationHidden(admin, pkg, hidden)
            }
        }
    }

    // ---- radios ---------------------------------------------------------------

    /**
     * Force GPS, WiFi and Bluetooth on. Returns the names of the radios we could not
     * turn on ourselves, so the UI can ask the rider to do it once by hand.
     */
    fun enableRadios(): List<String> {
        val failed = mutableListOf<String>()

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifi.isWifiEnabled) {
            // Blocked for ordinary apps on Android 10+, still allowed for a Device Owner.
            val ok = runCatching { wifi.setWifiEnabled(true) }.getOrDefault(false)
            if (!ok) failed += "WiFi"
        }

        val bt = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bt != null && !bt.isEnabled) {
            val ok = runCatching { bt.enable() }.getOrDefault(false)
            if (!ok) failed += "Bluetooth"
        }

        if (!isLocationEnabled()) {
            val ok = when {
                // setLocationEnabled is API 30+; this phone is 9/10, so the realistic
                // path is the secure-setting write, which Android 10 rejects.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isDeviceOwner ->
                    runCatching { dpm.setLocationEnabled(admin, true); true }.getOrDefault(false)
                isDeviceOwner -> runCatching {
                    dpm.setSecureSetting(admin, Settings.Secure.LOCATION_MODE, "3"); true
                }.getOrDefault(false)
                else -> false
            }
            if (!ok || !isLocationEnabled()) failed += "GPS"
        }

        return failed
    }

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    // ---- lock task ------------------------------------------------------------

    fun startKiosk(activity: Activity) {
        if (!isDeviceOwner) return
        step("startLockTask") {
            if (!isInLockTask(activity)) activity.startLockTask()
        }
    }

    fun stopKiosk(activity: Activity) {
        step("stopLockTask") {
            if (isInLockTask(activity)) activity.stopLockTask()
        }
    }

    private fun isInLockTask(activity: Activity): Boolean {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE)
            as android.app.ActivityManager
        return am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
    }

    // ---- escape hatch ---------------------------------------------------------

    /**
     * Full release: hands the phone back. Undoes the restrictions, the persistent HOME
     * binding and finally Device Owner itself, which cannot be re-granted without
     * another factory reset — so the UI must confirm before calling this.
     */
    fun releaseDevice(activity: Activity?) {
        activity?.let { stopKiosk(it) }
        if (!isDeviceOwner) return

        setBloatHidden(false)
        RESTRICTIONS.forEach { step("clearRestriction:$it") { dpm.clearUserRestriction(admin, it) } }

        step("clearPersistentHome") {
            dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        }
        step("statusBarEnabled") { dpm.setStatusBarDisabled(admin, false) }
        step("keyguardEnabled") { dpm.setKeyguardDisabled(admin, false) }
        step("uninstallAllowed") { dpm.setUninstallBlocked(admin, context.packageName, false) }
        step("clearDeviceOwner") { dpm.clearDeviceOwnerApp(context.packageName) }
    }

    private inline fun step(name: String, body: () -> Unit) {
        try {
            body()
        } catch (e: Exception) {
            // Several of these throw on OEM-restricted builds. A refused policy should
            // never stop the remaining ones from being applied.
            Log.w(TAG, "policy '$name' refused: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "WarivoKiosk"

        // Applied as Device Owner, cleared on release. One list so the two paths can't drift.
        val RESTRICTIONS = listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_CONFIG_LOCATION,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_APPS_CONTROL,     // no force-stop / disable / clear-data in Settings
            UserManager.DISALLOW_MODIFY_ACCOUNTS,  // stays account-free, like the ROM
        )

        // Hidden for this user (not uninstalled). A superset across OEMs — absent names
        // no-op. Never list SystemUI / Settings / the keyboard / WebView / PackageInstaller /
        // Play Services here: the system or the launcher depends on them.
        val BLOAT_PACKAGES = listOf(
            // Stock launchers — Warivo is HOME now.
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.launcher",
            // App stores + install surfaces.
            "com.android.vending",
            // Google / consumer apps a scooter dashboard never opens.
            "com.android.chrome",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.maps",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.google.android.gm",
            "com.google.android.apps.photos",
            "com.google.android.apps.docs",
            "com.google.android.play.games",
            "com.google.android.videos",
            "com.google.android.apps.tachyon",
            "com.google.android.keep",
            "com.google.android.apps.wellbeing",
            // Stock phone apps with no place on a head unit.
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.android.gallery3d",
            "com.android.calendar",
            "com.google.android.calendar",
            "com.android.contacts",
            "com.google.android.contacts",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.email",
        )
    }
}
