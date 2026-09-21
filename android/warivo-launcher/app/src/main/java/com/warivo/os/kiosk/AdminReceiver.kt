package com.warivo.os.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Owner admin component. Provision once on a factory-reset phone with no accounts:
 *
 *   adb shell dpm set-device-owner com.warivo.os/.kiosk.AdminReceiver
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "device admin enabled")
        // Apply the launcher policies immediately so a fresh provisioning already
        // lands in the right state, before the UI is ever opened.
        runCatching { KioskController(context).applyOwnerPolicies() }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "device admin disabled")
    }

    private companion object {
        const val TAG = "WarivoAdmin"
    }
}
