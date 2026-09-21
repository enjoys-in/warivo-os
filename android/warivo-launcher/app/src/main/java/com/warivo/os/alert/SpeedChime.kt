package com.warivo.os.alert

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.settings.WarivoSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Settings panel's speed alert: one chime when the scooter crosses the threshold.
 *
 * Edge-triggered, not level-triggered. A chime that repeats for as long as you are over
 * the limit is noise you learn to ignore, so it fires once on the way up and rearms only
 * after the speed drops back through the threshold minus a hysteresis band — otherwise
 * hovering at exactly 65 km/h would chime continuously.
 */
class SpeedChime(
    private val node: WarivoNodeClient,
    private val settings: WarivoSettings,
) {
    private var armed = true

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                val speed = node.telemetry.value?.speedKmh
                val threshold = settings.speedAlertKmh.value.toFloat()

                if (!settings.speedAlertOn.value || speed == null) {
                    armed = true
                } else if (armed && speed >= threshold) {
                    chime()
                    armed = false
                } else if (!armed && speed < threshold - HYSTERESIS_KMH) {
                    armed = true
                }
                delay(POLL_MS)
            }
        }
    }

    private fun chime() {
        runCatching {
            // STREAM_MUSIC so it reaches the Bluetooth speaker with the music.
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, CHIME_MS)
            // Release after the tone has played; ToneGenerator holds an audio track.
            Thread {
                Thread.sleep((CHIME_MS + 120).toLong())
                runCatching { tone.release() }
            }.start()
        }.onFailure { Log.w(TAG, "speed chime failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "WarivoSpeedChime"
        const val VOLUME = 90
        const val CHIME_MS = 260
        const val HYSTERESIS_KMH = 3f
        const val POLL_MS = 300L
    }
}
