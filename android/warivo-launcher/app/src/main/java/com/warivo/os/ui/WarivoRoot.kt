package com.warivo.os.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.R
import com.warivo.os.Warivo
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.kiosk.KioskController
import com.warivo.os.settings.BeepSource
import com.warivo.os.ui.theme.AccentBrush
import com.warivo.os.ui.theme.DockBottomInset
import com.warivo.os.ui.theme.DockBrush
import com.warivo.os.ui.theme.DockHeight
import com.warivo.os.ui.theme.DockRadius
import com.warivo.os.ui.theme.DockSideInset
import com.warivo.os.ui.theme.PageBrush
import com.warivo.os.ui.theme.ScreenBottomInset
import com.warivo.os.ui.theme.ScreenSideInset
import com.warivo.os.ui.theme.TopBarHeight
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoBlack
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The five panels in the dock. */
enum class Panel(val label: String, val icon: ImageVector) {
    DASHBOARD("Drive", Icons.Filled.Speed),
    MAP("Map", Icons.Filled.Navigation),
    MUSIC("Music", Icons.Filled.MusicNote),
    SEARCH("Search", Icons.Filled.Search),
    SETTINGS("Settings", Icons.Filled.WbSunny),
}

/**
 * The rider-facing shell, matching branding/mockups: a status strip and a dock pill that
 * both **float over** the content, as on Android Automotive / Tesla / Polestar head units.
 *
 * Floating rather than stacked means the map and the artwork run edge to edge behind the
 * chrome; the content area is simply inset to clear both.
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

    Box(
        Modifier
            .fillMaxSize()
            .background(PageBrush)
    ) {
        // Content first, so the chrome draws over it.
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    top = if (panel == Panel.MAP) 0.dp else TopBarHeight,
                    start = if (panel == Panel.MAP) 0.dp else ScreenSideInset,
                    end = if (panel == Panel.MAP) 0.dp else ScreenSideInset,
                    bottom = if (panel == Panel.MAP) 0.dp else ScreenBottomInset,
                )
        ) {
            when (panel) {
                Panel.DASHBOARD -> DashboardPanel()
                // The map is the one panel that wants the whole surface; it places its own
                // floating cards clear of the chrome.
                Panel.MAP -> MapPanel(onOpenSearch = { panel = Panel.SEARCH })
                Panel.MUSIC -> MusicPanel()
                Panel.SEARCH -> SearchPanel()
                Panel.SETTINGS -> SettingsPanel(
                    kiosk = kiosk,
                    onExitKiosk = onExitKiosk,
                    onReleaseDevice = onReleaseDevice,
                )
            }
        }

        TopBar(nodeState, modifier = Modifier.align(Alignment.TopCenter))

        Dock(
            selected = panel,
            kiosk = kiosk,
            onSelect = { panel = it },
            onExitKiosk = onExitKiosk,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ---- status strip ---------------------------------------------------------

/**
 * Clock and date, link state, radios, and the profile mark. A head unit with no clock
 * makes you reach for the phone it replaced, so the time leads.
 */
@Composable
private fun TopBar(nodeState: WarivoNodeClient.State, modifier: Modifier = Modifier) {
    val clock by produceState(initialValue = formatClock() to formatDate()) {
        while (true) {
            value = formatClock() to formatDate()
            delay(15_000)
        }
    }
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    val gpsOn by Warivo.gpsActive.collectAsStateWithLifecycle()
    val wifiOn by Warivo.wifiActive.collectAsStateWithLifecycle()
    val (linkColor, linkLabel) = linkAppearance(nodeState)

    val outside = telemetry?.tempOutC
    val dateLine = buildString {
        append(clock.second)
        if (outside != null) append(" · ${outside.toInt()}°C")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clock.first, color = WarivoText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(12.dp))
        Text(
            dateLine,
            color = WarivoTextDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        StatusChip(linkLabel, linkColor, dot = true)
        Spacer(Modifier.size(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioGlyph(Icons.Filled.Navigation, gpsOn)
            RadioGlyph(Icons.Filled.Bluetooth, nodeState == WarivoNodeClient.State.CONNECTED)
            RadioGlyph(Icons.Filled.Wifi, wifiOn)
        }
        Spacer(Modifier.size(14.dp))
        ProfileMark()
    }
}

@Composable
private fun RadioGlyph(icon: ImageVector, on: Boolean) {
    Icon(
        icon,
        contentDescription = null,
        tint = if (on) WarivoAccent else WarivoTextDim,
        modifier = Modifier.size(20.dp),
    )
}

/** `.avatar` — the script W in an accent disc. */
@Composable
private fun ProfileMark() {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(50))
            .background(AccentBrush, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "W",
            color = WarivoBlack,
            fontSize = 26.sp,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
        )
    }
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
private fun formatDate(): String = SimpleDateFormat("EEE · d MMM", Locale.US).format(Date())

// ---- dock ----------------------------------------------------------------

/**
 * The floating dock: panels on the left, vehicle-ish controls on the right.
 *
 * The right-hand group is deliberately limited to things the phone actually owns — the
 * proximity beep, media volume and the kiosk lock. The mockups show car controls there,
 * but Warivo OS displays scooter state and must never command the scooter.
 */
@Composable
private fun Dock(
    selected: Panel,
    kiosk: KioskController,
    onSelect: (Panel) -> Unit,
    onExitKiosk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val beepSource by Warivo.settings.beepSource.collectAsStateWithLifecycle()
    val kioskEnabled by Warivo.settings.kioskEnabled.collectAsStateWithLifecycle()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DockSideInset)
            .padding(bottom = DockBottomInset)
            .height(DockHeight)
            .clip(RoundedCornerShape(DockRadius))
            .background(DockBrush, RoundedCornerShape(DockRadius))
            .border(1.dp, WarivoHairline, RoundedCornerShape(DockRadius))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(60.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Warivo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(50.dp),
                )
            }
            Panel.entries.forEach { entry ->
                DockTile(
                    icon = entry.icon,
                    label = entry.label,
                    active = entry == selected,
                    onClick = { onSelect(entry) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlButton(
                Icons.Filled.NotificationsActive,
                "Proximity beep",
                on = beepSource != BeepSource.OFF,
            ) {
                Warivo.settings.setBeepSource(
                    if (beepSource == BeepSource.OFF) BeepSource.PHONE else BeepSource.OFF
                )
            }
            ControlButton(Icons.Filled.VolumeDown, "Volume down") { adjustVolume(context, -1) }
            ControlButton(
                if (kioskEnabled) Icons.Filled.Lock else Icons.Filled.LockOpen,
                if (kioskEnabled) "Unlock kiosk" else "Kiosk unlocked",
                on = kioskEnabled,
            ) {
                // Only ever unlocks from here. Turning the kiosk on is a deliberate act in
                // Settings, not a stray tap on the dock.
                if (kioskEnabled) {
                    Warivo.settings.setKioskEnabled(false)
                    onExitKiosk()
                }
            }
            ControlButton(Icons.Filled.VolumeUp, "Volume up") { adjustVolume(context, +1) }
        }
    }
}

private fun adjustVolume(context: Context, direction: Int) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    runCatching {
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
    }
}

@Composable
private fun DockTile(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (active) Modifier.background(AccentBrush, RoundedCornerShape(22.dp))
                else Modifier
            )
            .clickableTile(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) WarivoBlack else WarivoTextDim,
            modifier = Modifier.size(30.dp),
        )
    }
}
