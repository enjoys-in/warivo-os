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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
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
import com.warivo.os.bluetooth.BtDevice
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoGreen
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim

/**
 * Pair a Bluetooth speaker from inside the car UI, following the same card system as the
 * rest of Settings. This is how a rider adds audio output on a locked-down head unit that
 * never exposes the system Bluetooth screen — see [com.warivo.os.bluetooth.BluetoothController].
 */
@Composable
fun BluetoothScreen(onBack: () -> Unit) {
    val bt = Warivo.bluetooth
    val devices by bt.devices.collectAsStateWithLifecycle()
    val scanning by bt.scanning.collectAsStateWithLifecycle()

    // Discovery is battery-heavy, so it only runs while this screen is open.
    DisposableEffect(Unit) {
        bt.start()
        onDispose { bt.stop() }
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
                    "Bluetooth audio",
                    color = WarivoTextDim,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (scanning) {
                    CircularProgressIndicator(
                        color = WarivoAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
                OutlinedButton(enabled = bt.enabled, onClick = { bt.scan() }) {
                    Text("Scan", maxLines = 1)
                }
            }

            Text(
                "Pair a speaker for music. Warivo has its own picker, so you never need the " +
                    "phone's system settings.",
                color = WarivoTextDim,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )

            when {
                !bt.available -> WarivoCard(modifier = Modifier.fillMaxWidth()) {
                    CardLabel("Bluetooth")
                    Text(
                        "This phone has no Bluetooth radio.",
                        color = WarivoTextDim,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                !bt.enabled -> WarivoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        IconChip(Icons.Filled.Bluetooth, size = 48.dp, radius = 16.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Bluetooth is off", color = WarivoText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("Turn it on to find speakers", color = WarivoTextDim, fontSize = 14.sp)
                        }
                        Button(onClick = { bt.enable() }) { Text("Turn on") }
                    }
                }

                else -> WarivoCard(modifier = Modifier.fillMaxWidth(), padded = false) {
                    if (devices.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (scanning) "Searching for devices…"
                                else "No devices yet. Put your speaker in pairing mode, then Scan.",
                                color = WarivoTextDim,
                                fontSize = 16.sp,
                            )
                        }
                    } else {
                        devices.forEachIndexed { index, device ->
                            if (index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(WarivoHairline)
                                )
                            }
                            DeviceRow(device) { bt.select(device.address) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BtDevice, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableTile(onSelect)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconChip(
            if (device.audio) Icons.Filled.Speaker else Icons.Filled.Bluetooth,
            size = 48.dp,
            radius = 16.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(device.name, color = WarivoText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    device.connected -> "Output for music"
                    device.bonding -> "Pairing…"
                    device.bonded -> "Paired · tap to connect"
                    else -> "Tap to pair"
                },
                color = WarivoTextDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        when {
            device.connected -> StateBadge("connected", WarivoGreen)
            device.bonding -> StateBadge("pairing", WarivoAmber)
            device.bonded -> StateBadge("paired", WarivoAccent)
            else -> StateBadge("pair", WarivoTextDim)
        }
    }
}
