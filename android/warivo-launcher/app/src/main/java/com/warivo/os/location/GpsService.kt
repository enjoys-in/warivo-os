@file:Suppress("MissingPermission")

package com.warivo.os.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.warivo.os.R
import com.warivo.os.Warivo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps a GPS fix alive and pushes it to the node on fff2.
 *
 * Uses the platform [LocationManager], not FusedLocationProvider: Path B's custom ROM
 * ships without GApps, so anything depending on Play Services would break there.
 *
 * A foreground service is what stops Android 9/10 from throttling location updates once
 * the screen sleeps.
 */
class GpsService : Service() {

    private lateinit var locationManager: LocationManager

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastFix.value = location
            Warivo.node.writeGps(
                lat = location.latitude,
                lon = location.longitude,
                speedKmh = location.speed * 3.6f,
                epochSeconds = location.time / 1000,
            )
        }

        // Required on API < 30, where the default implementations do not exist.
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startForeground(NOTIFICATION_ID, buildNotification())
        requestUpdates()
    }

    private fun requestUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "no location permission; stopping")
            stopSelf()
            return
        }
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            runCatching {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider, MIN_INTERVAL_MS, MIN_DISTANCE_M, listener, mainLooper
                    )
                }
            }.onFailure { Log.w(TAG, "provider $provider unavailable: ${it.message}") }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(listener) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // IMPORTANCE_MIN: the kiosk hides the shade anyway, and this must never
        // interrupt the dashboard.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.gps_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        )
        nm.createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.gps_notification_title))
            .setContentText(getString(R.string.gps_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WarivoGps"
        private const val CHANNEL_ID = "warivo_gps"
        private const val NOTIFICATION_ID = 1
        private const val MIN_INTERVAL_MS = 1_000L
        private const val MIN_DISTANCE_M = 0f

        /** The phone's last known fix, for the map panel. */
        private val lastFix = MutableStateFlow<Location?>(null)
        val fix: StateFlow<Location?> = lastFix.asStateFlow()

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, GpsService::class.java))
            }.onFailure { Log.w(TAG, "could not start: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GpsService::class.java)) }
        }
    }
}
