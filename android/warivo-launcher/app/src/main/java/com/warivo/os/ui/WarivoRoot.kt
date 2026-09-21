package com.warivo.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.kiosk.KioskController
import com.warivo.os.ui.theme.DockHeight
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoAqua
import com.warivo.os.ui.theme.WarivoBlack
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Panel(val label: String, val icon: ImageVector) {
    DASHBOARD("Drive", Icons.Filled.Speed),
    MAP("Map", Icons.Filled.Map),
    MUSIC("Music", Icons.Filled.MusicNote),
    SEARCH("Search", Icons.Filled.Search),
    SETTINGS("Setup", Icons.Filled.Settings),
}

/**
 * The whole rider-facing surface: a slim status bar, one panel, and a bottom dock.
 *
 * The layout follows the reference head units rather than a phone app — cards on
 * near-black, and navigation in a **bottom dock of unlabelled monochrome icons**. The
 * dock sits at the bottom because that is the edge a thumb reaches on a bar-mounted
 * phone, and it is the same five targets in the same places every time, so it needs no
 * aim at speed.
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
    val nodeState by Warivo.node.state.collectAsStateWithLifecycle()

    Surface(color = WarivoBlack, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            StatusBar(nodeState)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (panel) {
                    Panel.DASHBOARD -> DashboardPanel()
                    Panel.MAP -> MapPanel()
                    Panel.MUSIC -> MusicPanel()
                    Panel.SEARCH -> SearchPanel()
                    Panel.SETTINGS -> SettingsPanel(
                        kiosk = kiosk,
                        onExitKiosk = onExitKiosk,
                        onReleaseDevice = onReleaseDevice,
                    )
                }
            }
            Dock(selected = panel, onSelect = { panel = it })
        }
    }
}

/**
 * Wordmark, clock, link state. The clock ticks once a minute — a head unit with no clock
 * makes you reach for the phone it replaced.
 */
@Composable
private fun StatusBar(nodeState: WarivoNodeClient.State) {
    val clock by produceState(initialValue = formatClock()) {
        while (true) {
            value = formatClock()
            delay(15_000)
        }
    }
    val (linkColor, linkLabel) = linkAppearance(nodeState)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "WARIVO",
                color = WarivoAqua,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Text(clock, color = WarivoText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Pill(linkLabel, linkColor)
    }
}

/** Link health in the words the rider needs: connected, looking, or what is blocking it. */
private fun linkAppearance(state: WarivoNodeClient.State): Pair<Color, String> = when (state) {
    WarivoNodeClient.State.CONNECTED -> WarivoAqua to "LINKED"
    WarivoNodeClient.State.SCANNING -> WarivoAmber to "SEARCHING"
    WarivoNodeClient.State.CONNECTING -> WarivoAmber to "CONNECTING"
    WarivoNodeClient.State.BLUETOOTH_OFF -> WarivoRed to "BLUETOOTH OFF"
    WarivoNodeClient.State.NO_PERMISSION -> WarivoRed to "PERMISSION"
    WarivoNodeClient.State.IDLE -> WarivoTextDim to "IDLE"
}

private fun formatClock(): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date())

@Composable
private fun Dock(selected: Panel, onSelect: (Panel) -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WarivoHairline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DockHeight)
                .background(WarivoBlack),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Panel.entries.forEach { entry ->
                DockButton(
                    panel = entry,
                    selected = entry == selected,
                    onClick = { onSelect(entry) },
                )
            }
        }
    }
}

@Composable
private fun DockButton(panel: Panel, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) WarivoAqua else WarivoTextDim
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .clickableTile(onClick)
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            panel.icon,
            contentDescription = panel.label,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
        // A dot rather than a label: the reference docks are unlabelled, and five icons
        // learned once do not need words on every glance.
        Box(Modifier.height(GridGap), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(WarivoAqua)
                )
            }
        }
    }
}

/** Shown on panels that need a heading inside the content area. */
@Composable
fun PanelTitle(text: String) {
    Text(
        text.uppercase(),
        color = WarivoTextDim,
        style = MaterialTheme.typography.labelMedium,
    )
}
