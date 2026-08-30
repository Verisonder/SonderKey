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
)
