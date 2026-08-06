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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.common.SonderPalette
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/**
 * Shape scale.
 *
 * M3 Expressive leans on shape to signal hierarchy, so these run noticeably rounder than the
 * Material 3 defaults: containers read as distinct objects rather than panels butted together.
 */
private val SonderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

private fun c(v: Int) = Color(v)

private fun darkSchemeFor(seed: Int) = with(SonderPalette) {
    val accent = accentFor(seed, true)
    darkColorScheme(
        primary = c(tone(accent, 74)),
        onPrimary = c(tone(accent, 18)),
        primaryContainer = c(tone(accent, 34)),
        onPrimaryContainer = c(tone(accent, 92)),
        inversePrimary = c(tone(accent, 42)),
        secondary = c(tone(accent, 68)),
        onSecondary = c(tone(accent, 16)),
        secondaryContainer = c(neutral(accent, 24)),
        onSecondaryContainer = c(neutral(accent, 92)),
        tertiary = c(tone(accent, 84)),
        onTertiary = c(tone(accent, 20)),
        background = c(neutral(accent, 7)),
        onBackground = c(neutral(accent, 93)),
        surface = c(neutral(accent, 7)),
        onSurface = c(neutral(accent, 93)),
        surfaceVariant = c(neutral(accent, 15)),
        onSurfaceVariant = c(neutral(accent, 78)),
        surfaceContainerLowest = c(neutral(accent, 5)),
        surfaceContainerLow = c(neutral(accent, 10)),
        surfaceContainer = c(neutral(accent, 12)),
        surfaceContainerHigh = c(neutral(accent, 16)),
        surfaceContainerHighest = c(neutral(accent, 21)),
        outline = c(neutral(accent, 62)),
        outlineVariant = c(neutral(accent, 26)),
        error = c(0xFFFFB4AB.toInt()),
        onError = c(0xFF690005.toInt())
    )
}

private fun lightSchemeFor(seed: Int) = with(SonderPalette) {
    val accent = accentFor(seed, false)
    lightColorScheme(
        primary = c(tone(accent, 40)),
        onPrimary = Color.White,
        primaryContainer = c(tone(accent, 90)),
        onPrimaryContainer = c(tone(accent, 14)),
        inversePrimary = c(tone(accent, 76)),
        secondary = c(tone(accent, 44)),
        onSecondary = Color.White,
        secondaryContainer = c(neutral(accent, 88)),
        onSecondaryContainer = c(neutral(accent, 14)),
        tertiary = c(tone(accent, 32)),
        onTertiary = Color.White,
        background = c(neutral(accent, 96)),
        onBackground = c(neutral(accent, 12)),
        surface = c(neutral(accent, 96)),
        onSurface = c(neutral(accent, 12)),
        surfaceVariant = c(neutral(accent, 89)),
        onSurfaceVariant = c(neutral(accent, 32)),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = c(neutral(accent, 97)),
        surfaceContainer = c(neutral(accent, 94)),
        surfaceContainerHigh = c(neutral(accent, 91)),
        surfaceContainerHighest = c(neutral(accent, 88)),
        outline = c(neutral(accent, 48)),
        outlineVariant = c(neutral(accent, 80)),
        error = c(0xFFBA1A1A.toInt()),
        onError = Color.White
    )
}

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { runCatching { ctx.prefs() }.getOrNull() }

    // Reading the seed once meant the settings UI kept the colour it started with until the app
    // was restarted, so picking a swatch appeared to only affect the keyboard. Watch the pref
    // instead and recolour immediately.
    var seed by remember {
        mutableIntStateOf(
            prefs?.getInt(Settings.PREF_SONDER_SEED_COLOR, Defaults.PREF_SONDER_SEED_COLOR)
                ?: Defaults.PREF_SONDER_SEED_COLOR
        )
    }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == Settings.PREF_SONDER_SEED_COLOR)
                seed = p.getInt(Settings.PREF_SONDER_SEED_COLOR, Defaults.PREF_SONDER_SEED_COLOR)
        }
        prefs?.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs?.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val colorScheme = remember(seed, dark) { if (dark) darkSchemeFor(seed) else lightSchemeFor(seed) }
    val base = Typography()

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = SonderShapes,
        typography = Typography(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp)
        ),
        content = content
    )
}
