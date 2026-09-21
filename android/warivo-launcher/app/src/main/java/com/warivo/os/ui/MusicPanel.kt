package com.warivo.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAqua
import com.warivo.os.ui.theme.WarivoBlack
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoSurfaceHigh
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Local files only. Output goes to whichever Bluetooth speaker is paired — the system's
 * A2DP routing handles that, so there is nothing here to configure.
 *
 * Laid out like the reference media cards: artwork and transport on one side, the library
 * list on the other, so changing track never hides what is playing.
 */
@Composable
fun MusicPanel() {
    val player = Warivo.music
    val tracks by player.tracks.collectAsStateWithLifecycle()
    val current by player.current.collectAsStateWithLifecycle()
    val playing by player.playing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (tracks.isEmpty()) player.refreshLibrary()
    }

    // MediaPlayer has no position callback, so the progress bar polls.
    val positionMs by produceState(initialValue = 0, playing, current) {
        while (true) {
            value = player.positionMs()
            delay(500)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        WarivoCard(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
            label = "Now playing",
            trailingIcon = Icons.Filled.MusicNote,
        ) {
            NowPlaying(
                title = current?.title ?: "Nothing playing",
                artist = current?.artist ?: "${tracks.size} tracks on this phone",
                playing = playing,
                positionMs = positionMs.toLong(),
                durationMs = current?.durationMs ?: 0L,
                onPrevious = player::previous,
                onToggle = player::toggle,
                onNext = player::next,
            )
        }

        WarivoCard(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            label = "Library",
        ) {
            if (tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No music found. Copy audio files to the phone's storage.",
                        color = WarivoTextDim,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(tracks, key = { it.id }) { track ->
                        val isCurrent = track.id == current?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) WarivoSurfaceHigh else Color.Transparent)
                                .clickableTile { player.play(track) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    color = if (isCurrent) WarivoAqua else WarivoText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.artist,
                                    color = WarivoTextDim,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(formatDuration(track.durationMs), color = WarivoTextDim, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(
    title: String,
    artist: String,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Artwork stands in as a tinted mark: decoding album art would mean an image
        // loader, and this panel is about reaching the controls, not browsing covers.
        Box(
            modifier = Modifier
                .fillMaxHeight(0.46f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(WarivoSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = WarivoAqua,
                modifier = Modifier.fillMaxSize(0.38f),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = WarivoText,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                artist,
                color = WarivoTextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        Column(Modifier.fillMaxWidth()) {
            ProgressBar(positionMs = positionMs, durationMs = durationMs)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDuration(positionMs), color = WarivoTextDim, fontSize = 12.sp)
                Text(formatDuration(durationMs), color = WarivoTextDim, fontSize = 12.sp)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(Icons.Filled.SkipPrevious, "Previous", 54.dp, onPrevious)
            TransportButton(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "Pause" else "Play",
                68.dp,
                onToggle,
                filled = true,
            )
            TransportButton(Icons.Filled.SkipNext, "Next", 54.dp, onNext)
        }
    }
}

/** Round transport control. Big, because it gets pressed with a thumb at a traffic light. */
@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    diameter: Dp,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(RoundedCornerShape(50))
            .background(if (filled) WarivoAqua else WarivoSurfaceHigh)
            .clickableTile(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (filled) WarivoBlack else WarivoText,
            modifier = Modifier.size(diameter * 0.5f),
        )
    }
}

@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(WarivoHairline)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(WarivoAqua)
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
