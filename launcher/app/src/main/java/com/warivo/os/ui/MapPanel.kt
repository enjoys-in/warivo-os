package com.warivo.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.location.GpsService
import com.warivo.os.ui.theme.DockBrush
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoSurface
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import java.util.Locale

/**
 * Full-bleed map with floating cards over it, as branding/mockups/03-map.html.
 *
 * The mockup also draws a turn-by-turn manoeuvre card and an ETA card. Those need a
 * routing engine and a destination, which this build has neither of, so they are omitted
 * rather than faked — what floats here is what we actually know: the fix, the speed the
 * node reports, and a way back to search.
 */
@Composable
fun MapPanel(onOpenSearch: () -> Unit) {
    val nav = Warivo.nav
    val fix by GpsService.fix.collectAsStateWithLifecycle()
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    val results by nav.results.collectAsStateWithLifecycle()
    val searching by nav.searching.collectAsStateWithLifecycle()
    val routing by nav.routing.collectAsStateWithLifecycle()
    val destination by nav.destination.collectAsStateWithLifecycle()
    val route by nav.route.collectAsStateWithLifecycle()
    val progress by nav.progress.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LON), 14f)
    }
    var follow by remember { mutableStateOf(true) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // Follow the phone's own GPS — the node has no receiver of its own. Panning by hand
    // stops the camera fighting the rider for control; the recentre button resumes it.
    LaunchedEffect(fix, follow) {
        if (!follow) return@LaunchedEffect
        val location = fix ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), 16f,
            )
        )
    }

    // A hand pan drops follow so the map holds still under the rider's finger.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving &&
            cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            follow = false
        }
    }

    // When a route arrives, frame the whole thing rather than staying zoomed on the rider.
    LaunchedEffect(route) {
        val r = route ?: return@LaunchedEffect
        if (r.points.size < 2) return@LaunchedEffect
        follow = false
        val bounds = LatLngBounds.builder()
        r.points.forEach { bounds.include(LatLng(it.lat, it.lon)) }
        runCatching {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), 140))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // In portrait the floating status strip fills the whole width, so map chrome that
        // sits at the very top (the search pill) has to drop below it.
        val narrow = maxWidth < 560.dp
        WarivoMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            interactive = true,
            myLocation = true,
        ) {
            route?.let { r ->
                val pts = r.points.map { LatLng(it.lat, it.lon) }
                if (pts.size >= 2) {
                    // Navy casing under a blush line, matching the mockup's route styling.
                    Polyline(points = pts, color = WarivoSurface, width = 30f)
                    Polyline(points = pts, color = WarivoAccent, width = 16f)
                }
            }
            destination?.let { d ->
                Marker(
                    state = rememberMarkerState(key = "${d.lat},${d.lon}", position = LatLng(d.lat, d.lon)),
                    title = d.name,
                    snippet = d.detail,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE),
                )
            }
        }
        val topInset = if (narrow) 78.dp else 14.dp

        when {
            // Destination search: field + geocoded results, with a way back to web search.
            searchOpen -> NavSearchOverlay(
                query = query,
                onQuery = { query = it; nav.search(it) },
                results = results,
                searching = searching,
                onPick = { place -> nav.navigateTo(place); searchOpen = false; query = "" },
                onWebSearch = { searchOpen = false; onOpenSearch() },
                onClose = { searchOpen = false; nav.clearResults() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset, start = 14.dp, end = 14.dp)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
            )

            // Navigating: the next-turn banner on top, the route card at the bottom.
            destination != null -> {
                progress?.let { p ->
                    ManeuverBanner(
                        p,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topInset, start = 14.dp, end = 14.dp)
                            .widthIn(max = 680.dp)
                            .fillMaxWidth(),
                    )
                }
                NavCard(
                    destination = destination!!,
                    progress = progress,
                    routing = routing,
                    onStop = { nav.stop() },
                    onRetry = { nav.retry() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 14.dp, end = 14.dp, bottom = 132.dp)
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                )
            }

            // Idle: the destination search pill.
            else -> Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = topInset,
                        start = if (narrow) 14.dp else 0.dp,
                        end = if (narrow) 14.dp else 0.dp,
                    )
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(DockBrush, RoundedCornerShape(50))
                    .border(1.dp, WarivoHairline, RoundedCornerShape(50))
                    .clickableTile { searchOpen = true }
                    .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                    "Where to? Search a destination…",
                    color = WarivoTextDim,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f),
                )
                AccentCircleButton(Icons.Filled.Search, "Search a destination", 44.dp) { searchOpen = true }
            }
        }

        // Recentre, hidden only while the search sheet is up.
        if (!searchOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (narrow) 148.dp else 88.dp, end = if (narrow) 18.dp else 26.dp)
            ) {
                AccentCircleButton(Icons.Filled.MyLocation, "Recentre on me", 56.dp) {
                    // Resume follow; the LaunchedEffect above animates back to the fix.
                    follow = true
                }
            }
        }

        // Speed bubble + position card only when idle (the nav card replaces them).
        if (!searchOpen && destination == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 34.dp, bottom = 140.dp)
                    .size(112.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DockBrush, RoundedCornerShape(50))
                    .border(1.dp, WarivoHairline, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        (telemetry?.speedKmh?.toInt() ?: 0).toString(),
                        color = WarivoText,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "KM/H",
                        color = WarivoTextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
            }

            WarivoCard(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 26.dp, bottom = 140.dp),
            ) {
                CardLabel(if (fix == null) "Waiting for GPS" else "Position")
                Text(
                    fix?.let {
                        String.format(Locale.US, "%.4f, %.4f", it.latitude, it.longitude)
                    } ?: "no fix yet",
                    color = if (fix == null) WarivoAmber else WarivoText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
