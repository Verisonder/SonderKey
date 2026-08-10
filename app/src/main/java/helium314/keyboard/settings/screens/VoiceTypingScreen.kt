// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlin.math.roundToInt
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.setToolbarKeyEnabled
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.isToolbarKeyEnabled
import helium314.keyboard.latin.utils.defaultToolbarPref
import helium314.keyboard.latin.utils.defaultPinnedToolbarPref
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.keyboard.KeyboardSwitcher
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import helium314.keyboard.latin.voice.VoiceEngine
import helium314.keyboard.latin.voice.VoiceEngineDownloader
import helium314.keyboard.latin.voice.VoiceModel
import helium314.keyboard.latin.voice.VoiceRecorder
import helium314.keyboard.latin.voice.VoiceModelDownloader
import helium314.keyboard.settings.SearchSettingsScreen
import kotlinx.coroutines.launch

@Composable
fun VoiceTypingScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = VoiceModel.PARAKEET_110M_EN

    var engineReady by remember { mutableStateOf(VoiceEngine.areLibrariesPresent(ctx)) }
    var modelReady by remember { mutableStateOf(model.isDownloaded(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(VoiceRecorder.hasPermission(ctx)) }
    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }
    val prefs = ctx.prefs()
    var inToolbar by remember {
        mutableStateOf(isToolbarKeyEnabled(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref, ToolbarKey.VOICE))
    }
    var voiceOnLeft by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_VOICE_KEY_ON_LEFT, Defaults.PREF_VOICE_KEY_ON_LEFT))
    }
    var pinned by remember {
        mutableStateOf(isToolbarKeyEnabled(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref, ToolbarKey.VOICE))
    }
    var pulseIndicator by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_VOICE_PULSE_INDICATOR, Defaults.PREF_VOICE_PULSE_INDICATOR))
    }
    var silenceStop by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_VOICE_SILENCE_STOP, Defaults.PREF_VOICE_SILENCE_STOP))
    }
    var numbersAsDigits by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_VOICE_NUMBERS_AS_DIGITS, Defaults.PREF_VOICE_NUMBERS_AS_DIGITS))
    }
    var autoFormat by remember {
        mutableStateOf(prefs.getBoolean(Settings.PREF_VOICE_AUTO_FORMAT, Defaults.PREF_VOICE_AUTO_FORMAT))
    }
    var silenceSeconds by remember {
        mutableIntStateOf(prefs.getInt(Settings.PREF_VOICE_SILENCE_SECONDS, Defaults.PREF_VOICE_SILENCE_SECONDS))
    }
    var transcriptionMode by remember {
        mutableStateOf(prefs.getString(Settings.PREF_VOICE_TRANSCRIPTION_MODE, Defaults.PREF_VOICE_TRANSCRIPTION_MODE)
            ?: Defaults.PREF_VOICE_TRANSCRIPTION_MODE)
    }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_voice_typing),
        settings = emptyList()
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            SetupStatus(engineReady = engineReady, modelReady = modelReady, busy = busy,
                onDownloadAll = {
                    busy = true; progress = 0; status = null
                    scope.launch {
                        var failure: String? = null
                        if (!engineReady) {
                            val r = VoiceEngineDownloader.download(ctx) { progress = it }
                            engineReady = VoiceEngine.areLibrariesPresent(ctx)
                            failure = r.exceptionOrNull()?.message
                        }
                        if (failure == null && !modelReady) {
                            val r = VoiceModelDownloader.download(ctx, model) { progress = it }
                            modelReady = model.isDownloaded(ctx)
                            failure = r.exceptionOrNull()?.message
                        }
                        busy = false
                        status = failure
                    }
                })

            Text(
                stringResource(R.string.voice_typing_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
            )

            if (!VoiceEngine.isSupportedDevice()) {
                Text(
                    stringResource(R.string.voice_typing_unsupported_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                return@Column
            }

            Text(
                stringResource(R.string.voice_typing_both_required),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Piece(
                title = stringResource(R.string.voice_typing_engine_numbered),
                detail = stringResource(R.string.voice_typing_engine_detail),
                ready = engineReady,
                busy = busy,
                onDownload = {
                    busy = true; progress = 0; status = null
                    scope.launch {
                        val result = VoiceEngineDownloader.download(ctx) { progress = it }
                        busy = false
                        engineReady = VoiceEngine.areLibrariesPresent(ctx)
                        status = result.exceptionOrNull()?.message
                    }
                },
                onDelete = { VoiceEngine.deleteLibraries(ctx); engineReady = false }
            )

            Spacer(Modifier.height(12.dp))

            Piece(
                title = stringResource(R.string.voice_typing_model_numbered, model.displayName, model.languages),
                detail = stringResource(R.string.voice_typing_model_detail, model.approximateMegabytes),
                ready = modelReady,
                busy = busy,
                onDownload = {
                    busy = true; progress = 0; status = null
                    scope.launch {
                        val result = VoiceModelDownloader.download(ctx, model) { progress = it }
                        busy = false
                        modelReady = model.isDownloaded(ctx)
                        status = result.exceptionOrNull()?.message
                    }
                },
                onDelete = { model.delete(ctx); modelReady = false }
            )

            if (engineReady && modelReady && !micGranted) {
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.voice_typing_permission_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.voice_typing_permission_detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        Button(onClick = {
                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }) { Text(stringResource(R.string.voice_typing_permission_allow)) }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.voice_typing_where),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ToggleRow(
                        title = stringResource(R.string.voice_typing_in_toolbar),
                        checked = inToolbar
                    ) {
                        inToolbar = it
                        setToolbarKeyEnabled(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref, ToolbarKey.VOICE, it)
                        KeyboardSwitcher.getInstance().setThemeNeedsReload()
                    }
                    ToggleRow(
                        title = stringResource(R.string.voice_typing_pinned),
                        checked = pinned
                    ) {
                        pinned = it
                        setToolbarKeyEnabled(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref, ToolbarKey.VOICE, it)
                        KeyboardSwitcher.getInstance().setThemeNeedsReload()
                    }
                    ToggleRow(
                        title = stringResource(R.string.voice_typing_on_left),
                        checked = voiceOnLeft
                    ) {
                        voiceOnLeft = it
                        prefs.edit { putBoolean(Settings.PREF_VOICE_KEY_ON_LEFT, it) }
                        KeyboardSwitcher.getInstance().setThemeNeedsReload()
                    }
                    ToggleRow(
                        title = stringResource(R.string.voice_typing_pulse_indicator),
                        checked = pulseIndicator
                    ) {
                        pulseIndicator = it
                        prefs.edit { putBoolean(Settings.PREF_VOICE_PULSE_INDICATOR, it) }
                    }
                    ToggleRow(
                        title = stringResource(R.string.voice_typing_silence_stop),
                        checked = silenceStop
                    ) {
                        silenceStop = it
                        prefs.edit { putBoolean(Settings.PREF_VOICE_SILENCE_STOP, it) }
                    }
                    if (silenceStop) {
                        Text(
                            text = stringResource(R.string.voice_typing_silence_seconds, silenceSeconds),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
                        )
                        Slider(
                            value = silenceSeconds.toFloat(),
                            onValueChange = { silenceSeconds = it.roundToInt() },
                            onValueChangeFinished = {
                                prefs.edit { putInt(Settings.PREF_VOICE_SILENCE_SECONDS, silenceSeconds) }
                            },
                            valueRange = 1f..15f,
                            steps = 13,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                    ToggleRow(
                        title = stringResource(R.string.voice_typing_numbers_as_digits),
                        summary = stringResource(R.string.voice_typing_numbers_as_digits_summary),
                        checked = numbersAsDigits
                    ) {
                        numbersAsDigits = it
                        prefs.edit { putBoolean(Settings.PREF_VOICE_NUMBERS_AS_DIGITS, it) }
                    }
                    ToggleRow(
                        title = stringResource(R.string.voice_typing_auto_format),
                        summary = stringResource(R.string.voice_typing_auto_format_summary),
                        checked = autoFormat
                    ) {
                        autoFormat = it
                        prefs.edit { putBoolean(Settings.PREF_VOICE_AUTO_FORMAT, it) }
                    }
                    Text(
                        text = stringResource(R.string.voice_typing_mode),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                    )
                    listOf(
                        "on_stop" to (R.string.voice_typing_mode_on_stop to R.string.voice_typing_mode_on_stop_summary),
                        "pauses" to (R.string.voice_typing_mode_pauses to R.string.voice_typing_mode_pauses_summary),
                        "rolling" to (R.string.voice_typing_mode_rolling to R.string.voice_typing_mode_rolling_summary)
                    ).forEach { (value, labels) ->
                        ChoiceRow(
                            title = stringResource(labels.first),
                            summary = stringResource(labels.second),
                            selected = transcriptionMode == value
                        ) {
                            transcriptionMode = value
                            prefs.edit { putString(Settings.PREF_VOICE_TRANSCRIPTION_MODE, value) }
                        }
                    }
                }
            }

            if (busy) {
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            status?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

        }
    }
}

@Composable
private fun ChoiceRow(title: String, summary: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** States in one place whether voice typing can actually be used, and offers to complete it. */
@Composable
private fun SetupStatus(
    engineReady: Boolean,
    modelReady: Boolean,
    busy: Boolean,
    onDownloadAll: () -> Unit
) {
    val ready = engineReady && modelReady
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (ready) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(
                    if (ready) R.string.voice_typing_status_ready else R.string.voice_typing_status_incomplete
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (ready) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(
                    if (ready) R.string.voice_typing_status_ready_detail
                    else R.string.voice_typing_status_incomplete_detail,
                    (if (engineReady) 0 else 25) + (if (modelReady) 0 else 126)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (ready) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!ready) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = onDownloadAll, enabled = !busy) {
                    Text(stringResource(R.string.voice_typing_download_all))
                }
            }
        }
    }
}

@Composable
private fun Piece(
    title: String,
    detail: String,
    ready: Boolean,
    busy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ready) {
                    Text(
                        stringResource(R.string.voice_typing_installed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.fillMaxWidth(0.02f))
                    TextButton(onClick = onDelete, enabled = !busy) {
                        Text(stringResource(R.string.voice_typing_remove))
                    }
                } else {
                    Button(onClick = onDownload, enabled = !busy) {
                        Text(stringResource(R.string.voice_typing_download))
                    }
                }
            }
        }
    }
}
