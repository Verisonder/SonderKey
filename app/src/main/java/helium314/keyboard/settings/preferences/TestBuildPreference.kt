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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Text
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.AppUpdater
import helium314.keyboard.latin.utils.TestBuildUpdater
import helium314.keyboard.settings.dialogs.TextInputDialog
import kotlinx.coroutines.launch

/**
 * Offers whatever build was published to the artifacts repository last.
 *
 * Three taps: one to unlock, one to look, one to install. The unlock is asked for once per visit
 * to the screen rather than remembered, since this is not a thing to leave standing open.
 *
 * The passphrase keeps this out of the way of someone poking around the debug screen. It is not a
 * security measure and should not be mistaken for one - the source is public, so the value below
 * is readable by anyone who cares to look. What makes this safe to ship is that it needs no
 * credential at all: the artifacts repository is public, so there is nothing here to steal.
 */
private const val PASSPHRASE = "826459173"

@Composable
fun TestBuildPreference(name: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<TestBuildUpdater.Build?>(null) }

    Preference(
        name = name,
        description = when {
            state.isNotEmpty() -> state
            pending != null -> "${pending!!.tag} - ${pending!!.published}"
            unlocked -> stringResource(R.string.test_build_check)
            else -> stringResource(R.string.test_build_locked)
        },
        onClick = onClick@{
            if (busy) return@onClick
            if (!unlocked) { asking = true; return@onClick }
            if (!TestBuildUpdater.isSupported()) {
                state = ctx.getString(R.string.update_not_available_offline)
                return@onClick
            }
            val ready = pending
            if (ready != null) {
                busy = true
                state = ctx.getString(R.string.update_downloading, 0)
                scope.launch {
                    val result = AppUpdater.download(ctx, TestBuildUpdater.asUpdate(ready)) { p ->
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
                return@onClick
            }
            busy = true
            state = ctx.getString(R.string.update_checking)
            scope.launch {
                val result = TestBuildUpdater.check()
                busy = false
                result.onSuccess { build ->
                    if (build == null) state = ctx.getString(R.string.test_build_none)
                    else { pending = build; state = "" }
                }.onFailure { t -> state = t.message ?: "Check failed" }
            }
        },
        icon = R.drawable.ic_settings_about_update
    )

    if (asking) {
        TextInputDialog(
            onDismissRequest = { asking = false },
            onConfirmed = { entered ->
                asking = false
                if (entered.trim() == PASSPHRASE) {
                    unlocked = true
                    state = ""
                } else {
                    state = ctx.getString(R.string.test_build_wrong_code)
                }
            },
            title = { Text(name) },
            keyboardType = KeyboardType.NumberPassword,
            checkTextValid = { it.isNotBlank() }
        )
    }
}
