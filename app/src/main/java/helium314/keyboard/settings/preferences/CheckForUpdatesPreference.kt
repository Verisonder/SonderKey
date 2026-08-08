// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.AppUpdater
import kotlinx.coroutines.launch

/**
 * The "Check for updates" row.
 *
 * Two taps: the first asks GitHub what the latest release is, the second downloads it and hands
 * it to the system installer. Kept here rather than inside a screen because it appears both in
 * About and in the System group of the main settings.
 */
@Composable
fun CheckForUpdatesPreference(name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<AppUpdater.Update?>(null) }

    Preference(
        name = name,
        description = when {
            busy -> state
            pending != null -> stringResource(R.string.update_available, pending!!.version)
            state.isNotEmpty() -> state
            else -> stringResource(R.string.check_for_updates_summary)
        },
        onClick = {
            if (busy) return@Preference
            if (!AppUpdater.isSupported()) {
                state = ctx.getString(R.string.update_not_available_offline)
                return@Preference
            }
            val ready = pending
            if (ready != null) {
                // second tap: fetch it and hand it to the system installer
                busy = true
                state = ctx.getString(R.string.update_downloading, 0)
                scope.launch {
                    val result = AppUpdater.download(ctx, ready) { p ->
                        state = ctx.getString(R.string.update_downloading, p)
                    }
                    busy = false
                    result.onSuccess { file ->
                        state = ""
                        pending = null
                        runCatching { AppUpdater.install(ctx, file) }
                            .onFailure { t -> state = t.message ?: "Could not open the installer" }
                    }.onFailure { t -> state = t.message ?: "Download failed" }
                }
                return@Preference
            }
            busy = true
            state = ctx.getString(R.string.update_checking)
            scope.launch {
                val result = AppUpdater.check()
                busy = false
                result.onSuccess { update ->
                    if (update == null) state = ctx.getString(R.string.update_up_to_date)
                    else { pending = update; state = "" }
                }.onFailure { t -> state = t.message ?: "Check failed" }
            }
        },
        icon = R.drawable.ic_settings_about_update
    )
}
