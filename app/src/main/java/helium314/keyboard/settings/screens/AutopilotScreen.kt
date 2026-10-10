// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.dialogs.ColorPickerDialog
import helium314.keyboard.keyboard.internal.AutopilotDebugDrawingPreview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

@Composable
fun AutopilotScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val enabled = prefs.getBoolean(Settings.PREF_AUTOPILOT, Defaults.PREF_AUTOPILOT)

    val items = buildList {
        add(Settings.PREF_AUTOPILOT)
        if (enabled) {
            add(Settings.PREF_AUTOPILOT_STRENGTH)
            add(Settings.PREF_AUTOPILOT_VISUAL)
            add(Settings.PREF_AUTOPILOT_DEBUG)
            if (prefs.getBoolean(Settings.PREF_AUTOPILOT_DEBUG, Defaults.PREF_AUTOPILOT_DEBUG)) {
                add(Settings.PREF_AUTOPILOT_DEBUG_SHAPE)
                add(Settings.PREF_AUTOPILOT_DEBUG_COLOR)
            }
        }
    }
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.autopilot),
        settings = items
    )
}

fun createAutopilotSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_AUTOPILOT, R.string.autopilot, R.string.autopilot_summary) {
        SwitchPreference(it, Defaults.PREF_AUTOPILOT)
    },
    Setting(context, Settings.PREF_AUTOPILOT_STRENGTH,
        R.string.autopilot_strength, R.string.autopilot_strength_summary) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_AUTOPILOT_STRENGTH,
            // Six to sixty percent of a key's width. One is a couple of pixels and barely
            // perceptible. Around five a favoured letter starts to reach into its neighbour
            // rather than only into the gap; ten claims a good part of it, and from about eight
            // upwards the effect applies to any press on a letter, not only ones near an edge.
            range = 1f..10f,
            description = { it.toString() }
        )
    },
    Setting(context, Settings.PREF_AUTOPILOT_VISUAL,
        R.string.autopilot_visual, R.string.autopilot_visual_summary) {
        SwitchPreference(it, Defaults.PREF_AUTOPILOT_VISUAL)
    },
    Setting(context, Settings.PREF_AUTOPILOT_DEBUG,
        R.string.autopilot_debug, R.string.autopilot_debug_summary) {
        SwitchPreference(it, Defaults.PREF_AUTOPILOT_DEBUG)
    },
    Setting(context, Settings.PREF_AUTOPILOT_DEBUG_SHAPE, R.string.autopilot_debug_shape) { def ->
        val ctx = LocalContext.current
        ListPreference(
            def,
            listOf(
                ctx.getString(R.string.autopilot_debug_shape_rect) to AutopilotDebugDrawingPreview.SHAPE_RECT,
                ctx.getString(R.string.autopilot_debug_shape_dashed) to AutopilotDebugDrawingPreview.SHAPE_DASHED,
                ctx.getString(R.string.autopilot_debug_shape_rounded) to AutopilotDebugDrawingPreview.SHAPE_ROUNDED,
                ctx.getString(R.string.autopilot_debug_shape_oval) to AutopilotDebugDrawingPreview.SHAPE_OVAL,
                ctx.getString(R.string.autopilot_debug_shape_filled) to AutopilotDebugDrawingPreview.SHAPE_FILLED,
            ),
            Defaults.PREF_AUTOPILOT_DEBUG_SHAPE
        )
    },
    Setting(context, Settings.PREF_AUTOPILOT_DEBUG_COLOR, R.string.autopilot_debug_color) { def ->
        val prefs = LocalContext.current.prefs()
        var showPicker by rememberSaveable { mutableStateOf(false) }
        val current = prefs.getInt(def.key, Defaults.PREF_AUTOPILOT_DEBUG_COLOR)
        Preference(name = def.title, onClick = { showPicker = true }) {
            Box(Modifier.size(40.dp).background(Color(current), CircleShape))
        }
        if (showPicker) {
            ColorPickerDialog(
                onDismissRequest = { showPicker = false },
                initialColor = current,
                title = def.title,
                showDefault = true,
                onDefault = { prefs.edit { putInt(def.key, Defaults.PREF_AUTOPILOT_DEBUG_COLOR) } },
                onConfirmed = { prefs.edit { putInt(def.key, it) } }
            )
        }
    },
)
