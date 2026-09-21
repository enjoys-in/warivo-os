package com.warivo.companion.net

import android.util.Log
import com.warivo.companion.model.AlertEvent
import com.warivo.companion.model.Device
import com.warivo.companion.model.FleetSettings
import com.warivo.companion.model.RideSummary
import com.warivo.companion.model.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** Success, or a reason a human can act on. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failed(val message: String) : ApiResult<Nothing>
}

/**
 * The owner-facing client for the fleet server (docs/FLEET.md §6).
 *
 * `HttpURLConnection` and `org.json`, no Retrofit or serialization plugin: seven endpoints
 * returning shapes that are already defined by the device protocol. A client library would
 * be more code to configure than to replace.
 */
class OwnerApi(
    private val baseUrl: () -> String,
    private val token: () -> String,
) {
    suspend fun devices(): ApiResult<List<Device>> =
        get("/v1/devices") { body ->
            body.optJSONArray("devices").mapObjects(Device::from)
        }

    suspend fun latest(deviceId: String): ApiResult<Sample?> =
        get("/v1/devices/$deviceId/latest") { body ->
            // A brand-new device has no samples; that is not an error.
            if (body.length() == 0) null else Sample.from(body)
        }

    suspend fun track(deviceId: String, fromMs: Long, toMs: Long): ApiResult<List<Sample>> =
        get("/v1/devices/$deviceId/track?from=${fromMs / 1000}&to=${toMs / 1000}") { body ->
            body.optJSONArray("samples").mapObjects(Sample::from)
        }

    suspend fun rides(deviceId: String, limit: Int = 50): ApiResult<List<RideSummary>> =
        get("/v1/devices/$deviceId/rides?limit=$limit") { body ->
            body.optJSONArray("rides").mapObjects(RideSummary::from)
        }

    suspend fun events(deviceId: String, limit: Int = 100): ApiResult<List<AlertEvent>> =
        get("/v1/devices/$deviceId/events?limit=$limit") { body ->
            body.optJSONArray("events").mapObjects(AlertEvent::from)
        }

    suspend fun putConfig(deviceId: String, settings: FleetSettings): ApiResult<Unit> {
        val zones = JSONArray()
        settings.zones.forEach { zone ->
            zones.put(
                JSONObject()
                    .put("id", zone.id)
                    .put("lat", zone.lat)
                    .put("lon", zone.lon)
                    .put("radius_m", zone.radiusM)
            )
        }
        val body = JSONObject()
            .put("interval_s", settings.intervalS)
            .put("speed_alert_kmh", settings.speedAlertKmh)
            .put("battery_alert_pct", settings.batteryAlertPct)
            .put("zones", zones)
        return send("PUT", "/v1/devices/$deviceId/config", body) { }
    }

    /**
     * Queues a command. It is **queued, not delivered** — the scooter may be offline for
     * hours. Callers must present it as pending until the device acknowledges, because
     * telling an owner their scooter is locked when it is not is the one lie this system
     * must not tell.
     */
    suspend fun command(
        deviceId: String,
        type: String,
        message: String? = null,
    ): ApiResult<String> {
        val body = JSONObject().put("type", type)
        message?.let { body.put("message", it) }
        return send("POST", "/v1/devices/$deviceId/commands", body) { response ->
            response.optString("id").ifBlank { "queued" }
        }
    }

    // ---- plumbing ----------------------------------------------------------

    private suspend fun <T> get(path: String, parse: (JSONObject) -> T): ApiResult<T> =
        send("GET", path, null, parse)

    private suspend fun <T> send(
        method: String,
        path: String,
        body: JSONObject?,
        parse: (JSONObject) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val base = baseUrl().trim().trimEnd('/')
        if (base.isBlank()) return@withContext ApiResult.Failed("No server configured")
        val url = runCatching { URL(base + path) }.getOrNull()
            ?: return@withContext ApiResult.Failed("Server address is not a URL")
        if (!url.protocol.equals("https", ignoreCase = true)) {
            // The app sets usesCleartextTraffic=false; say so rather than failing opaquely.
            return@withContext ApiResult.Failed("Server must be https")
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${token()}")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            body?.let { payload ->
                connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            }

            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (code !in 200..299) {
                return@withContext ApiResult.Failed(
                    when (code) {
                        401, 403 -> "Token rejected by the server"
                        404 -> "Not found on the server"
                        in 500..599 -> "Server error ($code)"
                        else -> "HTTP $code"
                    }
                )
            }
            val json = runCatching {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            }.getOrNull() ?: return@withContext ApiResult.Failed("Server sent malformed JSON")
            ApiResult.Ok(parse(json))
        } catch (e: Exception) {
            Log.d(TAG, "$method $path failed: ${e.javaClass.simpleName}: ${e.message}")
            ApiResult.Failed("Cannot reach the server")
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private companion object {
        const val TAG = "WarivoOwnerApi"
    }
}

private fun <T> JSONArray?.mapObjects(parse: (JSONObject) -> T): List<T> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        array.optJSONObject(i)?.let(parse)
    }
}
