// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.keyboard.internal.KeyPressEffectDrawingPreview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ColorPickerDialog
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import kotlin.math.roundToInt

@Composable
fun KeyPressEffectScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val enabled = prefs.getBoolean(Settings.PREF_KEY_PRESS_EFFECT, Defaults.PREF_KEY_PRESS_EFFECT)
    val colorChoice = prefs.getString(Settings.PREF_KEY_PRESS_EFFECT_COLOR, Defaults.PREF_KEY_PRESS_EFFECT_COLOR)

    val items = buildList {
        add(Settings.PREF_KEY_PRESS_EFFECT)
        // Everything below only means anything once the effect is on, and a screen of dead
        // controls invites fiddling with them and wondering why nothing happens.
        if (enabled) {
            add(R.string.settings_category_visuals)
            add(Settings.PREF_KEY_PRESS_EFFECT_SHAPE)
            add(Settings.PREF_KEY_PRESS_EFFECT_COLOR)
            if (colorChoice == KeyPressEffectDrawingPreview.COLOR_CUSTOM)
                add(Settings.PREF_KEY_PRESS_EFFECT_CUSTOM_COLOR)
            add(Settings.PREF_KEY_PRESS_EFFECT_SIZE)

            add(R.string.settings_category_motion)
            add(Settings.PREF_KEY_PRESS_EFFECT_COUNT)
            add(Settings.PREF_KEY_PRESS_EFFECT_SPEED)
            add(Settings.PREF_KEY_PRESS_EFFECT_SPREAD)
            add(Settings.PREF_KEY_PRESS_EFFECT_GRAVITY)
            add(Settings.PREF_KEY_PRESS_EFFECT_DURATION)
        }
    }
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.key_press_effect),
        settings = items
    )
}

/** Shows a multiplier the way someone reading it would say it: "1.4x". */
private fun times(value: Float) = "${(value * 10).roundToInt() / 10f}x"

fun createKeyPressEffectSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT,
        R.string.key_press_effect, R.string.key_press_effect_summary) {
        SwitchPreference(it, Defaults.PREF_KEY_PRESS_EFFECT)
    },
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT_SHAPE, R.string.key_press_effect_shape) { def ->
        val ctx = LocalContext.current
        ListPreference(
            def,
            listOf(
                ctx.getString(R.string.key_press_effect_shape_circle) to KeyPressEffectDrawingPreview.SHAPE_CIRCLE,
                ctx.getString(R.string.key_press_effect_shape_ring) to KeyPressEffectDrawingPreview.SHAPE_RING,
                ctx.getString(R.string.key_press_effect_shape_square) to KeyPressEffectDrawingPreview.SHAPE_SQUARE,
                ctx.getString(R.string.key_press_effect_shape_star) to KeyPressEffectDrawingPreview.SHAPE_STAR,
            ),
            Defaults.PREF_KEY_PRESS_EFFECT_SHAPE
        )
    },
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT_COLOR, R.string.key_press_effect_color) { def ->
        val ctx = LocalContext.current
        ListPreference(
            def,
            listOf(
                ctx.getString(R.string.key_press_effect_color_key_text) to KeyPressEffectDrawingPreview.COLOR_KEY_TEXT,
                ctx.getString(R.string.key_press_effect_color_accent) to KeyPressEffectDrawingPreview.COLOR_ACCENT,
                ctx.getString(R.string.key_press_effect_color_trail) to KeyPressEffectDrawingPreview.COLOR_GESTURE_TRAIL,
                ctx.getString(R.string.key_press_effect_color_random) to KeyPressEffectDrawingPreview.COLOR_RANDOM,
                ctx.getString(R.string.key_press_effect_color_custom) to KeyPressEffectDrawingPreview.COLOR_CUSTOM,
            ),
            Defaults.PREF_KEY_PRESS_EFFECT_COLOR
        )
    },
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT_CUSTOM_COLOR, R.string.key_press_effect_custom_color) { def ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        var showPicker by rememberSaveable { mutableStateOf(false) }
        val current = prefs.getInt(def.key, Defaults.PREF_KEY_PRESS_EFFECT_CUSTOM_COLOR)
        Preference(
            name = def.title,
            onClick = { showPicker = true }
        ) {
            // A swatch says what a hex string cannot. Sized to match the switch it sits beside.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(current), CircleShape)
            )
        }
        if (showPicker) {
            ColorPickerDialog(
                onDismissRequest = { showPicker = false },
                initialColor = current,
                title = def.title,
                showDefault = true,
                onDefault = { prefs.edit { putInt(def.key, Defaults.PREF_KEY_PRESS_EFFECT_CUSTOM_COLOR) } },
                onConfirmed = { prefs.edit { putInt(def.key, it) } }
            )
        }
    },
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT_COUNT,
        R.string.key_press_effect_count, R.string.key_press_effect_count_summary) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_KEY_PRESS_EFFECT_COUNT,
            range = 1f..40f,
            description = { it.toString() }
        )
    },
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT_SIZE, R.string.key_press_effect_size) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_KEY_PRESS_EFFECT_SIZE,
            range = 0.3f..3f,
            description = { times(it) }
        )
    },
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT_SPEED, R.string.key_press_effect_speed) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_KEY_PRESS_EFFECT_SPEED,
            range = 0.2f..3f,
            description = { times(it) }
        )
    },
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT_SPREAD,
        R.string.key_press_effect_spread, R.string.key_press_effect_spread_summary) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_KEY_PRESS_EFFECT_SPREAD,
            range = 0.05f..1f,
            description = { "${(it * 100).roundToInt()}%" }
        )
    },
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT_GRAVITY,
        R.string.key_press_effect_gravity, R.string.key_press_effect_gravity_summary) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_KEY_PRESS_EFFECT_GRAVITY,
            // Below zero the pull reverses and particles rise instead of falling, which is
            // worth having: it turns the same burst into smoke or bubbles.
            range = -1f..3f,
            description = { times(it) }
        )
    },
    Setting(context, Settings.PREF_KEY_PRESS_EFFECT_DURATION, R.string.key_press_effect_duration) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_KEY_PRESS_EFFECT_DURATION,
            range = 150f..2500f,
            description = { stringResource(R.string.abbreviation_unit_milliseconds, it.toString()) }
        )
    },
)
