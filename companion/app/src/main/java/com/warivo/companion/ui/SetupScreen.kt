package com.warivo.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.companion.Owner
import com.warivo.companion.model.Device
import com.warivo.companion.net.ApiResult
import com.warivo.companion.ui.theme.Gap
import com.warivo.companion.ui.theme.WarivoAccent
import com.warivo.companion.ui.theme.WarivoAmber
import com.warivo.companion.ui.theme.WarivoGreen
import com.warivo.companion.ui.theme.WarivoText
import com.warivo.companion.ui.theme.WarivoTextDim
import kotlinx.coroutines.launch

/** Server address, owner token, and which scooter this app is watching. */
@Composable
fun SetupScreen(firstRun: Boolean) {
    val store = Owner.store
    val baseUrl by store.baseUrl.collectAsStateWithLifecycle()
    val token by store.token.collectAsStateWithLifecycle()
    val deviceId by store.deviceId.collectAsStateWithLifecycle()

    var urlDraft by remember(baseUrl) { mutableStateOf(baseUrl) }
    var tokenDraft by remember(token) { mutableStateOf(token) }
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadDevices() {
        scope.launch {
            status = "Checking…"
            status = when (val result = Owner.api.devices()) {
                is ApiResult.Ok -> {
                    devices = result.value
                    // Pick the only scooter automatically; choosing from a list of one is
                    // busywork.
                    if (deviceId.isBlank() && result.value.size == 1) {
                        store.setDeviceId(result.value.first().deviceId)
                    }
                    if (result.value.isEmpty()) {
                        "Connected, but the server lists no devices yet"
                    } else {
                        "Connected · ${result.value.size} device(s)"
                    }
                }
                is ApiResult.Failed -> result.message
            }
        }
    }

    LaunchedEffect(baseUrl, token) {
        if (store.configured) loadDevices()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Text(
            if (firstRun) "Connect to your server" else "Setup",
            color = WarivoText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        if (firstRun) {
            Text(
                "Warivo does not run a service for you. Point this app at your own server " +
                    "— the same one the scooter reports to.",
                color = WarivoTextDim,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        OwnerCard(Modifier.fillMaxWidth()) {
            Label("Server")
            OwnerField(
                label = "Address (https only)",
                value = baseUrl,
                placeholder = "https://fleet.example.com",
                draft = urlDraft,
                onDraftChange = { urlDraft = it },
                onSave = { store.setBaseUrl(urlDraft) },
            )
            OwnerField(
                label = "Owner token",
                value = token,
                placeholder = "owner token, not the device token",
                draft = tokenDraft,
                onDraftChange = { tokenDraft = it },
                onSave = { store.setToken(tokenDraft) },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Test connection", enabled = store.configured) { loadDevices() }
            }
            status?.let { message ->
                Text(
                    message,
                    color = if (message.startsWith("Connected")) WarivoGreen else WarivoAmber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Text(
                "Use the owner token, never a device token: a device token sits on a " +
                    "scooter that might be stolen, and can only upload.",
                color = WarivoTextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (devices.isNotEmpty()) {
            OwnerCard(Modifier.fillMaxWidth()) {
                Label("Scooter")
                Spacer(Modifier.height(8.dp))
                devices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tap { store.setDeviceId(device.deviceId) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                device.name,
                                color = if (device.deviceId == deviceId) WarivoAccent else WarivoText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(device.deviceId, color = WarivoTextDim, fontSize = 13.sp)
                        }
                        Badge(
                            if (device.online) "online" else "offline",
                            if (device.online) WarivoGreen else WarivoTextDim,
                        )
                    }
                }
            }
        }

        OwnerCard(Modifier.fillMaxWidth()) {
            Label("What this app can and cannot do")
            Spacer(Modifier.height(8.dp))
            Text(
                "Locking sends a full-screen owner message to the scooter's display and " +
                    "keeps it reporting its position. It does not stop the scooter: the " +
                    "Warivo node only reads sensors and drives nothing. Cutting power to " +
                    "a moving vehicle is a crash, so no such command exists.",
                color = WarivoTextDim,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
