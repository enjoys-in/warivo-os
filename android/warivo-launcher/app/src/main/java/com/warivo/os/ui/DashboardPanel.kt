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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
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
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAccentDeep
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoGreen
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.util.Locale

/** Speedo full-scale. The scooter will not see this, but the ring needs a ceiling. */
private const val SPEED_MAX_KMH = 80f

/** Ride modes, in the order the scooter's gear switch reports them (see audit.md). */
private val RIDE_MODES = listOf("Eco", "City", "Sport")

/**
 * The Drive panel, laid out as branding/mockups/png/02-dashboard.png: speed on the left,
 * battery and range in the middle, and a column of stat tiles on the right.
 *
 * Speed gets the largest card and the only ring the rider reads while moving. The tiles
 * are the numbers you check at a stop, which is why they can be small.
 */
@Composable
fun DashboardPanel() {
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    val nodeState by Warivo.node.state.collectAsStateWithLifecycle()
    val trip by Warivo.trips.trip.collectAsStateWithLifecycle()
    val lifetimeKm by Warivo.trips.lifetimeKm.collectAsStateWithLifecycle()

    val t = telemetry
    if (t == null) {
        WaitingForNode(nodeState)
        return
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        SpeedColumn(t, modifier = Modifier.weight(0.31f))
        BatteryCard(t, modifier = Modifier.weight(0.45f))
        TileColumn(
            t = t,
            tripKm = trip.distanceKm,
            lifetimeKm = lifetimeKm,
            modifier = Modifier.weight(0.24f),
        )
    }
}

@Composable
private fun SpeedColumn(t: Telemetry, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        WarivoCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RingGauge(
                    fraction = t.speedKmh / SPEED_MAX_KMH,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            t.speedKmh.toInt().toString(),
                            color = WarivoText,
                            style = MaterialTheme.typography.displayLarge,
                        )
                        Text(
                            "KM / H",
                            color = WarivoTextDim,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                        )
                    }
                }
            }
        }

        // Ride mode, read from the gear switch. Shown only once that tap is wired, and
        // read-only — Warivo OS never commands the scooter.
        t.gear?.let { gear ->
            SegmentedDisplay(
                options = RIDE_MODES,
                selectedIndex = (gear - 1).coerceIn(0, RIDE_MODES.lastIndex),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (t.hasSwitchSignals) {
            WarivoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextTelltaleTile("R", t.reverse, WarivoRed)
                    TelltaleTile(Icons.Filled.FlashOn, t.highBeam, WarivoGreen, "High beam")
                    TelltaleTile(Icons.Filled.TurnLeft, t.indLeft, WarivoAccent, "Left indicator")
                    TelltaleTile(Icons.Filled.TurnRight, t.indRight, WarivoAccent, "Right indicator")
                    TelltaleTile(Icons.Filled.Lightbulb, t.headlight, WarivoAmber, "Headlight")
                    TelltaleTile(Icons.Filled.LightMode, t.parkingLight, WarivoAmber, "Parking light")
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(t: Telemetry, modifier: Modifier = Modifier) {
    WarivoCard(modifier = modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                RingGauge(
                    fraction = t.soc / 100f,
                    from = WarivoAccentDeep.copy(alpha = 0.9f),
                    to = socColor(t.soc),
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "${t.soc}",
                                color = socColor(t.soc),
                                fontSize = 62.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "%",
                                color = socColor(t.soc),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        CardLabel("${fmt(t.volts, 1)} V · Battery")
                    }
                }
            }

            Column(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.Center,
            ) {
                CardLabel("Estimated range")
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        fmt(t.rangeKm, 0),
                        color = WarivoText,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " km",
                        color = WarivoTextDim,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                SegmentBars(
                    fraction = t.soc / 100f,
                    modifier = Modifier.padding(top = 16.dp),
                )
                // Below ~15% a lead-acid pack sags hard, so the tail is not usable range.
                // Naming it a reserve is more honest than counting it.
                CardLabel(
                    "Eco reserve · ${fmt(t.rangeKm * 0.15f, 0)} km",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TileColumn(
    t: Telemetry,
    tripKm: Double,
    lifetimeKm: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        StatTile(
            Icons.Filled.Bolt,
            "Power",
            fmt(t.watts / 1000f, 1),
            "kW",
            valueColor = if (t.watts > 0) WarivoAccent else WarivoText,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        StatTile(
            Icons.Filled.ShowChart,
            "Trip",
            fmt(tripKm, 1),
            "km",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        StatTile(
            Icons.Filled.Schedule,
            "Odometer",
            fmt(maxOf(t.odoKm, lifetimeKm), 0),
            "km",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        StatTile(
            Icons.Filled.DeviceThermostat,
            if (t.tempBatC != null) "Pack temp" else "Current",
            if (t.tempBatC != null) fmt(t.tempBatC, 0) else fmt(t.amps, 1),
            if (t.tempBatC != null) "°C" else "A",
            valueColor = if ((t.tempBatC ?: 0f) > 55f) WarivoRed else WarivoText,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun WaitingForNode(state: WarivoNodeClient.State) {
    val message = when (state) {
        WarivoNodeClient.State.BLUETOOTH_OFF -> "Bluetooth is off"
        WarivoNodeClient.State.NO_PERMISSION ->
            "Location permission is needed to scan for the node"
        WarivoNodeClient.State.CONNECTED -> "Connected — waiting for the first frame"
        WarivoNodeClient.State.IDLE -> "Node link idle"
        else -> "Looking for ${WarivoNodeClient.DEVICE_NAME}…"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "WARIVO",
                color = WarivoAccent,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 10.sp,
            )
            Spacer(Modifier.height(12.dp))
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
