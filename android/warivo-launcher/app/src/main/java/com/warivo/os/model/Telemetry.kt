package com.warivo.os.model

import org.json.JSONObject

/**
 * One telemetry frame from the Warivo Node, pushed on characteristic fff1 ~5x/sec.
 *
 * The firmware's sentinels are normalised away here: temperature -127 and distance -1
 * both mean "no sensor", and become null.
 *
 * The throttle/gear/switch fields come from the signal-tap audit and are not in the
 * firmware yet, so they stay null until it starts sending them. The dashboard renders
 * them only when present, which means no app change is needed when they land.
 */
data class Telemetry(
    val volts: Float,
    val soc: Int,
    val speedKmh: Float,
    val odoKm: Double,
    val amps: Float,
    val watts: Float,
    val rangeKm: Float,
    val tempOutC: Float?,
    val tempBatC: Float?,
    val distCm: Float?,
    val nodeUptimeMs: Long,
    val throttlePct: Int? = null,
    val gear: Int? = null,
    val reverse: Boolean? = null,
    val headlight: Boolean? = null,
    val highBeam: Boolean? = null,
    val parkingLight: Boolean? = null,
    val indLeft: Boolean? = null,
    val indRight: Boolean? = null,
    val receivedAtMs: Long = System.currentTimeMillis(),
) {
    /** True once the node reports any of the audit's switch/gear/throttle taps. */
    val hasSwitchSignals: Boolean
        get() = gear != null || throttlePct != null || reverse != null ||
            headlight != null || highBeam != null || parkingLight != null ||
            indLeft != null || indRight != null

    companion object {
        private const val NO_TEMP = -100f   // firmware sends -127 when unwired
        private const val NO_DIST = 0f      // firmware sends -1 when unwired

        /** Returns null for a malformed or truncated frame rather than throwing. */
        fun parse(json: String): Telemetry? = try {
            val o = JSONObject(json)
            fun f(k: String, d: Float = 0f) = o.optDouble(k, d.toDouble()).toFloat()
            fun boolOrNull(k: String) = if (o.has(k)) o.optInt(k, 0) != 0 else null
            fun intOrNull(k: String) = if (o.has(k)) o.optInt(k, 0) else null

            val tOut = f("tout", -127f)
            val tBat = f("tbat", -127f)
            val dist = f("dist", -1f)

            Telemetry(
                volts = f("v"),
                soc = o.optInt("soc", 0),
                speedKmh = f("spd"),
                odoKm = o.optDouble("odo", 0.0),
                amps = f("a"),
                watts = f("w"),
                rangeKm = f("rng"),
                tempOutC = tOut.takeIf { it > NO_TEMP },
                tempBatC = tBat.takeIf { it > NO_TEMP },
                distCm = dist.takeIf { it > NO_DIST },
                nodeUptimeMs = o.optLong("up", 0L),
                throttlePct = intOrNull("thr"),
                gear = intOrNull("gear"),
                reverse = boolOrNull("rev"),
                headlight = boolOrNull("head"),
                highBeam = boolOrNull("high"),
                parkingLight = boolOrNull("park"),
                indLeft = boolOrNull("left"),
                indRight = boolOrNull("right"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
