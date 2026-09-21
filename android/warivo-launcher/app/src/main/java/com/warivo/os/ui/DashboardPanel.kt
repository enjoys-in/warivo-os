package com.warivo.os.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.model.Telemetry
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoAqua
import com.warivo.os.ui.theme.WarivoBlue
import com.warivo.os.ui.theme.WarivoGreen
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.util.Locale

/** Speedo full-scale. The scooter will not see this, but the ring needs a ceiling. */
private const val SPEED_MAX_KMH = 80f

/**
 * The Drive panel: a three-column card grid.
 *
 * Speed gets the largest card and the ring gauge because it is the only value read while
 * moving; everything else is a number you check at a stop, so it becomes a small card.
 */
@Composable
fun DashboardPanel() {
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    val nodeState by Warivo.node.state.collectAsStateWithLifecycle()
    val trip by Warivo.trips.trip.collectAsStateWithLifecycle()

    val t = telemetry
    if (t == null) {
        WaitingForNode(nodeState)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        // --- speed ---
        WarivoCard(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxHeight(),
            label = "Speed",
            trailingIcon = Icons.Filled.Speed,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                RingGauge(
                    fraction = t.speedKmh / SPEED_MAX_KMH,
                    color = WarivoAqua,
                    strokeWidth = 14f,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                ) {
                    GaugeReadout(t.speedKmh.toInt().toString(), "km/h")
                }
            }
            CardFooter(
                listOf(
                    FooterStat("Trip", "${fmt(trip.distanceKm, 1)} km"),
                    FooterStat("Avg", "${fmt(trip.avgSpeedKmh, 0)} km/h"),
                    FooterStat("Max", "${fmt(trip.maxSpeedKmh, 0)} km/h"),
                )
            )
        }

        // --- battery ---
        WarivoCard(
            modifier = Modifier
                .weight(0.30f)
                .fillMaxHeight(),
            label = "Battery",
            trailingIcon = Icons.Filled.BatteryChargingFull,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                RingGauge(
                    fraction = t.soc / 100f,
                    color = socColor(t.soc),
                    tickCount = 24,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${t.soc}",
                            color = socColor(t.soc),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("PERCENT", color = WarivoTextDim, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            CardFooter(
                listOf(
                    FooterStat("Pack", "${fmt(t.volts, 1)} V"),
                    FooterStat("Range", "${fmt(t.rangeKm, 0)} km"),
                )
            )
        }

        // --- the numbers you check at a stop ---
        Column(
            modifier = Modifier
                .weight(0.30f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(GridGap),
            ) {
                StatCard(
                    "Power",
                    t.watts.toInt().toString(),
                    "W",
                    valueColor = if (t.watts > 0) WarivoAqua else WarivoText,
                    modifier = Modifier.weight(1f),
                )
                StatCard("Current", fmt(t.amps, 1), "A", modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(GridGap),
            ) {
                StatCard("Odometer", fmt(t.odoKm, 1), "km", modifier = Modifier.weight(1f))
                StatCard(
                    "Use",
                    if (trip.consumptionWhPerKm > 0) fmt(trip.consumptionWhPerKm, 0) else "—",
                    "Wh/km",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(GridGap),
            ) {
                StatCard(
                    "Outside",
                    t.tempOutC?.let { fmt(it, 1) } ?: "—",
                    "°C",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    "Pack temp",
                    t.tempBatC?.let { fmt(it, 1) } ?: "—",
                    "°C",
                    valueColor = if ((t.tempBatC ?: 0f) > 55f) WarivoRed else WarivoText,
                    modifier = Modifier.weight(1f),
                )
            }
            // Only earns a row once the node reports at least one of these.
            if (t.hasSwitchSignals) {
                WarivoCard(modifier = Modifier.fillMaxWidth()) {
                    Telltales(t)
                }
            }
            if (t.distCm != null) {
                StatCard(
                    "Obstacle",
                    fmt(t.distCm, 0),
                    "cm",
                    valueColor = if (t.distCm < 40f) WarivoRed else WarivoText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Gear, throttle and the indicator lamps. Every one of these comes from the signal-tap
 * audit and is absent from the firmware today, so this row renders only what actually
 * arrives — it will fill in by itself once the taps are wired.
 */
@Composable
private fun Telltales(t: Telemetry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        t.gear?.let { gear ->
            Text("G$gear", color = WarivoBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        TextTelltale("R", t.reverse, WarivoRed)
        Telltale(Icons.Filled.TurnLeft, t.indLeft, WarivoAqua, "Left indicator")
        Telltale(Icons.Filled.TurnRight, t.indRight, WarivoAqua, "Right indicator")
        Telltale(Icons.Filled.Lightbulb, t.headlight, WarivoAmber, "Headlight")
        Telltale(Icons.Filled.FlashOn, t.highBeam, WarivoBlue, "High beam")
        Telltale(Icons.Filled.LightMode, t.parkingLight, WarivoAmber, "Parking light")
        t.throttlePct?.let { throttle ->
            Spacer(Modifier.weight(1f))
            Text("THR $throttle%", color = WarivoTextDim, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun WaitingForNode(state: WarivoNodeClient.State) {
    val message = when (state) {
        WarivoNodeClient.State.BLUETOOTH_OFF -> "Bluetooth is off"
        WarivoNodeClient.State.NO_PERMISSION -> "Location permission needed to scan for the node"
        WarivoNodeClient.State.CONNECTED -> "Connected — waiting for the first frame"
        WarivoNodeClient.State.IDLE -> "Node link idle"
        else -> "Looking for ${WarivoNodeClient.DEVICE_NAME}…"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "WARIVO",
                color = WarivoAqua,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(message, color = WarivoTextDim, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun socColor(soc: Int): Color = when {
    soc <= 15 -> WarivoRed
    soc <= 35 -> WarivoAmber
    else -> WarivoGreen
}

private fun fmt(value: Float, decimals: Int) = String.format(Locale.US, "%.${decimals}f", value)
private fun fmt(value: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", value)
