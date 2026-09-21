package com.warivo.os.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warivo brand (branding/warivo-mark.svg) rendered in the card-on-near-black idiom the
// reference head units use: the page recedes to almost black so the cards float, and
// Celestial Aqua is the only accent. Amber and red are reserved for warnings, so nothing
// decorative may use them.
val WarivoBlack = Color(0xFF03080F)       // page: Deep Space Blue pushed to near-black
val WarivoSurface = Color(0xFF0B1424)     // card
val WarivoSurfaceHigh = Color(0xFF16233C) // card inset / gauge track
val WarivoHairline = Color(0xFF1E2C45)    // dividers and card edges
val WarivoAqua = Color(0xFF5FF0DE)        // Celestial Aqua, primary accent
val WarivoAquaDeep = Color(0xFF1FB6C9)
val WarivoAmber = Color(0xFFFFB020)       // warning only
val WarivoRed = Color(0xFFFF4D4D)         // warning only
val WarivoBlue = Color(0xFF4DA3FF)
val WarivoText = Color(0xFFEDF3FA)
val WarivoTextDim = Color(0xFF7789A3)

// Shared geometry, so every card and gap on every panel matches.
val CardRadius = 22.dp
val CardPadding = 16.dp
val GridGap = 10.dp
val DockHeight = 74.dp

private val WarivoColors = darkColorScheme(
    primary = WarivoAqua,
    onPrimary = WarivoBlack,
    secondary = WarivoAquaDeep,
    background = WarivoBlack,
    onBackground = WarivoText,
    surface = WarivoSurface,
    onSurface = WarivoText,
    surfaceVariant = WarivoSurfaceHigh,
    onSurfaceVariant = WarivoTextDim,
    error = WarivoRed,
    outline = WarivoHairline,
)

// The reference dashboards lean on one hierarchy everywhere: a tiny wide-tracked
// uppercase label above a large tight-tracked value. These two styles carry it.
private val WarivoTypography = Typography(
    // Gauge readout.
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 72.sp,
        letterSpacing = (-2).sp,
    ),
    // Card value.
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
    ),
    // Card label.
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
    ),
)

/** Always dark — a head unit has no light mode. */
@Composable
fun WarivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WarivoColors,
        typography = WarivoTypography,
        content = content,
    )
}
