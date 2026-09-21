package com.warivo.os.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.kiosk.KioskController
import com.warivo.os.location.GpsService
import com.warivo.os.settings.BeepSource
import com.warivo.os.settings.SavedPlace
import com.warivo.os.settings.WarivoSettings
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoGreen
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.util.Locale

/**
 * Settings, laid out as branding/mockups/png/06-settings.png: a row of quick toggles over
 * two detail cards.
 *
 * The radio toggles act through [KioskController], which only really works as Device
 * Owner — a stock install will show them but be unable to force a radio on, so each one
 * reflects real state rather than what was tapped.
 */
@Composable
fun SettingsPanel(
    kiosk: KioskController,
    onExitKiosk: () -> Unit,
    onReleaseDevice: () -> Unit,
) {
    val context = LocalContext.current
    val settings = Warivo.settings
    val beepSource by settings.beepSource.collectAsStateWithLifecycle()
    val beepCm by settings.beepCm.collectAsStateWithLifecycle()
    val kioskEnabled by settings.kioskEnabled.collectAsStateWithLifecycle()
    val nodeState by Warivo.node.state.collectAsStateWithLifecycle()
    val nodeAddress by Warivo.node.deviceAddress.collectAsStateWithLifecycle()
    val lifetimeKm by Warivo.trips.lifetimeKm.collectAsStateWithLifecycle()
    val gpsOn by Warivo.gpsActive.collectAsStateWithLifecycle()
    val wifiOn by Warivo.wifiActive.collectAsStateWithLifecycle()
    val speedAlertOn by settings.speedAlertOn.collectAsStateWithLifecycle()
    val speedAlertKmh by settings.speedAlertKmh.collectAsStateWithLifecycle()
    val places by settings.places.collectAsStateWithLifecycle()
    val fix by GpsService.fix.collectAsStateWithLifecycle()

    var confirmRelease by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        Text("Settings", color = WarivoText, fontSize = 30.sp, fontWeight = FontWeight.Bold)

        // ---- quick toggles ----
        Row(horizontalArrangement = Arrangement.spacedBy(GridGap), modifier = Modifier.fillMaxWidth()) {
            QuickToggleCard(
                icon = Icons.Filled.Wifi,
                name = "Wi-Fi",
                detail = if (wifiOn) "on · maps and search" else "off",
                checked = wifiOn,
                modifier = Modifier.weight(1f),
            ) { kiosk.enableRadios() }
            QuickToggleCard(
                icon = Icons.Filled.Bluetooth,
                name = "Bluetooth",
                detail = if (nodeState == WarivoNodeClient.State.CONNECTED) {
                    WarivoNodeClient.DEVICE_NAME
                } else {
                    "node not connected"
                },
                checked = nodeState == WarivoNodeClient.State.CONNECTED,
                modifier = Modifier.weight(1f),
            ) { Warivo.node.reconnect() }
            QuickToggleCard(
                icon = Icons.Filled.LocationOn,
                name = "GPS",
                detail = if (gpsOn) "high accuracy" else "off",
                checked = gpsOn,
                modifier = Modifier.weight(1f),
            ) { kiosk.enableRadios() }
            QuickToggleCard(
                icon = Icons.Filled.CheckBox,
                name = "Kiosk lock",
                detail = if (kiosk.isDeviceOwner) "Warivo launcher only" else "needs Device Owner",
                checked = kioskEnabled,
                enabled = kiosk.isDeviceOwner,
                modifier = Modifier.weight(1f),
            ) { enabled ->
                settings.setKioskEnabled(enabled)
                if (!enabled) onExitKiosk()
            }
        }

        // ---- detail cards ----
        Row(
            horizontalArrangement = Arrangement.spacedBy(GridGap),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            WarivoCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CardLabel("Warivo node")
                Spacer(Modifier.height(6.dp))
                SettingsRow(
                    icon = Icons.Filled.Memory,
                    title = "ESP32-C6 telemetry link",
                    subtitle = nodeAddress?.let { "${WarivoNodeClient.DEVICE_NAME} · BLE fff0 · $it" }
                        ?: "${WarivoNodeClient.DEVICE_NAME} · BLE fff0",
                    showDivider = false,
                ) {
                    val connected = nodeState == WarivoNodeClient.State.CONNECTED
                    StateBadge(
                        if (connected) "paired" else "searching",
                        if (connected) WarivoGreen else WarivoAmber,
                    )
                }
                SettingsRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Proximity beep",
                    subtitle = when (beepSource) {
                        BeepSource.OFF -> "off — opt in below"
                        BeepSource.PHONE -> "chimes on the phone speaker"
                        BeepSource.NODE -> "chimes on the node buzzer"
                    },
                ) {
                    Text(
                        if (beepSource == BeepSource.OFF) "Off" else "$beepCm cm",
                        color = WarivoAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (beepSource != BeepSource.OFF) {
                    Slider(
                        value = beepCm.toFloat(),
                        onValueChange = { settings.setBeepCm(it.toInt()) },
                        valueRange = 10f..80f,
                        steps = 13,
                    )
                }
                BeepSourceRow(current = beepSource, onSelect = settings::setBeepSource)
                SettingsRow(
                    icon = Icons.Filled.Speed,
                    title = "Speed alert",
                    subtitle = if (speedAlertOn) {
                        "Chimes once above the threshold"
                    } else {
                        "Off"
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (speedAlertOn) {
                            Text(
                                "$speedAlertKmh km/h",
                                color = WarivoAccent,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        WarivoToggle(checked = speedAlertOn) { settings.setSpeedAlertOn(it) }
                    }
                }
                if (speedAlertOn) {
                    Slider(
                        value = speedAlertKmh.toFloat(),
                        onValueChange = { settings.setSpeedAlertKmh(it.toInt()) },
                        valueRange = 20f..100f,
                        steps = 15,
                    )
                }
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "Warivo OS",
                    subtitle = "Version 0.1 · NOVA-S · never compiled",
                ) {
                    Text(
                        "${fmt(lifetimeKm, 1)} km",
                        color = WarivoText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            WarivoCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CardLabel("Display & device")
                Spacer(Modifier.height(6.dp))
                SettingsRow(
                    icon = Icons.Filled.VolumeUp,
                    title = "Media volume",
                    subtitle = "Routes to the paired speaker",
                    showDivider = false,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { adjust(context, -1) }) { Text("−") }
                        OutlinedButton(onClick = { adjust(context, +1) }) { Text("+") }
                    }
                }
                SettingsRow(
                    icon = Icons.Filled.DarkMode,
                    title = "Night theme",
                    subtitle = "Always dark · head unit",
                ) {
                    // Not a setting: a head unit has no light mode, and offering the
                    // switch would imply one exists.
                    Text("Always", color = WarivoTextDim, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                SettingsRow(
                    icon = Icons.Filled.CreditCard,
                    title = "Units",
                    subtitle = "Metric · km/h",
                ) {
                    Text("km", color = WarivoAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                SettingsRow(
                    icon = Icons.Filled.Bookmark,
                    title = "Saved places",
                    subtitle = placesSubtitle(places),
                ) {
                    // Saved from the current fix, because there is no geocoder here to
                    // turn a typed address into coordinates.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = fix != null,
                            onClick = {
                                fix?.let {
                                    settings.setPlace(
                                        WarivoSettings.PLACE_HOME, it.latitude, it.longitude
                                    )
                                }
                            },
                        ) { Text("Home = here") }
                        OutlinedButton(
                            enabled = fix != null,
                            onClick = {
                                fix?.let {
                                    settings.setPlace(
                                        WarivoSettings.PLACE_WORK, it.latitude, it.longitude
                                    )
                                }
                            },
                        ) { Text("Work = here") }
                    }
                }
                SettingsRow(
                    icon = Icons.Filled.RestartAlt,
                    title = "Trip data",
                    subtitle = "Resets the current trip only",
                ) {
                    OutlinedButton(onClick = { Warivo.trips.resetTrip() }) { Text("Reset") }
                }

                Spacer(Modifier.weight(1f))

                if (!kiosk.isDeviceOwner) {
                    Text(
                        "Kiosk is not provisioned. On a factory-reset phone with no " +
                            "accounts:\nadb shell dpm set-device-owner " +
                            "com.warivo.os/.kiosk.AdminReceiver",
                        color = WarivoAmber,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                // The escape hatch. Without it, a provisioned phone with a broken build
                // has to be factory reset to become a phone again.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onExitKiosk) { Text("Unlock now") }
                    Button(onClick = { confirmRelease = true }) { Text("Release phone") }
                }
            }
        }
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text("Release this phone?") },
            text = {
                Text(
                    "This drops Device Owner and every kiosk policy, so the phone becomes " +
                        "an ordinary phone again. Device Owner cannot be granted again " +
                        "without another factory reset."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRelease = false
                    Warivo.settings.setKioskEnabled(false)
                    onReleaseDevice()
                }) { Text("Release", color = WarivoRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRelease = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BeepSourceRow(current: BeepSource, onSelect: (BeepSource) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    ) {
        BeepSource.entries.forEach { source ->
            val selected = source == current
            OutlinedButton(onClick = { onSelect(source) }) {
                Text(
                    when (source) {
                        BeepSource.OFF -> "Off"
                        BeepSource.PHONE -> "Phone"
                        BeepSource.NODE -> "Node"
                    },
                    color = if (selected) WarivoAccent else WarivoTextDim,
                )
            }
        }
    }
}

private fun adjust(context: Context, direction: Int) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    runCatching {
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
    }
}

private fun fmt(value: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", value)

private fun placesSubtitle(places: Map<String, SavedPlace>): String {
    val saved = WarivoSettings.PLACE_KEYS.filter { places.containsKey(it) }
    return when (saved.size) {
        0 -> "None set — Home shows them as shortcuts"
        WarivoSettings.PLACE_KEYS.size -> "Home and Work set"
        else -> "${saved.first().replaceFirstChar { it.uppercase() }} set"
    }
}
