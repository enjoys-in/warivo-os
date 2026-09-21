package com.warivo.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.os.Warivo
import com.warivo.os.fleet.UplinkState
import com.warivo.os.location.GpsService
import com.warivo.os.ui.theme.PageBrush
import com.warivo.os.ui.theme.WarivoRed
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.util.Locale

/**
 * Shown when the owner has sent `lock_head_unit`.
 *
 * What this is: the head unit replaced by the owner's message, with reporting continuing
 * underneath. What it is **not**, and cannot be: an immobiliser. The node is a read-only
 * tap and drives nothing on the scooter — see docs/FLEET.md §1. Cutting power to a moving
 * vehicle is a crash, so there is deliberately no verb for it anywhere in the protocol.
 *
 * It still does the useful thing: a thief gets the owner's phone number instead of a
 * working dashboard, and the scooter keeps saying where it is.
 *
 * There is no dismiss control. The lock clears when the owner sends `unlock_head_unit`,
 * which is the point — and it survives a reboot, because the message is persisted.
 */
@Composable
fun LockScreen(message: String) {
    val fix by GpsService.fix.collectAsStateWithLifecycle()
    val uplinkState by Warivo.fleet.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).padding(40.dp),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = WarivoRed,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "LOCKED",
                color = WarivoRed,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 10.sp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                message,
                color = WarivoText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(32.dp))

            // Stated plainly so a finder knows what is happening, and so nobody believes
            // the scooter itself has been disabled.
            Text(
                "This display has been locked remotely. The scooter's own controls are " +
                    "unaffected. Its position is being reported to the owner.",
                color = WarivoTextDim,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(
                    when (uplinkState) {
                        UplinkState.IDLE, UplinkState.SENDING -> "Reporting"
                        else -> "Reporting when online"
                    },
                    WarivoRed,
                    dot = true,
                )
                fix?.let {
                    StatusChip(
                        String.format(Locale.US, "%.4f, %.4f", it.latitude, it.longitude),
                        WarivoTextDim,
                    )
                }
            }
        }
    }
}
