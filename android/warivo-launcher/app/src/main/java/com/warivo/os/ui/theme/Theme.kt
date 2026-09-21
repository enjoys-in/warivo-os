package com.warivo.os.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warivo brand palette, mirrored by branding/mockups/warivo.css — change it here and
// there together, or the mockups stop describing the product.
//
// The page recedes to almost black so the cards float, and one accent carries everything
// interactive. Amber and red are reserved for warnings, so nothing decorative may use
// them. The accent is named by role, not by colour: it has already been Celestial Aqua
// and is now Vintage Blush, and only these two tokens should change if it moves again.
val WarivoBlack = Color(0xFF05102A)       // Deep Space Blue, page
val WarivoSurface = Color(0xFF0B1E45)     // card
val WarivoSurfaceHigh = Color(0xFF142C5C) // raised card / gauge track
val WarivoHairline = Color(0x1AFFC0CB)    // card edge: 10% blush, as in the mockups
val WarivoAccent = Color(0xFFFFC0CB)        // Vintage Blush (soft pink), primary accent
val WarivoAccentDeep = Color(0xFFFF8FA8)
val WarivoGreen = Color(0xFF57E28A)       // healthy state of charge
val WarivoAmber = Color(0xFFFFB020)       // warning only
val WarivoRed = Color(0xFFFF6B7D)         // warning only
val WarivoBlue = Color(0xFF4DA3FF)
val WarivoText = Color(0xFFF1EFF3)
val WarivoTextDim = Color(0xFF9AA6C4)

// Shared geometry, mirrored from branding/mockups/warivo.css (--radius, --radius-lg).
val RailWidth = 104.dp
val StatusBarHeight = 60.dp
val ContentPadding = 30.dp
val CardRadius = 28.dp
val CardRadiusLarge = 34.dp
val CardPadding = 22.dp
val GridGap = 18.dp

/**
 * Cards in the mockups are a 165° gradient, not a flat fill — it is what keeps a screen
 * of dark rectangles from looking like a spreadsheet. Vertical is close enough to 165°
 * at card size, and costs no layout pass.
 */
val CardBrush: Brush
    get() = Brush.verticalGradient(listOf(Color(0x99183060), Color(0x800C1E42)))

/** The accent fill: active rail button, play button, segment meters. */
val AccentBrush: Brush
    get() = Brush.verticalGradient(listOf(WarivoAccent, WarivoAccentDeep))

private val WarivoColors = darkColorScheme(
    primary = WarivoAccent,
    onPrimary = WarivoBlack,
    secondary = WarivoAccentDeep,
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
