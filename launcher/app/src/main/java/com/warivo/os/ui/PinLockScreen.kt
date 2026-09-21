package com.warivo.os.ui

import androidx.compose.runtime.produceState
import com.warivo.os.ble.WarivoNodeClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.settings.WarivoSettings
import com.warivo.os.ui.theme.PageBrush
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoSurface
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import kotlinx.coroutines.delay

/**
 * The unlock screen, following branding/mockups/png/08-lock.png: the glowing mark, a
 * four-dot PIN field and a keypad.
 *
 * Shown after the boot animation, before the dashboard. It is the scooter's ignition as
 * far as the head unit is concerned — but only as far as the head unit: the scooter itself
 * still rides, because the node drives nothing (docs/FLEET.md §1). This keeps a stranger
 * out of the dashboard, the trip log and the tracking settings; it is not an immobiliser.
 *
 * No fingerprint key, unlike the mockup. `BiometricPrompt` needs a `FragmentActivity`
 * host and the androidx.biometric dependency, and on a phone cradled to a handlebar the
 * sensor is usually behind the mount anyway. Left out rather than half-built.
 */
@Composable
fun PinLockScreen(onUnlocked: () -> Unit) {
    val settings = Warivo.settings
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val nodeState by Warivo.node.state.collectAsStateWithLifecycle()

    // Clear the shake state shortly after a wrong PIN so the field is usable again.
    LaunchedEffect(wrong) {
        if (wrong) {
            delay(900)
            wrong = false
            entered = ""
        }
    }

    fun press(digit: String) {
        if (wrong || entered.length >= WarivoSettings.PIN_LENGTH) return
        entered += digit
        if (entered.length == WarivoSettings.PIN_LENGTH) {
            if (settings.checkPin(entered)) onUnlocked() else wrong = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBrush),
    ) {
        // Same strip as the shell, so the clock and the link are visible before unlocking —
        // whether the scooter is even connected is worth knowing from the lock screen.
        LockStatusStrip(nodeState)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlowingMark(size = 88.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                if (wrong) "Wrong PIN" else "Warivo is locked",
                color = if (wrong) WarivoRed else WarivoText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (wrong) "Try again" else "Enter your PIN to unlock the ride",
                color = WarivoTextDim,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(WarivoSettings.PIN_LENGTH) { index ->
                    val filled = index < entered.length
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (filled) {
                                    Modifier.background(if (wrong) WarivoRed else WarivoAccent)
                                } else {
                                    Modifier.border(
                                        2.dp,
                                        WarivoTextDim.copy(alpha = 0.5f),
                                        RoundedCornerShape(50),
                                    )
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Keypad(
                onDigit = ::press,
                onBackspace = { if (!wrong) entered = entered.dropLast(1) },
            )

            // Shown only while the PIN has never been changed. Once the owner sets one in
            // Settings this disappears, so the hint cannot outlive its usefulness and
            // become a printed password on the dashboard.
            if (settings.pinIsDefault) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "DEFAULT PIN",
                        color = WarivoTextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        WarivoSettings.DEFAULT_PIN,
                        color = WarivoAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        "· change it in Settings",
                        color = WarivoTextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun Keypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            .forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { digit -> PinKey(digit) { onDigit(digit) } }
                }
            }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Empty slot where the mockup puts fingerprint; see the file comment.
            Spacer(Modifier.size(KEY_SIZE))
            PinKey("0") { onDigit("0") }
            Box(
                modifier = Modifier
                    .size(KEY_SIZE)
                    .clip(RoundedCornerShape(50))
                    .clickableTile(onBackspace),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = WarivoTextDim,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(KEY_SIZE)
            .clip(RoundedCornerShape(50))
            .background(WarivoSurface.copy(alpha = 0.55f))
            .border(1.dp, WarivoHairline, RoundedCornerShape(50))
            .clickableTile(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = WarivoText, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Big enough to hit without aiming, which is the whole job of a keypad on a vehicle. */
private val KEY_SIZE = 76.dp

/**
 * A slim strip above the keypad: clock, date, and whether the scooter is even connected.
 *
 * Worth having before unlocking — "is the node paired" is exactly the question you have
 * while standing next to the scooter, and making someone unlock to find out is pointless.
 */
@Composable
private fun LockStatusStrip(nodeState: WarivoNodeClient.State) {
    val clock by produceState(initialValue = strips()) {
        while (true) {
            value = strips()
            delay(15_000)
        }
    }
    val telemetry by Warivo.node.telemetry.collectAsStateWithLifecycle()
    val connected = nodeState == WarivoNodeClient.State.CONNECTED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clock.first, color = WarivoText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(12.dp))
        Text(
            buildString {
                append(clock.second)
                telemetry?.tempOutC?.let { append(" · ${it.toInt()}°C") }
            },
            color = WarivoTextDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        StatusChip(
            if (connected) WarivoNodeClient.DEVICE_NAME else "Scooter not connected",
            if (connected) WarivoAccent else WarivoTextDim,
            dot = true,
        )
    }
}

private fun strips(): Pair<String, String> =
    SimpleDateFormat("HH:mm", Locale.US).format(Date()) to
        SimpleDateFormat("EEE · d MMM", Locale.US).format(Date())
