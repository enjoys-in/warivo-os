package com.warivo.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.ble.WarivoNodeClient
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoGreen
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim

/**
 * Choose which Warivo node (ESP32) this head unit connects to — the scooter-side twin of
 * the Bluetooth picker. Nodes are found by their fff0 service, so a custom firmware name
 * still shows up; picking one pins its BLE address in settings and reconnects.
 */
@Composable
fun NodePickerScreen(onBack: () -> Unit) {
    val node = Warivo.node
    val nodes by node.nodes.collectAsStateWithLifecycle()
    val discovering by node.discovering.collectAsStateWithLifecycle()
    val nodeState by node.state.collectAsStateWithLifecycle()
    val connectedAddr by node.deviceAddress.collectAsStateWithLifecycle()
    val selected by Warivo.settings.nodeAddress.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        node.startDiscovery()
        onDispose { node.stopDiscovery() }
    }

    fun choose(address: String?) {
        Warivo.settings.setNodeAddress(address)
        node.preferredAddress = address
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GhostCircleButton(Icons.Filled.ArrowBackIosNew, "Back to Settings", 44.dp) { onBack() }
                Text(
                    "Warivo node",
                    color = WarivoTextDim,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (discovering) {
                    CircularProgressIndicator(
                        color = WarivoAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
                OutlinedButton(onClick = { node.startDiscovery() }) { Text("Scan", maxLines = 1) }
            }

            Text(
                "Pick which scooter's node this head unit connects to. Nodes advertise the " +
                    "fff0 service; the firmware's default name is ${WarivoNodeClient.DEVICE_NAME}.",
                color = WarivoTextDim,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )

            WarivoCard(modifier = Modifier.fillMaxWidth(), padded = false) {
                NodeRow(
                    title = "Automatic",
                    subtitle = "Connect to the first Warivo node found",
                    selected = selected == null,
                    connected = false,
                ) { choose(null) }

                nodes.forEach { n ->
                    Divider()
                    val isConnected = nodeState == WarivoNodeClient.State.CONNECTED &&
                        connectedAddr?.equals(n.address, ignoreCase = true) == true
                    NodeRow(
                        title = n.name,
                        subtitle = if (n.rssi != 0) "${n.address} · ${signal(n.rssi)}" else n.address,
                        selected = selected?.equals(n.address, ignoreCase = true) == true,
                        connected = isConnected,
                    ) { choose(n.address) }
                }

                if (nodes.isEmpty()) {
                    Divider()
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (discovering) "Searching for nodes…"
                            else "No Warivo nodes found. Power the node, then Scan.",
                            color = WarivoTextDim,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    connected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableTile(onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconChip(Icons.Filled.Memory, size = 48.dp, radius = 16.dp)
        Column(Modifier.weight(1f)) {
            Text(title, color = WarivoText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = WarivoTextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
        }
        when {
            connected -> StateBadge("connected", WarivoGreen)
            selected -> StateBadge("selected", WarivoAccent)
            else -> Unit
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WarivoHairline)
    )
}

private fun signal(rssi: Int): String = when {
    rssi >= -60 -> "strong"
    rssi >= -75 -> "good"
    else -> "weak"
}
