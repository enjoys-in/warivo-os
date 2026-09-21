package com.warivo.os

import android.app.Application
import android.content.Context
import com.warivo.os.alert.ProximityBeeper
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.music.MusicPlayer
import com.warivo.os.settings.WarivoSettings
import com.warivo.os.trip.TripLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

/**
 * Process-wide singletons.
 *
 * A plain service locator rather than a DI framework: there are four objects, they all
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

    internal fun init(context: Context) {
        node = WarivoNodeClient(context)
        settings = WarivoSettings(context)
        trips = TripLog(context)
        music = MusicPlayer(context)

        // Trip totals must accrue whenever the node is connected, not only while the
        // dashboard panel happens to be on screen.
        scope.launch {
            node.telemetry.collect { telemetry -> telemetry?.let { trips.onTelemetry(it) } }
        }

        ProximityBeeper(node, settings).start(scope)
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
