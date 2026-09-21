package com.warivo.os.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Raster OpenStreetMap style.
 *
 * Kept as an inline style so the app needs no vector-tile API key and works the moment it
 * is installed. MapLibre's HTTP disk cache then makes every area you have already ridden
 * through available offline, which is the offline behaviour the guide asks for.
 *
 * Note: tile.openstreetmap.org is fine for one personal device but its usage policy
 * forbids heavy or commercial use — swap in your own tile source or a MapTiler key before
 * this goes on more than one scooter.
 */
const val OSM_RASTER_STYLE = """
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

/** Lucknow, matching the sample GPS write in the guide's protocol section. */
const val DEFAULT_LAT = 26.8467
const val DEFAULT_LON = 80.9462

/**
 * A MapLibre map with its Android-View lifecycle driven by hand.
 *
 * Shared by the Map panel and the Home panel's map card. It is safe for both to use it
 * because panels are swapped with `when`, so only one is composed at a time and only one
 * GL surface ever exists — two live MapViews would each hold their own renderer.
 *
 * Skipping `onCreate` leaves the renderer uninitialised and the map blank, which is the
 * easiest thing to get wrong here.
 */
@Composable
fun MapSurface(
    modifier: Modifier = Modifier,
    zoom: Double = 14.0,
    interactive: Boolean = true,
    onMapReady: (MapLibreMap) -> Unit = {},
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(OSM_RASTER_STYLE))
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isAttributionEnabled = interactive
            map.uiSettings.isLogoEnabled = false
            if (!interactive) {
                // The Home card is a glanceable preview, not a map to drag around; the
                // Map panel is one tap away for that.
                map.uiSettings.setAllGesturesEnabled(false)
            }
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(DEFAULT_LAT, DEFAULT_LON))
                .zoom(zoom)
                .build()
            ready = true
            onMapReady(map)
        }
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.fillMaxSize())
}
