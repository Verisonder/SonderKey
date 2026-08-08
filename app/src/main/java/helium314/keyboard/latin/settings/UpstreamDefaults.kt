// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardTheme

/**
 * Defaults as they stood in LeanType / HeliBoard at the point SonderKey forked.
 *
 * SharedPreferences only ever stores keys the user actually changed. A setting left untouched
 * upstream therefore has no entry at all, is absent from an upstream backup, and after a restore
 * falls through to SonderKey's own default. Where that default differs from upstream's, the user
 * sees settings they never touched come back changed and reads it as the restore having failed.
 *
 * On a restore from an upstream backup we write the upstream value for every key the backup did
 * not carry, so the keyboard behaves the way it did in the app the backup came from. Anything the
 * backup did carry is left alone - a real choice always wins over a default.
 *
 * Keep this in sync with [Defaults]: whenever a default here is changed away from upstream's
 * value, the upstream value belongs in this file.
 *
 * Deliberately absent: PREF_DONT_SHOW_SPONSOR_DIALOG. Upstream shows the sponsor dialog
 * periodically and SonderKey does not. Restoring that default would start showing a donation
 * prompt the user has never seen, which is not a setting they had.
 */
object UpstreamDefaults {

    private val booleans = mapOf(
        Settings.PREF_SHOW_HINTS to true,
        Settings.PREF_NARROW_KEY_GAPS to true,
        Settings.PREF_SUGGEST_EMOJIS to true,
        Settings.PREF_SHOW_EMOJI_KEY to false,
        Settings.PREF_ALWAYS_SHOW_SUGGESTIONS to false,
        Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE to false,
        Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD to false,
    )

    private val ints = mapOf(
        Settings.PREF_KEY_LONGPRESS_TIMEOUT to 300,
    )

    // Keyed the same way MultiSliderPreference and Settings.readXxx build their keys, so the
    // indices below have to match the dimension count used at those call sites.
    private val floats by lazy {
        mapOf(
            Settings.PREF_FONT_SCALE to 0.85f,
            // upstream: Array(2) { 0.77f }
            createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 1) to 0.77f,
            createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 1, 1) to 0.77f,
            // upstream: arrayOf(1.05f, 0f)
            createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 0, 1) to 1.05f,
            createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 1, 1) to 0f,
            // upstream: Array(4) { 0.15f }
            createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 2) to 0.15f,
            createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 1, 2) to 0.15f,
            createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 2, 2) to 0.15f,
            createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 3, 2) to 0.15f,
        )
    }

    private val strings by lazy {
        mapOf(
            Settings.PREF_SPACE_VERTICAL_SWIPE to "touchpad_mode",
            Settings.PREF_ICON_STYLE to KeyboardTheme.STYLE_ROUNDED,
            Settings.PREF_THEME_COLORS_NIGHT to
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) KeyboardTheme.THEME_DYNAMIC
                    else KeyboardTheme.THEME_DARK,
        )
    }

    /** Writes the upstream value for every key the backup did not bring along. Existing keys win. */
    fun applyMissing(prefs: SharedPreferences) {
        prefs.edit(commit = true) {
            booleans.forEach { (key, value) -> if (!prefs.contains(key)) putBoolean(key, value) }
            ints.forEach { (key, value) -> if (!prefs.contains(key)) putInt(key, value) }
            floats.forEach { (key, value) -> if (!prefs.contains(key)) putFloat(key, value) }
            strings.forEach { (key, value) -> if (!prefs.contains(key)) putString(key, value) }
        }
    }
}
