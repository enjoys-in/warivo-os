package com.warivo.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.music.Track
import com.warivo.os.ui.theme.AccentBrush
import com.warivo.os.ui.theme.CardRadiusLarge
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Local files only, laid out as branding/mockups/04-music.html: the player on the left,
 * output and queue on the right, so changing track never hides what is playing.
 *
 * Output goes to whichever Bluetooth speaker is paired — the system's A2DP routing
 * handles that, so there is nothing here to configure.
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
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        Column(
            modifier = Modifier
                .width(PLAYER_COLUMN)
                .fillMaxHeight()
        ) {
            Artwork(playing = playing)
            Column(Modifier.padding(top = 26.dp)) {
                CardLabel("From your library", color = WarivoAccent)
                Text(
                    current?.title ?: "Nothing playing",
                    color = WarivoText,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 44.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    current?.artist ?: "${tracks.size} tracks on this phone",
                    color = WarivoTextDim,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Progress(positionMs = positionMs.toLong(), durationMs = current?.durationMs ?: 0L)
            Controls(
                playing = playing,
                onPrevious = player::previous,
                onToggle = player::toggle,
                onNext = player::next,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            OutputCard(playing = playing)
            QueueCard(
                tracks = tracks,
                currentId = current?.id,
                onPlay = player::play,
            )
        }
    }
}

private val PLAYER_COLUMN = 480.dp

/**
 * Artwork stands in as a gradient tile with the Warivo mark. Decoding real album art
 * would mean an image-loading dependency, and this panel is about reaching the controls
 * at a traffic light, not browsing covers.
 */
@Composable
private fun Artwork(playing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(CardRadiusLarge))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFF8FA8), Color(0xFF3457B8), Color(0xFF7B3EA8))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxSize(0.34f),
        )
        if (playing) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x8C05102A))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(WarivoAccent)
                )
                CardLabel("Now playing", color = WarivoAccent)
            }
        }
    }
}

@Composable
private fun Progress(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(WarivoTextDim.copy(alpha = 0.25f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AccentBrush)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatDuration(positionMs), color = WarivoTextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(formatDuration(durationMs), color = WarivoTextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Controls(
    playing: Boolean,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shuffle and repeat are drawn dimmed in the mockups and are not wired: a
            // one-track-at-a-time MediaPlayer has no queue model to shuffle yet.
            GhostCircleButton(Icons.Filled.Shuffle, "Shuffle", 56.dp, WarivoTextDim.copy(alpha = 0.5f)) {}
            GhostCircleButton(Icons.Filled.SkipPrevious, "Previous", 60.dp, onClick = onPrevious)
            AccentCircleButton(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "Pause" else "Play",
                88.dp,
                onToggle,
            )
            GhostCircleButton(Icons.Filled.SkipNext, "Next", 60.dp, onClick = onNext)
            GhostCircleButton(Icons.Filled.Repeat, "Repeat", 56.dp, WarivoTextDim.copy(alpha = 0.5f)) {}
        }
    }
}

@Composable
private fun OutputCard(playing: Boolean) {
    WarivoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            IconChip(Icons.Filled.Bluetooth, size = 60.dp, radius = 20.dp)
            Column(Modifier.weight(1f)) {
                CardLabel("Output · Bluetooth")
                Text(
                    "Paired speaker",
                    color = WarivoText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (playing) {
                LevelBars(listOf(0.4f, 0.7f, 1f, 0.6f, 0.3f))
            }
        }
    }
}

@Composable
private fun QueueCard(
    tracks: List<Track>,
    currentId: Long?,
    onPlay: (Track) -> Unit,
) {
    WarivoCard(modifier = Modifier.fillMaxSize(), padded = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Up next", color = WarivoText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "${tracks.size} tracks",
                color = WarivoTextDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No music found.\nCopy audio files to the phone's storage.",
                    color = WarivoTextDim,
                    textAlign = TextAlign.Center,
                )
            }
            return@WarivoCard
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(tracks, key = { it.id }) { track ->
                val isCurrent = track.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (isCurrent) Modifier
                                .background(WarivoAccent.copy(alpha = 0.08f))
                                .border(1.dp, WarivoAccent.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                            else Modifier
                        )
                        .clickableTile { onPlay(track) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF8FA8), Color(0xFF3457B8))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            color = if (isCurrent) WarivoAccent else WarivoText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artist,
                            color = WarivoTextDim,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(
                        formatDuration(track.durationMs),
                        color = WarivoTextDim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
