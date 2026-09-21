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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
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
import com.warivo.os.ui.theme.ContentPadding
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAccentDeep
import com.warivo.os.ui.theme.WarivoGreen
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.util.Locale

/** Speedo full-scale. The scooter will not see this, but the ring needs a ceiling. */
private const val SPEED_MAX_KMH = 80f

/** Gear names, so the mode pill reads like a scooter and not like an array index. */
private val GEAR_NAMES = mapOf(1 to "Eco", 2 to "City", 3 to "Sport")

/**
 * The Drive panel, laid out as branding/mockups/02-dashboard.html: a fixed speed cluster
 * on the left, and a right column of battery plus six tiles.
 *
 * Speed gets the whole left column because it is the only value read while moving.
 * Everything in the tile grid is checked at a stop, which is why it can be small.
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
        modifier = Modifier
            .fillMaxSize()
            .padding(start = ContentPadding, end = ContentPadding, bottom = ContentPadding, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SpeedCluster(t, modifier = Modifier.width(SPEED_COLUMN))

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            BatteryRow(t)
            TileGrid(t, trip.distanceKm, trip.avgSpeedKmh, lifetimeKm)
        }
    }
}

private val SPEED_COLUMN = 560.dp

@Composable
private fun SpeedCluster(t: Telemetry, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(GridGap)) {
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
                        // Appears only once the gear tap is wired (see audit.md).
                        t.gear?.let { gear ->
                            Spacer(Modifier.height(14.dp))
                            ModePill(GEAR_NAMES[gear] ?: "Gear $gear")
                        }
                    }
                }
            }
        }

        // Only earns its card once the node reports at least one switch signal.
        if (t.hasSwitchSignals) {
            WarivoCard(modifier = Modifier.fillMaxWidth(), padded = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TelltaleTile(Icons.Filled.TurnLeft, t.indLeft, WarivoGreen, "Left indicator")
                        TelltaleTile(Icons.Filled.TurnRight, t.indRight, WarivoGreen, "Right indicator")
                        TelltaleTile(Icons.Filled.Lightbulb, t.headlight, WarivoAmber, "Headlight")
                        TelltaleTile(Icons.Filled.FlashOn, t.highBeam, WarivoAccent, "High beam")
                        TelltaleTile(Icons.Filled.LightMode, t.parkingLight, WarivoAmber, "Parking light")
                        TextTelltaleTile("R", t.reverse, WarivoRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryRow(t: Telemetry) {
    Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
        WarivoCard(modifier = Modifier.width(300.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                RingGauge(
                    fraction = t.soc / 100f,
                    from = WarivoAccentDeep,
                    to = socColor(t.soc),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "${t.soc}",
                                color = socColor(t.soc),
                                fontSize = 66.sp,
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
        }

        WarivoCard(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                CardLabel("Est. range")
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        fmt(t.rangeKm, 0),
                        color = WarivoText,
                        fontSize = 62.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " km",
                        color = WarivoTextDim,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                }
                SegmentBars(
                    fraction = t.soc / 100f,
                    modifier = Modifier.padding(top = 14.dp),
                )
                // Below ~15% a lead-acid pack sags hard, so what is left is not really
                // usable range. Naming it as reserve is more honest than counting it.
                CardLabel(
                    "Reserve ${fmt(t.rangeKm * 0.15f, 0)} km",
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun TileGrid(
    t: Telemetry,
    tripKm: Double,
    avgSpeedKmh: Float,
    lifetimeKm: Double,
) {
    // A hand-built 3x2 grid rather than LazyVerticalGrid: it is six fixed cells that must
    // divide the leftover height exactly, which weights do and a lazy grid does not.
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            StatTile(
                Icons.Filled.Bolt,
                "Power",
                fmt(t.watts / 1000f, 1),
                "kW",
                valueColor = if (t.watts > 0) WarivoAccent else WarivoText,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                Icons.Filled.ShowChart,
                "Trip",
                fmt(tripKm, 1),
                "km",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                Icons.Filled.Schedule,
                "Odometer",
                fmt(maxOf(t.odoKm, lifetimeKm), 0),
                "km",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            StatTile(
                Icons.Filled.DeviceThermostat,
                "Pack temp",
                t.tempBatC?.let { fmt(it, 0) } ?: "—",
                "°C",
                valueColor = if ((t.tempBatC ?: 0f) > 55f) WarivoRed else WarivoText,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                Icons.Filled.FlashOn,
                "Current",
                fmt(t.amps, 1),
                "A",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                Icons.Filled.TrendingUp,
                "Avg speed",
                fmt(avgSpeedKmh, 0),
                "km/h",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
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
