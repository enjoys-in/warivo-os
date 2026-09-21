package com.warivo.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.companion.Owner
import com.warivo.companion.model.AlertEvent
import com.warivo.companion.model.FleetSettings
import com.warivo.companion.model.Sample
import com.warivo.companion.model.Zone
import com.warivo.companion.net.ApiResult
import com.warivo.companion.ui.theme.Gap
import com.warivo.companion.ui.theme.WarivoAccent
import com.warivo.companion.ui.theme.WarivoAmber
import com.warivo.companion.ui.theme.WarivoGreen
import com.warivo.companion.ui.theme.WarivoRed
import com.warivo.companion.ui.theme.WarivoText
import com.warivo.companion.ui.theme.WarivoTextDim
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Alert thresholds, the zone, and what has fired.
 *
 * Thresholds are pushed to the scooter, which evaluates them itself — so they still fire
 * with no signal and arrive late rather than never.
 */
@Composable
fun AlertsScreen() {
    val deviceId by Owner.store.deviceId.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(FleetSettings()) }
    var events by remember { mutableStateOf<List<AlertEvent>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var zoneHere by remember { mutableStateOf<Sample?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(deviceId) {
        if (deviceId.isBlank()) {
            status = "No scooter selected — pick one in Setup"
            return@LaunchedEffect
        }
        when (val result = Owner.api.events(deviceId)) {
            is ApiResult.Ok -> events = result.value
            is ApiResult.Failed -> status = result.message
        }
        when (val latest = Owner.api.latest(deviceId)) {
            is ApiResult.Ok -> zoneHere = latest.value
            is ApiResult.Failed -> Unit   // a zone can wait for a position
        }
    }

    fun push(next: FleetSettings) {
        settings = next
        scope.launch {
            status = when (val result = Owner.api.putConfig(deviceId, next)) {
                is ApiResult.Ok -> "Saved — the scooter picks it up on its next report"
                is ApiResult.Failed -> result.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Text("Alerts", color = WarivoText, fontSize = 26.sp, fontWeight = FontWeight.Bold)

        OwnerCard(Modifier.fillMaxWidth()) {
            Label("Thresholds")
            Spacer(Modifier.height(10.dp))

            KeyValueRow("Speed", "${settings.speedAlertKmh} km/h", WarivoAccent)
            Slider(
                value = settings.speedAlertKmh.toFloat(),
                onValueChange = { settings = settings.copy(speedAlertKmh = it.toInt()) },
                onValueChangeFinished = { push(settings) },
                valueRange = 20f..100f,
                steps = 15,
            )

            KeyValueRow("Battery", "${settings.batteryAlertPct}%", WarivoAccent)
            Slider(
                value = settings.batteryAlertPct.toFloat(),
                onValueChange = { settings = settings.copy(batteryAlertPct = it.toInt()) },
                onValueChangeFinished = { push(settings) },
                valueRange = 5f..50f,
                steps = 8,
            )

            KeyValueRow("Report every", "${settings.intervalS}s", WarivoAccent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 30).forEach { option ->
                    OutlineButton(
                        "${option}s",
                        tint = if (option == settings.intervalS) WarivoAccent else WarivoTextDim,
                    ) { push(settings.copy(intervalS = option)) }
                }
            }
        }

        OwnerCard(Modifier.fillMaxWidth()) {
            Label("Zone")
            Spacer(Modifier.height(8.dp))
            Text(
                if (settings.zones.isEmpty()) {
                    "No zone set. A zone alerts you when the scooter leaves the area."
                } else {
                    settings.zones.joinToString("\n") {
                        "${it.id} · ${it.radiusM.toInt()} m radius"
                    }
                },
                color = WarivoTextDim,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Centred on the scooter's last position, because that is the only
                // coordinate this app reliably has — there is no map-picker or geocoder
                // here yet, and inventing one would be a worse guess than "where it is".
                val here = zoneHere
                if (here?.lat != null && here.lon != null) {
                    listOf(500, 2000).forEach { radius ->
                        PrimaryButton("Zone ${radius}m here") {
                            push(
                                settings.copy(
                                    zones = listOf(
                                        Zone("home", here.lat, here.lon, radius.toDouble())
                                    )
                                )
                            )
                        }
                    }
                } else {
                    Text(
                        "Waiting for a position to centre a zone on.",
                        color = WarivoTextDim,
                        fontSize = 14.sp,
                    )
                }
            }
            if (settings.zones.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                OutlineButton("Clear zone", tint = WarivoRed) {
                    push(settings.copy(zones = emptyList()))
                }
            }
        }

        status?.let { message ->
            Text(
                message,
                color = if (message.startsWith("Saved")) WarivoGreen else WarivoAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Label("Recent alerts")
        if (events.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Nothing has fired.", color = WarivoTextDim)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { it.tsMs.toString() + it.type }) { event ->
                    OwnerCard(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Badge(label(event.type), colour(event.type))
                            Text(
                                stamp(event.tsMs),
                                color = WarivoTextDim,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        val detail = describe(event)
                        if (detail.isNotBlank()) {
                            Text(
                                detail,
                                color = WarivoText,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun label(type: String) = when (type) {
    "speed" -> "over speed"
    "battery" -> "low battery"
    "zone_exit" -> "left zone"
    "zone_enter" -> "back in zone"
    "power_on" -> "switched on"
    else -> type.replace('_', ' ')
}

private fun colour(type: String) = when (type) {
    "speed", "zone_exit" -> WarivoRed
    "battery" -> WarivoAmber
    "zone_enter" -> WarivoGreen
    else -> WarivoTextDim
}

private fun describe(event: AlertEvent): String = with(event.detail) {
    when (event.type) {
        "speed" -> "Reached ${optInt("kmh")} km/h (limit ${optInt("limit")})"
        "battery" -> "Charge fell to ${optInt("soc")}%"
        "zone_exit", "zone_enter" ->
            if (has("lat")) {
                String.format(Locale.US, "At %.5f, %.5f", optDouble("lat"), optDouble("lon"))
            } else {
                ""
            }
        else -> ""
    }
}

private fun stamp(ms: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.US).format(Date(ms))
