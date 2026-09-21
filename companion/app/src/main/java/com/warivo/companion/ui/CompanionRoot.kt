package com.warivo.companion.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warivo.companion.Owner
import com.warivo.companion.ui.theme.AccentBrush
import com.warivo.companion.ui.theme.PageBrush
import com.warivo.companion.ui.theme.WarivoAccent
import com.warivo.companion.ui.theme.WarivoBlack
import com.warivo.companion.ui.theme.WarivoHairline
import com.warivo.companion.ui.theme.WarivoTextDim

private enum class Tab(val label: String, val icon: ImageVector) {
    MAP("Where", Icons.Filled.Map),
    HISTORY("History", Icons.Filled.History),
    ALERTS("Alerts", Icons.Filled.NotificationsActive),
    SETUP("Setup", Icons.Filled.Settings),
}

/**
 * The owner app: four tabs on a phone, portrait.
 *
 * Until a server is configured there is nothing any other tab can show, so Setup takes
 * over rather than presenting three empty screens and letting the owner work out why.
 */
@Composable
fun CompanionRoot() {
    val baseUrl by Owner.store.baseUrl.collectAsStateWithLifecycle()
    val token by Owner.store.token.collectAsStateWithLifecycle()
    val configured = baseUrl.isNotBlank() && token.isNotBlank()

    var tab by remember { mutableStateOf(Tab.MAP) }

    Box(
        Modifier
            .fillMaxSize()
            .background(PageBrush)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (!configured) {
                    SetupScreen(firstRun = true)
                } else {
                    when (tab) {
                        Tab.MAP -> WhereScreen()
                        Tab.HISTORY -> HistoryScreen()
                        Tab.ALERTS -> AlertsScreen()
                        Tab.SETUP -> SetupScreen(firstRun = false)
                    }
                }
            }
            if (configured) {
                TabBar(selected = tab, onSelect = { tab = it })
            }
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
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
                .height(68.dp)
                .background(WarivoBlack),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { entry ->
                val active = entry == selected
                Column(
                    modifier = Modifier.tap { onSelect(entry) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 30.dp)
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (active) {
                                    Modifier.background(AccentBrush, RoundedCornerShape(50))
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = entry.label,
                            tint = if (active) WarivoBlack else WarivoTextDim,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Text(
                        entry.label,
                        color = if (active) WarivoAccent else WarivoTextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
