// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.SharedPreferences
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.settings.Settings
import java.util.Locale

/**
 * The comma key popup used to be a hardcoded list. It is now a reorderable, switchable
 * preference holding [ToolbarKey] names, plus one pseudo-entry.
 *
 * The language switch key is a keyboard key rather than a toolbar key, so it has no enum
 * constant. It is carried as a plain name instead — the preference, the reorder dialog and
 * the icon set are all string keyed, so nothing else needs to know it is special.
 * Its lowercase form deliberately matches KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY so the
 * icon resolves, and there is a string resource of the same name for the label.
 */
const val LANGUAGE_SWITCH_POPUP_KEY = "LANGUAGE_SWITCH_KEY"

/** Keys that only make sense inside the clipboard view and never in a popup. */
private val commaPopupUnsupportedKeys = listOf(ToolbarKey.CLOSE_HISTORY, ToolbarKey.CLIPBOARD_SEARCH)

/** On by default. Reproduces what the hardcoded list produced on a stock configuration. */
private val commaPopupDefaultEnabled = listOf(
    ToolbarKey.CLIPBOARD.name,
    LANGUAGE_SWITCH_POPUP_KEY,
    ToolbarKey.ONE_HANDED.name,
    ToolbarKey.SETTINGS.name
)

val defaultCommaPopupPref by lazy {
    // `order` fixes the position of the entries in the customiser; only the first four are on.
    val order = commaPopupDefaultEnabled + ToolbarKey.EMOJI.name
    val others = ToolbarKey.entries
        .filterNot { it in commaPopupUnsupportedKeys || it.name in order }
        .map { it.name }
    (order + others).joinToString(Separators.ENTRY) {
        it + Separators.KV + (it in commaPopupDefaultEnabled)
    }
}

/**
 * Entry names the user switched on, in the order they arranged them.
 * Unknown names — an entry removed from [ToolbarKey] in a later version — are dropped.
 */
fun getEnabledCommaPopupKeys(prefs: SharedPreferences): List<String> =
    prefs.getString(Settings.PREF_COMMA_POPUP_KEYS, defaultCommaPopupPref)!!
        .split(Separators.ENTRY)
        .mapNotNull {
            val split = it.split(Separators.KV)
            if (split.size < 2 || split.last() != "true") return@mapNotNull null
            val name = split.first()
            if (isCommaPopupKeyAvailable(name)) name else null
        }

/** Whether the entry exists at all on this build flavour. */
fun isCommaPopupKeyAvailable(name: String): Boolean {
    if (name == LANGUAGE_SWITCH_POPUP_KEY) return true
    val key = try {
        ToolbarKey.valueOf(name)
    } catch (_: IllegalArgumentException) {
        return false
    }
    if (key in commaPopupUnsupportedKeys) return false
    return when {
        key.name.startsWith("CUSTOM_AI_") ->
            BuildConfig.FLAVOR == "standard" || BuildConfig.FLAVOR == "standardfull" || BuildConfig.FLAVOR == "offline"
        key == ToolbarKey.HANDWRITING -> BuildConfig.FLAVOR == "standardfull"
        key == ToolbarKey.PROOFREAD || key == ToolbarKey.TRANSLATE -> BuildConfig.FLAVOR != "offlinelite"
        else -> true
    }
}

/**
 * Popup key specification for an entry, or null if it has no usable code.
 *
 * KeyboardCodesSet.getCode falls back to parsing the name as an integer, so a raw negative
 * [KeyCode] works in a spec even though it has no entry in that class's name table.
 */
fun commaPopupKeySpec(name: String): String? {
    if (name == LANGUAGE_SWITCH_POPUP_KEY)
        return "!icon/language_switch_key|!code/key_language_switch"
    val key = try {
        ToolbarKey.valueOf(name)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val code = getCodeForToolbarKey(key)
    if (code == KeyCode.UNSPECIFIED) return null
    return "!icon/${key.name.lowercase(Locale.US)}|!code/$code"
}
