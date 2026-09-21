package com.warivo.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.warivo.companion.model.Sample
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * The same raster OpenStreetMap style the head unit uses — no vector-tile key needed, and
 * no Play Services.
 *
 * `tile.openstreetmap.org` is fine for one owner's phone but its usage policy forbids
 * heavy use; point it at your own tiles before shipping this to a fleet.
 */
private const val OSM_STYLE = """
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
 * Last known position, and the day's trail.
 *
 * Markers rather than a GeoJSON line layer: a line needs a source and a layer added after
 * the style finishes loading, which is more moving parts — and more style-lifecycle race
 * conditions — than a handful of points justifies.
 *
 * **Most likely thing here to need fixing on the first build.** MapLibre inherited
 * `addMarker`/`MarkerOptions` from Mapbox and has been deprecating the annotations API
 * across 10.x/11.x. If it is gone in the version that resolves, the replacement is either
 * the `maplibre-android-plugin-annotation` artifact or a `symbol` layer over a GeoJSON
 * source. The camera work above is unaffected either way, so the map still centres on the
 * scooter even with the markers commented out.
 */
@Composable
fun TrackMap(latest: Sample?, track: List<Sample>) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(Unit) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { ready ->
            ready.setStyle(Style.Builder().fromJson(OSM_STYLE))
            ready.uiSettings.isRotateGesturesEnabled = false
            ready.uiSettings.isLogoEnabled = false
            map = ready
        }
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(latest, track, map) {
        val ready = map ?: return@LaunchedEffect
        val points = buildList {
            track.forEach { sample ->
                if (sample.lat != null && sample.lon != null) {
                    add(LatLng(sample.lat, sample.lon))
                }
            }
            if (latest?.lat != null && latest.lon != null) {
                add(LatLng(latest.lat, latest.lon))
            }
        }
        if (points.isEmpty()) return@LaunchedEffect

        ready.clear()
        points.forEach { point ->
            ready.addMarker(MarkerOptions().position(point))
        }
        if (points.size == 1) {
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 16.0))
        } else {
            // Fit the whole trail, with padding so markers are not clipped at the edge.
            val bounds = LatLngBounds.Builder().includes(points).build()
            ready.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60))
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    }
}
