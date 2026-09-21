package com.warivo.os.fleet

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A circular geofence the owner has drawn. */
data class Zone(
    val id: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
)

/** How the uplink is doing, for the status bar and the Settings row. */
enum class UplinkState { OFF, IDLE, SENDING, OFFLINE, REJECTED }

/**
 * Fleet settings: where to report, how often, and the thresholds the owner has set.
 *
 * **Off until an endpoint is configured.** Continuous location reporting is not something
 * to enable by default and discover later — see docs/FLEET.md §1.
 *
 * Thresholds that the rider can also see locally (speed, battery) are mirrored into
 * [com.warivo.os.settings.WarivoSettings] when remote config arrives, so the on-device
 * chime and the uploaded alert always share one number rather than drifting apart.
 */
class FleetConfig(context: Context) {

    private val prefs = context.getSharedPreferences("warivo_fleet", Context.MODE_PRIVATE)

    /** Stable per-install id. Generated once; the server keys everything on it. */
    val deviceId: String = prefs.getString(KEY_DEVICE_ID, null) ?: newDeviceId().also {
        prefs.edit().putString(KEY_DEVICE_ID, it).apply()
    }

    private val _endpoint = MutableStateFlow(prefs.getString(KEY_ENDPOINT, "") ?: "")
    val endpoint: StateFlow<String> = _endpoint.asStateFlow()

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, "") ?: "")
    val token: StateFlow<String> = _token.asStateFlow()

    private val _intervalS = MutableStateFlow(prefs.getInt(KEY_INTERVAL, 10))
    val intervalS: StateFlow<Int> = _intervalS.asStateFlow()

    private val _batteryAlertPct = MutableStateFlow(prefs.getInt(KEY_BATT_PCT, 20))
    val batteryAlertPct: StateFlow<Int> = _batteryAlertPct.asStateFlow()

    private val _zones = MutableStateFlow(readZones())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    private val _configVersion = MutableStateFlow(prefs.getInt(KEY_CONFIG_VERSION, 0))
    val configVersion: StateFlow<Int> = _configVersion.asStateFlow()

    private val _state = MutableStateFlow(UplinkState.OFF)
    val state: StateFlow<UplinkState> = _state.asStateFlow()

    private val _lastOkAtMs = MutableStateFlow(prefs.getLong(KEY_LAST_OK, 0L))
    val lastOkAtMs: StateFlow<Long> = _lastOkAtMs.asStateFlow()

    /** Owner message pushed by `lock_head_unit`; null when not locked. */
    private val _lockMessage = MutableStateFlow(prefs.getString(KEY_LOCK_MSG, null))
    val lockMessage: StateFlow<String?> = _lockMessage.asStateFlow()

    /** True once there is somewhere to report to. */
    val configured: Boolean get() = _endpoint.value.isNotBlank() && _token.value.isNotBlank()

    fun setEndpoint(value: String) {
        val trimmed = value.trim()
        _endpoint.value = trimmed
        prefs.edit().putString(KEY_ENDPOINT, trimmed).apply()
    }

    fun setToken(value: String) {
        val trimmed = value.trim()
        _token.value = trimmed
        prefs.edit().putString(KEY_TOKEN, trimmed).apply()
    }

    fun setIntervalS(value: Int) {
        // Below 5 s the upload is mostly HTTP overhead and the battery cost stops being
        // worth the extra resolution; above 60 s a theft track becomes useless.
        val clamped = value.coerceIn(5, 60)
        _intervalS.value = clamped
        prefs.edit().putInt(KEY_INTERVAL, clamped).apply()
    }

    fun setBatteryAlertPct(value: Int) {
        val clamped = value.coerceIn(5, 50)
        _batteryAlertPct.value = clamped
        prefs.edit().putInt(KEY_BATT_PCT, clamped).apply()
    }

    fun setState(value: UplinkState) {
        _state.value = value
    }

    fun noteUploadOk(atMs: Long) {
        _lastOkAtMs.value = atMs
        prefs.edit().putLong(KEY_LAST_OK, atMs).apply()
    }

    fun setLockMessage(message: String?) {
        _lockMessage.value = message
        prefs.edit().apply {
            if (message == null) remove(KEY_LOCK_MSG) else putString(KEY_LOCK_MSG, message)
        }.apply()
    }

    /** Applies a `config` object from the server. Returns true if anything changed. */
    fun applyRemoteConfig(version: Int, config: JSONObject): Boolean {
        if (version <= _configVersion.value) return false

        if (config.has("interval_s")) setIntervalS(config.optInt("interval_s", _intervalS.value))
        if (config.has("battery_alert_pct")) {
            setBatteryAlertPct(config.optInt("battery_alert_pct", _batteryAlertPct.value))
        }
        if (config.has("zones")) {
            val parsed = mutableListOf<Zone>()
            val array = config.optJSONArray("zones") ?: JSONArray()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { "zone-$i" }
                // A zone with no radius is not a zone; skip rather than defaulting to
                // something arbitrary that would fire alerts the owner never asked for.
                val radius = o.optDouble("radius_m", 0.0)
                if (radius <= 0.0) continue
                parsed += Zone(id, o.optDouble("lat"), o.optDouble("lon"), radius)
            }
            _zones.value = parsed
            writeZones(parsed)
        }

        _configVersion.value = version
        prefs.edit().putInt(KEY_CONFIG_VERSION, version).apply()
        return true
    }

    private fun readZones(): List<Zone> = try {
        val raw = prefs.getString(KEY_ZONES, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Zone(o.optString("id"), o.optDouble("lat"), o.optDouble("lon"), o.optDouble("radius_m"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeZones(zones: List<Zone>) {
        val array = JSONArray()
        zones.forEach { zone ->
            array.put(
                JSONObject()
                    .put("id", zone.id)
                    .put("lat", zone.lat)
                    .put("lon", zone.lon)
                    .put("radius_m", zone.radiusM)
            )
        }
        prefs.edit().putString(KEY_ZONES, array.toString()).apply()
    }

    private fun newDeviceId(): String =
        "warivo-" + UUID.randomUUID().toString().replace("-", "").take(8)

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_TOKEN = "token"
        const val KEY_INTERVAL = "interval_s"
        const val KEY_BATT_PCT = "battery_alert_pct"
        const val KEY_ZONES = "zones"
        const val KEY_CONFIG_VERSION = "config_version"
        const val KEY_LAST_OK = "last_ok"
        const val KEY_LOCK_MSG = "lock_message"
    }
}
