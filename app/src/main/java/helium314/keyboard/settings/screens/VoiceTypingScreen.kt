// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Arrangement
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
import helium314.keyboard.latin.R
import helium314.keyboard.latin.voice.VoiceEngine
import helium314.keyboard.latin.voice.VoiceEngineDownloader
import helium314.keyboard.latin.voice.VoiceModel
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

            Piece(
                title = stringResource(R.string.voice_typing_engine),
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
                title = "${model.displayName} · ${model.languages}",
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

            if (engineReady && modelReady) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.voice_typing_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
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
