package com.warivo.os.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.location.GpsService
import com.warivo.os.ui.theme.CardRadius
import com.warivo.os.ui.theme.WarivoAmber
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Raster OpenStreetMap style.
 *
 * Kept as an inline style so the app needs no vector-tile API key and works the moment
 * it is installed. MapLibre's own HTTP disk cache then makes every area you have already
 * ridden through available offline, which is the offline behaviour the guide asks for.
 *
 * Note: tile.openstreetmap.org is fine for one personal device but its usage policy
 * forbids heavy or commercial use — swap in your own tile source or a MapTiler key
 * before this goes on more than one scooter.
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

@Composable
fun MapPanel() {
    val context = LocalContext.current
    val fix by GpsService.fix.collectAsStateWithLifecycle()

    // The MapView is an Android View, so its lifecycle has to be driven by hand.
    // Skipping onCreate leaves the renderer uninitialised and the map blank.
    val mapView = remember { MapView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

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

    // Follow the phone's own GPS — the node has no receiver of its own.
    LaunchedEffect(fix, map) {
        val location = fix ?: return@LaunchedEffect
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                16.0,
            )
        )
    }

    // Framed in a card like every other panel, so the map does not break the grid.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(bottom = 14.dp)
    ) {
        WarivoCard(modifier = Modifier.fillMaxSize(), contentPadding = false) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(CardRadius)),
            )
        }
        if (fix == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(14.dp)
            ) {
                Pill("WAITING FOR GPS", WarivoAmber)
            }
        }
    }
}

// Lucknow, matching the sample GPS write in the guide's protocol section.
private const val DEFAULT_LAT = 26.8467
private const val DEFAULT_LON = 80.9462
