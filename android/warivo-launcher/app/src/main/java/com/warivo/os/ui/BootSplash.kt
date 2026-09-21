package com.warivo.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warivo.os.ui.theme.AccentBrush
import com.warivo.os.ui.theme.PageBrush
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * The launch splash, following branding/mockups/png/01-boot.png and the sequence in
 * branding/README.md: the orbit ring draws in, the monogram and wordmark arrive, then a
 * progress bar runs while the app settles.
 *
 * Drawn natively rather than loading `boot-animation.html` in a WebView, as
 * branding/README suggests. A WebView costs a whole browser engine starting up on the
 * critical path of the thing whose job is to look instant — and this is the same handful
 * of shapes.
 *
 * This is **not** the Android boot animation. That needs `/system/media/bootanimation.zip`
 * and therefore root or the Path B ROM; this covers the window between the launcher
 * starting and the dashboard being ready.
 */
@Composable
fun BootSplash(onFinished: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        while (true) {
            val elapsed = (System.currentTimeMillis() - startedAt).toFloat()
            progress = (elapsed / DURATION_MS).coerceIn(0f, 1f)
            if (progress >= 1f) break
            delay(16)
        }
        onFinished()
    }

    // Staged reveal: the ring draws first, then the monogram, then the type.
    val ringSweep = (progress / 0.45f).coerceIn(0f, 1f)
    val markAlpha = ((progress - 0.25f) / 0.3f).coerceIn(0f, 1f)
    val typeAlpha = ((progress - 0.5f) / 0.3f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 4.dp.toPx()
                    val radius = min(size.width, size.height) / 2f - stroke
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    drawArc(
                        color = WarivoAccent,
                        startAngle = -90f,
                        sweepAngle = 360f * ringSweep,
                        useCenter = false,
                        topLeft = Offset(centre.x - radius, centre.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    "W",
                    color = WarivoAccent,
                    fontSize = 74.sp,
                    fontFamily = FontFamily.Cursive,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(markAlpha),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(typeAlpha)
                    .padding(top = 26.dp),
            ) {
                Text(
                    "WARIVO",
                    color = WarivoText,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 14.sp,
                )
                Text(
                    "NOVA-S",
                    color = WarivoTextDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 7.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "STARTING WARIVO OS",
                    color = WarivoTextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(top = 30.dp),
                )
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .width(240.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(WarivoTextDim.copy(alpha = 0.25f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(AccentBrush)
                    )
                }
            }
        }
    }
}

/** Long enough to read the wordmark, short enough not to be in the way. */
private const val DURATION_MS = 2400f
