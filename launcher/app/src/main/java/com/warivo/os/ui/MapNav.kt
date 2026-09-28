package com.warivo.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warivo.os.nav.NavProgress
import com.warivo.os.nav.Place
import com.warivo.os.ui.theme.AccentBrush
import com.warivo.os.ui.theme.DockBrush
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAmber
import com.warivo.os.ui.theme.WarivoBlack
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import java.util.Locale

/** The destination search: a field, its results, and a way back to the web search. */
@Composable
fun NavSearchOverlay(
    query: String,
    onQuery: (String) -> Unit,
    results: List<Place>,
    searching: Boolean,
    onPick: (Place) -> Unit,
    onWebSearch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(DockBrush, RoundedCornerShape(26.dp))
            .border(1.dp, WarivoHairline, RoundedCornerShape(26.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Filled.ArrowBackIosNew, "Close search", tint = WarivoTextDim, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(50)).clickableTile(onClose).padding(2.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = WarivoText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(WarivoAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onQuery(query) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Where to?", color = WarivoTextDim, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    inner()
                },
            )
            if (searching) {
                CircularProgressIndicator(color = WarivoAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else if (query.isNotEmpty()) {
                Icon(Icons.Filled.Close, "Clear", tint = WarivoTextDim, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(50)).clickableTile { onQuery("") }.padding(2.dp))
            }
        }

        if (results.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            ) {
                results.forEach { place ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickableTile { onPick(place) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        IconChip(Icons.Filled.Place, size = 42.dp, radius = 13.dp)
                        Column(Modifier.weight(1f)) {
                            Text(place.name, color = WarivoText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(place.detail, color = WarivoTextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else if (query.isNotBlank() && !searching) {
            Text(
                "No places found. Try a landmark or a full address.",
                color = WarivoTextDim,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickableTile(onWebSearch)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconChip(Icons.Filled.Search, size = 42.dp, radius = 13.dp)
            Text("Search Google instead", color = WarivoTextDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The next-turn banner shown at the top while navigating. */
@Composable
fun ManeuverBanner(progress: NavProgress, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AccentBrush, RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.NearMe, contentDescription = null, tint = WarivoBlack, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (progress.arrived) "You have arrived" else progress.nextInstruction,
                color = WarivoBlack,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!progress.arrived) {
                Text("in ${fmtDistance(progress.nextDistanceM)}", color = WarivoBlack.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The bottom card: destination, distance/ETA, and Stop — plus routing / failure states. */
@Composable
fun NavCard(
    destination: Place,
    progress: NavProgress?,
    routing: Boolean,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DockBrush, RoundedCornerShape(24.dp))
            .border(1.dp, WarivoHairline, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconChip(Icons.Filled.Place, size = 46.dp, radius = 15.dp)
            Column(Modifier.weight(1f)) {
                CardLabel("Destination")
                Text(destination.name, color = WarivoText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GhostCircleButton(Icons.Filled.Close, "Stop navigation", 46.dp, onClick = onStop)
        }

        when {
            progress != null -> Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column {
                    CardLabel(if (progress.arrived) "Arrived" else "Distance")
                    Text(fmtDistance(progress.remainingM), color = WarivoText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    CardLabel("ETA")
                    Text(
                        if (progress.arrived) "—" else "${progress.etaMinutes} min",
                        color = WarivoAccent,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            routing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = WarivoAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text("Finding a route…", color = WarivoTextDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No route — offline or no GPS.", color = WarivoAmber, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "Retry",
                    color = WarivoAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickableTile(onRetry).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

internal fun fmtDistance(m: Double): String = when {
    m < 950 -> "${((m / 10).toInt() * 10).coerceAtLeast(0)} m"
    else -> String.format(Locale.US, "%.1f km", m / 1000)
}
