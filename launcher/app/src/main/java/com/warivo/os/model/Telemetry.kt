package com.warivo.os.model

import org.json.JSONObject

/**
 * One telemetry frame from the Warivo Node, pushed on characteristic fff1 ~5x/sec.
 *
 * The firmware's sentinels are normalised away here: temperature -127 and distance -1
 * both mean "no sensor", and become null.
 *
 * All temperatures arrive as whole degrees. A DS18B20 is ±0.5 °C and every screen renders
 * them with toInt(), so the node does not spend frame bytes on a decimal nobody displays.
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
    /** The pack figure: the hottest battery, or the BMS's reading when there is one. */
    val tempBatC: Float?,
    /**
     * One temperature per 12V battery, battery 1 first, from the node's `tb` array.
     *
     * Empty when no per-battery probe is fitted; an entry is null when that one probe has
     * stopped answering while the others still do. Those two cases are genuinely
     * different — "this scooter has no per-battery sensing" versus "probe 3 has failed" —
     * and the UI must not render the second as a comfortable 0 °C.
     */
    val batteryTempsC: List<Float?> = emptyList(),
    val distCm: Float?,
    val nodeUptimeMs: Long,
    /** Speed cap the selected gear enforces, from `glim`. Null when no gear switch. */
    val gearLimitKmh: Float? = null,
    /** Trip average speed as the node counts it — survives a dropped BLE link. */
    val nodeAvgSpeedKmh: Float? = null,
    /** Measured consumption, from `whkm`. */
    val nodeWhPerKm: Float? = null,
    /** Projected km on a full charge at this ride's efficiency, from `mil`. */
    val mileageKm: Float? = null,
    /** Equivalent full charge cycles the node has counted, from `cyc`. */
    val chargeCycles: Int? = null,
    /**
     * True when the immobiliser relay is actually open — the controller is disabled.
     * Null when the node reports no relay at all.
     */
    val immobilised: Boolean? = null,
    /**
     * True when an engage has been asked for but the node is still waiting for the wheel
     * to stop. Distinct from [immobilised] on purpose: while the scooter is rolling the
     * request is pending and the scooter still rides, and a UI that says "locked" then is
     * lying about a safety-relevant state.
     */
    val immobiliseQueued: Boolean = false,
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
    /** The hottest battery's temperature, and its 1-based number. Null with no probes. */
    val hottestBatteryC: Float?
        get() = batteryTempsC.filterNotNull().maxOrNull()

    val hottestBattery: Int?
        get() {
            val hottest = hottestBatteryC ?: return null
            return batteryTempsC.indexOfFirst { it == hottest }.takeIf { it >= 0 }?.plus(1)
        }

    /**
     * Spread between the hottest and coldest battery.
     *
     * This is the number that earns the five probes. Five batteries in series all carry
     * the same current, so in a healthy pack they sit within a couple of degrees of each
     * other; a battery going high-resistance dissipates the difference as heat and pulls
     * away from the rest long before the pack voltage admits anything is wrong. Null
     * until at least two probes are reporting, because a spread of one is not a spread.
     */
    val batterySpreadC: Float?
        get() {
            val readings = batteryTempsC.filterNotNull()
            if (readings.size < 2) return null
            val hottest = readings.maxOrNull() ?: return null
            val coldest = readings.minOrNull() ?: return null
            return hottest - coldest
        }

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
            // `tb` is absent entirely when no per-battery probe is fitted, which is not
            // the same as five probes all reading -127: one means "this scooter has no
            // per-battery sensing", the other means "five probes have failed".
            val packTemps = o.optJSONArray("tb")?.let { arr ->
                (0 until arr.length()).map { i ->
                    arr.optDouble(i, -127.0).toFloat().takeIf { it > NO_TEMP }
                }
            } ?: emptyList()

            Telemetry(
                volts = f("v"),
                soc = o.optInt("soc", 0),
                speedKmh = f("spd"),
                odoKm = o.optDouble("odo", 0.0),
                amps = f("a"),
                watts = f("w"),
                rangeKm = f("rng"),
                tempOutC = tOut.takeIf { it > NO_TEMP },
                // Fall back to the hottest probe if a node ever sends `tb` without
                // `tbat`; they are the same number unless a BMS supplied the pack figure.
                tempBatC = tBat.takeIf { it > NO_TEMP }
                    ?: packTemps.filterNotNull().maxOrNull(),
                batteryTempsC = packTemps,
                distCm = dist.takeIf { it > NO_DIST },
                nodeUptimeMs = o.optLong("up", 0L),
                // The node sends these unconditionally, using 0 for "not fitted" or
                // "not known yet". Mapping 0 to null matters: the dashboard treats a
                // present gear as a wired gear switch, so a literal 0 would light the
                // ride-mode segment as "Eco" on a scooter that has no switch at all.
                gearLimitKmh = f("glim").takeIf { it > 0f },
                nodeAvgSpeedKmh = f("avg", -1f).takeIf { it >= 0f },
                nodeWhPerKm = f("whkm").takeIf { it > 0f },
                mileageKm = f("mil").takeIf { it > 0f },
                chargeCycles = intOrNull("cyc"),
                immobilised = boolOrNull("lock"),
                immobiliseQueued = o.optInt("lockq", 0) != 0,
                throttlePct = intOrNull("thr"),
                gear = intOrNull("gear")?.takeIf { it in 1..3 },
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
