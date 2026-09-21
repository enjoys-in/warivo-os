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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.companion.Owner
import com.warivo.companion.model.Sample
import com.warivo.companion.net.ApiResult
import com.warivo.companion.ui.theme.CardRadius
import com.warivo.companion.ui.theme.Gap
import com.warivo.companion.ui.theme.WarivoAccent
import com.warivo.companion.ui.theme.WarivoAmber
import com.warivo.companion.ui.theme.WarivoGreen
import com.warivo.companion.ui.theme.WarivoRed
import com.warivo.companion.ui.theme.WarivoText
import com.warivo.companion.ui.theme.WarivoTextDim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where the scooter is now, and the owner's controls.
 *
 * Polls rather than holding a socket: a phone app that keeps a connection open to watch a
 * vehicle that reports every 10 seconds is spending battery to learn nothing sooner.
 */
@Composable
fun WhereScreen() {
    val deviceId by Owner.store.deviceId.collectAsStateWithLifecycle()
    var latest by remember { mutableStateOf<Sample?>(null) }
    var track by remember { mutableStateOf<List<Sample>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        if (deviceId.isBlank()) {
            error = "No scooter selected — pick one in Setup"
            return
        }
        when (val result = Owner.api.latest(deviceId)) {
            is ApiResult.Ok -> { latest = result.value; error = null }
            is ApiResult.Failed -> error = result.message
        }
        val now = System.currentTimeMillis()
        when (val result = Owner.api.track(deviceId, now - TRACK_WINDOW_MS, now)) {
            is ApiResult.Ok -> track = result.value.filter { it.hasPosition }
            is ApiResult.Failed -> Unit    // the position matters more than the trail
        }
    }

    LaunchedEffect(deviceId) {
        while (true) {
            refresh()
            delay(REFRESH_MS)
        }
    }

    fun send(type: String, message: String? = null) {
        scope.launch {
            pending = "Queued…"
            pending = when (val result = Owner.api.command(deviceId, type, message)) {
                // Queued, not delivered. The scooter may be offline for hours, and saying
                // "locked" before it has acknowledged would be a lie.
                is ApiResult.Ok -> "Queued — applies when the scooter is next online"
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Where", color = WarivoText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            OutlineButton("Refresh", Icons.Filled.Refresh) { scope.launch { refresh() } }
        }

        error?.let { message ->
            OwnerCard(Modifier.fillMaxWidth()) {
                Text(message, color = WarivoAmber, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(CardRadius))
        ) {
            TrackMap(latest = latest, track = track)
        }

        OwnerCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Label("Last report")
                val age = latest?.let { System.currentTimeMillis() - it.tsMs }
                Badge(
                    when {
                        age == null -> "no data"
                        age < 60_000 -> "live"
                        age < 3_600_000 -> "${age / 60_000}m ago"
                        else -> "${age / 3_600_000}h ago"
                    },
                    when {
                        age == null -> WarivoTextDim
                        age < 120_000 -> WarivoGreen
                        else -> WarivoAmber
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            val sample = latest
            if (sample == null) {
                Text(
                    "Nothing reported yet. The scooter uploads once its head unit is on " +
                        "and has a signal.",
                    color = WarivoTextDim,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                KeyValueRow("Seen", stamp(sample.tsMs))
                sample.speedKmh?.let { KeyValueRow("Speed", "${it.toInt()} km/h") }
                sample.soc?.let { soc ->
                    KeyValueRow(
                        "Battery",
                        "$soc%",
                        if (soc <= 20) WarivoRed else WarivoText,
                    )
                }
                sample.odoKm?.let { KeyValueRow("Odometer", "${it.toInt()} km") }
                if (sample.hasPosition) {
                    KeyValueRow(
                        "Position",
                        String.format(Locale.US, "%.5f, %.5f", sample.lat, sample.lon),
                    )
                    sample.accuracyM?.let { KeyValueRow("Accuracy", "±${it.toInt()} m") }
                }
            }
        }

        OwnerCard(Modifier.fillMaxWidth()) {
            Label("Controls")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Lock display", Icons.Filled.Lock, WarivoRed) {
                    send("lock_head_unit", "Reported stolen. Please call the owner.")
                }
                OutlineButton("Unlock", Icons.Filled.LockOpen, WarivoGreen) {
                    send("unlock_head_unit")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Sound alarm", Icons.Filled.NotificationsActive, WarivoAmber) {
                    send("alarm")
                }
                OutlineButton("Report now", Icons.Filled.Refresh) { send("ping") }
            }

            // The immobiliser, shown separately from the display lock because they do
            // genuinely different things and confusing them is how someone locks the
            // wrong one and walks away.
            val immobilised = latest?.immobilised
            Spacer(Modifier.height(14.dp))
            Label("Immobiliser")
            Spacer(Modifier.height(8.dp))
            if (immobilised == null) {
                Text(
                    "This scooter has no immobiliser relay fitted, so it cannot be " +
                        "stopped remotely — only its display can be locked.",
                    color = WarivoTextDim,
                    fontSize = 13.sp,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Badge(
                        if (immobilised) "immobilised" else "free to ride",
                        if (immobilised) WarivoRed else WarivoGreen,
                    )
                    OutlineButton("Immobilise", Icons.Filled.Lock, WarivoRed) {
                        send("immobilise")
                    }
                    OutlineButton("Release", Icons.Filled.LockOpen, WarivoGreen) {
                        send("release")
                    }
                }
                Text(
                    "Engaging waits until the scooter is stopped — the node refuses above " +
                        "walking pace, whatever this app asks. Releasing is immediate.",
                    color = WarivoTextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            pending?.let { message ->
                Text(
                    message,
                    color = WarivoAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Text(
                "Locking replaces the scooter's display and keeps it reporting. It does " +
                    "not stop the scooter — see Setup.",
                color = WarivoTextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private fun stamp(ms: Long): String =
    SimpleDateFormat("EEE d MMM, HH:mm:ss", Locale.US).format(Date(ms))

/** 12 hours: enough to see today's riding without pulling a week of samples over mobile. */
private const val TRACK_WINDOW_MS = 12 * 60 * 60 * 1000L
private const val REFRESH_MS = 15_000L
