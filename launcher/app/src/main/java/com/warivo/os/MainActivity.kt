package com.warivo.os

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.warivo.os.kiosk.KioskController
import com.warivo.os.location.GpsService
import com.warivo.os.settings.BeepSource
import com.warivo.os.ui.BootSplash
import com.warivo.os.ui.PinLockScreen
import com.warivo.os.ui.WarivoRoot
import com.warivo.os.ui.theme.WarivoTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The single activity, registered as CATEGORY_HOME. Everything the rider can reach is a
 * panel inside it — that is what makes this a launcher rather than a full-screen app.
 */
class MainActivity : ComponentActivity() {

    private lateinit var kiosk: KioskController

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onPermissionsSettled()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kiosk = KioskController(this)

        // A dashboard must stay lit while riding; the phone is permanently powered.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goFullScreen()

        kiosk.applyOwnerPolicies()

        setContent {
            WarivoTheme {
                // The power-on flow branding/README specifies: boot animation, then PIN
                // unlock, then home. The splash is not the Android boot animation (that
                // needs the ROM or root) — it covers the launcher's own start-up.
                var booting by remember { mutableStateOf(true) }
                var locked by remember { mutableStateOf(Warivo.settings.pinEnabled.value) }

                when {
                    booting -> BootSplash(onFinished = { booting = false })
                    locked -> PinLockScreen(onUnlocked = { locked = false })
                    else -> WarivoRoot(
                        kiosk = kiosk,
                        onExitKiosk = { kiosk.stopKiosk(this) },
                        onReleaseDevice = { kiosk.releaseDevice(this) },
                        onLock = { locked = true },
                    )
                }
            }
        }

        // Whatever the rider (or the Device Owner auto-grant) settles on, only start the
        // GPS uplink once location is actually permitted.
        requestPermissionsIfNeeded()

        // Keep the node's beep config in step with the settings panel.
        lifecycleScope.launch {
            combine(Warivo.settings.beepSource, Warivo.settings.beepCm) { source, cm ->
                source to cm
            }.collect { (source, cm) ->
                Warivo.node.writeConfig(beepEnabled = source == BeepSource.NODE, beepCm = cm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        kiosk.enableRadios()
        Warivo.node.start()
        if (Warivo.settings.kioskEnabled.value) kiosk.startKiosk(this)
    }

    override fun onResume() {
        super.onResume()
        goFullScreen()
    }

    override fun onStop() {
        Warivo.trips.flush()
        super.onStop()
    }

    override fun onDestroy() {
        // The BLE link is only needed while the head unit is up; GpsService keeps
        // running so a boot-time trip is still logged before the screen is touched.
        Warivo.node.stop()
        super.onDestroy()
    }

    /**
     * Pressing HOME on a launcher should return to the dashboard, never leave. With Lock
     * Task active the system already suppresses it; this covers the un-provisioned case.
     */
    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // Swallowed: the panel rail is the only navigation.
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_APP_SWITCH) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun requestPermissionsIfNeeded() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isEmpty()) onPermissionsSettled() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun onPermissionsSettled() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            GpsService.start(this)
            Warivo.node.start()
        }
        Warivo.music.refreshLibrary()
    }

    /** No status bar, no nav bar — and swiping does not bring them back. */
    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
