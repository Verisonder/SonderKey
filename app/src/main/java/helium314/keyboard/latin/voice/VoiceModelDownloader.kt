// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a [VoiceModel]'s files, checksums them, and only then puts them in place. */
object VoiceModelDownloader {
    private const val TAG = "VoiceModelDownloader"
    private const val USER_AGENT = "SonderKey/1.0"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000

    suspend fun download(
        context: Context,
        model: VoiceModel,
        onProgress: ((Int) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Download straight into the model's own directory rather than the cache. A model is
        // large enough that writing it to cacheDir can push Android into trimming that directory
        // mid-download, which took a sibling file's parent with it. Partial files carry a suffix
        // and are only renamed once every one of them has arrived.
        val dir = model.dir(context)
        try {
            dir.deleteRecursively()
            if (!dir.mkdirs() && !dir.isDirectory)
                return@withContext Result.failure(Exception("Could not create the model directory"))

            // Weight each file's share of the bar by its size, so a 300 MB encoder does not sit at
            // the same width as the tokens file beside it and make the bar lurch.
            val totalWeight = model.assets.sumOf { it.approximateMegabytes }.coerceAtLeast(1)
            var doneWeight = 0
            val parts = model.assets.map { asset ->
                val part = File(dir, asset.localName + ".part")
                val share = asset.approximateMegabytes
                fetch(model.urlOf(asset), part) { percentOfThisFile ->
                    val overall = (doneWeight + share * percentOfThisFile / 100f) / totalWeight
                    onProgress?.invoke((overall * 100).toInt().coerceIn(0, 100))
                }
                doneWeight += share
                asset to part
            }
            onProgress?.invoke(100)

            // Rename only once every file is down. A half-populated directory would otherwise
            // read as a complete model on the next launch, since presence is what marks one ready.
            parts.forEach { (asset, part) ->
                if (!part.renameTo(model.fileOf(context, asset)))
                    return@withContext Result.failure(Exception("Could not store ${asset.localName}"))
            }

            if (!model.verify(context)) {
                model.delete(context)
                return@withContext Result.failure(Exception("Checksum mismatch, download discarded"))
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "model download failed", t)
            model.delete(context)
            Result.failure(t)
        }
    }

    private fun fetch(url: String, target: File, onProgress: (Int) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK)
            throw Exception("Download failed: HTTP ${connection.responseCode} for $url")
        target.parentFile?.mkdirs()
        val total = connection.contentLength.toLong()
        connection.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(1 shl 16)
                var read: Int
                var done = 0L
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) onProgress((done * 100 / total).toInt())
                }
            }
        }
    }
}
