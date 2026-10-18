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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import helium314.keyboard.latin.common.SonderPalette
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
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

private fun darkScheme(accent: Color, onAccent: Color, container: Color, onContainer: Color) = darkColorScheme(
    primary = accent, onPrimary = onAccent,
    primaryContainer = container, onPrimaryContainer = onContainer,
    inversePrimary = accent,
    secondary = accent, onSecondary = onAccent,
    secondaryContainer = Color(0xFF1E1E1E), onSecondaryContainer = Color.White,
    tertiary = accent, onTertiary = onAccent,
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

private fun lightScheme(accent: Color, onAccent: Color, container: Color, onContainer: Color) = lightColorScheme(
    primary = accent, onPrimary = onAccent,
    primaryContainer = container, onPrimaryContainer = onContainer,
    inversePrimary = accent,
    secondary = accent, onSecondary = onAccent,
    secondaryContainer = Color(0xFFF0F0EE), onSecondaryContainer = Color(0xFF0D0D0D),
    tertiary = accent, onTertiary = onAccent,
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

/**
 * The settings accent follows the keyboard's own accent (Appearance → Sonder theme), so the
 * one colour picker recolours both. It is tuned for the page the same way the keyboard tunes
 * it, so it stays readable on black and on light.
 */
private fun schemeFor(seed: Int, dark: Boolean) = with(SonderPalette) {
    val a = accentFor(seed, dark)
    val accent = Color(a)
    val onAccent = if (lightness(a) > 60) Color(0xFF0D0D0D) else Color.White
    if (dark) darkScheme(accent, onAccent, Color(tone(a, 28)), Color(tone(a, 92)))
    else lightScheme(accent, onAccent, Color(tone(a, 90)), Color(tone(a, 16)))
}

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { runCatching { ctx.prefs() }.getOrNull() }
    // Watched, not read once, so picking a colour recolours the open settings straight away.
    var seed by remember {
        mutableIntStateOf(
            prefs?.getInt(Settings.PREF_SONDER_SEED_COLOR, Defaults.PREF_SONDER_SEED_COLOR)
                ?: Defaults.PREF_SONDER_SEED_COLOR
        )
    }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == Settings.PREF_SONDER_SEED_COLOR) seed = p.getInt(key, Defaults.PREF_SONDER_SEED_COLOR)
        }
        prefs?.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs?.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val colorScheme = remember(seed, dark) { schemeFor(seed, dark) }
    val base = Typography()
    fun TextStyle.body() = copy(fontFamily = BodyFont)
    fun TextStyle.display(weight: FontWeight = FontWeight.ExtraBold) =
        copy(fontFamily = DisplayFont, fontWeight = weight, letterSpacing = (-0.02f * fontSize.value).sp)
    MaterialTheme(
        colorScheme = colorScheme,
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
