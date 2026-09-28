package com.warivo.os.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

/** Lucknow, matching the sample GPS write in the guide's protocol section. */
const val DEFAULT_LAT = 26.8467
const val DEFAULT_LON = 80.9462

/**
 * A dark Google Maps style in the Warivo palette (branding/mockups/warivo.css).
 *
 * Google's default map is bright and off-brand next to a deep-space dashboard, so the map
 * is restyled to the same blues the rest of the UI uses, with roads picked out a shade
 * lighter so they still read at a glance while riding.
 */
const val WARIVO_MAP_STYLE = """
[
  {"elementType":"geometry","stylers":[{"color":"#0b1e45"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#9aa6c4"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#05102a"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#142c5c"}]},
  {"featureType":"administrative.land_parcel","stylers":[{"visibility":"off"}]},
  {"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#c6cfe6"}]},
  {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#8493b8"}]},
  {"featureType":"poi.business","stylers":[{"visibility":"off"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#123a3a"}]},
  {"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#5a8f7a"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#1c3566"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#0b1e45"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#aab6d6"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#274582"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#33528f"}]},
  {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#0b1e45"}]},
  {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#d6def2"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#05102a"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4a5a80"}]}
]
"""

/**
 * The shared Google Map, restyled for Warivo.
 *
 * Real Google Maps needs Google Play Services on the device and a Maps API key
 * (local.properties → MAPS_API_KEY); without either it renders blank. The Map panel and
 * the Home preview both use this — panels are swapped with `when`, so only one map is ever
 * composed at once.
 *
 * [content] draws map overlays (route polylines, destination markers) and must contain
 * only Google-Map composables.
 */
@Composable
fun WarivoMap(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    interactive: Boolean = true,
    myLocation: Boolean = false,
    content: @Composable @GoogleMapComposable () -> Unit = {},
) {
    val context = LocalContext.current
    // isMyLocationEnabled throws without the permission; GpsService already holds it when
    // running, but guard anyway so a cold start never crashes the map.
    val hasLocation = remember {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapType = MapType.NORMAL,
            mapStyleOptions = MapStyleOptions(WARIVO_MAP_STYLE),
            isMyLocationEnabled = myLocation && hasLocation,
        ),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = false,
            // A glance-only preview (the Home card) must not pan; the Map panel is one tap
            // away for that.
            scrollGesturesEnabled = interactive,
            zoomGesturesEnabled = interactive,
        ),
        content = content,
    )
}

