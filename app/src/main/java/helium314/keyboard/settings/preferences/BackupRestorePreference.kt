// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.AppUpgrade
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.filePicker
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.core.content.edit
import helium314.keyboard.settings.FeedbackManager

@Composable
fun BackupRestorePreference(setting: Setting) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var error: String? by rememberSaveable { mutableStateOf(null) }
    var selectedCategories by remember {
        mutableStateOf(
            BackupCategory.entries.toSet()
        )
    }
    val backupLauncher = backupLauncher(selectedCategories) { error = it }
    val restoreLauncher = restoreLauncher(selectedCategories) { error = it }
    Preference(name = setting.title, onClick = { showDialog = true })
    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            content = {
                Column {
                    Text(
                        text = stringResource(R.string.backup_select_items),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val categories = listOf(
                        BackupCategory.LAYOUTS to R.string.backup_category_layouts,
                        BackupCategory.THEME_APPEARANCE to R.string.backup_category_theme,
                        BackupCategory.DICTIONARY_HISTORY to R.string.backup_category_dictionary,
                        BackupCategory.DOWNLOADED_DICTIONARIES to R.string.backup_category_downloaded_dictionaries,
                        BackupCategory.CLIPBOARD to R.string.backup_category_clipboard,
                        BackupCategory.GENERAL_SETTINGS to R.string.backup_category_general,
                        BackupCategory.VOICE_TYPING to R.string.backup_category_voice,
                        BackupCategory.PLUGINS to R.string.backup_category_plugins
                    )
                    val allSelected = selectedCategories.size == BackupCategory.entries.size
                    TextButton(
                        onClick = {
                            selectedCategories =
                                if (allSelected) emptySet() else BackupCategory.entries.toSet()
                        }
                    ) {
                        Text(
                            stringResource(
                                if (allSelected) R.string.backup_select_none else R.string.backup_select_all
                            )
                        )
                    }
                    categories.forEach { (category, stringResId) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .toggleable(
                                    value = selectedCategories.contains(category),
                                    onValueChange = { checked ->
                                        selectedCategories = if (checked) {
                                            selectedCategories + category
                                        } else {
                                            selectedCategories - category
                                        }
                                    }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedCategories.contains(category),
                                onCheckedChange = null
                            )
                            Text(
                                text = stringResource(stringResId),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.backup_restore_message))
                }
            },
            confirmButtonText = stringResource(R.string.button_backup),
            neutralButtonText = stringResource(R.string.button_restore),
            onNeutral = {
                if (selectedCategories.isEmpty()) {
                    Toast.makeText(ctx, "Please select at least one category", Toast.LENGTH_SHORT).show()
                    return@ConfirmationDialog
                }
                showDialog = false
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                restoreLauncher.launch(intent)
            },
            onConfirmed = {
                if (selectedCategories.isEmpty()) {
                    Toast.makeText(ctx, "Please select at least one category", Toast.LENGTH_SHORT).show()
                    return@ConfirmationDialog
                }
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        ctx.getString(R.string.english_ime_name)
                            .replace(" ", "_") + "_backup_$currentDate.zip"
                    )
                    .setType("application/zip")
                backupLauncher.launch(intent)
            }
        )
    }
    if (error != null) {
        InfoDialog(
            if (error!!.startsWith("b"))
                stringResource(R.string.backup_error, error!!.drop(1))
            else stringResource(R.string.restore_error, error!!.drop(1))
        ) { error = null }
    }
}

@Composable
private fun backupLauncher(
    selectedCategories: Set<BackupCategory>,
    onError: (String) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        val filesDir = ctx.filesDir ?: return@filePicker
        val filesPath = filesDir.path + File.separator
        val files = mutableListOf<File>()
        filesDir.walk().forEach { file ->
            val path = file.path.replace(filesPath, "")
            if (file.isFile && backupFilePatterns.any { path.matches(it) }) {
                val cat = getCategoryForFilePath(path)
                if (cat == null || selectedCategories.contains(cat)) {
                    files.add(file)
                }
            }
        }
        val protectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx)
        val protectedFilesPath = protectedFilesDir.path + File.separator
        val protectedFiles = mutableListOf<File>()
        protectedFilesDir.walk().forEach { file ->
            val path = file.path.replace(protectedFilesPath, "")
            if (file.isFile && backupFilePatterns.any { path.matches(it) }) {
                val cat = getCategoryForFilePath(path)
                if (cat == null || selectedCategories.contains(cat)) {
                    protectedFiles.add(file)
                }
            }
        }
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                    val zipStream = ZipOutputStream(os)
                    files.forEach {
                        val fileStream = FileInputStream(it).buffered()
                        zipStream.putNextEntry(ZipEntry(it.path.replace(filesPath, "")))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    protectedFiles.forEach {
                        val fileStream = FileInputStream(it).buffered()
                        zipStream.putNextEntry(ZipEntry(it.path.replace(protectedFilesDir.path, "unprotected")))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    if (selectedCategories.contains(BackupCategory.CLIPBOARD)) {
                        val dbFile = ctx.getDatabasePath(Database.NAME)
                        if (dbFile.exists()) {
                            val fileStream = FileInputStream(dbFile).buffered()
                            zipStream.putNextEntry(ZipEntry(Database.NAME))
                            fileStream.copyTo(zipStream, 1024)
                            fileStream.close()
                            zipStream.closeEntry()
                        }
                    }
                    val filteredPrefs = ctx.prefs().all.filter {
                        selectedCategories.contains(getCategoryForPrefKey(it.key))
                    }
                    zipStream.putNextEntry(ZipEntry(PREFS_FILE_NAME))
                    settingsToJsonStream(filteredPrefs, zipStream)
                    zipStream.closeEntry()

                    val filteredProtectedPrefs = ctx.protectedPrefs().all.filter {
                        selectedCategories.contains(getCategoryForPrefKey(it.key))
                    }
                    zipStream.putNextEntry(ZipEntry(PROTECTED_PREFS_FILE_NAME))
                    settingsToJsonStream(filteredProtectedPrefs, zipStream)
                    zipStream.closeEntry()

                    for ((entryName, prefsForBackup) in auxiliaryPrefsToBackUp(ctx)) {
                        val cat = getCategoryForFilePath(entryName)
                        if (cat == null || selectedCategories.contains(cat)) {
                            val filteredAuxPrefs = prefsForBackup.all.filter {
                                selectedCategories.contains(getCategoryForPrefKey(it.key))
                            }
                            zipStream.putNextEntry(ZipEntry(entryName))
                            settingsToJsonStream(filteredAuxPrefs, zipStream)
                            zipStream.closeEntry()
                        }
                    }
                    zipStream.close()
                }
            } catch (t: Throwable) {
                onError("b" + t.message)
                Log.w("AdvancedScreen", "error during backup", t)
            } finally {
                wait.countDown()
            }
        }
        // A voice model alone is well over a hundred megabytes, and writing that through the
        // document provider on a slow device takes far longer than the half minute a settings-only
        // backup ever needed. Waiting too briefly does not cancel anything, it just stops us
        // hearing how it went, so the error dialog never appears for the backups most likely to fail.
        val timeoutSeconds = if (selectedCategories.any { it in largeCategories }) 600L else 30L
        if (!wait.await(timeoutSeconds, TimeUnit.SECONDS)) {
            Log.w("AdvancedScreen", "Backup timed out")
        }
    }
}

@Composable
private fun restoreLauncher(
    selectedCategories: Set<BackupCategory>,
    onError: (String) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        val wait = CountDownLatch(1)
        val restoredDb = ctx.getDatabasePath(Database.NAME + "_restored")
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                ctx.getActivity()?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        val filesDir = ctx.filesDir ?: return@execute
                        val deviceProtectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx)

                        // Targeted deletion based on selected categories
                        if (selectedCategories.contains(BackupCategory.LAYOUTS)) {
                            File(filesDir, "layouts").deleteRecursively()
                        }
                        if (selectedCategories.contains(BackupCategory.DICTIONARY_HISTORY)) {
                            // Only the learned words. Downloaded dictionaries sit in the same tree
                            // and are their own category now, so clearing the whole directory
                            // would take them out with someone who only asked for their history.
                            File(filesDir, "dicts").walkBottomUp().forEach {
                                if (it.isFile && it.name.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)) it.delete()
                            }
                            File(filesDir, "blacklists").deleteRecursively()
                            File(deviceProtectedFilesDir, "blacklists").deleteRecursively()
                            filesDir.listFiles()?.forEach {
                                if (it.name.startsWith("UserHistoryDictionary")) it.delete()
                            }
                        }
                        if (selectedCategories.contains(BackupCategory.DOWNLOADED_DICTIONARIES)) {
                            File(filesDir, "dicts").walkBottomUp().forEach {
                                if (it.isFile && it.name.endsWith(".dict")
                                    && !it.name.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)
                                ) it.delete()
                            }
                        }
                        if (selectedCategories.contains(BackupCategory.VOICE_TYPING)) {
                            File(filesDir, "voice-engine").deleteRecursively()
                            File(filesDir, "voice-models").deleteRecursively()
                        }
                        if (selectedCategories.contains(BackupCategory.PLUGINS)) {
                            File(filesDir, "handwriting_plugin.apk").delete()
                        }
                        if (selectedCategories.contains(BackupCategory.THEME_APPEARANCE)) {
                            File(filesDir, "custom_font").delete()
                            File(filesDir, "custom_emoji_font").delete()
                            deviceProtectedFilesDir.listFiles()?.forEach {
                                if (it.name.startsWith("custom_background_image")) it.delete()
                                if (it.name == "key_press_effect_particle") it.delete()
                            }
                        }
                        if (selectedCategories.contains(BackupCategory.CLIPBOARD)) {
                            ClipboardDao.closeInstance()
                            Database.closeInstance()
                            ctx.deleteDatabase(Database.NAME)
                        }

                        LayoutUtilsCustom.onLayoutFileChanged()
                        Settings.getInstance().stopListener()
                        while (entry != null) {
                            if (entry.name.startsWith("unprotected${File.separator}")) {
                                val adjustedName = entry.name.substringAfter("unprotected${File.separator}")
                                if (backupFilePatterns.any { adjustedName.matches(it) }) {
                                    val cat = getCategoryForFilePath(adjustedName)
                                    if (cat == null || selectedCategories.contains(cat)) {
                                        File(deviceProtectedFilesDir, adjustedName).delete()
                                        if (!restoreEntryToDir(zip, deviceProtectedFilesDir, adjustedName)) {
                                            Log.w("AdvancedScreen", "skipping unsafe backup entry $adjustedName")
                                        }
                                    }
                                }
                            } else if (backupFilePatterns.any { entry.name.matches(it) }) {
                                val cat = getCategoryForFilePath(entry.name)
                                if (cat == null || selectedCategories.contains(cat)) {
                                    File(filesDir, entry.name).delete()
                                    if (!restoreEntryToDir(zip, filesDir, entry.name)) {
                                        Log.w("AdvancedScreen", "skipping unsafe backup entry ${entry.name}")
                                    }
                                }
                            } else if (entry.name == Database.NAME) {
                                if (selectedCategories.contains(BackupCategory.CLIPBOARD)) {
                                    FileUtils.copyStreamToNewFile(zip, restoredDb)
                                }
                            } else if (entry.name == PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                restoreJsonLinesToSettings(prefLines, ctx.prefs(), selectedCategories)
                            } else if (entry.name == PROTECTED_PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                restoreJsonLinesToSettings(prefLines, ctx.protectedPrefs(), selectedCategories)
                            } else {
                                val auxPrefs = auxiliaryPrefsToBackUp(ctx)[entry.name]
                                if (auxPrefs != null) {
                                    val cat = getCategoryForFilePath(entry.name)
                                    if (cat == null || selectedCategories.contains(cat)) {
                                        val prefLines = String(zip.readBytes()).split("\n")
                                        restoreJsonLinesToSettings(prefLines, auxPrefs, selectedCategories)
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                if (selectedCategories.contains(BackupCategory.CLIPBOARD)) {
                    Database.copyFromDb(restoredDb, ctx)
                }
                Handler(Looper.getMainLooper()).post {
                    FeedbackManager.message(ctx, R.string.backup_restored)
                }
            } catch (t: Throwable) {
                onError("r" + t.message)
                Log.w("AdvancedScreen", "error during restore", t)
            } finally {
                wait.countDown()
            }
        }
        val timeoutSeconds = if (selectedCategories.any { it in largeCategories }) 600L else 30L
        if (!wait.await(timeoutSeconds, TimeUnit.SECONDS)) {
            Log.w("AdvancedScreen", "Restore timed out")
        }
        AppUpgrade.checkVersionUpgrade(ctx, isRestore = true)
        AppUpgrade.transferOldPinnedClips(ctx)
        Settings.getInstance().startListener()
        SubtypeSettings.reloadEnabledSubtypes(ctx)
        val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
        ctx.getActivity()?.sendBroadcast(newDictBroadcast)
        LayoutUtilsCustom.onLayoutFileChanged()
        LayoutUtilsCustom.removeMissingLayouts(ctx)
        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
        SupportedEmojis.load(ctx)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}

@Suppress("UNCHECKED_CAST") // it is checked... but whatever (except string set, because can't check for that))
private fun settingsToJsonStream(settings: Map<String?, Any?>, out: OutputStream) {
    val booleans = settings.filter { it.key is String && it.value is Boolean } as Map<String, Boolean>
    val ints = settings.filter { it.key is String && it.value is Int } as Map<String, Int>
    val longs = settings.filter { it.key is String && it.value is Long } as Map<String, Long>
    val floats = settings.filter { it.key is String && it.value is Float } as Map<String, Float>
    val strings = settings.filter { it.key is String && it.value is String } as Map<String, String>
    val stringSets = settings.filter { it.key is String && it.value is Set<*> } as Map<String, Set<String>>
    // now write
    out.write("boolean settings\n".toByteArray())
    out.write(Json.encodeToString(booleans).toByteArray())
    out.write("\nint settings\n".toByteArray())
    out.write(Json.encodeToString(ints).toByteArray())
    out.write("\nlong settings\n".toByteArray())
    out.write(Json.encodeToString(longs).toByteArray())
    out.write("\nfloat settings\n".toByteArray())
    out.write(Json.encodeToString(floats).toByteArray())
    out.write("\nstring settings\n".toByteArray())
    out.write(Json.encodeToString(strings).toByteArray())
    out.write("\nstring set settings\n".toByteArray())
    out.write(Json.encodeToString(stringSets).toByteArray())
}

/** Every value a restore intends to write, held before anything is touched. */
private class ParsedPreferences {
    val booleans = mutableMapOf<String, Boolean>()
    val ints = mutableMapOf<String, Int>()
    val longs = mutableMapOf<String, Long>()
    val floats = mutableMapOf<String, Float>()
    val strings = mutableMapOf<String, String>()
    val stringSets = mutableMapOf<String, Set<String>>()
}

/**
 * Reads a backup's preference file without touching anything, and throws if it cannot.
 *
 * Parsing is kept apart from writing on purpose. The restore used to clear the selected keys in
 * its own committed edit and only then start decoding, so a file that failed to parse - one
 * malformed line, one truncated section, one value of the wrong type - left the settings deleted
 * and nothing written back in their place.
 *
 * Every failure here is now allowed to escape to the caller. It swallowed exceptions and returned
 * a boolean that all three call sites ignored, which meant a wiped configuration was reported to
 * the user as "Backup restored".
 */
private fun parseJsonLines(list: List<String>, selectedCategories: Set<BackupCategory>): ParsedPreferences {
    val parsed = ParsedPreferences()
    val i = list.iterator()
    // Each heading is followed by its data on the next line. A file ending on a heading is
    // truncated, and saying so beats the NoSuchElementException that used to be swallowed.
    fun body(heading: String): String {
        if (!i.hasNext()) throw IllegalArgumentException("the backup ends after \"$heading\" with no data")
        return i.next()
    }
    fun wanted(key: String) = selectedCategories.contains(getCategoryForPrefKey(key))
    while (i.hasNext()) {
        val heading = i.next()
        when (heading) {
            "boolean settings" -> parsed.booleans.putAll(
                Json.decodeFromString<Map<String, Boolean>>(body(heading)).filterKeys(::wanted))
            "int settings" -> parsed.ints.putAll(
                Json.decodeFromString<Map<String, Int>>(body(heading)).filterKeys(::wanted))
            "long settings" -> parsed.longs.putAll(
                Json.decodeFromString<Map<String, Long>>(body(heading)).filterKeys(::wanted))
            "float settings" -> parsed.floats.putAll(
                Json.decodeFromString<Map<String, Float>>(body(heading)).filterKeys(::wanted))
            "string settings" -> parsed.strings.putAll(
                Json.decodeFromString<Map<String, String>>(body(heading)).filterKeys(::wanted))
            "string set settings" -> parsed.stringSets.putAll(
                Json.decodeFromString<Map<String, Set<String>>>(body(heading)).filterKeys(::wanted))
        }
    }
    return parsed
}

/**
 * Replaces the selected categories of [prefs] with what the backup holds.
 *
 * The clearing and the writing are one edit and one commit, so the old settings survive right up
 * until the new ones are ready to take their place. If the file cannot be read, this throws
 * before reaching the edit and the existing settings are left exactly as they were - the restore
 * then fails visibly, which is the point.
 */
private fun restoreJsonLinesToSettings(
    list: List<String>,
    prefs: SharedPreferences,
    selectedCategories: Set<BackupCategory>
) {
    val parsed = parseJsonLines(list, selectedCategories)
    prefs.edit(commit = true) {
        prefs.all.keys.forEach { key ->
            if (selectedCategories.contains(getCategoryForPrefKey(key))) remove(key)
        }
        parsed.booleans.forEach { putBoolean(it.key, it.value) }
        parsed.ints.forEach { putInt(it.key, it.value) }
        parsed.longs.forEach { putLong(it.key, it.value) }
        parsed.floats.forEach { putFloat(it.key, it.value) }
        parsed.strings.forEach { putString(it.key, it.value) }
        parsed.stringSets.forEach { putStringSet(it.key, it.value) }
    }
}

/**
 * Auxiliary SharedPreferences files (other than the main prefs and protectedPrefs) that
 * should be included in backups. The key is the zip entry name to use, and the value
 * is the SharedPreferences instance to read from / write back into on restore.
 *
 * NOTE: This must NOT include EncryptedSharedPreferences (e.g. "gemini_prefs"), because
 * those values are encrypted with a device-bound master key and would be unreadable on
 * any other device. Plus they typically hold credentials, which we don't want in a plain
 * backup zip.
 */
private fun auxiliaryPrefsToBackUp(ctx: android.content.Context): Map<String, SharedPreferences> =
    mapOf(
        FLOATING_KEYBOARD_PREFS_FILE_NAME
            to DeviceProtectedUtils.getSharedPreferences(ctx, "floating_keyboard_prefs"),
    )

private fun restoreEntryToDir(zip: ZipInputStream, baseDir: File, entryName: String): Boolean {
    val file = File(baseDir, entryName)
    val canonicalBase = baseDir.canonicalFile
    val canonicalTarget = file.canonicalFile
    if (canonicalTarget.path != canonicalBase.path
        && !canonicalTarget.path.startsWith(canonicalBase.path + File.separator)
    ) return false
    FileUtils.copyStreamToNewFile(zip, file)
    return true
}

private const val PREFS_FILE_NAME = "preferences.json"
private const val PROTECTED_PREFS_FILE_NAME = "protected_preferences.json"
private const val FLOATING_KEYBOARD_PREFS_FILE_NAME = "floating_keyboard_preferences.json"

private val backupFilePatterns by lazy { listOf(
    "blacklists${File.separator}.*\\.txt".toRegex(),
    "layouts${File.separator}.*${LayoutUtilsCustom.CUSTOM_LAYOUT_PREFIX}+\\..{0,4}".toRegex(), // can't expect a period at the end, as this would break restoring older backups
    "dicts${File.separator}.*${File.separator}.*user\\.dict".toRegex(),
    // Every other dictionary in the tree - the downloaded main, addon and emoji dictionaries.
    // Only the user's own words were ever matched above, so a restore left someone re-downloading
    // every language they had installed.
    "dicts${File.separator}.*${File.separator}.*\\.dict".toRegex(),
    "voice-engine${File.separator}.*".toRegex(),
    "voice-models${File.separator}.*".toRegex(),
    "handwriting_plugin\\.apk".toRegex(),
    Regex.escape(JniUtils.JNI_LIB_IMPORT_FILE_NAME).toRegex(),
    "UserHistoryDictionary.*${File.separator}UserHistoryDictionary.*\\.(body|header)".toRegex(),
    "custom_background_image.*".toRegex(),
    "key_press_effect_particle".toRegex(),
    "custom_font".toRegex(),
    "custom_emoji_font".toRegex(),
) }

enum class BackupCategory {
    LAYOUTS,
    THEME_APPEARANCE,
    DICTIONARY_HISTORY,
    DOWNLOADED_DICTIONARIES,
    CLIPBOARD,
    GENERAL_SETTINGS,
    VOICE_TYPING,
    PLUGINS
}

/**
 * Categories that can carry tens or hundreds of megabytes, so the backup needs longer than the
 * ordinary wait before it gives up reporting on itself.
 */
private val largeCategories = setOf(BackupCategory.VOICE_TYPING, BackupCategory.PLUGINS)

private fun getCategoryForPrefKey(key: String): BackupCategory {
    if (key.startsWith("layout_")) return BackupCategory.LAYOUTS
    
    val themeKeys = setOf(
        "theme_style", "icon_style", "theme_colors", "theme_colors_night",
        "theme_key_borders", "theme_auto_day_night", "custom_icon_names",
        "navbar_color", "font_scale", "emoji_font_scale", "narrow_key_gaps",
        "narrow_key_gaps_level", "emoji_key_fit", "emoji_skin_tone", "space_bar_text"
    )
    if (themeKeys.contains(key) 
        || key.startsWith("user_colors_") 
        || key.startsWith("user_all_colors_")
        || key.startsWith("user_more_colors_")
        || key.startsWith("keyboard_height_scale")
        || key.startsWith("bottom_padding_scale")
        || key.startsWith("side_padding_scale")
        || key.startsWith("split_spacer_scale")
    ) {
        return BackupCategory.THEME_APPEARANCE
    }
    
    val dictKeys = setOf(
        "use_personalized_dicts", "block_potentially_offensive", "next_word_prediction", "first_word_prediction",
        "suggest_emojis", "inline_emoji_search", "show_emoji_descriptions",
        "auto_correction", "more_auto_correction", "auto_correct_threshold",
        "autocorrect_shortcuts", "backspace_reverts_autocorrect", "suggest_punctuation",
        "add_to_personal_dictionary"
    )
    if (dictKeys.contains(key) || key.startsWith("pref_text_expander_")) return BackupCategory.DICTIONARY_HISTORY
    
    val clipboardKeys = setOf(
        "enable_clipboard_history", "suggest_screenshots", "compress_screenshots",
        "clipboard_history_retention_time", "clipboard_history_pinned_first",
        "clipboard_fold_pinned", "clear_clipboard_icon"
    )
    if (clipboardKeys.contains(key)) return BackupCategory.CLIPBOARD
    
    return BackupCategory.GENERAL_SETTINGS
}

private fun getCategoryForFilePath(path: String): BackupCategory? {
    if (path.startsWith("layouts${File.separator}") || path.contains("layouts/")) {
        return BackupCategory.LAYOUTS
    }
    if (path.startsWith("custom_background_image") || path == "custom_font" || path == "custom_emoji_font"
        || path == "key_press_effect_particle" || path == FLOATING_KEYBOARD_PREFS_FILE_NAME) {
        return BackupCategory.THEME_APPEARANCE
    }
    // The learned words and the downloaded dictionaries share a directory but not a purpose:
    // one is irreplaceable, the other is a re-download. Split them so either can be left out.
    if ((path.startsWith("dicts${File.separator}") || path.startsWith("dicts/"))
        && !path.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)
    ) {
        return BackupCategory.DOWNLOADED_DICTIONARIES
    }
    if (path.startsWith("dicts${File.separator}") || path.startsWith("dicts/")
        || path.startsWith("blacklists${File.separator}") || path.startsWith("blacklists/")
        || path.startsWith("UserHistoryDictionary")
    ) {
        return BackupCategory.DICTIONARY_HISTORY
    }
    if (path.startsWith("voice-engine") || path.startsWith("voice-models")) {
        return BackupCategory.VOICE_TYPING
    }
    if (path == "handwriting_plugin.apk" || path == JniUtils.JNI_LIB_IMPORT_FILE_NAME) {
        return BackupCategory.PLUGINS
    }
    if (path == Database.NAME) {
        return BackupCategory.CLIPBOARD
    }
    return null
}
