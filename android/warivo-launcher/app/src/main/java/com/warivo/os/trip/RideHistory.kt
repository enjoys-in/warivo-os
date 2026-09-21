package com.warivo.os.trip

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One completed ride. */
data class Ride(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceKm: Double,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val whUsed: Double,
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0)

    val consumptionWhPerKm: Float
        get() = if (distanceKm < 0.05) 0f else (whUsed / distanceKm).toFloat()
}

/**
 * The offline ride log: the last [MAX_RIDES] completed rides.
 *
 * Stored as JSON in SharedPreferences rather than Room. It is a short, append-mostly list
 * read all at once, so a database would add a dependency, a schema and a migration story
 * to solve a problem we do not have — and every dependency left out is one less thing to
 * carry into the Path B ROM.
 *
 * Rides are only kept if they are long enough to be a ride at all; see [MIN_DISTANCE_KM].
 */
class RideHistory(context: Context) {

    private val prefs = context.getSharedPreferences("warivo_rides", Context.MODE_PRIVATE)

    private val _rides = MutableStateFlow(read())
    val rides: StateFlow<List<Ride>> = _rides.asStateFlow()

    /** Returns true if the ride was worth keeping. */
    fun record(ride: Ride): Boolean {
        if (ride.distanceKm < MIN_DISTANCE_KM) return false
        // Newest first, so the UI never has to reverse it.
        _rides.value = (listOf(ride) + _rides.value).take(MAX_RIDES)
        write()
        return true
    }

    fun clear() {
        _rides.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun read(): List<Ride> = try {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Ride(
                startedAtMs = o.optLong("s"),
                endedAtMs = o.optLong("e"),
                distanceKm = o.optDouble("d"),
                avgSpeedKmh = o.optDouble("a").toFloat(),
                maxSpeedKmh = o.optDouble("m").toFloat(),
                whUsed = o.optDouble("wh"),
            )
        }
    } catch (e: Exception) {
        // A corrupt log is not worth crashing the dashboard over; start a fresh one.
        Log.w(TAG, "ride log unreadable, discarding: ${e.message}")
        emptyList()
    }

    private fun write() {
        val array = JSONArray()
        _rides.value.forEach { ride ->
            array.put(
                JSONObject()
                    .put("s", ride.startedAtMs)
                    .put("e", ride.endedAtMs)
                    .put("d", ride.distanceKm)
                    .put("a", ride.avgSpeedKmh.toDouble())
                    .put("m", ride.maxSpeedKmh.toDouble())
                    .put("wh", ride.whUsed)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val TAG = "WarivoRides"
        const val KEY = "rides"
        const val MAX_RIDES = 20
        const val MIN_DISTANCE_KM = 0.2
    }
}
