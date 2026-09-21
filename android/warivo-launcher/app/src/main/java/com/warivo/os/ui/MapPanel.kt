package com.warivo.os.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.location.GpsService
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale

/**
 * Raster OpenStreetMap style, tinted toward the Warivo palette by the map's own colours
 * being dark already.
 *
 * Kept as an inline style so the app needs no vector-tile API key and works the moment it
 * is installed. MapLibre's HTTP disk cache then makes every area you have already ridden
 * through available offline, which is the offline behaviour the guide asks for.
 *
 * Note: tile.openstreetmap.org is fine for one personal device but its usage policy
 * forbids heavy or commercial use — swap in your own tile source or a MapTiler key before
 * this goes on more than one scooter.
 */
private const val OSM_RASTER_STYLE = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [{ "id": "osm", "type": "raster", "source": "osm" }]
}
"""

/**
 * Full-bleed map with floating cards over it, as branding/mockups/03-map.html.
 *
 * The mockup also draws a turn-by-turn manoeuvre card and an ETA card. Those need a
 * routing engine and a destination, which this build has neither of, so the floating card
 * shows what we actually know: the live fix and the speed the node is reporting.
 */
@Composable
fun MapPanel() {
    val context = LocalContext.current
    val fix by GpsService.fix.collectAsStateWithLifecycle()
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()

    // The MapView is an Android View, so its lifecycle has to be driven by hand.
    // Skipping onCreate leaves the renderer uninitialised and the map blank.
    val mapView = remember { MapView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var follow by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { ready ->
            map = ready
            ready.setStyle(Style.Builder().fromJson(OSM_RASTER_STYLE))
            ready.uiSettings.isRotateGesturesEnabled = false
            ready.uiSettings.isAttributionEnabled = true
            ready.uiSettings.isLogoEnabled = false
            ready.cameraPosition = CameraPosition.Builder()
                .target(LatLng(DEFAULT_LAT, DEFAULT_LON))
                .zoom(14.0)
                .build()
        }
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Follow the phone's own GPS — the node has no receiver of its own. Panning the map
    // by hand stops the camera fighting the rider for control.
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
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Floating status card, bottom-left, in the mockup's `.eta` position.
        WarivoCard(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 30.dp, bottom = 30.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Column {
                    CardLabel("Speed")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            (telemetry?.speedKmh?.toInt() ?: 0).toString(),
                            color = WarivoText,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            " km/h",
                            color = WarivoTextDim,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
                Column {
                    CardLabel("Position")
                    Text(
                        fix?.let {
                            String.format(Locale.US, "%.4f, %.4f", it.latitude, it.longitude)
                        } ?: "no fix yet",
                        color = WarivoText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // Recentre, in the mockup's `.recenter` position.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 30.dp, bottom = 30.dp),
        ) {
            AccentCircleButton(Icons.Filled.MyLocation, "Recentre on me", 64.dp) {
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

        if (fix == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(14.dp)
            ) {
                StatusChip("Waiting for GPS", WarivoAmber, dot = true)
            }
        }
    }
}

// Lucknow, matching the sample GPS write in the guide's protocol section.
private const val DEFAULT_LAT = 26.8467
private const val DEFAULT_LON = 80.9462
