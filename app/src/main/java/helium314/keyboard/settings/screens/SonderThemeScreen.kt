// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.SonderPalette
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen

private fun Int.toHex() = String.format("%06X", this and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
}

/** Hue / saturation / value sliders operating on the seed, so any colour is reachable. */
private fun hsvOf(color: Int): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(color, out)
    return out
}

private fun fromHsv(h: Float, s: Float, v: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

@Composable
fun SonderThemeScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()

    var seed by remember {
        mutableIntStateOf(prefs.getInt(Settings.PREF_SONDER_SEED_COLOR, Defaults.PREF_SONDER_SEED_COLOR))
    }
    var surface by remember {
        mutableIntStateOf(prefs.getInt(Settings.PREF_SONDER_SURFACE_COLOR, Defaults.PREF_SONDER_SURFACE_COLOR))
    }
    // which of the two colours the presets, sliders and hex field are editing
    var editingAccent by remember { mutableStateOf(true) }
    val current = if (editingAccent) seed else surface
    var hexField by remember(editingAccent) { mutableStateOf(current.toHex()) }

    fun apply(newValue: Int) {
        if (editingAccent) {
            seed = newValue
            prefs.edit { putInt(Settings.PREF_SONDER_SEED_COLOR, newValue) }
        } else {
            surface = newValue
            prefs.edit { putInt(Settings.PREF_SONDER_SURFACE_COLOR, newValue) }
        }
        hexField = newValue.toHex()
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_sonder_theme),
        settings = emptyList()
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            PreviewCard(seed, surface)

            SectionTitle(stringResource(R.string.sonder_theme_which))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                ChannelChip(stringResource(R.string.sonder_theme_accent), Color(seed), editingAccent) {
                    editingAccent = true
                }
                ChannelChip(stringResource(R.string.sonder_theme_surface), Color(surface), !editingAccent) {
                    editingAccent = false
                }
            }

            SectionTitle(stringResource(R.string.sonder_theme_presets))
            SwatchGrid(current, editingAccent) { apply(it) }

            SectionTitle(stringResource(R.string.sonder_theme_custom))
            val hsv = remember(current) { hsvOf(current) }
            ChannelSlider("Hue", hsv[0], 0f..360f) { apply(fromHsv(it, hsv[1], hsv[2])) }
            ChannelSlider("Saturation", hsv[1], 0f..1f) { apply(fromHsv(hsv[0], it, hsv[2])) }
            ChannelSlider("Brightness", hsv[2], 0f..1f) { apply(fromHsv(hsv[0], hsv[1], it)) }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hexField,
                    onValueChange = { v ->
                        hexField = v.uppercase().filter { it.isDigit() || it in "ABCDEF" }.take(6)
                        parseHex(hexField)?.let { apply(it) }
                    },
                    label = { Text(stringResource(R.string.sonder_theme_hex)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.width(190.dp)
                )
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = {
                    apply(if (editingAccent) SonderPalette.DEFAULT_SEED else SonderPalette.DEFAULT_SURFACE)
                }) {
                    Text(stringResource(R.string.sonder_theme_reset))
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.sonder_theme_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

/** Live sample of the derived palette — the point of a seed picker is seeing what it produces. */
@Composable
private fun PreviewCard(seed: Int, surfaceSeed: Int) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val kb = remember(seed, surfaceSeed, dark) { SonderPalette.Keyboard(seed, surfaceSeed, dark) }
    val spring = spring<Color>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    val bg by animateColorAsState(Color(kb.background), spring, label = "bg")
    val key by animateColorAsState(Color(kb.keyBackground), spring, label = "key")
    val fn by animateColorAsState(Color(kb.functionalKey), spring, label = "fn")
    val accent by animateColorAsState(Color(kb.accent), spring, label = "accent")
    val text by animateColorAsState(Color(kb.keyText), spring, label = "text")

    Surface(
        color = bg,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(6) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(key)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .width(46.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(fn)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(key)
                )
                Box(
                    Modifier
                        .width(46.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "SonderKey",
                color = text,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun SwatchGrid(seed: Int, accent: Boolean, onPick: (Int) -> Unit) {
    val presets = if (accent) SonderPalette.PRESETS else SonderPalette.SURFACE_PRESETS
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.toList().chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { color -> Swatch(color, color == seed) { onPick(color) } }
            }
        }
    }
}

/** Springy press feedback and a size change on selection — expressive motion, kept short. */
@Composable
private fun Swatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "swatchScale"
    )
    val ring by animateDpAsState(
        if (selected) 3.dp else 0.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "swatchRing"
    )
    Box(
        Modifier
            .scale(scale)
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(ring, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    )
}

/** Small pill showing one of the two colours; tapping it switches what the controls edit. */
@Composable
private fun ChannelChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
