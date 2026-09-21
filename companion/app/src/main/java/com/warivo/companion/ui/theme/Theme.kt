package com.warivo.companion.ui.theme

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

// Deliberately a copy of the launcher's palette rather than a shared library module.
// The two apps ship separately and a shared module would couple their builds — for a dozen
// colour tokens that is the wrong trade. Keep in step with
// launcher/.../ui/theme/Theme.kt and branding/mockups/warivo.css by hand.
val WarivoBlack = Color(0xFF05102A)
val WarivoSurface = Color(0xFF0B1E45)
val WarivoSurfaceHigh = Color(0xFF142C5C)
val WarivoHairline = Color(0x14FFFFFF)
val WarivoAccent = Color(0xFFFFC0CB)
val WarivoAccentDeep = Color(0xFFFF8FA8)
val WarivoGreen = Color(0xFF57E28A)
val WarivoAmber = Color(0xFFFFB020)
val WarivoRed = Color(0xFFFF6B7D)
val WarivoText = Color(0xFFF1EFF3)
val WarivoTextDim = Color(0xFF9AA6C4)

val CardRadius = 22.dp
val CardPadding = 18.dp
val Gap = 14.dp

val CardBrush: Brush
    get() = Brush.verticalGradient(listOf(Color(0x99183060), Color(0x800C1E42)))

val AccentBrush: Brush
    get() = Brush.verticalGradient(listOf(WarivoAccent, WarivoAccentDeep))

val PageBrush: Brush
    get() = Brush.verticalGradient(listOf(Color(0xFF0B1E40), Color(0xFF071638), WarivoBlack))

private val Colors = darkColorScheme(
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

private val Type = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
    ),
)

/** Always dark, to match the head unit it reports on. */
@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
