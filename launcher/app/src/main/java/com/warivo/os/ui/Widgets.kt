package com.warivo.os.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import com.warivo.os.R
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
 * A stat tile: icon chip on the left, value and label on the right. The mockups stack
 * four of these down a narrow right-hand column, which is why they read across rather
 * than down.
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
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconChip(icon, size = 42.dp, radius = 13.dp)
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, color = valueColor, style = MaterialTheme.typography.headlineMedium)
                    if (unit != null) {
                        Text(
                            " $unit",
                            color = WarivoTextDim,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                CardLabel(label, modifier = Modifier.padding(top = 3.dp))
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

/**
 * `.seg` — the ride-mode segmented control.
 *
 * Read-only on purpose: the selected mode comes from the scooter's own gear switch (see
 * audit.md), and Warivo OS must never command the scooter. Tapping it would imply it can.
 */
@Composable
fun SegmentedDisplay(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(WarivoBlack.copy(alpha = 0.6f))
            .border(1.dp, WarivoHairline, RoundedCornerShape(20.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val on = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .then(
                        if (on) Modifier.background(AccentBrush, RoundedCornerShape(15.dp))
                        else Modifier
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.uppercase(),
                    color = if (on) WarivoBlack else WarivoTextDim,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

/** `.toggle` — the pill switch used across Settings. */
@Composable
fun WarivoToggle(checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 34.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (checked) Modifier.background(AccentBrush, RoundedCornerShape(50))
                else Modifier.background(WarivoTextDim.copy(alpha = 0.28f))
            )
            .clickableTile { if (enabled) onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .size(26.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (checked) WarivoBlack.copy(alpha = alpha)
                    else Color(0xFFEEF1F7).copy(alpha = alpha)
                )
        )
    }
}

/** A Settings quick-toggle card: icon chip, switch, name, one line of state. */
@Composable
fun QuickToggleCard(
    icon: ImageVector,
    name: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    WarivoCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(icon, size = 46.dp, radius = 14.dp)
            WarivoToggle(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
        Text(
            name,
            color = WarivoText,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            detail,
            color = WarivoTextDim,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * A Settings list row: icon chip, title over subtitle, and a trailing slot for the value
 * or the control. Rows are separated by a hairline, as in the mockups.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    if (showDivider) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WarivoHairline)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconChip(icon, size = 44.dp, radius = 14.dp)
        Column(Modifier.weight(1f)) {
            Text(title, color = WarivoText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                color = WarivoTextDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        trailing()
    }
}

/** A small state badge, as Settings uses for the node's PAIRED state. */
@Composable
fun StateBadge(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(text.uppercase(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** A square dock/quick control button; `.ctrl` and `.ctrl.on` in the stylesheet. */
@Composable
fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    on: Boolean = false,
    size: Dp = 66.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (on) WarivoAccent.copy(alpha = 0.12f) else WarivoSurface.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (on) WarivoAccent.copy(alpha = 0.35f) else WarivoHairline,
                RoundedCornerShape(20.dp),
            )
            .clickableTile(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (on) WarivoAccent else WarivoText,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/**
 * A labelled meter: caption and value on one line, a bar underneath.
 *
 * Used for throttle position and obstacle proximity — both are continuous values where
 * the *trend* matters more than the exact number, which a bar shows and a digit does not.
 */
@Composable
fun LabelledMeter(
    label: String,
    value: String,
    fraction: Float,
    barColor: Color = WarivoAccent,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            CardLabel(label)
            Text(value, color = barColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(WarivoTextDim.copy(alpha = 0.22f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
    }
}

/**
 * The Warivo mark with its pulsing glow — boot, lock screen, dock brand, About.
 *
 * The pulse is what branding/README asks for ("keeps a soft pulsing glow for the whole
 * boot"). It is a slow breath rather than a blink: on a screen that is in the rider's
 * peripheral vision for hours, anything faster reads as an alert.
 */
@Composable
fun GlowingMark(size: Dp, pulsing: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "markGlow")
    val glow by transition.animateFloat(
        initialValue = if (pulsing) 0.35f else 0.55f,
        targetValue = if (pulsing) 0.75f else 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "markGlowAlpha",
    )

    Box(
        modifier = Modifier.size(size * 1.9f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        WarivoAccent.copy(alpha = glow * 0.5f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = this.size.minDimension / 2f,
                ),
                radius = this.size.minDimension / 2f,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Warivo",
            tint = Color.Unspecified,
            modifier = Modifier.size(size),
        )
    }
}
