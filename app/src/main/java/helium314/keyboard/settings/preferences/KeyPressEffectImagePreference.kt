// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.BitmapUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ConfirmationDialog

/**
 * Chooses the image a custom particle is drawn from.
 *
 * Tapping picks a new one; tapping when one is already set offers to remove it instead, which is
 * how the background image preference behaves and is worth matching rather than inventing.
 */
@Composable
fun KeyPressEffectImagePreference(setting: Setting) {
    val ctx = LocalContext.current
    val file = Settings.getKeyPressEffectImageFile(ctx)
    // Bumped after a change so the thumbnail is decoded again; the file path never changes, so
    // nothing else here would tell Compose that what is behind it has.
    var revision by remember { mutableIntStateOf(0) }
    var showRemoveDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            FileUtils.copyContentUriToNewFile(uri, ctx, file)
            // Decoded straight away as a check: a file that will not decode is better refused
            // here, where it can be explained, than silently ignored when the keyboard draws.
            if (BitmapUtils.decodeSampledBitmap(file, 256, false) == null) {
                file.delete()
                showErrorDialog = true
            }
        } catch (t: Throwable) {
            Log.w("KeyPressEffectImage", "could not read the chosen image", t)
            file.delete()
            showErrorDialog = true
        }
        revision++
    }

    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("image/*")

    val thumbnail = remember(revision) {
        if (file.isFile) BitmapUtils.decodeSampledBitmap(file, 128, false) else null
    }

    Preference(
        name = setting.title,
        description = setting.description,
        onClick = {
            if (file.isFile) showRemoveDialog = true else launcher.launch(intent)
        }
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
    }

    if (showRemoveDialog) {
        ConfirmationDialog(
            onDismissRequest = { showRemoveDialog = false },
            onConfirmed = {
                file.delete()
                revision++
            },
            confirmButtonText = stringResource(R.string.delete),
            neutralButtonText = stringResource(R.string.key_press_effect_image_replace),
            onNeutral = {
                showRemoveDialog = false
                launcher.launch(intent)
            },
            content = { androidx.compose.material3.Text(stringResource(R.string.key_press_effect_image_remove)) }
        )
    }

    if (showErrorDialog) {
        ConfirmationDialog(
            onDismissRequest = { showErrorDialog = false },
            onConfirmed = { },
            content = { androidx.compose.material3.Text(stringResource(R.string.key_press_effect_image_failed)) }
        )
    }
}
