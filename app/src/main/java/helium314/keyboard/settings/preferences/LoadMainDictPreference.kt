// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the main dictionary for the current language.
 *
 * Without it there are no word suggestions and, less obviously, no gesture typing: a swipe is
 * matched against dictionary entries, so with an empty dictionary the gesture engine runs and
 * returns nothing at all. That failure is silent, which makes it worth offering during setup
 * rather than leaving it to be discovered.
 */
@Composable
fun LoadMainDictPreference(
    title: String,
    onSuccess: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = RichInputMethodManager.getInstance().currentSubtype.locale
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

    val installed = DictionaryInfoUtils.getCachedDictsForLocale(locale, ctx)
        ?.any { it.name.startsWith("main") } == true

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (installed) "Installed"
                    else "Word suggestions and gesture typing need this",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            when {
                busy -> CircularProgressIndicator(Modifier.size(24.dp))
                installed -> Text("\u2713", color = MaterialTheme.colorScheme.primary)
                else -> TextButton(onClick = {
                    busy = true; error = null
                    scope.launch {
                        val result = downloadMainDict(ctx, locale)
                        busy = false
                        error = result
                        if (result == null) onSuccess()
                    }
                }) { Text("Download") }
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** @return null on success, otherwise a message to show. */
private suspend fun downloadMainDict(ctx: android.content.Context, locale: java.util.Locale): String? =
    withContext(Dispatchers.IO) {
        // The repository names files main_<locale>.dict with the locale lowercased and
        // underscore separated, e.g. main_en_us.dict. Fall back to the bare language.
        val candidates = listOfNotNull(
            if (locale.country.isNotEmpty()) "main_${locale.language}_${locale.country}.dict".lowercase() else null,
            "main_${locale.language}.dict".lowercase()
        )
        for (name in candidates) {
            val urlStr = "${Links.DICTIONARY_URL}${Links.DICTIONARY_DOWNLOAD_SUFFIX}${Links.DICTIONARY_NORMAL_SUFFIX}$name"
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) continue
                val dir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, ctx)
                    ?: return@withContext "Could not find where to store the dictionary"
                File(dir).mkdirs()
                val target = File(dir, DictionaryInfoUtils.MAIN_DICT_FILE_NAME)
                conn.inputStream.use { input -> FileOutputStream(target).use { input.copyTo(it) } }
                return@withContext null
            } catch (t: Throwable) {
                Log.w("LoadMainDictPreference", "could not fetch $urlStr", t)
            }
        }
        "No dictionary available for ${locale.displayName}"
    }
