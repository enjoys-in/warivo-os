package com.warivo.os

import android.app.Application
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import com.warivo.os.alert.ProximityBeeper
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.music.MusicPlayer
import com.warivo.os.settings.WarivoSettings
import com.warivo.os.trip.TripLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

/**
 * Process-wide singletons.
 *
 * A plain service locator rather than a DI framework: there are five objects, they all
 * live as long as the process, and every dependency left out is one less thing to carry
 * into the Path B ROM build.
 */
object Warivo {
    lateinit var node: WarivoNodeClient
        private set
    lateinit var settings: WarivoSettings
        private set
    lateinit var trips: TripLog
        private set
    lateinit var music: MusicPlayer
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Radio state for the status bar. Polled rather than read during composition: the
    // status bar recomposes with every telemetry frame (5x/sec) and these are system
    // service calls, not fields.
    private val _gpsActive = MutableStateFlow(false)
    val gpsActive: StateFlow<Boolean> = _gpsActive.asStateFlow()

    private val _wifiActive = MutableStateFlow(false)
    val wifiActive: StateFlow<Boolean> = _wifiActive.asStateFlow()

    internal fun init(context: Context) {
        val app = context.applicationContext
        node = WarivoNodeClient(app)
        settings = WarivoSettings(app)
        trips = TripLog(app)
        music = MusicPlayer(app)

        // Trip totals must accrue whenever the node is connected, not only while the
        // dashboard panel happens to be on screen.
        scope.launch {
            node.telemetry.collect { telemetry -> telemetry?.let { trips.onTelemetry(it) } }
        }

        ProximityBeeper(node, settings).start(scope)

        scope.launch {
            val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            while (true) {
                _gpsActive.value = runCatching {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                }.getOrDefault(false)
                _wifiActive.value = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
                delay(5_000)
            }
        }
    }
}

class WarivoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must run before any MapView is created.
        MapLibre.getInstance(this)
        Warivo.init(this)
    }
}
