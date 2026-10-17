// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R

/**
 * The settings app wears the Verisonder look shared with SonderShield: a black page, panels a
 * step lighter, one electric-blue accent, Unbounded for headings and DM Sans for text. The
 * keyboard itself is unaffected — its colours still come from the Sonder theme settings.
 */
private val SonderShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

/** Electric blue, as in SonderShield. */
val SonderAccent = Color(0xFF3B82FF)

/** Unbounded: headings and numbers. */
val DisplayFont = FontFamily(
    Font(R.font.unbounded_bold, FontWeight.Bold),
    Font(R.font.unbounded_extrabold, FontWeight.ExtraBold),
)

/** DM Sans: everything else. */
val BodyFont = FontFamily(
    Font(R.font.dmsans_regular, FontWeight.Normal),
    Font(R.font.dmsans_medium, FontWeight.Medium),
    Font(R.font.dmsans_bold, FontWeight.Bold),
)

private val DarkScheme = darkColorScheme(
    primary = SonderAccent, onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF14284D), onPrimaryContainer = Color(0xFFD6E4FF),
    inversePrimary = Color(0xFF1D5BD8),
    secondary = SonderAccent, onSecondary = Color(0xFF0D0D0D),
    secondaryContainer = Color(0xFF1E1E1E), onSecondaryContainer = Color.White,
    tertiary = SonderAccent, onTertiary = Color(0xFF0D0D0D),
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF111111), onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111), surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF1E1E1E),
    inverseSurface = Color.White, inverseOnSurface = Color.Black,
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF262626),
    error = Color(0xFFFF6B5E), onError = Color.Black,
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1D5BD8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF), onPrimaryContainer = Color(0xFF0B1F45),
    inversePrimary = SonderAccent,
    secondary = Color(0xFF1D5BD8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0EE), onSecondaryContainer = Color(0xFF0D0D0D),
    tertiary = Color(0xFF1D5BD8), onTertiary = Color.White,
    background = Color(0xFFF5F5F4), onBackground = Color(0xFF0D0D0D),
    surface = Color(0xFFF5F5F4), onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color.White, onSurfaceVariant = Color(0xFF3A3A3A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAF9),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF0F0EE),
    surfaceContainerHighest = Color(0xFFE8E8E6),
    inverseSurface = Color(0xFF0D0D0D), inverseOnSurface = Color.White,
    outline = Color(0xFF6B6B6B), outlineVariant = Color(0xFFE2E2E0),
    error = Color(0xFFD12C1F), onError = Color.White,
)

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.body() = copy(fontFamily = BodyFont)
    fun TextStyle.display(weight: FontWeight = FontWeight.ExtraBold) =
        copy(fontFamily = DisplayFont, fontWeight = weight, letterSpacing = (-0.02f * fontSize.value).sp)
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        shapes = SonderShapes,
        typography = Typography(
            displayLarge = base.displayLarge.display(),
            displayMedium = base.displayMedium.display(),
            displaySmall = base.displaySmall.display(),
            headlineLarge = base.headlineLarge.display(),
            headlineMedium = base.headlineMedium.display(),
            headlineSmall = base.headlineSmall.display(FontWeight.Bold),
            titleLarge = base.titleLarge.display(),
            titleMedium = base.titleMedium.copy(fontFamily = BodyFont, fontWeight = FontWeight.Bold),
            titleSmall = base.titleSmall.copy(fontFamily = BodyFont, fontWeight = FontWeight.Bold),
            bodyLarge = base.bodyLarge.body(),
            bodyMedium = base.bodyMedium.copy(fontFamily = BodyFont, lineHeight = 20.sp),
            bodySmall = base.bodySmall.body(),
            labelLarge = base.labelLarge.copy(fontFamily = BodyFont, fontWeight = FontWeight.Bold),
            labelMedium = base.labelMedium.body(),
            labelSmall = base.labelSmall.body(),
        ),
        content = content
    )
}
