package com.warivo.os.trip

import android.content.Context
import com.warivo.os.model.Telemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Trip(
    val distanceKm: Double = 0.0,
    val movingMs: Long = 0L,
    val maxSpeedKmh: Float = 0f,
    val whUsed: Double = 0.0,
) {
    val avgSpeedKmh: Float
        get() = if (movingMs < 1_000) 0f else (distanceKm / (movingMs / 3_600_000.0)).toFloat()

    /** Wh/km — the number that actually predicts range on this scooter. */
    val consumptionWhPerKm: Float
        get() = if (distanceKm < 0.05) 0f else (whUsed / distanceKm).toFloat()
}

/**
 * Trip and lifetime totals, derived entirely from the telemetry stream so it works
 * with no signal.
 *
 * Distance comes from the node's odometer *delta* rather than integrating speed, so a
 * dropped BLE link loses nothing. The node's odometer resets when it reboots, which
 * shows up as a negative delta — that is treated as a restart, not as distance.
 */
class TripLog(context: Context) {

    private val prefs = context.getSharedPreferences("warivo_trip", Context.MODE_PRIVATE)

    private val _trip = MutableStateFlow(Trip())
    val trip: StateFlow<Trip> = _trip.asStateFlow()

    private val _lifetimeKm = MutableStateFlow(prefs.getFloat(KEY_LIFETIME_KM, 0f).toDouble())
    val lifetimeKm: StateFlow<Double> = _lifetimeKm.asStateFlow()

    private var lastOdoKm: Double? = null
    private var lastFrameMs: Long? = null

    fun onTelemetry(t: Telemetry) {
        val prevOdo = lastOdoKm
        val prevMs = lastFrameMs
        lastOdoKm = t.odoKm
        lastFrameMs = t.receivedAtMs

        if (prevOdo == null || prevMs == null) return

        val deltaKm = t.odoKm - prevOdo
        // Negative = the node rebooted and its odometer went back to zero. A implausibly
        // large jump is a garbled frame. Skip both rather than corrupting the totals.
        if (deltaKm < 0 || deltaKm > MAX_PLAUSIBLE_STEP_KM) return

        val dtMs = (t.receivedAtMs - prevMs).coerceIn(0L, 5_000L)
        val moving = t.speedKmh > 1f

        val current = _trip.value
        _trip.value = current.copy(
            distanceKm = current.distanceKm + deltaKm,
            movingMs = current.movingMs + if (moving) dtMs else 0L,
            maxSpeedKmh = maxOf(current.maxSpeedKmh, t.speedKmh),
            whUsed = current.whUsed + t.watts * (dtMs / 3_600_000.0),
        )

        if (deltaKm > 0) {
            val lifetime = _lifetimeKm.value + deltaKm
            _lifetimeKm.value = lifetime
            // Persist sparsely; this runs 5x/sec.
            if (lifetime - prefs.getFloat(KEY_LIFETIME_KM, 0f) > PERSIST_EVERY_KM) {
                prefs.edit().putFloat(KEY_LIFETIME_KM, lifetime.toFloat()).apply()
            }
        }
    }

    fun flush() {
        prefs.edit().putFloat(KEY_LIFETIME_KM, _lifetimeKm.value.toFloat()).apply()
    }

    fun resetTrip() {
        _trip.value = Trip()
        lastOdoKm = null
        lastFrameMs = null
    }

    private companion object {
        const val KEY_LIFETIME_KM = "lifetime_km"
        const val MAX_PLAUSIBLE_STEP_KM = 0.5   // 0.5 km in one 200ms frame is impossible
        const val PERSIST_EVERY_KM = 0.1
    }
}
