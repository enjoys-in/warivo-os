package com.warivo.os.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.R
import com.warivo.os.Warivo
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.kiosk.KioskController
import com.warivo.os.ui.theme.AccentBrush
import com.warivo.os.ui.theme.ContentPadding
import com.warivo.os.ui.theme.RailWidth
import com.warivo.os.ui.theme.StatusBarHeight
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoBlack
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The four panels on the rail. Setup sits apart, at the foot, as in the mockups. */
private enum class Panel(val label: String, val icon: ImageVector) {
    DASHBOARD("Drive", Icons.Filled.Speed),
    MAP("Map", Icons.Filled.Navigation),
    MUSIC("Music", Icons.Filled.MusicNote),
    SEARCH("Search", Icons.Filled.Search),
}

/**
 * The rider-facing shell, matching `.app` in branding/mockups/warivo.css: a 104dp left
 * navigation rail, a 60dp status bar, and the panel.
 *
 * The rail is on the left rather than the bottom because that is what the mockups
 * specify, and because a landscape head unit has width to spare and height to protect —
 * a bottom bar would cost the speed gauge its diameter.
 *
 * Deliberately not a swipeable pager: the map and the search WebView both consume
 * horizontal drags, so swiping panels would fight them on exactly the two panels where it
 * matters.
 */
@Composable
fun WarivoRoot(
    kiosk: KioskController,
    onExitKiosk: () -> Unit,
    onReleaseDevice: () -> Unit,
) {
    var panel by remember { mutableStateOf(Panel.DASHBOARD) }
    var settingsOpen by remember { mutableStateOf(false) }
    val nodeState by Warivo.node.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(PageBrush)) {
        Row(Modifier.fillMaxSize()) {
            NavRail(
                selected = panel,
                settingsOpen = settingsOpen,
                onSelect = { panel = it; settingsOpen = false },
                onSettings = { settingsOpen = true },
                onPower = onExitKiosk,
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                StatusBar(nodeState)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        settingsOpen -> SettingsPanel(
                            kiosk = kiosk,
                            onExitKiosk = onExitKiosk,
                            onReleaseDevice = onReleaseDevice,
                        )
                        panel == Panel.DASHBOARD -> DashboardPanel()
                        panel == Panel.MAP -> MapPanel()
                        panel == Panel.MUSIC -> MusicPanel()
                        panel == Panel.SEARCH -> SearchPanel()
                    }
                }
            }
        }
    }
}

/** The page gradient from `body` in the mockup stylesheet. */
private val PageBrush: Brush
    get() = Brush.verticalGradient(
        listOf(Color(0xFF0A1B40), Color(0xFF061534), WarivoBlack)
    )

// ---- rail -----------------------------------------------------------------

@Composable
private fun NavRail(
    selected: Panel,
    settingsOpen: Boolean,
    onSelect: (Panel) -> Unit,
    onSettings: () -> Unit,
    onPower: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(RailWidth)
            .background(
                Brush.verticalGradient(listOf(Color(0xBF09183A), Color(0x8C06112A)))
            )
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Brand tile: the W monogram already shipped as the launcher icon foreground.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Warivo",
                tint = Color.Unspecified,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        Panel.entries.forEach { entry ->
            RailButton(
                icon = entry.icon,
                label = entry.label,
                active = !settingsOpen && entry == selected,
                onClick = { onSelect(entry) },
            )
        }

        Spacer(Modifier.weight(1f))

        RailButton(
            icon = Icons.Filled.Settings,
            label = "Setup",
            active = settingsOpen,
            onClick = onSettings,
        )
        // Unlocks the kiosk. The mockups put a power glyph here; it is the visible half of
        // the escape hatch, so it stays reachable from every panel.
        RailButton(
            icon = Icons.Filled.PowerSettingsNew,
            label = "Unlock",
            active = false,
            onClick = onPower,
        )
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(width = RailWidth, height = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The glowing tab on the rail's inner edge that marks the active panel.
        if (active) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .size(width = 5.dp, height = 30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(WarivoAccent)
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .then(
                    if (active) Modifier.background(AccentBrush, RoundedCornerShape(20.dp))
                    else Modifier
                )
                .clickableTile(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) WarivoBlack else WarivoTextDim,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

// ---- status bar -----------------------------------------------------------

/**
 * Clock, link state, outside temperature and radio state. A head unit with no clock makes
 * you reach for the phone it replaced, so the time is the first thing on the bar.
 */
@Composable
private fun StatusBar(nodeState: WarivoNodeClient.State) {
    val clock by produceState(initialValue = formatClock()) {
        while (true) {
            value = formatClock()
            delay(15_000)
        }
    }
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    val gpsOn by Warivo.gpsActive.collectAsStateWithLifecycle()
    val wifiOn by Warivo.wifiActive.collectAsStateWithLifecycle()
    val (linkColor, linkLabel) = linkAppearance(nodeState)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(StatusBarHeight)
            .padding(horizontal = ContentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(clock, color = WarivoText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        StatusChip(linkLabel, linkColor, dot = true)
        Spacer(Modifier.weight(1f))
        telemetry?.tempOutC?.let { outside ->
            StatusChip(
                "${outside.toInt()}°C",
                WarivoTextDim,
                icon = Icons.Filled.DeviceThermostat,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioGlyph(Icons.Filled.LocationOn, gpsOn)
            RadioGlyph(Icons.Filled.Bluetooth, nodeState == WarivoNodeClient.State.CONNECTED)
            RadioGlyph(Icons.Filled.Wifi, wifiOn)
        }
    }
}

@Composable
private fun RadioGlyph(icon: ImageVector, on: Boolean) {
    Icon(
        icon,
        contentDescription = null,
        tint = if (on) WarivoAccent else WarivoTextDim,
        modifier = Modifier.size(19.dp),
    )
}

/** Link health in the words the rider needs: connected, looking, or what is blocking it. */
private fun linkAppearance(state: WarivoNodeClient.State): Pair<Color, String> = when (state) {
    WarivoNodeClient.State.CONNECTED -> WarivoAccent to WarivoNodeClient.DEVICE_NAME
    WarivoNodeClient.State.SCANNING -> WarivoAmber to "Searching…"
    WarivoNodeClient.State.CONNECTING -> WarivoAmber to "Connecting…"
    WarivoNodeClient.State.BLUETOOTH_OFF -> WarivoRed to "Bluetooth off"
    WarivoNodeClient.State.NO_PERMISSION -> WarivoRed to "Permission needed"
    WarivoNodeClient.State.IDLE -> WarivoTextDim to "Idle"
}

private fun formatClock(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())
