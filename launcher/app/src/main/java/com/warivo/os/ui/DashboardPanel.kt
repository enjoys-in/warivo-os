package com.warivo.os.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.warivo.os.ui.theme.WarivoSurfaceHigh
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
    val stale by Warivo.node.stale.collectAsStateWithLifecycle()
    val beepCm by Warivo.settings.beepCm.collectAsStateWithLifecycle()

    val t = telemetry
    if (t == null) {
        WaitingForNode(nodeState)
        return
    }

    Box(Modifier.fillMaxSize()) {
        // Dimmed rather than hidden: the last known values are still the best guess at
        // the scooter's state, but they must not look live.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (stale) 0.4f else 1f),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            SpeedColumn(t, beepCm = beepCm.toFloat(), modifier = Modifier.weight(0.31f))
            BatteryCard(t, modifier = Modifier.weight(0.45f))
            TileColumn(
                t = t,
                tripKm = trip.distanceKm,
                lifetimeKm = lifetimeKm,
                modifier = Modifier.weight(0.24f),
            )
        }
        val obstacle = t.distCm
        when {
            // Ranked by what the rider needs to know first. "The scooter will not move" wins
            // over a stalled link or a close obstacle: it explains a dead throttle, which is
            // otherwise indistinguishable from a broken scooter.
            t.immobilised == true -> Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                StatusChip("Immobilised — unlock in Settings", WarivoRed, dot = true)
            }
            t.immobiliseQueued -> Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                StatusChip("Locking when you stop", WarivoAmber, dot = true)
            }
            stale -> Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                StatusChip("Link stalled — last known values", WarivoAmber, dot = true)
            }
            obstacle != null && obstacle <= beepCm -> Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                StatusChip("Obstacle ${obstacle.toInt()} cm", WarivoRed, dot = true)
            }
        }
    }
}

@Composable
private fun SpeedColumn(t: Telemetry, beepCm: Float, modifier: Modifier = Modifier) {
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
        // read-only — Warivo OS never commands the scooter, the controller enforces the cap.
        t.gear?.let { gear ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedDisplay(
                    options = RIDE_MODES,
                    selectedIndex = (gear - 1).coerceIn(0, RIDE_MODES.lastIndex),
                    modifier = Modifier.fillMaxWidth(),
                )
                t.gearLimitKmh?.let { cap ->
                    CardLabel(
                        "Limited to ${cap.toInt()} km/h in this gear",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Renders only for signals the node actually sends, so an unwired scooter shows
        // nothing here rather than a row of dead lamps.
        val hasAux = t.hasSwitchSignals || t.throttlePct != null || t.distCm != null
        if (hasAux) {
            WarivoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (t.hasSwitchSignals) {
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
                    t.throttlePct?.let { throttle ->
                        LabelledMeter(
                            label = "Throttle",
                            value = "$throttle%",
                            fraction = throttle / 100f,
                        )
                    }
                    // The obstacle sensor drives the proximity beep; it was invisible on
                    // screen, which meant the only way to know why the phone had beeped
                    // was to guess. Bar fills as the obstacle gets closer.
                    t.distCm?.let { cm ->
                        val close = cm <= beepCm
                        LabelledMeter(
                            label = if (close) "Obstacle — close" else "Obstacle",
                            value = "${cm.toInt()} cm",
                            fraction = 1f - (cm / OBSTACLE_RANGE_CM).coerceIn(0f, 1f),
                            barColor = if (close) WarivoRed else WarivoAccent,
                        )
                    }
                }
            }
        }
    }
}

/** The IR sensor's usable range; the proximity bar is scaled against it. */
private const val OBSTACLE_RANGE_CM = 80f

/** A lead-acid battery this hot is being damaged now, whatever the other four read. */
private const val PACK_TEMP_HOT_C = 55f

/**
 * Spread across the five batteries that means one of them is the problem.
 *
 * They are in series, so they all pass the same current; in a healthy pack they track
 * each other within a couple of degrees. Once one is 8 C clear of the coldest it is
 * dissipating noticeably more than its neighbours, which is what a battery does on its
 * way out — and it shows up here long before it shows up in pack voltage.
 */
private const val PACK_TEMP_SPREAD_C = 8f

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
                if (t.batteryTempsC.isNotEmpty()) {
                    PackTempStrip(t, modifier = Modifier.padding(top = 18.dp))
                }
            }
        }
    }
}

/**
 * The five 12V batteries, one temperature each.
 *
 * Shown as five numbers rather than one because the average is the figure that hides the
 * fault: four batteries at 30 C and one at 60 C averages to a reassuring 36 C, and 36 C
 * is exactly what a healthy pack reads. So the outlier gets the colour, and the rider is
 * told which battery it is — that is the difference between "the pack is warm" and "take
 * battery 3 out".
 */
@Composable
private fun PackTempStrip(t: Telemetry, modifier: Modifier = Modifier) {
    val temps = t.batteryTempsC
    val hottest = t.hottestBatteryC
    val spread = t.batterySpreadC ?: 0f
    Column(modifier = modifier.fillMaxWidth()) {
        CardLabel(
            when {
                hottest != null && hottest >= PACK_TEMP_HOT_C ->
                    "Battery temps · ${t.hottestBattery} is hot"
                spread >= PACK_TEMP_SPREAD_C ->
                    "Battery temps · ${t.hottestBattery} is ${fmt(spread, 0)}°C above the coolest"
                else -> "Battery temps"
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            temps.forEachIndexed { index, temp ->
                val color = when {
                    // A probe that has stopped answering reads as absent, never as 0 °C:
                    // a dash is honest, and a cold-looking battery is not.
                    temp == null -> WarivoTextDim
                    temp >= PACK_TEMP_HOT_C -> WarivoRed
                    spread >= PACK_TEMP_SPREAD_C && temp == hottest -> WarivoAmber
                    else -> WarivoText
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarivoSurfaceHigh.copy(alpha = 0.55f))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (temp == null) "–" else "${temp.toInt()}°",
                        color = color,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${index + 1}",
                        color = WarivoTextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
        // The node's own average, when it reports one: it keeps counting through a BLE
        // dropout, where the phone-side figure silently loses that stretch of the ride.
        val avg = t.nodeAvgSpeedKmh
        if (avg != null) {
            StatTile(
                Icons.Filled.TrendingUp,
                "Avg speed",
                fmt(avg, 0),
                "km/h",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            // Named by battery when the per-battery probes are fitted: "Pack temp 47°C"
            // leaves the rider nothing to act on, "Battery 3 · hottest" does.
            val hottestLabel = t.hottestBattery?.let { "Battery $it · hottest" } ?: "Pack temp"
            StatTile(
                Icons.Filled.DeviceThermostat,
                if (t.tempBatC != null) hottestLabel else "Current",
                if (t.tempBatC != null) fmt(t.tempBatC, 0) else fmt(t.amps, 1),
                if (t.tempBatC != null) "°C" else "A",
                valueColor = if ((t.tempBatC ?: 0f) >= PACK_TEMP_HOT_C) WarivoRed else WarivoText,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
