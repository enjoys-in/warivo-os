package com.warivo.os.ui

import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoGreen
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.util.Locale

/**
 * About, following branding/mockups/png/07-about.png.
 *
 * Everything on this screen is read from the device rather than hardcoded, so it is
 * useful when something is wrong — which is the only time anyone opens an About screen.
 * Where the mockup shows a value we cannot actually obtain, it says so rather than
 * inventing a plausible one.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var showLicences by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    val nodeState by Warivo.node.state.collectAsStateWithLifecycle()
    val nodeAddress by Warivo.node.deviceAddress.collectAsStateWithLifecycle()
    val lifetimeKm by Warivo.trips.lifetimeKm.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GhostCircleButton(Icons.Filled.ArrowBackIosNew, "Back to Settings", 44.dp) { onBack() }
            Text(
                "Settings / About Warivo OS",
                color = WarivoTextDim,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
            IdentityCard(modifier = Modifier.weight(0.34f))

            Column(
                modifier = Modifier.weight(0.66f),
                verticalArrangement = Arrangement.spacedBy(GridGap),
            ) {
                WarivoCard(modifier = Modifier.fillMaxWidth()) {
                    CardLabel("Device")
                    Spacer(Modifier.height(6.dp))
                    SettingsRow(
                        icon = Icons.Filled.PhoneAndroid,
                        title = "Model",
                        subtitle = "${Build.MANUFACTURER} ${Build.DEVICE}",
                        showDivider = false,
                    ) { Value(Build.MODEL) }
                    SettingsRow(
                        icon = Icons.Filled.Memory,
                        title = "Build",
                        subtitle = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                    ) { Value(Build.ID) }
                    SettingsRow(
                        icon = Icons.Filled.Description,
                        title = "Device id",
                        // Build.getSerial() needs a privileged permission on API 29+, so
                        // the fleet id is the serial that actually identifies this unit.
                        subtitle = "Used by the fleet server",
                    ) { Value(Warivo.fleet.deviceId) }
                }

                WarivoCard(modifier = Modifier.fillMaxWidth()) {
                    CardLabel("Warivo node")
                    Spacer(Modifier.height(6.dp))
                    SettingsRow(
                        icon = Icons.Filled.Memory,
                        title = "ESP32-C6 telemetry link",
                        subtitle = nodeAddress?.let { "BLE fff0 · $it" } ?: "BLE fff0",
                        showDivider = false,
                    ) {
                        val paired = nodeState == WarivoNodeClient.State.CONNECTED
                        StateBadge(
                            if (paired) "paired" else "not connected",
                            if (paired) WarivoGreen else WarivoTextDim,
                        )
                    }
                    SettingsRow(
                        icon = Icons.Filled.Storage,
                        title = "Storage",
                        subtitle = "Internal",
                    ) { Value(storageLabel()) }
                    SettingsRow(
                        icon = Icons.Filled.Schedule,
                        title = "Uptime",
                        subtitle = "Since this phone last booted",
                    ) { Value(uptimeLabel()) }
                    SettingsRow(
                        icon = Icons.Filled.Schedule,
                        title = "Distance logged",
                        subtitle = "Lifetime, as this head unit counted it",
                    ) { Value("${fmt(lifetimeKm, 1)} km") }
                }

                WarivoCard(modifier = Modifier.fillMaxWidth()) {
                    CardLabel("Legal")
                    Spacer(Modifier.height(6.dp))
                    SettingsRow(
                        icon = Icons.Filled.Description,
                        title = "Open-source licences",
                        subtitle = "What this app is built from",
                        showDivider = false,
                    ) {
                        GhostCircleButton(Icons.Filled.Description, "Open licences", 40.dp) {
                            showLicences = true
                        }
                    }
                    SettingsRow(
                        icon = Icons.Filled.PrivacyTip,
                        title = "Privacy & data",
                        subtitle = "What leaves this device, and where it goes",
                    ) {
                        GhostCircleButton(Icons.Filled.PrivacyTip, "Open privacy", 40.dp) {
                            showPrivacy = true
                        }
                    }
                }
            }
        }
    }

    if (showLicences) {
        InfoDialog("Open-source licences", LICENCES) { showLicences = false }
    }
    if (showPrivacy) {
        InfoDialog("Privacy & data", privacyText()) { showPrivacy = false }
    }
}

@Composable
private fun IdentityCard(modifier: Modifier = Modifier) {
    WarivoCard(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardLabel("Warivo OS · NOVA-S", modifier = Modifier.padding(bottom = 10.dp))
            GlowingMark(size = 76.dp)
            Text(
                "Warivo",
                color = WarivoText,
                fontSize = 46.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .padding(horizontal = 0.dp)
            ) {
                StatusChip("Version $APP_VERSION", WarivoAccent)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Android ${Build.VERSION.RELEASE} · kiosk launcher",
                color = WarivoTextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            // No update channel exists. The mockup shows "Check for updates"; a button
            // that cannot check anything is worse than none, so this says what is true.
            Text(
                "No update channel configured. Builds are installed over ADB, or baked " +
                    "into the ROM.",
                color = WarivoTextDim,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Built by Enjoys",
                color = WarivoTextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun Value(text: String) {
    Text(text, color = WarivoText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(body, color = WarivoTextDim, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun storageLabel(): String = runCatching {
    val stat = StatFs(Environment.getDataDirectory().path)
    val total = stat.blockCountLong * stat.blockSizeLong
    val free = stat.availableBlocksLong * stat.blockSizeLong
    "${gb(total - free)} of ${gb(total)} used"
}.getOrDefault("unavailable")

private fun gb(bytes: Long): String =
    String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)

private fun uptimeLabel(): String {
    val minutes = SystemClock.elapsedRealtime() / 60_000
    val days = minutes / 1440
    val hours = (minutes % 1440) / 60
    return if (days > 0) "${days}d ${hours}h ${minutes % 60}m" else "${hours}h ${minutes % 60}m"
}

private fun fmt(value: Double, decimals: Int) =
    String.format(Locale.US, "%.${decimals}f", value)

private const val APP_VERSION = "0.1.0"

/**
 * Hand-maintained rather than generated.
 *
 * A licence screen that is out of date is worse than none, so this lists what the app
 * actually declares in build.gradle.kts — keep the two in step. Everything here is
 * permissively licensed; there is no copyleft in the tree.
 */
private val LICENCES = """
Warivo Launcher itself — see the repository.

Jetpack Compose, AndroidX Core, Lifecycle, Activity
  Apache License 2.0 — The Android Open Source Project

Kotlin standard library and kotlinx.coroutines
  Apache License 2.0 — JetBrains

MapLibre GL Native for Android
  BSD 2-Clause — MapLibre contributors
  (forked from Mapbox GL Native before its licence change)

Map tiles © OpenStreetMap contributors
  Open Database Licence (ODbL). Tiles from tile.openstreetmap.org are used under
  the OSM tile usage policy, which permits light personal use only.

Kaushan Script — Pablo Impallari
  SIL Open Font Licence 1.1 (used in the branding assets)
""".trimIndent()

/**
 * Written plainly on purpose. The head unit can upload a position every few seconds, and
 * a rider is entitled to read what that means in one screen rather than infer it.
 */
private fun privacyText(): String {
    val fleet = Warivo.fleet
    val endpoint = fleet.endpoint.value
    val reporting = if (endpoint.isBlank()) {
        "Reporting is OFF. Nothing leaves this device."
    } else {
        "Reporting is ON, to $endpoint, every ${fleet.intervalS.value} seconds."
    }
    return """
$reporting

When reporting is on, this device sends:
  · GPS position and accuracy
  · speed, battery charge and voltage, odometer, power
  · completed rides (distance, average and top speed, energy)
  · alerts you have configured (speed, battery, zone)
  · this device's id: ${fleet.deviceId}

It does not send: audio, contacts, browsing from the Search panel, or anything
about the phone's other apps.

Data is spooled on this device when there is no signal and uploaded later, so a
ride through a dead zone is still recorded.

The server is yours, not Warivo's. Where that data goes afterwards, how long it
is kept and who can see it are decisions made by whoever runs it.

If this scooter is ridden by someone other than its owner, they should be told it
reports its position. In many places that is also a legal requirement.

Reporting can be switched off by clearing the server in Settings → Tracking.
    """.trimIndent()
}
