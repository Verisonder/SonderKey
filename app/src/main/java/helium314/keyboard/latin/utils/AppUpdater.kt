// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import helium314.keyboard.latin.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub for a newer release and installs it.
 *
 * SonderKey is not on an app store, so without this the only way to update is to notice a release
 * and fetch the APK by hand. The download is handed to the system installer, which shows its own
 * confirmation — nothing is installed silently.
 */
object AppUpdater {
    private const val TAG = "AppUpdater"
    private const val LATEST_RELEASE =
        "https://api.github.com/repos/Verisonder/SonderKey/releases/latest"
    private const val USER_AGENT = "SonderKey/${BuildConfig.VERSION_NAME}"

    data class Update(val version: String, val apkUrl: String, val sizeBytes: Long, val notes: String)

    /** The offline flavour has no INTERNET permission, so there is nothing to offer there. */
    fun isSupported() = BuildConfig.FLAVOR != "offline" && BuildConfig.FLAVOR != "offlinelite"

    /** @return an [Update] if one is newer than what is running, null if already current. */
    suspend fun check(): Result<Update?> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(fetchText(LATEST_RELEASE))
            val tag = json.getString("tag_name").removePrefix("v")
            if (compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) return@withContext Result.success(null)

            // Releases now ship a single APK named SonderKey_<version>.apk. Older releases
            // carried a flavour in the name, so prefer an exact flavour match and otherwise
            // fall back to the plain build.
            val assets = json.getJSONArray("assets")
            var url: String? = null
            var size = 0L
            var fallbackUrl: String? = null
            var fallbackSize = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.getString("name")
                if (!name.endsWith(".apk")) continue
                if (name.contains("-${BuildConfig.FLAVOR}-")) {
                    url = a.getString("browser_download_url"); size = a.getLong("size"); break
                }
                if (!name.contains("-") || name.startsWith("SonderKey_")) {
                    fallbackUrl = a.getString("browser_download_url"); fallbackSize = a.getLong("size")
                }
            }
            if (url == null) { url = fallbackUrl; size = fallbackSize }
            if (url == null) return@withContext Result.failure(
                Exception("Release $tag has no build for this variant")
            )
            Result.success(Update(tag, url, size, json.optString("body", "")))
        } catch (t: Throwable) {
            Log.w(TAG, "update check failed", t)
            Result.failure(t)
        }
    }

    suspend fun download(
        context: Context,
        update: Update,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // one file per version, and clear older ones so downloads do not accumulate
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "SonderKey-${update.version}.apk")
        try {
            val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK)
                return@withContext Result.failure(Exception("Download failed: HTTP ${connection.responseCode}"))
            val total = if (connection.contentLength > 0) connection.contentLength.toLong() else update.sizeBytes
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress?.invoke((done * 100 / total).toInt())
                    }
                }
            }
            Result.success(target)
        } catch (t: Throwable) {
            Log.e(TAG, "update download failed", t)
            target.delete()
            Result.failure(t)
        }
    }

    /** Hands the APK to the system installer, which asks the user to confirm. */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
            instanceFollowRedirects = true
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK)
            throw Exception("HTTP ${connection.responseCode}")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    /** Numeric comparison, so 2.4.10 is correctly newer than 2.4.9. */
    private fun compareVersions(a: String, b: String): Int {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrNull(i) ?: 0) - (y.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        return 0
    }
}
