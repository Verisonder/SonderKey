// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SonderKey brand palette.
 *
 * Deliberately not Material You: the settings UI is part of the product's identity, and taking
 * its colours from the user's wallpaper made every screen look like a generic system menu.
 * These values are the same teal / graphite pair used by the Sonder keyboard themes.
 */
private object Sonder {
    val Teal = Color(0xFF2FB8A6)
    val TealDeep = Color(0xFF00695E)
    val TealDark = Color(0xFF004E46)
    val TealPale = Color(0xFFA6F2E5)
    val InkDark = Color(0xFF00201B)

    // dark surfaces — matches Sonder Dark keyboard theme
    val Bg = Color(0xFF111215)
    val Surface1 = Color(0xFF191C20)
    val Surface2 = Color(0xFF1E2126)
    val Surface3 = Color(0xFF282C33)
    val TextDark = Color(0xFFECF2F0)
    val TextDarkMuted = Color(0xFFB9C4C1)
    val OutlineDark = Color(0xFF3A4046)

    // light surfaces — matches Sonder Light keyboard theme
    val BgLight = Color(0xFFF2F5F4)
    val SurfaceLight1 = Color(0xFFE9EEEC)
    val SurfaceLight2 = Color(0xFFDCE5E2)
    val TextLight = Color(0xFF15201D)
    val TextLightMuted = Color(0xFF3F4946)
    val OutlineLight = Color(0xFFBEC9C5)

    val ErrorDark = Color(0xFFFFB4AB)
    val ErrorLight = Color(0xFFBA1A1A)
}

private val SonderDark = darkColorScheme(
    primary = Sonder.Teal,
    onPrimary = Sonder.InkDark,
    primaryContainer = Sonder.TealDark,
    onPrimaryContainer = Sonder.TealPale,
    secondary = Sonder.Teal,
    onSecondary = Sonder.InkDark,
    secondaryContainer = Sonder.Surface3,
    onSecondaryContainer = Sonder.TextDark,
    tertiary = Sonder.TealPale,
    onTertiary = Sonder.InkDark,
    background = Sonder.Bg,
    onBackground = Sonder.TextDark,
    surface = Sonder.Bg,
    onSurface = Sonder.TextDark,
    surfaceVariant = Sonder.Surface2,
    onSurfaceVariant = Sonder.TextDarkMuted,
    surfaceContainerLowest = Sonder.Bg,
    surfaceContainerLow = Sonder.Surface1,
    surfaceContainer = Sonder.Surface1,
    surfaceContainerHigh = Sonder.Surface2,
    surfaceContainerHighest = Sonder.Surface3,
    outline = Sonder.TextDarkMuted,
    outlineVariant = Sonder.OutlineDark,
    error = Sonder.ErrorDark,
    onError = Color(0xFF690005)
)

private val SonderLight = lightColorScheme(
    primary = Sonder.TealDeep,
    onPrimary = Color.White,
    primaryContainer = Sonder.TealPale,
    onPrimaryContainer = Sonder.InkDark,
    secondary = Sonder.TealDeep,
    onSecondary = Color.White,
    secondaryContainer = Sonder.SurfaceLight2,
    onSecondaryContainer = Sonder.TextLight,
    tertiary = Sonder.TealDark,
    onTertiary = Color.White,
    background = Sonder.BgLight,
    onBackground = Sonder.TextLight,
    surface = Sonder.BgLight,
    onSurface = Sonder.TextLight,
    surfaceVariant = Sonder.SurfaceLight2,
    onSurfaceVariant = Sonder.TextLightMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEDF2F0),
    surfaceContainer = Sonder.SurfaceLight1,
    surfaceContainerHigh = Color(0xFFE3E9E7),
    surfaceContainerHighest = Sonder.SurfaceLight2,
    outline = Sonder.TextLightMuted,
    outlineVariant = Sonder.OutlineLight,
    error = Sonder.ErrorLight,
    onError = Color.White
)

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = if (dark) SonderDark else SonderLight,
        typography = Typography(
            // titles get weight and slightly tighter tracking so headers read as deliberate
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            // category headers: small, wide-tracked, uppercase-friendly
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp)
        ),
        content = content
    )
}
