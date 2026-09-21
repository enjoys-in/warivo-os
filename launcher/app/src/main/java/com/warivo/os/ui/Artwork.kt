package com.warivo.os.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.warivo.os.Warivo
import com.warivo.os.music.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Album art for a track, falling back to a brand gradient.
 *
 * Decoding happens on the IO dispatcher keyed to the track, so switching tracks never
 * blocks the dashboard — a dropped frame on a moving vehicle is worse than a late cover.
 * Tracks with no embedded art, and Android 9 (where `loadThumbnail` does not exist), get
 * the gradient, which is why it has to look deliberate rather than like a failure.
 */
@Composable
fun Artwork(
    track: Track?,
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    glyphFraction: Float = 0.34f,
) {
    val art by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, track?.id) {
        val current = track
        value = if (current == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                Warivo.music.artworkFor(current)?.asImageBitmap()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(PLACEHOLDER_BRUSH),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = art
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxSize(glyphFraction),
            )
        }
    }
}

private val PLACEHOLDER_BRUSH: Brush
    get() = Brush.linearGradient(
        listOf(Color(0xFFFF8FA8), Color(0xFF7B3EA8), Color(0xFF3457B8))
    )
