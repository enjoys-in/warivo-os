package com.warivo.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warivo.os.ui.theme.AccentBrush
import com.warivo.os.ui.theme.CardBrush
import com.warivo.os.ui.theme.CardPadding
import com.warivo.os.ui.theme.CardRadius
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoAccentDeep
import com.warivo.os.ui.theme.WarivoBlack
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoSurface
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import kotlin.math.min

/** Click with no ripple or bounce — a moving vehicle is not the place for animation. */
@Composable
fun Modifier.clickableTile(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * The container every panel is built from, matching `.card` in
 * branding/mockups/warivo.css: a 165° gradient, a 10%-aqua hairline and radius 26.
 */
@Composable
fun WarivoCard(
    modifier: Modifier = Modifier,
    radius: Dp = CardRadius,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(CardBrush, RoundedCornerShape(radius))
            .border(1.dp, WarivoHairline, RoundedCornerShape(radius))
            .padding(if (padded) CardPadding else 0.dp),
        content = content,
    )
}

/** `.label` — the tiny wide-tracked uppercase caption above every value. */
@Composable
fun CardLabel(text: String, color: Color = WarivoTextDim, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier,
    )
}

/**
 * The instrument ring: a 270° arc from bottom-left, gradient-stroked, under a soft halo.
 *
 * No tick marks — the mockups keep the ring clean and let the gradient plus the glow
 * carry the reading, which stays legible at a glance without adding visual noise around
 * the numeral.
 */
@Composable
fun RingGauge(
    fraction: Float,
    modifier: Modifier = Modifier,
    from: Color = WarivoAccentDeep,
    to: Color = WarivoAccent,
    strokeWidth: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val radius = min(size.width, size.height) / 2f - stroke / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            drawArc(
                color = WarivoTextDim.copy(alpha = 0.18f),
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (clamped <= 0f) return@Canvas

            val brush = Brush.linearGradient(
                colors = listOf(from, to),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
            )
            // Stand-in for the mockup's drop-shadow glow: a wider, faint arc beneath.
            // Canvas has no cheap blur, and at this stroke width the difference does not
            // survive a glance.
            drawArc(
                color = to.copy(alpha = 0.22f),
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE * clamped,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke * 1.7f, cap = StrokeCap.Round),
            )
            drawArc(
                brush = brush,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE * clamped,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

/** `.mode` — the gear/mode pill that sits under the speed numeral. */
@Composable
fun ModePill(text: String) {
    Text(
        text.uppercase(),
        color = WarivoAccent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(WarivoAccent.copy(alpha = 0.12f))
            .border(1.dp, WarivoAccent.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 7.dp),
    )
}

/** `.t-ico` — the rounded accent chip that heads each small tile. */
@Composable
fun IconChip(
    icon: ImageVector,
    size: Dp = 44.dp,
    radius: Dp = 14.dp,
    tint: Color = WarivoAccent,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(tint.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * `.tile` — icon chip at the top, value and label pushed to the bottom. The six of these
 * on the Drive panel are the numbers you check at a stop rather than while moving.
 */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String? = null,
    valueColor: Color = WarivoText,
    modifier: Modifier = Modifier,
) {
    WarivoCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            IconChip(icon)
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value,
                        color = valueColor,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (unit != null) {
                        Text(
                            " $unit",
                            color = WarivoTextDim,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                CardLabel(label, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

/** `.bars` — the segmented meter under the range readout. */
@Composable
fun SegmentBars(
    fraction: Float,
    segments: Int = 10,
    modifier: Modifier = Modifier,
) {
    val filled = (fraction.coerceIn(0f, 1f) * segments).toInt()
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(segments) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (index < filled) Modifier.background(AccentBrush)
                        else Modifier.background(WarivoTextDim.copy(alpha = 0.22f))
                    )
            )
        }
    }
}

/**
 * `.tell` — a telltale as a rounded square tile that lights up, rather than a bare icon.
 * Hidden entirely while [active] is null, because the audit's taps are not wired yet.
 */
@Composable
fun TelltaleTile(
    icon: ImageVector,
    active: Boolean?,
    activeColor: Color = WarivoAccent,
    contentDescription: String,
) {
    if (active == null) return
    val tint = if (active) activeColor else WarivoTextDim
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WarivoSurface.copy(alpha = 0.7f))
            .border(
                1.dp,
                if (active) activeColor.copy(alpha = 0.4f) else WarivoHairline,
                RoundedCornerShape(18.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** A lettered telltale tile, for reverse ("R") and gear, which glyphs only obscure. */
@Composable
fun TextTelltaleTile(label: String, active: Boolean?, activeColor: Color = WarivoAccent) {
    if (active == null) return
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WarivoSurface.copy(alpha = 0.7f))
            .border(
                1.dp,
                if (active) activeColor.copy(alpha = 0.4f) else WarivoHairline,
                RoundedCornerShape(18.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) activeColor else WarivoTextDim,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** `.chip` / `.chip.live` — the small status pills in the top bar. */
@Composable
fun StatusChip(
    text: String,
    color: Color = WarivoTextDim,
    dot: Boolean = false,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(WarivoSurface.copy(alpha = 0.6f))
            .border(1.dp, WarivoHairline, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A round accent button: the play control, the search mic, the map recentre. */
@Composable
fun AccentCircleButton(
    icon: ImageVector,
    contentDescription: String,
    diameter: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(RoundedCornerShape(50))
            .background(AccentBrush, RoundedCornerShape(50))
            .clickableTile(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = WarivoBlack,
            modifier = Modifier.size(diameter * 0.45f),
        )
    }
}

/** A plain round button, for the ghost transport controls beside the play button. */
@Composable
fun GhostCircleButton(
    icon: ImageVector,
    contentDescription: String,
    diameter: Dp,
    tint: Color = WarivoText,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(RoundedCornerShape(50))
            .clickableTile(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(diameter * 0.52f),
        )
    }
}

/** A static level meter, as the mockups draw beside the audio output. */
@Composable
fun LevelBars(heights: List<Float>, color: Color = WarivoAccent, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(34.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .size(width = 6.dp, height = (34 * h.coerceIn(0.1f, 1f)).dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}
