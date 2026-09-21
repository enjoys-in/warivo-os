package com.warivo.os.fleet

import android.location.Location
import com.warivo.os.model.Telemetry
import org.json.JSONObject

/**
 * Turns the live state into alert events.
 *
 * Evaluated **on the device** so alerts still fire with no coverage — they spool and
 * arrive late with their original timestamp, which is the whole point of an alert about a
 * scooter that has gone somewhere it should not.
 *
 * Every alert is **edge-triggered with hysteresis**. A level-triggered speed alert hovering
 * at the threshold would emit an event every sample and turn the owner's phone into a slot
 * machine; each one here fires once on crossing and rearms only after the value comes back
 * past a margin.
 */
class FleetAlerts {

    private var speedArmed = true
    private var batteryArmed = true
    private var wasInsideZone: Boolean? = null

    /** Returns the events to spool for this tick. */
    fun evaluate(
        telemetry: Telemetry?,
        fix: Location?,
        speedLimitKmh: Int,
        batteryPct: Int,
        zones: List<Zone>,
        nowMs: Long,
    ): List<JSONObject> {
        val events = mutableListOf<JSONObject>()

        telemetry?.let { t ->
            if (speedArmed && t.speedKmh >= speedLimitKmh) {
                events += event(nowMs, "speed") {
                    put("kmh", t.speedKmh.toInt()); put("limit", speedLimitKmh)
                }
                speedArmed = false
            } else if (!speedArmed && t.speedKmh < speedLimitKmh - SPEED_HYSTERESIS_KMH) {
                speedArmed = true
            }

            if (batteryArmed && t.soc <= batteryPct) {
                events += event(nowMs, "battery") {
                    put("soc", t.soc); put("threshold", batteryPct)
                }
                batteryArmed = false
            } else if (!batteryArmed && t.soc > batteryPct + BATTERY_HYSTERESIS_PCT) {
                batteryArmed = true
            }
        }

        // No fix is *unknown*, not *outside*. Treating it as outside would fire an exit
        // alert every time the scooter parks in a basement.
        if (fix != null && zones.isNotEmpty()) {
            val inside = zones.any { zone -> within(fix, zone) }
            val previously = wasInsideZone
            if (previously != null && previously != inside) {
                events += event(nowMs, if (inside) "zone_enter" else "zone_exit") {
                    put("lat", fix.latitude)
                    put("lon", fix.longitude)
                    zones.minByOrNull { distanceTo(fix, it) }?.let { put("zone", it.id) }
                }
            }
            wasInsideZone = inside
        }

        return events
    }

    /** Emitted once when the head unit starts — a useful theft signal at 3am. */
    fun powerOnEvent(nowMs: Long): JSONObject = event(nowMs, "power_on") {}

    private fun within(fix: Location, zone: Zone): Boolean =
        distanceTo(fix, zone) <= zone.radiusM

    private fun distanceTo(fix: Location, zone: Zone): Double {
        val out = FloatArray(1)
        Location.distanceBetween(fix.latitude, fix.longitude, zone.lat, zone.lon, out)
        return out[0].toDouble()
    }

    private inline fun event(
        nowMs: Long,
        type: String,
        detail: JSONObject.() -> Unit,
    ): JSONObject = JSONObject()
        .put("ts", nowMs / 1000)
        .put("type", type)
        .put("detail", JSONObject().apply(detail))

    private companion object {
        const val SPEED_HYSTERESIS_KMH = 3
        const val BATTERY_HYSTERESIS_PCT = 5
    }
}
