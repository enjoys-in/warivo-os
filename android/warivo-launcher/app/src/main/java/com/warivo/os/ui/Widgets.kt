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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warivo.os.ui.theme.CardPadding
import com.warivo.os.ui.theme.CardRadius
import com.warivo.os.ui.theme.WarivoAqua
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoSurface
import com.warivo.os.ui.theme.WarivoSurfaceHigh
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Click with no ripple or bounce — a moving vehicle is not the place for animation. */
@Composable
fun Modifier.clickableTile(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * The one container every panel is built from: a rounded dark card with a hairline edge,
 * an optional tiny uppercase label, and an optional trailing icon on the label row.
 *
 * Everything on screen is one of these, which is what makes the reference head units read
 * as a single system rather than a pile of widgets.
 */
@Composable
fun WarivoCard(
    modifier: Modifier = Modifier,
    label: String? = null,
    trailingIcon: ImageVector? = null,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CardRadius))
            .background(WarivoSurface)
            .border(1.dp, WarivoHairline, RoundedCornerShape(CardRadius))
            .padding(if (contentPadding) CardPadding else 0.dp),
    ) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label.uppercase(),
                    color = WarivoTextDim,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (trailingIcon != null) {
                    Icon(
                        trailingIcon,
                        contentDescription = null,
                        tint = WarivoTextDim,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        content()
    }
}

/**
 * A tick-marked ring gauge, opening at the bottom, with a bright cap at the leading edge.
 *
 * The ticks are what make a glance readable: a bare arc tells you "somewhere past
 * halfway", ticks tell you roughly how far. Ticks below the value take the accent colour
 * so the filled span is legible even in peripheral vision.
 */
@Composable
fun RingGauge(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = WarivoAqua,
    trackColor: Color = WarivoSurfaceHigh,
    tickCount: Int = 36,
    strokeWidth: Float = 12f,
    content: @Composable () -> Unit,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.dp.toPx()
            val tickLen = 7.dp.toPx()
            val tickInset = 4.dp.toPx()
            val outerR = min(size.width, size.height) / 2f
            val ringR = outerR - tickLen - tickInset - stroke / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Radial ticks just outside the ring.
            for (i in 0..tickCount) {
                val t = i / tickCount.toFloat()
                val rad = Math.toRadians((START_ANGLE + SWEEP_ANGLE * t).toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()
                val r1 = ringR + stroke / 2f + tickInset
                val r2 = r1 + tickLen
                drawLine(
                    color = if (t <= clamped) color else trackColor,
                    start = Offset(center.x + cosA * r1, center.y + sinA * r1),
                    end = Offset(center.x + cosA * r2, center.y + sinA * r2),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val arcTopLeft = Offset(center.x - ringR, center.y - ringR)
            val arcSize = Size(ringR * 2, ringR * 2)
            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE * clamped,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Leading cap plus a soft halo, so the eye lands on the current value.
            if (clamped > 0f) {
                val rad = Math.toRadians((START_ANGLE + SWEEP_ANGLE * clamped).toDouble())
                val cap = Offset(
                    center.x + cos(rad).toFloat() * ringR,
                    center.y + sin(rad).toFloat() * ringR,
                )
                drawCircle(color = color.copy(alpha = 0.18f), radius = stroke * 1.6f, center = cap)
                drawCircle(color = WarivoText, radius = stroke * 0.42f, center = cap)
            }
        }
        content()
    }
}

private const val START_ANGLE = 150f
private const val SWEEP_ANGLE = 240f

/** The gauge readout: a big number with its unit tucked alongside. */
@Composable
fun GaugeReadout(value: String, unit: String, valueColor: Color = WarivoText) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = valueColor, style = MaterialTheme.typography.displayLarge)
        Text(
            " $unit",
            color = WarivoTextDim,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }
}

/**
 * The reference dashboards hang two or three stats under a gauge, split by thin vertical
 * rules. [CardFooter] draws that row; each entry is a [FooterStat].
 */
data class FooterStat(val label: String, val value: String)

@Composable
fun CardFooter(stats: List<FooterStat>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, stat ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stat.label.uppercase(),
                    color = WarivoTextDim,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
                Text(stat.value, color = WarivoText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            if (index != stats.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(WarivoHairline)
                )
            }
        }
    }
}

/** A small card whose whole body is one label + value pair. */
@Composable
fun StatCard(
    label: String,
    value: String,
    unit: String? = null,
    valueColor: Color = WarivoText,
    modifier: Modifier = Modifier,
) {
    WarivoCard(modifier = modifier, label = label) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = valueColor, style = MaterialTheme.typography.headlineMedium)
            if (unit != null) {
                Text(
                    " $unit",
                    color = WarivoTextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}

/**
 * An indicator lamp. Dark when inactive, lit when active, and completely hidden when the
 * node is not reporting that signal at all (the audit's throttle/gear/switch taps are not
 * wired yet, so [active] is null until they are).
 */
@Composable
fun Telltale(
    icon: ImageVector,
    active: Boolean?,
    activeColor: Color,
    contentDescription: String,
) {
    if (active == null) return
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (active) activeColor else WarivoSurfaceHigh,
        modifier = Modifier.size(26.dp),
    )
}

/**
 * A lettered telltale, for signals a glyph would only obscure — a car shows reverse as
 * "R" and a gear as a number, so the dashboard does too. Hidden while [active] is null.
 */
@Composable
fun TextTelltale(label: String, active: Boolean?, activeColor: Color) {
    if (active == null) return
    Text(
        label,
        color = if (active) activeColor else WarivoSurfaceHigh,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** A pill chip, as the reference layouts use for weather and status in a corner. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(WarivoSurface)
            .border(1.dp, WarivoHairline, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** Filler so an unavailable signal still occupies its slot instead of reflowing the grid. */
@Composable
fun EmptyCard(label: String, note: String, modifier: Modifier = Modifier) {
    WarivoCard(modifier = modifier, label = label) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text(note, color = WarivoTextDim, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
