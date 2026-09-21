package com.warivo.os.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the proximity warning sounds. */
enum class BeepSource { PHONE, NODE, OFF }

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

    fun setWhPerKm(value: Float) {
        val clamped = value.coerceIn(5f, 80f)
        _whPerKm.value = clamped
        prefs.edit().putFloat(KEY_WH_PER_KM, clamped).apply()
    }

    private companion object {
        const val KEY_BEEP_SOURCE = "beep_source"
        const val KEY_BEEP_CM = "beep_cm"
        const val KEY_KIOSK = "kiosk_enabled"
        const val KEY_WH_PER_KM = "wh_per_km"
    }
}
