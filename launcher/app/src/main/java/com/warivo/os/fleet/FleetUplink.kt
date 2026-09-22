package com.warivo.os.fleet

import android.location.Location
import android.util.Log
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.location.GpsService
import com.warivo.os.model.Telemetry
import com.warivo.os.settings.WarivoSettings
import com.warivo.os.trip.RideHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The uplink loop: sample, spool, send, apply what comes back.
 *
 * One request per interval carries telemetry up and config and commands down
 * (docs/FLEET.md §2). A scooter moves through patchy coverage, so every avoidable request
 * costs battery and reliability — polling separately for commands would double the traffic
 * to learn nothing most of the time.
 *
 * Sampling continues whether or not the network is there; the spool is what makes a ride
 * through a dead zone appear on the owner's map afterwards rather than vanishing.
 */
class FleetUplink(
    private val config: FleetConfig,
    private val spool: FleetSpool,
    private val client: FleetClient,
    private val node: WarivoNodeClient,
    private val settings: WarivoSettings,
    private val rides: RideHistory,
) {
    private val alerts = FleetAlerts()

    /** Commands the loop cannot apply itself, e.g. sounding the alarm. */
    private val _commands = MutableSharedFlow<FleetCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<FleetCommand> = _commands.asSharedFlow()

    private val pendingAcks = mutableSetOf<String>()
    private var uploadedRideCount = 0
    private var backoffS = 0

    fun start(scope: CoroutineScope) {
        scope.launch {
            spool.addEvent(alerts.powerOnEvent(System.currentTimeMillis()))
            while (true) {
                val interval = config.intervalS.value
                if (!config.configured) {
                    config.setState(UplinkState.OFF)
                    delay(POLL_WHEN_OFF_MS)
                    continue
                }
                runCatching { tick() }.onFailure {
                    Log.w(TAG, "tick failed: ${it.javaClass.simpleName}: ${it.message}")
                }
                // Back off on network failure so a dead endpoint does not retry every
                // 10 s all day; the spool is holding the data anyway.
                delay((interval + backoffS).toLong() * 1000L)
            }
        }
    }

    private suspend fun tick() {
        val nowMs = System.currentTimeMillis()
        val telemetry = node.telemetry.value
        val fix = GpsService.fix.value

        // A sample with neither a fix nor telemetry says nothing; skip it rather than
        // filling the spool with empty rows.
        if (fix != null || telemetry != null) {
            spool.addSample(sample(nowMs, fix, telemetry))
        }

        alerts.evaluate(
            telemetry = telemetry,
            fix = fix,
            speedLimitKmh = settings.speedAlertKmh.value,
            batteryPct = config.batteryAlertPct.value,
            zones = config.zones.value,
            nowMs = nowMs,
        ).forEach { spool.addEvent(it) }

        spoolNewRides()

        val samples = spool.samples(BATCH_SAMPLES)
        val events = spool.events(BATCH_EVENTS)
        val rideLines = spool.rides(BATCH_RIDES)
        if (samples.isEmpty() && events.isEmpty() && rideLines.isEmpty() && pendingAcks.isEmpty()) {
            config.setState(UplinkState.IDLE)
            return
        }

        config.setState(UplinkState.SENDING)
        val body = JSONObject()
            .put("device_id", config.deviceId)
            .put("sent_at", nowMs / 1000)
            .put("app", APP_VERSION)
            .put("config_version", config.configVersion.value)
            .put("samples", spool.toArray(samples))
            .put("events", spool.toArray(events))
            .put("rides", spool.toArray(rideLines))
            .put("acks", JSONArray().apply { pendingAcks.forEach { put(it) } })

        when (val result = client.ingest(config.endpoint.value, config.token.value, body)) {
            is IngestResult.Ok -> {
                // Only drop what we actually sent: samples keep arriving while a request
                // is in flight, and clearing the spool would discard them.
                spool.drop(samples.size, events.size, rideLines.size)
                pendingAcks.clear()
                backoffS = 0
                config.noteUploadOk(nowMs)
                config.setState(UplinkState.IDLE)
                applyResponse(result.body)
            }
            is IngestResult.Unreachable -> {
                config.setState(UplinkState.OFFLINE)
                backoffS = if (backoffS == 0) 15 else (backoffS * 2).coerceAtMost(MAX_BACKOFF_S)
                Log.d(TAG, "offline (${result.reason}); backing off ${backoffS}s")
            }
            is IngestResult.Rejected -> {
                // The server answered and refused. Resending the same bytes will not help,
                // so drop this batch rather than wedging the spool behind it forever.
                config.setState(UplinkState.REJECTED)
                spool.drop(samples.size, events.size, rideLines.size)
                pendingAcks.clear()
                backoffS = MAX_BACKOFF_S
                Log.w(TAG, "rejected: HTTP ${result.code} ${result.reason}")
            }
        }
    }

    /** Uploads completed rides once each, so "where has it been" survives a server wipe. */
    private fun spoolNewRides() {
        val all = rides.rides.value
        if (all.size <= uploadedRideCount) {
            // The list is newest-first and capped, so a shrink means it was cleared.
            uploadedRideCount = all.size
            return
        }
        all.take(all.size - uploadedRideCount).forEach { ride ->
            spool.addRide(
                JSONObject()
                    .put("started", ride.startedAtMs / 1000)
                    .put("ended", ride.endedAtMs / 1000)
                    .put("km", ride.distanceKm)
                    .put("avg", ride.avgSpeedKmh.toDouble())
                    .put("max", ride.maxSpeedKmh.toDouble())
                    .put("wh", ride.whUsed)
            )
        }
        uploadedRideCount = all.size
    }

    private suspend fun applyResponse(body: JSONObject) {
        val version = body.optInt("config_version", 0)
        body.optJSONObject("config")?.let { remote ->
            if (config.applyRemoteConfig(version, remote)) {
                // Mirror the thresholds the rider can also see, so the on-device chime and
                // the uploaded alert share one number instead of drifting apart.
                if (remote.has("speed_alert_kmh")) {
                    settings.setSpeedAlertKmh(remote.optInt("speed_alert_kmh"))
                    settings.setSpeedAlertOn(true)
                }
                Log.i(TAG, "applied config v$version")
            }
        }

        val commands = body.optJSONArray("commands") ?: return
        for (i in 0 until commands.length()) {
            val o = commands.optJSONObject(i) ?: continue
            // `continue` cannot cross an inline lambda boundary in Kotlin, so this is
            // spelled out rather than folded into ifBlank {}.
            val id = o.optString("id")
            if (id.isBlank()) continue
            // Acknowledge even an unknown verb: an unacknowledged command is re-sent
            // forever, and a server newer than this app would jam the queue.
            pendingAcks += id
            when (o.optString("type")) {
                "lock_head_unit" -> config.setLockMessage(
                    o.optString("message").ifBlank { DEFAULT_LOCK_MESSAGE }
                )
                "unlock_head_unit" -> config.setLockMessage(null)
                "alarm" -> _commands.tryEmit(FleetCommand.Alarm)
                "ping" -> _commands.tryEmit(FleetCommand.Ping)
                // The node owns the safety interlock: this only forwards the request, and
                // engaging waits for the wheel to stop. writeLock returns false when the
                // node exposes no fff4 — no relay, or firmware predating it.
                "immobilise", "immobilize" -> {
                    val sent = node.writeLock(true)
                    if (!sent) Log.w(TAG, "immobilise requested but the node has no fff4")
                }
                "release" -> node.writeLock(false)
                "apply_config" -> Unit          // already applied above
                else -> Log.i(TAG, "ignoring unknown command '${o.optString("type")}'")
            }
        }
    }

    private fun sample(
        nowMs: Long,
        fix: Location?,
        telemetry: Telemetry?,
    ): JSONObject = JSONObject().apply {
        put("ts", nowMs / 1000)
        fix?.let {
            put("lat", it.latitude)
            put("lon", it.longitude)
            put("acc", it.accuracy.toDouble())
        }
        telemetry?.let {
            put("spd", it.speedKmh.toDouble())
            put("soc", it.soc)
            put("v", it.volts.toDouble())
            put("odo", it.odoKm)
            put("w", it.watts.toDouble())
            // Per-battery temperature, spelled out. The BLE frame has to fit one
            // notification so it ships a terse `tb` array; this is HTTP, batched and
            // gzip-friendly, so the field names can say what they mean — and a fleet
            // operator querying "which scooters have a battery over 55 C" should not
            // have to know that battery 3 is index 2.
            it.batteryTempsC.forEachIndexed { index, temp ->
                temp?.let { c -> put("battery${index + 1}_temp", c.toDouble()) }
            }
            it.tempBatC?.let { c -> put("battery_temp", c.toDouble()) }
            it.tempOutC?.let { c -> put("ambient_temp", c.toDouble()) }
            // Reported so the owner app can show the real state rather than assuming a
            // queued command took effect.
            it.immobilised?.let { locked -> put("lock", if (locked) 1 else 0) }
        }
    }

    private companion object {
        const val TAG = "WarivoFleet"
        const val APP_VERSION = "0.1"
        const val BATCH_SAMPLES = 200
        const val BATCH_EVENTS = 100
        const val BATCH_RIDES = 20
        const val MAX_BACKOFF_S = 300
        const val POLL_WHEN_OFF_MS = 5_000L
        const val DEFAULT_LOCK_MESSAGE = "This scooter has been locked by its owner."
    }
}

/** Commands the UI or an actuator has to carry out, rather than the loop itself. */
sealed interface FleetCommand {
    data object Alarm : FleetCommand
    data object Ping : FleetCommand
}
