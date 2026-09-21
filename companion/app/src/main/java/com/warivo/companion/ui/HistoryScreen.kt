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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.companion.Owner
import com.warivo.companion.model.RideSummary
import com.warivo.companion.net.ApiResult
import com.warivo.companion.ui.theme.Gap
import com.warivo.companion.ui.theme.WarivoAccent
import com.warivo.companion.ui.theme.WarivoAmber
import com.warivo.companion.ui.theme.WarivoText
import com.warivo.companion.ui.theme.WarivoTextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where the scooter has been, ride by ride, with the totals on top. */
@Composable
fun HistoryScreen() {
    val deviceId by Owner.store.deviceId.collectAsStateWithLifecycle()
    var rides by remember { mutableStateOf<List<RideSummary>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deviceId) {
        if (deviceId.isBlank()) {
            error = "No scooter selected — pick one in Setup"
            return@LaunchedEffect
        }
        when (val result = Owner.api.rides(deviceId)) {
            is ApiResult.Ok -> { rides = result.value; error = null }
            is ApiResult.Failed -> error = result.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Text("History", color = WarivoText, fontSize = 26.sp, fontWeight = FontWeight.Bold)

        error?.let { message ->
            OwnerCard(Modifier.fillMaxWidth()) {
                Text(message, color = WarivoAmber, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (rides.isNotEmpty()) {
            OwnerCard(Modifier.fillMaxWidth()) {
                Label("Totals · ${rides.size} rides")
                Spacer(Modifier.height(10.dp))
                val totalKm = rides.sumOf { it.km }
                val totalWh = rides.sumOf { it.wh }
                KeyValueRow("Distance", "${fmt(totalKm, 1)} km")
                KeyValueRow("Fastest", "${rides.maxOf { it.maxKmh }.toInt()} km/h")
                if (totalWh > 0 && totalKm > 0.1) {
                    // Only shown with a current sensor fitted; otherwise the node reports
                    // no energy and an average would be a divide by nothing.
                    KeyValueRow("Consumption", "${(totalWh / totalKm).toInt()} Wh/km")
                }
            }
        }

        if (rides.isEmpty() && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No rides recorded yet.",
                    color = WarivoTextDim,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rides, key = { it.startedMs }) { ride ->
                OwnerCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            "${fmt(ride.km, 1)} km",
                            color = WarivoAccent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stamp(ride.startedMs),
                            color = WarivoTextDim,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        buildList {
                            add("${ride.avgKmh.toInt()} km/h avg")
                            add("${ride.maxKmh.toInt()} max")
                            add(duration(ride.endedMs - ride.startedMs))
                            if (ride.wh > 0 && ride.km > 0.05) {
                                add("${(ride.wh / ride.km).toInt()} Wh/km")
                            }
                        }.joinToString(" · "),
                        color = WarivoTextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private fun stamp(ms: Long): String =
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.US).format(Date(ms))

private fun duration(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"
}

private fun fmt(value: Double, decimals: Int) =
    String.format(Locale.US, "%.${decimals}f", value)
