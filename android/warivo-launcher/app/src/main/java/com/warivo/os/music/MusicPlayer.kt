package com.warivo.os.music

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Build
import android.util.Size
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

/**
 * Local-file music for the media panel.
 *
 * Plain [MediaPlayer] over MediaStore, with no media3/ExoPlayer dependency: playback
 * routes to the paired Bluetooth speaker automatically via the system's A2DP routing, so
 * there is nothing for the app to do about output. Swap in media3 later if you want a
 * media session, lock-screen controls or gapless playback.
 */
class MusicPlayer(private val context: Context) {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private var player: MediaPlayer? = null

    /** Reads the device's audio library. Cheap enough to re-run when the panel opens. */
    fun refreshLibrary() {
        val found = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    found += Track(
                        id = cursor.getLong(idCol),
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown artist",
                        durationMs = cursor.getLong(durCol),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "library scan failed: ${it.message}") }
        _tracks.value = found
    }

    fun play(track: Track) {
        release()
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id
        )
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                setOnCompletionListener { next() }
                prepare()
                start()
            }
            _current.value = track
            _playing.value = true
        }.onFailure {
            Log.w(TAG, "playback failed for ${track.title}: ${it.message}")
            _playing.value = false
        }
    }

    fun toggle() {
        val p = player
        if (p == null) {
            _tracks.value.firstOrNull()?.let { play(it) }
            return
        }
        if (p.isPlaying) {
            p.pause()
            _playing.value = false
        } else {
            p.start()
            _playing.value = true
        }
    }

    fun next() = step(+1)

    fun previous() = step(-1)

    private fun step(delta: Int) {
        val list = _tracks.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == _current.value?.id }
        val target = if (index < 0) 0 else (index + delta).mod(list.size)
        play(list[target])
    }

    /**
     * Album art for a track, or null when the file has none.
     *
     * `loadThumbnail` is API 29 and does the decoding and downscaling itself, so real
     * artwork costs no image-loading dependency at all — which matters because every
     * dependency left out is one less thing to carry into the Path B ROM. On Android 9
     * there is no equivalent that is worth the code, so those devices keep the gradient
     * placeholder.
     *
     * Call this off the main thread; it reads and decodes a file.
     */
    fun artworkFor(track: Track): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id
        )
        return runCatching {
            context.contentResolver.loadThumbnail(uri, Size(ART_PX, ART_PX), null)
        }.getOrNull()
    }

    /** Current position, for the progress bar. Polled by the UI. */
    fun positionMs(): Int = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    fun release() {
        runCatching { player?.release() }
        player = null
        _playing.value = false
    }

    private companion object {
        const val TAG = "WarivoMusic"
        const val ART_PX = 512
    }
}
