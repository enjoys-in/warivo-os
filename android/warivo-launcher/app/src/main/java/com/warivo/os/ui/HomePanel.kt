package com.warivo.os.ui

import android.location.Location
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.location.GpsService
import com.warivo.os.model.Telemetry
import com.warivo.os.settings.SavedPlace
import com.warivo.os.settings.WarivoSettings
import com.warivo.os.ui.theme.AccentBrush
import com.warivo.os.ui.theme.CardRadius
import com.warivo.os.ui.theme.DockBrush
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAccentDeep
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoGreen
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.util.Calendar
import java.util.Locale

/**
 * The Home panel, laid out as branding/mockups/png/00-home.png: a greeting, a map card
 * with a search pill and saved-place shortcuts, and a right column of vehicle state and
 * now-playing.
 *
 * This is the panel the rider lands on: it answers "can I go, and where" at a glance,
 * without the full instrument cluster.
 */
@Composable
fun HomePanel(onOpenSearch: () -> Unit, onOpenMap: () -> Unit, onOpenMusic: () -> Unit) {
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    val fix by GpsService.fix.collectAsStateWithLifecycle()
    val places by Warivo.settings.places.collectAsStateWithLifecycle()
    val trip by Warivo.trips.trip.collectAsStateWithLifecycle()
    val lifetimeKm by Warivo.trips.lifetimeKm.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        Greeting(outsideC = telemetry?.tempOutC)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            MapCard(
                fix = fix,
                places = places,
                onOpenSearch = onOpenSearch,
                onOpenMap = onOpenMap,
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight(),
            )

            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(GridGap),
            ) {
                VehicleCard(
                    telemetry = telemetry,
                    tripKm = trip.distanceKm,
                    lifetimeKm = lifetimeKm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                NowPlayingCard(
                    onOpenMusic = onOpenMusic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Greeting(outsideC: Float?) {
    // Weather would need a forecast API and a key; the node already measures the air
    // temperature, so the greeting says what we actually know.
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..4 -> "Good night"
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(greeting, color = WarivoText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            outsideC?.let { "Ready to ride · ${it.toInt()}°C outside" } ?: "Ready to ride",
            color = WarivoTextDim,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun MapCard(
    fix: Location?,
    places: Map<String, SavedPlace>,
    onOpenSearch: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CardRadius))
            .border(1.dp, WarivoHairline, RoundedCornerShape(CardRadius))
    ) {
        MapSurface(zoom = 15.0, interactive = false)

        // Tapping the map opens the full Map panel; the preview itself does not pan.
        Box(
            Modifier
                .fillMaxSize()
                .clickableTile(onOpenMap)
        )

        // Search pill, floating at the top of the card.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(DockBrush, RoundedCornerShape(50))
                .border(1.dp, WarivoHairline, RoundedCornerShape(50))
                .clickableTile(onOpenSearch)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = WarivoTextDim,
                modifier = Modifier.size(22.dp),
            )
            Text(
                "Search Google or pick a place…",
                color = WarivoTextDim,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f),
            )
            AccentCircleButton(Icons.Filled.Search, "Open search", 42.dp, onOpenSearch)
        }

        // Saved places. Straight-line distance, not an ETA: there is no routing engine,
        // so a time estimate would be invented. Set them from Settings.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlaceChip(
                icon = Icons.Filled.Home,
                label = "Home",
                place = places[WarivoSettings.PLACE_HOME],
                fix = fix,
                onClick = onOpenMap,
            )
            PlaceChip(
                icon = Icons.Filled.Work,
                label = "Work",
                place = places[WarivoSettings.PLACE_WORK],
                fix = fix,
                onClick = onOpenMap,
            )
        }
    }
}

@Composable
private fun PlaceChip(
    icon: ImageVector,
    label: String,
    place: SavedPlace?,
    fix: Location?,
    onClick: () -> Unit,
) {
    val detail = when {
        place == null -> "not set"
        fix == null -> "waiting for GPS"
        else -> {
            val results = FloatArray(1)
            Location.distanceBetween(fix.latitude, fix.longitude, place.lat, place.lon, results)
            val km = results[0] / 1000f
            if (km < 1f) "${(results[0]).toInt()} m away"
            else "${String.format(Locale.US, "%.1f", km)} km away"
        }
    }
    Row(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DockBrush, RoundedCornerShape(18.dp))
            .border(1.dp, WarivoHairline, RoundedCornerShape(18.dp))
            .clickableTile(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconChip(icon, size = 36.dp, radius = 11.dp)
        Column {
            Text(label, color = WarivoText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = WarivoTextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun VehicleCard(
    telemetry: Telemetry?,
    tripKm: Double,
    lifetimeKm: Double,
    modifier: Modifier = Modifier,
) {
    WarivoCard(modifier = modifier) {
        if (telemetry == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CardLabel("Scooter", color = WarivoAmber)
                    Text(
                        "Not connected",
                        color = WarivoTextDim,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            return@WarivoCard
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RingGauge(
                fraction = telemetry.soc / 100f,
                from = WarivoAccentDeep.copy(alpha = 0.9f),
                to = socColour(telemetry.soc),
                strokeWidth = 14.dp,
                modifier = Modifier
                    .fillMaxHeight(0.78f)
                    .aspectRatio(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "${telemetry.soc}",
                            color = socColour(telemetry.soc),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "%",
                            color = socColour(telemetry.soc),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        "${String.format(Locale.US, "%.1f", telemetry.volts)} V",
                        color = WarivoTextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                // "Parked" is derived, not reported: below 1 km/h the wheel sensor is not
                // turning, which is the only definition of stopped we have.
                val moving = telemetry.speedKmh >= 1f
                StateBadge(
                    if (moving) "riding" else "parked",
                    if (moving) WarivoAccent else WarivoTextDim,
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(
                        String.format(Locale.US, "%.0f", telemetry.rangeKm),
                        color = WarivoText,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " km range",
                        color = WarivoTextDim,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    "Odometer ${String.format(Locale.US, "%.0f", maxOf(telemetry.odoKm, lifetimeKm))} km" +
                        " · Trip ${String.format(Locale.US, "%.1f", tripKm)} km",
                    color = WarivoTextDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(onOpenMusic: () -> Unit, modifier: Modifier = Modifier) {
    val player = Warivo.music
    val current by player.current.collectAsStateWithLifecycle()
    val playing by player.playing.collectAsStateWithLifecycle()

    WarivoCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.7f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF8FA8), Color(0xFF7B3EA8))
                        )
                    )
                    .clickableTile(onOpenMusic),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.75f))
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickableTile(onOpenMusic)
            ) {
                CardLabel("Now playing", color = WarivoAccent)
                Text(
                    current?.title ?: "Nothing playing",
                    color = WarivoText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    current?.artist ?: "Tap to open the library",
                    color = WarivoTextDim,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(WarivoTextDim.copy(alpha = 0.25f))
                ) {
                    val fraction = current?.let { track ->
                        if (track.durationMs <= 0) 0f
                        else (player.positionMs().toFloat() / track.durationMs).coerceIn(0f, 1f)
                    } ?: 0f
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(AccentBrush)
                    )
                }
            }

            AccentCircleButton(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "Pause" else "Play",
                58.dp,
            ) { player.toggle() }
        }
    }
}

private fun socColour(soc: Int): Color = when {
    soc <= 15 -> WarivoRed
    soc <= 35 -> WarivoAmber
    else -> WarivoGreen
}
