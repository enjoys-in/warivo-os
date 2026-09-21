package com.warivo.os.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the proximity warning sounds. */
enum class BeepSource { PHONE, NODE, OFF }

/** A place the rider saved, so Home can show how far away it is. */
data class SavedPlace(val key: String, val lat: Double, val lon: Double)

/**
 * SharedPreferences-backed settings, exposed as flows for Compose.
 *
 * Deliberately not DataStore/Room: this is a handful of scalars, and staying on
 * SharedPreferences keeps the dependency list (and therefore the ROM build in Path B)
 * as small as possible.
 */
class WarivoSettings(context: Context) {

    private val prefs = context.getSharedPreferences("warivo", Context.MODE_PRIVATE)

    private val _beepSource = MutableStateFlow(
        runCatching { BeepSource.valueOf(prefs.getString(KEY_BEEP_SOURCE, null) ?: "OFF") }
            .getOrDefault(BeepSource.OFF)
    )
    val beepSource: StateFlow<BeepSource> = _beepSource.asStateFlow()

    private val _beepCm = MutableStateFlow(prefs.getInt(KEY_BEEP_CM, 40))
    val beepCm: StateFlow<Int> = _beepCm.asStateFlow()

    private val _kioskEnabled = MutableStateFlow(prefs.getBoolean(KEY_KIOSK, false))
    val kioskEnabled: StateFlow<Boolean> = _kioskEnabled.asStateFlow()

    private val _whPerKm = MutableStateFlow(prefs.getFloat(KEY_WH_PER_KM, 25f))
    val whPerKm: StateFlow<Float> = _whPerKm.asStateFlow()

    fun setBeepSource(value: BeepSource) {
        _beepSource.value = value
        prefs.edit().putString(KEY_BEEP_SOURCE, value.name).apply()
    }

    fun setBeepCm(value: Int) {
        val clamped = value.coerceIn(10, 80)
        _beepCm.value = clamped
        prefs.edit().putInt(KEY_BEEP_CM, clamped).apply()
    }

    fun setKioskEnabled(value: Boolean) {
        _kioskEnabled.value = value
        prefs.edit().putBoolean(KEY_KIOSK, value).apply()
    }

    // --- speed alert (the Settings mockup's "chime above threshold") ---

    private val _speedAlertOn = MutableStateFlow(prefs.getBoolean(KEY_SPEED_ALERT, false))
    val speedAlertOn: StateFlow<Boolean> = _speedAlertOn.asStateFlow()

    private val _speedAlertKmh = MutableStateFlow(prefs.getInt(KEY_SPEED_ALERT_KMH, 65))
    val speedAlertKmh: StateFlow<Int> = _speedAlertKmh.asStateFlow()

    fun setSpeedAlertOn(value: Boolean) {
        _speedAlertOn.value = value
        prefs.edit().putBoolean(KEY_SPEED_ALERT, value).apply()
    }

    fun setSpeedAlertKmh(value: Int) {
        val clamped = value.coerceIn(20, 100)
        _speedAlertKmh.value = clamped
        prefs.edit().putInt(KEY_SPEED_ALERT_KMH, clamped).apply()
    }

    // --- saved places, for the Home panel's shortcut chips ---

    private val _places = MutableStateFlow(readPlaces())
    val places: StateFlow<Map<String, SavedPlace>> = _places.asStateFlow()

    private fun readPlaces(): Map<String, SavedPlace> =
        PLACE_KEYS.mapNotNull { key ->
            val lat = prefs.getFloat("place_${key}_lat", Float.NaN)
            val lon = prefs.getFloat("place_${key}_lon", Float.NaN)
            if (lat.isNaN() || lon.isNaN()) null
            else key to SavedPlace(key, lat.toDouble(), lon.toDouble())
        }.toMap()

    /** Stores a place at the given coordinates, or clears it when [lat] is null. */
    fun setPlace(key: String, lat: Double?, lon: Double?) {
        val editor = prefs.edit()
        if (lat == null || lon == null) {
            editor.remove("place_${key}_lat").remove("place_${key}_lon")
        } else {
            editor.putFloat("place_${key}_lat", lat.toFloat())
            editor.putFloat("place_${key}_lon", lon.toFloat())
        }
        editor.apply()
        _places.value = readPlaces()
    }

    fun setWhPerKm(value: Float) {
        val clamped = value.coerceIn(5f, 80f)
        _whPerKm.value = clamped
        prefs.edit().putFloat(KEY_WH_PER_KM, clamped).apply()
    }

    companion object {
        const val PLACE_HOME = "home"
        const val PLACE_WORK = "work"
        val PLACE_KEYS = listOf(PLACE_HOME, PLACE_WORK)

        private const val KEY_SPEED_ALERT = "speed_alert"
        private const val KEY_SPEED_ALERT_KMH = "speed_alert_kmh"
        const val KEY_BEEP_SOURCE = "beep_source"
        const val KEY_BEEP_CM = "beep_cm"
        const val KEY_KIOSK = "kiosk_enabled"
        const val KEY_WH_PER_KM = "wh_per_km"
    }
}
