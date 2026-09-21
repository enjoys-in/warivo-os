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

    /** Completed rides, segmented out of the same telemetry stream. */
    val history = RideHistory(context)

    private var rideStartedAtMs: Long? = null
    private var lastMovingAtMs: Long = 0L

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

        segmentRide(moving, t.receivedAtMs)

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

    /**
     * Splits the stream into rides.
     *
     * A ride starts the first time the wheel turns and ends after [RIDE_IDLE_END_MS] of
     * not turning — long enough that a traffic light or a shop stop stays one ride, short
     * enough that leaving the scooter parked closes it. The node's odometer keeps the
     * distance honest across the gap either way.
     */
    private fun segmentRide(moving: Boolean, nowMs: Long) {
        if (moving) {
            if (rideStartedAtMs == null) {
                rideStartedAtMs = nowMs
                _trip.value = Trip()          // a new ride starts a fresh trip readout
            }
            lastMovingAtMs = nowMs
            return
        }
        val startedAt = rideStartedAtMs ?: return
        if (nowMs - lastMovingAtMs < RIDE_IDLE_END_MS) return
        closeRide(startedAt, lastMovingAtMs)
    }

    private fun closeRide(startedAtMs: Long, endedAtMs: Long) {
        val trip = _trip.value
        history.record(
            Ride(
                startedAtMs = startedAtMs,
                endedAtMs = endedAtMs,
                distanceKm = trip.distanceKm,
                avgSpeedKmh = trip.avgSpeedKmh,
                maxSpeedKmh = trip.maxSpeedKmh,
                whUsed = trip.whUsed,
            )
        )
        rideStartedAtMs = null
    }

    fun flush() {
        prefs.edit().putFloat(KEY_LIFETIME_KM, _lifetimeKm.value.toFloat()).apply()
        // Close an in-progress ride rather than losing it if the process goes away.
        rideStartedAtMs?.let { closeRide(it, lastMovingAtMs) }
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
        const val RIDE_IDLE_END_MS = 3 * 60 * 1000L
    }
}
