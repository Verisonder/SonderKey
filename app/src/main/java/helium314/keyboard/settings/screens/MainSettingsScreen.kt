// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.SponsorDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.CheckForUpdatesPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.previewDark

/** The wordmark, the line SonderKey stands on, and the swoosh, as on SonderShield's home. */
@Composable
private fun SettingsHero() {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp)) {
        Row {
            Text("Sonder", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Text("Key", style = MaterialTheme.typography.headlineLarge, color = accent)
        }
        Text(
            stringResource(R.string.settings_hero_line),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    // The blade: a thin accent curve, sweeping in from the right once when the page opens.
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, androidx.compose.animation.core.tween(700)) }
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val w = size.width
        val h = size.height
        val sx = w / 360f
        val sy = h / 46f
        val blade = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 46f * sy)
            quadraticTo(130f * sx, 4f * sy, 360f * sx, 12f * sy)
            lineTo(360f * sx, 20f * sy)
            quadraticTo(155f * sx, 16f * sy, 0f, 46f * sy)
            close()
        }
        clipRect(left = w * (1f - progress.value)) { drawPath(blade, accent) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickKeyPressEffect: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickGesture: () -> Unit,
    onClickLibraries: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickAIIntegration: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
        settings = emptyList(),
        searchBar = true,
    ) {
        val enabledSubtypes = remember { SubtypeSettings.getEnabledSubtypes(true) }
        val enabledSubtypesDescription = remember(enabledSubtypes) {
            enabledSubtypes.joinToString(", ") { it.displayName() }
        }
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val neverShowAgain = prefs.getBoolean(
            Settings.PREF_DONT_SHOW_SPONSOR_DIALOG,
            Defaults.PREF_DONT_SHOW_SPONSOR_DIALOG
        )
        val lastShown = prefs.getLong(
            Settings.PREF_LAST_SPONSOR_DIALOG_SHOWN,
            Defaults.PREF_LAST_SPONSOR_DIALOG_SHOWN
        )
        var settingsOpenCount by remember { mutableStateOf(prefs.getInt("settings_open_count", 0)) }
        LaunchedEffect(Unit) {
            val newCount = settingsOpenCount + 1
            prefs.edit { putInt("settings_open_count", newCount) }
            settingsOpenCount = newCount
        }
        val sixDaysMs = 6L * 24 * 60 * 60 * 1000
        val shouldShow = !neverShowAgain && (
            (lastShown == 0L && settingsOpenCount >= 2) ||
            (lastShown > 0L && System.currentTimeMillis() - lastShown >= sixDaysMs)
        )
        var showSponsorDialog by rememberSaveable { mutableStateOf(false) }
        
        if (shouldShow) {
            LaunchedEffect(Unit) {
                showSponsorDialog = true
            }
        }
        
        if (showSponsorDialog) {
            LaunchedEffect(Unit) {
                prefs.edit { putLong(Settings.PREF_LAST_SPONSOR_DIALOG_SHOWN, System.currentTimeMillis()) }
            }
            SponsorDialog(
                onDismissRequest = { showSponsorDialog = false },
                onSponsor = {
                    val intent = Intent()
                    intent.data = Links.SPONSOR.toUri()
                    intent.action = Intent.ACTION_VIEW
                    ctx.startActivity(intent)
                },
                prefs = prefs
            )
        }
        
        Scaffold(contentWindowInsets = WindowInsets(0)) { innerPadding ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(vertical = 8.dp)
            ) {
                SettingsHero()
                // Eight places, each named for what it holds, with a line saying what that is.
                // Search above reaches every individual setting directly.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        Preference(
                            name = stringResource(R.string.language_and_layouts_title),
                            description = enabledSubtypesDescription,
                            onClick = onClickLanguage,
                            icon = R.drawable.ic_settings_languages
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_appearance),
                            description = stringResource(R.string.settings_summary_appearance),
                            onClick = onClickAppearance,
                            icon = R.drawable.ic_settings_appearance
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_keys),
                            description = stringResource(R.string.settings_summary_keys),
                            onClick = onClickPreferences,
                            icon = R.drawable.ic_settings_preferences
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_typing),
                            description = stringResource(R.string.settings_summary_typing),
                            onClick = onClickTextCorrection,
                            icon = R.drawable.ic_settings_correction
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_toolbar_clipboard),
                            description = stringResource(R.string.settings_summary_toolbar_clipboard),
                            onClick = onClickToolbar,
                            icon = R.drawable.ic_settings_toolbar
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_words),
                            description = stringResource(R.string.settings_summary_words),
                            onClick = onClickLibraries,
                            icon = R.drawable.ic_dictionary
                        ) { NextScreenIcon() }
                        if (BuildConfig.FLAVOR != "offlinelite") {
                            Preference(
                                name = stringResource(R.string.settings_screen_ai_integration),
                                description = stringResource(R.string.settings_summary_ai),
                                onClick = onClickAIIntegration,
                                icon = R.drawable.ic_proofread
                            ) { NextScreenIcon() }
                        }
                        Preference(
                            name = stringResource(R.string.settings_screen_system),
                            description = stringResource(R.string.settings_summary_system),
                            onClick = onClickAdvanced,
                            icon = R.drawable.ic_settings_advanced
                        ) { NextScreenIcon() }
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    CheckForUpdatesPreference(stringResource(R.string.check_for_updates))
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
