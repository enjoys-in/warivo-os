package com.warivo.os.alert

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.settings.BeepSource
import com.warivo.os.settings.WarivoSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The phone-speaker half of the proximity warning.
 *
 * The node can beep on its own buzzer (settings write to fff3), but the rider is far
 * more likely to hear the Bluetooth speaker, so the same alert can be produced here
 * instead. Exactly one of the two sounds: [BeepSource] is a single choice, and the node
 * is told to stay quiet unless it is the one selected.
 *
 * Tones go out on STREAM_MUSIC so they route to the paired speaker alongside the music.
 */
class ProximityBeeper(
    private val node: WarivoNodeClient,
    private val settings: WarivoSettings,
) {
    private var tone: ToneGenerator? = null

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                val source = settings.beepSource.value
                val distance = node.telemetry.value?.distCm
                val threshold = settings.beepCm.value

                if (source != BeepSource.PHONE || distance == null || distance > threshold) {
                    releaseTone()
                    delay(IDLE_POLL_MS)
                    continue
                }

                beep()
                // Closer means faster, mirroring the node's own buzzer cadence.
                val ratio = (distance / threshold).coerceIn(0f, 1f)
                delay((MIN_GAP_MS + ratio * (MAX_GAP_MS - MIN_GAP_MS)).toLong())
            }
        }
    }

    /**
     * Sounds the alarm on demand, for the owner's remote `alarm` command.
     *
     * Deliberately louder and longer than the proximity chime, and it ignores the beep
     * settings: the rider turning the proximity warning off should not also disable the
     * owner's ability to make a stolen scooter audible.
     */
    fun sound(scope: CoroutineScope) {
        scope.launch {
            repeat(ALARM_BURSTS) {
                runCatching {
                    val tone = ToneGenerator(AudioManager.STREAM_MUSIC, ALARM_VOLUME)
                    tone.startTone(ToneGenerator.TONE_CDMA_HIGH_L, ALARM_MS)
                    delay((ALARM_MS + 80).toLong())
                    tone.release()
                }.onFailure { Log.w(TAG, "alarm failed: ${it.message}") }
            }
        }
    }

    private fun beep() {
        val generator = tone ?: runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
        }.onFailure { Log.w(TAG, "tone generator unavailable: ${it.message}") }
            .getOrNull()?.also { tone = it } ?: return
        runCatching { generator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS) }
    }

    private fun releaseTone() {
        tone?.let { runCatching { it.release() } }
        tone = null
    }

    private companion object {
        const val TAG = "WarivoBeep"
        const val VOLUME = 80
        const val BEEP_MS = 90
        const val MIN_GAP_MS = 120f
        const val MAX_GAP_MS = 700f
        const val IDLE_POLL_MS = 400L
        const val ALARM_VOLUME = 100
        const val ALARM_MS = 700
        const val ALARM_BURSTS = 6
    }
}
