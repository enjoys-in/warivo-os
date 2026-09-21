package com.warivo.companion.model

import org.json.JSONObject

/** A scooter the owner can see. */
data class Device(
    val deviceId: String,
    val name: String,
    val lastSeenMs: Long,
    val online: Boolean,
) {
    companion object {
        fun from(o: JSONObject) = Device(
            deviceId = o.optString("device_id"),
            name = o.optString("name").ifBlank { o.optString("device_id") },
            lastSeenMs = o.optLong("last_seen") * 1000,
            online = o.optBoolean("online", false),
        )
    }
}

/**
 * One position/telemetry sample.
 *
 * The same shape the head unit writes (docs/FLEET.md §2) — one schema for both directions,
 * so a sample the scooter sent is the object the app reads.
 */
data class Sample(
    val tsMs: Long,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Float?,
    val speedKmh: Float?,
    val soc: Int?,
    val volts: Float?,
    val odoKm: Double?,
    val watts: Float?,
) {
    val hasPosition: Boolean get() = lat != null && lon != null

    companion object {
        fun from(o: JSONObject) = Sample(
            tsMs = o.optLong("ts") * 1000,
            lat = o.optDoubleOrNull("lat"),
            lon = o.optDoubleOrNull("lon"),
            accuracyM = o.optDoubleOrNull("acc")?.toFloat(),
            speedKmh = o.optDoubleOrNull("spd")?.toFloat(),
            soc = if (o.has("soc")) o.optInt("soc") else null,
            volts = o.optDoubleOrNull("v")?.toFloat(),
            odoKm = o.optDoubleOrNull("odo"),
            watts = o.optDoubleOrNull("w")?.toFloat(),
        )
    }
}

/** A completed ride, as the head unit logged it. */
data class RideSummary(
    val startedMs: Long,
    val endedMs: Long,
    val km: Double,
    val avgKmh: Float,
    val maxKmh: Float,
    val wh: Double,
) {
    companion object {
        fun from(o: JSONObject) = RideSummary(
            startedMs = o.optLong("started") * 1000,
            endedMs = o.optLong("ended") * 1000,
            km = o.optDouble("km", 0.0),
            avgKmh = o.optDouble("avg", 0.0).toFloat(),
            maxKmh = o.optDouble("max", 0.0).toFloat(),
            wh = o.optDouble("wh", 0.0),
        )
    }
}

/** An alert the device raised. */
data class AlertEvent(
    val tsMs: Long,
    val type: String,
    val detail: JSONObject,
) {
    companion object {
        fun from(o: JSONObject) = AlertEvent(
            tsMs = o.optLong("ts") * 1000,
            type = o.optString("type"),
            detail = o.optJSONObject("detail") ?: JSONObject(),
        )
    }
}

/** A geofence. */
data class Zone(val id: String, val lat: Double, val lon: Double, val radiusM: Double)

/** The config the owner controls, as the device will receive it. */
data class FleetSettings(
    val intervalS: Int = 10,
    val speedAlertKmh: Int = 65,
    val batteryAlertPct: Int = 20,
    val zones: List<Zone> = emptyList(),
) {
    companion object {
        fun from(o: JSONObject): FleetSettings {
            val zones = mutableListOf<Zone>()
            o.optJSONArray("zones")?.let { array ->
                for (i in 0 until array.length()) {
                    val z = array.optJSONObject(i) ?: continue
                    zones += Zone(
                        z.optString("id").ifBlank { "zone-$i" },
                        z.optDouble("lat"),
                        z.optDouble("lon"),
                        z.optDouble("radius_m"),
                    )
                }
            }
            return FleetSettings(
                intervalS = o.optInt("interval_s", 10),
                speedAlertKmh = o.optInt("speed_alert_kmh", 65),
                batteryAlertPct = o.optInt("battery_alert_pct", 20),
                zones = zones,
            )
        }
    }
}

/**
 * `optDouble` cannot express "absent": it returns NaN, and a sample legitimately has no
 * position when the scooter was underground. This keeps absent and zero distinct, which
 * matters because 0,0 is a real place in the Gulf of Guinea.
 */
internal fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return if (value.isNaN()) null else value
}
