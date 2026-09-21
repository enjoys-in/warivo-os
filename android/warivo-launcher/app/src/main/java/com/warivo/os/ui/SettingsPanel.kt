package com.warivo.os.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.kiosk.KioskController
import com.warivo.os.settings.BeepSource
import com.warivo.os.ui.theme.ContentPadding
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.util.Locale

/**
 * Setup. No mockup exists for this panel, so it is built from the same card system as the
 * ones that do — a labelled card per concern, values right-aligned.
 */
@Composable
fun SettingsPanel(
    kiosk: KioskController,
    onExitKiosk: () -> Unit,
    onReleaseDevice: () -> Unit,
) {
    val settings = Warivo.settings
    val beepSource by settings.beepSource.collectAsStateWithLifecycle()
    val beepCm by settings.beepCm.collectAsStateWithLifecycle()
    val kioskEnabled by settings.kioskEnabled.collectAsStateWithLifecycle()
    val nodeState by Warivo.node.state.collectAsStateWithLifecycle()
    val nodeAddress by Warivo.node.deviceAddress.collectAsStateWithLifecycle()
    val lifetimeKm by Warivo.trips.lifetimeKm.collectAsStateWithLifecycle()
    val trip by Warivo.trips.trip.collectAsStateWithLifecycle()

    var confirmRelease by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ContentPadding)
            .padding(bottom = ContentPadding, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        Section("Scooter link", Icons.Filled.BluetoothSearching) {
            KeyValue("State", nodeState.name.lowercase().replaceFirstChar { it.uppercase() })
            KeyValue("Node", nodeAddress ?: "not found")
            KeyValue("Trip", "${fmt(trip.distanceKm, 2)} km")
            KeyValue("Lifetime", "${fmt(lifetimeKm, 1)} km")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { Warivo.node.reconnect() }) { Text("Reconnect") }
                OutlinedButton(onClick = { Warivo.trips.resetTrip() }) { Text("Reset trip") }
            }
            if (nodeState == WarivoNodeClient.State.NO_PERMISSION) {
                Note(
                    "Scanning for ${WarivoNodeClient.DEVICE_NAME} needs the location " +
                        "permission — Android ties BLE discovery to location."
                )
            }
        }

        Section("Proximity beep", Icons.Filled.NotificationsActive) {
            Text(
                "Off by default. The node only beeps when it is told to; nothing else is " +
                    "ever written to the scooter.",
                color = WarivoTextDim,
                style = MaterialTheme.typography.bodyLarge,
            )
            BeepSourceRow(current = beepSource, onSelect = settings::setBeepSource)
            if (beepSource != BeepSource.OFF) {
                KeyValue("Trigger distance", "$beepCm cm")
                Slider(
                    value = beepCm.toFloat(),
                    onValueChange = { settings.setBeepCm(it.toInt()) },
                    valueRange = 10f..80f,
                    steps = 13,
                )
            }
        }

        Section("Kiosk", Icons.Filled.Lock) {
            KeyValue("Device Owner", if (kiosk.isDeviceOwner) "yes" else "no")
            KeyValue("Location services", if (kiosk.isLocationEnabled()) "on" else "off")

            if (!kiosk.isDeviceOwner) {
                Note(
                    "Not provisioned. On a factory-reset phone with no accounts, run:\n" +
                        "adb shell dpm set-device-owner com.warivo.os/.kiosk.AdminReceiver"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Lock to the dashboard on boot", color = WarivoText)
                Switch(
                    checked = kioskEnabled,
                    enabled = kiosk.isDeviceOwner,
                    onCheckedChange = { enabled ->
                        settings.setKioskEnabled(enabled)
                        if (!enabled) onExitKiosk()
                    },
                )
            }

            // The escape hatch. Without it, a provisioned phone with a broken build has to
            // be factory reset to become a phone again.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onExitKiosk) { Text("Unlock now") }
                Button(onClick = { confirmRelease = true }) { Text("Release phone") }
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
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    WarivoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconChip(icon, size = 38.dp, radius = 12.dp)
            Text(title, color = WarivoText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun BeepSourceRow(current: BeepSource, onSelect: (BeepSource) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BeepSource.entries.forEach { source ->
            val selected = source == current
            OutlinedButton(onClick = { onSelect(source) }) {
                Text(
                    when (source) {
                        BeepSource.OFF -> "Off"
                        BeepSource.PHONE -> "Phone speaker"
                        BeepSource.NODE -> "Node buzzer"
                    },
                    color = if (selected) WarivoAccent else WarivoTextDim,
                )
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = WarivoTextDim)
        Text(value, color = WarivoText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Note(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(
            Modifier
                .width(3.dp)
                .height(38.dp)
        )
        Text(text, color = WarivoAmber, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun fmt(value: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", value)
