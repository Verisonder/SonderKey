// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.latin.translation.TranslationLoader
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.preferences.LoadHandwritingPluginPreference
import helium314.keyboard.settings.preferences.LoadTranslationPluginPreference
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import androidx.compose.runtime.collectAsState

/**
 * Words & dictionaries: every place that decides which words the keyboard knows, in one list.
 * The destination is still called Libraries so links from elsewhere keep working.
 */
@Composable
fun LibrariesHubScreen(
    onClickBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onClickDictionaries: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val autoAdd = prefs.getBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, Defaults.PREF_ADD_TO_PERSONAL_DICTIONARY)
    val items = buildList {
        add(R.string.settings_category_dictionaries)
        add(SettingsWithoutKey.SCREEN_NAV_DICTIONARIES)
        add(SettingsWithoutKey.SCREEN_NAV_PERSONAL_DICTIONARIES)
        add(SettingsWithoutKey.SCREEN_NAV_LEARNED_WORDS)
        add(SettingsWithoutKey.SCREEN_NAV_BLOCKED_WORDS)
        add(R.string.settings_category_new_words)
        add(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY)
        if (autoAdd) add(Settings.PREF_PERSONAL_DICT_ADD_AFTER)
        if (autoAdd) add(Settings.PREF_PERSONAL_DICT_REVIEW)
        if (BuildConfig.FLAVOR == "standardfull" || BuildConfig.FLAVOR == "standard") {
            add(R.string.settings_category_plugins)
            if (BuildConfig.FLAVOR == "standardfull") add(SettingsWithoutKey.HANDWRITING_PLUGIN)
            add(SettingsWithoutKey.TRANSLATION_PLUGIN)
        }
    }
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_words),
        settings = items
    )
}

fun createWordsSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_ADD_TO_PERSONAL_DICTIONARY,
        R.string.add_to_personal_dictionary, R.string.add_to_personal_dictionary_summary) {
        SwitchPreference(it, Defaults.PREF_ADD_TO_PERSONAL_DICTIONARY)
    },
    Setting(context, Settings.PREF_PERSONAL_DICT_ADD_AFTER, R.string.personal_dict_add_after) { def ->
        val res = LocalContext.current.resources
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_PERSONAL_DICT_ADD_AFTER,
            range = 1f..10f,
            description = { res.getQuantityString(R.plurals.personal_dict_add_after_value, it, it) }
        )
    },
    Setting(context, Settings.PREF_PERSONAL_DICT_REVIEW,
        R.string.personal_dict_review, R.string.personal_dict_review_summary) {
        SwitchPreference(it, Defaults.PREF_PERSONAL_DICT_REVIEW)
    },
    Setting(context, SettingsWithoutKey.HANDWRITING_PLUGIN, R.string.libraries_hub_handwriting_title) {
        val ctx = LocalContext.current
        var installed by remember { mutableStateOf(HandwritingLoader.hasPlugin(ctx)) }
        LoadHandwritingPluginPreference(
            title = it.title,
            summary = stringResource(if (installed) R.string.libraries_status_active else R.string.libraries_status_not_installed),
            icon = R.drawable.ic_edit,
            onSuccess = { installed = HandwritingLoader.hasPlugin(ctx) }
        )
    },
    Setting(context, SettingsWithoutKey.TRANSLATION_PLUGIN, R.string.libraries_hub_translation_title) {
        val ctx = LocalContext.current
        var installed by remember { mutableStateOf(TranslationLoader.hasPlugin(ctx)) }
        LoadTranslationPluginPreference(
            title = it.title,
            summary = stringResource(if (installed) R.string.libraries_status_active else R.string.libraries_status_not_installed),
            icon = R.drawable.ic_translate,
            onSuccess = { installed = TranslationLoader.hasPlugin(ctx) }
        )
    },
)
