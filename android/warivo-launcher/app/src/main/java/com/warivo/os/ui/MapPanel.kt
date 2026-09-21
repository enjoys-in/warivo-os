package com.warivo.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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
    val fix by GpsService.fix.collectAsStateWithLifecycle()
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var follow by remember { mutableStateOf(true) }

    // Follow the phone's own GPS — the node has no receiver of its own. Panning by hand
    // stops the camera fighting the rider for control; the recentre button resumes it.
    LaunchedEffect(fix, map, follow) {
        if (!follow) return@LaunchedEffect
        val location = fix ?: return@LaunchedEffect
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                16.0,
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
        MapSurface(zoom = 14.0, onMapReady = { map = it })

        // Floating destination pill. Geocoding a destination needs a places API we do not
        // have, so it hands off to the Search panel instead of pretending to route.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(DockBrush, RoundedCornerShape(50))
                .border(1.dp, WarivoHairline, RoundedCornerShape(50))
                .clickableTile(onOpenSearch)
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
                "Search here — cafés, chargers, addresses…",
                color = WarivoTextDim,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f),
            )
            AccentCircleButton(Icons.Filled.Search, "Open search", 44.dp, onOpenSearch)
        }

        // GPS state, top-right, clear of the status strip's own chips.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 88.dp, end = 26.dp)
        ) {
            AccentCircleButton(Icons.Filled.MyLocation, "Recentre on me", 56.dp) {
                follow = true
                fix?.let { location ->
                    map?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude), 16.0
                        )
                    )
                }
            }
        }

        // Speed bubble, bottom-right, above the dock.
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

        // Position readout, bottom-left, where the mockup puts the ETA card.
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
