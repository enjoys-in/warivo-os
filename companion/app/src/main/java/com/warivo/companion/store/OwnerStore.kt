package com.warivo.companion.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the server is and which scooter is selected.
 *
 * SharedPreferences, like the head unit: three strings. An owner token is a bearer
 * credential, so it is worth saying plainly that this is *not* hardened storage — a rooted
 * or compromised phone can read it. If that matters for your fleet, the token belongs
 * behind the Keystore or a real login, and that is a deliberate next step rather than
 * something to assume is already done.
 */
class OwnerStore(context: Context) {

    private val prefs = context.getSharedPreferences("warivo_owner", Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(prefs.getString(KEY_URL, "") ?: "")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, "") ?: "")
    val token: StateFlow<String> = _token.asStateFlow()

    private val _deviceId = MutableStateFlow(prefs.getString(KEY_DEVICE, "") ?: "")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    val configured: Boolean
        get() = _baseUrl.value.isNotBlank() && _token.value.isNotBlank()

    fun setBaseUrl(value: String) {
        val trimmed = value.trim()
        _baseUrl.value = trimmed
        prefs.edit().putString(KEY_URL, trimmed).apply()
    }

    fun setToken(value: String) {
        val trimmed = value.trim()
        _token.value = trimmed
        prefs.edit().putString(KEY_TOKEN, trimmed).apply()
    }

    fun setDeviceId(value: String) {
        _deviceId.value = value
        prefs.edit().putString(KEY_DEVICE, value).apply()
    }

    private companion object {
        const val KEY_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_DEVICE = "device_id"
    }
}
