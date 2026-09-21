package com.warivo.os.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.text.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.fleet.UplinkState
import com.warivo.os.kiosk.KioskController
import com.warivo.os.location.GpsService
import com.warivo.os.settings.BeepSource
import com.warivo.os.settings.SavedPlace
import com.warivo.os.settings.WarivoSettings
import com.warivo.os.trip.Ride
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoGreen
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.text.SimpleDateFormat
import java.util.Date
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
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    val pinEnabled by settings.pinEnabled.collectAsStateWithLifecycle()
    val brightness by settings.brightness.collectAsStateWithLifecycle()
    val rides by Warivo.trips.history.rides.collectAsStateWithLifecycle()

    var confirmRelease by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
                .height(DETAIL_CARD_HEIGHT),
        ) {
            WarivoCard(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
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
                // Battery health, straight from the node's coulomb counting. Cycles are
                // the number that actually predicts a lead-acid pack's remaining life.
                SettingsRow(
                    icon = Icons.Filled.BatteryChargingFull,
                    title = "Battery health",
                    subtitle = telemetry?.let { t ->
                        buildList {
                            t.chargeCycles?.let { add("$it charge cycles") }
                            t.nodeWhPerKm?.let { add("${it.toInt()} Wh/km") }
                            t.mileageKm?.let { add("~${it.toInt()} km per charge") }
                        }.joinToString(" · ").ifEmpty { "node has not reported yet" }
                    } ?: "node not connected",
                ) {
                    Text(
                        telemetry?.chargeCycles?.toString() ?: "—",
                        color = WarivoAccent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
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

            WarivoCard(modifier = Modifier.weight(0.85f).fillMaxHeight()) {
                CardLabel("Display & device")
                Spacer(Modifier.height(6.dp))
                SettingsRow(
                    icon = Icons.Filled.BrightnessHigh,
                    title = "Brightness",
                    subtitle = "This screen only",
                    showDivider = false,
                ) {
                    Text(
                        "${(brightness * 100).toInt()}%",
                        color = WarivoAccent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = brightness,
                    onValueChange = { settings.setBrightness(it) },
                    valueRange = WarivoSettings.MIN_BRIGHTNESS..1f,
                )
                SettingsRow(
                    icon = Icons.Filled.VolumeUp,
                    title = "Media volume",
                    subtitle = "Routes to the paired speaker",
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
                    icon = Icons.Filled.Lock,
                    title = "Screen lock",
                    subtitle = if (pinEnabled) {
                        if (settings.pinIsDefault) {
                            "PIN on — still the default, change it"
                        } else {
                            "PIN on"
                        }
                    } else {
                        "Off — boots straight to Home"
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(onClick = { changingPin = true }) { Text("Change") }
                        WarivoToggle(checked = pinEnabled) { settings.setPinEnabled(it) }
                    }
                }
                SettingsRow(
                    icon = Icons.Filled.RestartAlt,
                    title = "Trip data",
                    subtitle = "Resets the current trip only",
                ) {
                    OutlinedButton(onClick = { Warivo.trips.resetTrip() }) { Text("Reset") }
                }
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "About Warivo OS",
                    subtitle = "Version, device, node, licences and privacy",
                ) {
                    OutlinedButton(onClick = { showAbout = true }) { Text("Open") }
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

            RidesCard(rides = rides, modifier = Modifier.weight(0.85f).fillMaxHeight())
        }

        ScooterLockCard(modifier = Modifier.fillMaxWidth())
        TrackingCard(modifier = Modifier.fillMaxWidth())
    }

    if (changingPin) {
        ChangePinDialog(
            onSave = { pin -> settings.setPin(pin); changingPin = false },
            onDismiss = { changingPin = false },
        )
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

/**
 * The offline ride log. Rides are segmented out of the telemetry stream by the wheel
 * sensor, so this fills in by itself as the scooter is used — there is nothing to start
 * or stop.
 */
@Composable
private fun RidesCard(rides: List<Ride>, modifier: Modifier = Modifier) {
    WarivoCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardLabel("Recent rides")
            if (rides.isNotEmpty()) {
                Text(
                    "${rides.size}",
                    color = WarivoTextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (rides.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No rides logged yet.\nA ride is recorded once the wheel turns.",
                    color = WarivoTextDim,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@WarivoCard
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(rides, key = { it.startedAtMs }) { ride ->
                Column(Modifier.padding(vertical = 9.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            "${fmt(ride.distanceKm, 1)} km",
                            color = WarivoText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            rideWhen(ride.startedAtMs),
                            color = WarivoTextDim,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        buildList {
                            add("${ride.avgSpeedKmh.toInt()} km/h avg")
                            add("${ride.maxSpeedKmh.toInt()} max")
                            add(durationLabel(ride.durationMs))
                            if (ride.consumptionWhPerKm > 0) {
                                add("${ride.consumptionWhPerKm.toInt()} Wh/km")
                            }
                        }.joinToString(" · "),
                        color = WarivoTextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun rideWhen(startedAtMs: Long): String =
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.US).format(Date(startedAtMs))

private fun durationLabel(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"
}

/** Fixed rather than weighted, because the panel scrolls; see the Column above. */
private val DETAIL_CARD_HEIGHT = 330.dp

/**
 * Location reporting, alerts and the remote lock.
 *
 * Shown on the head unit, not hidden in the owner's app, and reporting stays off until an
 * endpoint is set — a rider should be able to see that the scooter reports where it goes.
 * See docs/FLEET.md §1.
 */
@Composable
private fun TrackingCard(modifier: Modifier = Modifier) {
    val fleet = Warivo.fleet
    val endpoint by fleet.endpoint.collectAsStateWithLifecycle()
    val token by fleet.token.collectAsStateWithLifecycle()
    val intervalS by fleet.intervalS.collectAsStateWithLifecycle()
    val batteryPct by fleet.batteryAlertPct.collectAsStateWithLifecycle()
    val zones by fleet.zones.collectAsStateWithLifecycle()
    val state by fleet.state.collectAsStateWithLifecycle()
    val lastOk by fleet.lastOkAtMs.collectAsStateWithLifecycle()
    val configVersion by fleet.configVersion.collectAsStateWithLifecycle()

    WarivoCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardLabel("Tracking & alerts")
            StateBadge(
                when (state) {
                    UplinkState.OFF -> "off"
                    UplinkState.IDLE -> "reporting"
                    UplinkState.SENDING -> "sending"
                    UplinkState.OFFLINE -> "offline"
                    UplinkState.REJECTED -> "rejected"
                },
                when (state) {
                    UplinkState.IDLE, UplinkState.SENDING -> WarivoGreen
                    UplinkState.OFFLINE -> WarivoAmber
                    UplinkState.REJECTED -> WarivoRed
                    UplinkState.OFF -> WarivoTextDim
                },
            )
        }
        Spacer(Modifier.height(6.dp))

        SettingsRow(
            icon = Icons.Filled.CloudUpload,
            title = "Report position",
            subtitle = if (endpoint.isBlank()) {
                "Off — no server configured"
            } else {
                "Every ${intervalS}s to $endpoint"
            },
            showDivider = false,
        ) {
            Text(
                if (lastOk == 0L) "never" else "sent ${agoLabel(lastOk)}",
                color = WarivoTextDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Typed here rather than only in the owner app so a device can be pointed at a
        // server without one, which is what you want while bringing the server up.
        FleetField(
            label = "Server (https only)",
            value = endpoint,
            placeholder = "https://fleet.example.com",
            onChange = fleet::setEndpoint,
        )
        FleetField(
            label = "Device token",
            value = token,
            placeholder = "paste the token from your server",
            masked = true,
            onChange = fleet::setToken,
        )

        SettingsRow(
            icon = Icons.Filled.Fingerprint,
            title = "Device id",
            subtitle = "The server keys everything on this",
        ) {
            Text(
                fleet.deviceId,
                color = WarivoAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        SettingsRow(
            icon = Icons.Filled.Timer,
            title = "Interval",
            subtitle = "How often a position is recorded",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 30).forEach { option ->
                    OutlinedButton(onClick = { fleet.setIntervalS(option) }) {
                        Text(
                            "${option}s",
                            color = if (option == intervalS) WarivoAccent else WarivoTextDim,
                        )
                    }
                }
            }
        }

        SettingsRow(
            icon = Icons.Filled.BatteryAlert,
            title = "Battery alert",
            subtitle = "Alerts the owner below this charge",
        ) {
            Text(
                "$batteryPct%",
                color = WarivoAccent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        SettingsRow(
            icon = Icons.Filled.MyLocation,
            title = "Zones",
            subtitle = if (zones.isEmpty()) {
                "None — set them in the owner app"
            } else {
                zones.joinToString(" · ") { "${it.id} ${it.radiusM.toInt()}m" }
            },
        ) {
            Text(
                "config v$configVersion",
                color = WarivoTextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            "Speed, battery and zone alerts are evaluated on this device, so they still " +
                "fire with no signal and upload later. The owner can lock this display " +
                "remotely; that does not and cannot stop the scooter.",
            color = WarivoTextDim,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }
}

@Composable
private fun FleetField(
    label: String,
    value: String,
    placeholder: String,
    masked: Boolean = false,
    onChange: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
        CardLabel(label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(color = WarivoText, fontSize = 16.sp),
                cursorBrush = SolidColor(WarivoAccent),
                // Masked only visually: the token still has to be pasteable and checkable
                // on a screen bolted to a scooter.
                visualTransformation = if (masked && draft.isNotBlank()) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.weight(1f),
            )
            if (draft != value) {
                OutlinedButton(onClick = { onChange(draft) }) { Text("Save") }
            }
        }
        if (draft.isBlank()) {
            Text(placeholder, color = WarivoTextDim, fontSize = 14.sp)
        }
    }
}

private fun agoLabel(atMs: Long): String {
    val seconds = ((System.currentTimeMillis() - atMs) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86_400}d ago"
    }
}

/**
 * Sets a new unlock PIN.
 *
 * Requires it twice. A head unit that has been locked with a mistyped PIN needs a factory
 * reset of the app to recover, so confirming is worth one extra field.
 */
@Composable
private fun ChangePinDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val complete = first.length == WarivoSettings.PIN_LENGTH && first == second

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change unlock PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PinEntryField("New ${WarivoSettings.PIN_LENGTH}-digit PIN", first) { first = it }
                PinEntryField("Repeat it", second) { second = it }
                if (first.isNotEmpty() && second.isNotEmpty() && first != second) {
                    Text("Those do not match.", color = WarivoRed, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = complete, onClick = { onSave(first) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinEntryField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        CardLabel(label)
        BasicTextField(
            value = value,
            onValueChange = { typed ->
                // Digits only, and never longer than a PIN: filtering here means the
                // dialog cannot produce a value setPin would silently reject.
                onChange(typed.filter { it.isDigit() }.take(WarivoSettings.PIN_LENGTH))
            },
            singleLine = true,
            textStyle = TextStyle(color = WarivoText, fontSize = 24.sp, letterSpacing = 8.sp),
            cursorBrush = SolidColor(WarivoAccent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * The immobiliser.
 *
 * Deliberately a separate card from "Screen lock", and worded to keep them apart: one
 * blanks this display, the other stops the scooter being ridden. Conflating them in a UI
 * is how someone locks the wrong thing and walks away.
 *
 * The card reports three distinct states, because they are genuinely different:
 * unavailable (no relay fitted), pending (asked for, waiting for the wheel to stop) and
 * engaged. See docs/IMMOBILIZER.md.
 */
@Composable
private fun ScooterLockCard(modifier: Modifier = Modifier) {
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    var refused by remember { mutableStateOf<String?>(null) }

    val immobilised = telemetry?.immobilised
    val queued = telemetry?.immobiliseQueued == true
    val fitted = immobilised != null

    WarivoCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardLabel("Scooter lock")
            when {
                !fitted -> StateBadge("no relay", WarivoTextDim)
                queued -> StateBadge("waiting to stop", WarivoAmber)
                immobilised == true -> StateBadge("immobilised", WarivoRed)
                else -> StateBadge("free to ride", WarivoGreen)
            }
        }
        Spacer(Modifier.height(6.dp))

        SettingsRow(
            icon = Icons.Filled.Lock,
            title = "Immobiliser",
            subtitle = when {
                !fitted -> "No relay fitted — see docs/IMMOBILIZER.md"
                queued -> "Engages as soon as the wheel stops"
                immobilised == true -> "Controller disabled; the throttle does nothing"
                else -> "Opens the controller's key-switch line when stopped"
            },
            showDivider = false,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = fitted && immobilised != true,
                    onClick = {
                        refused = if (Warivo.node.writeLock(true)) null
                        else "This node has no lock characteristic (fff4)."
                    },
                ) { Text("Lock") }
                OutlinedButton(
                    enabled = fitted && (immobilised == true || queued),
                    onClick = {
                        refused = if (Warivo.node.writeLock(false)) null
                        else "This node has no lock characteristic (fff4)."
                    },
                ) { Text("Unlock") }
            }
        }

        refused?.let { message ->
            Text(
                message,
                color = WarivoAmber,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }

        Text(
            "Your key still comes first: the relay sits in series with it, so the key off " +
                "means off as always. The node refuses to engage above walking pace no " +
                "matter what is asked of it, and it will not touch motor current, brakes " +
                "or steering. Keep the physical bypass reachable — a flat phone should " +
                "never be why you cannot ride home.",
            color = WarivoTextDim,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }
}
