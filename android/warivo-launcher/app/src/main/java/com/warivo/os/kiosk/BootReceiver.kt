package com.warivo.os.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.warivo.os.location.GpsService

/**
 * As HOME the activity is started by the system at boot, but the GPS uplink and the
 * owner policies should not wait for the screen to be looked at.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val kiosk = KioskController(context)
        kiosk.applyOwnerPolicies()
        kiosk.enableRadios()
        GpsService.start(context)
    }
}
