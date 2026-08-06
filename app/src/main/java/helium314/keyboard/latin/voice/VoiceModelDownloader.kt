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
        val dir = model.dir(context)
        val tmpDir = File(context.cacheDir, "voice-model-${model.id}")
        try {
            tmpDir.deleteRecursively()
            tmpDir.mkdirs()

            // The model dwarfs the tokens file, so weight progress almost entirely towards it
            // rather than letting a 10 KB file take half the bar.
            val tmpModel = File(tmpDir, "model.onnx")
            fetch(model.modelUrl, tmpModel) { onProgress?.invoke((it * 0.99f).toInt()) }
            val tmpTokens = File(tmpDir, "tokens.txt")
            fetch(model.tokensUrl, tmpTokens) { }
            onProgress?.invoke(100)

            dir.deleteRecursively()
            dir.mkdirs()
            if (!tmpModel.renameTo(model.modelFile(context))) tmpModel.copyTo(model.modelFile(context), true)
            if (!tmpTokens.renameTo(model.tokensFile(context))) tmpTokens.copyTo(model.tokensFile(context), true)
            tmpDir.deleteRecursively()

            if (!model.verify(context)) {
                model.delete(context)
                return@withContext Result.failure(Exception("Checksum mismatch, download discarded"))
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "model download failed", t)
            tmpDir.deleteRecursively()
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
