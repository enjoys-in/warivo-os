package com.warivo.os

import android.app.Application
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import com.warivo.os.alert.ProximityBeeper
import com.warivo.os.alert.SpeedChime
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.fleet.FleetClient
import com.warivo.os.fleet.FleetCommand
import com.warivo.os.fleet.FleetConfig
import com.warivo.os.fleet.FleetSpool
import com.warivo.os.fleet.FleetUplink
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
 * A plain service locator rather than a DI framework: a handful of objects that all live
 * as long as the process, and every dependency left out is one less thing to carry into
 * the Path B ROM build.
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
    lateinit var fleet: FleetConfig
        private set
    lateinit var uplink: FleetUplink
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
        fleet = FleetConfig(app)
        uplink = FleetUplink(
            config = fleet,
            spool = FleetSpool(app),
            client = FleetClient(),
            node = node,
            settings = settings,
            rides = trips.history,
        )

        // Trip totals must accrue whenever the node is connected, not only while the
        // dashboard panel happens to be on screen.
        scope.launch {
            node.telemetry.collect { telemetry -> telemetry?.let { trips.onTelemetry(it) } }
        }

        val beeper = ProximityBeeper(node, settings)
        beeper.start(scope)
        SpeedChime(node, settings).start(scope)

        // Reporting is off until an endpoint is configured; see docs/FLEET.md §1.
        uplink.start(scope)
        scope.launch {
            uplink.commands.collect { command ->
                when (command) {
                    // The owner's alarm borrows the proximity chime rather than adding a
                    // second sound path; it is already routed to the paired speaker.
                    FleetCommand.Alarm -> beeper.sound(scope)
                    FleetCommand.Ping -> Unit   // the next tick uploads within seconds
                }
            }
        }

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
