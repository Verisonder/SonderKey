// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Fetches the speech-recognition libraries and unpacks them beside the app's own files.
 *
 * The archive is checksummed before anything is written into place, so a truncated or tampered
 * download is discarded rather than loaded.
 */
object VoiceEngineDownloader {
    private const val TAG = "VoiceEngineDownloader"
    private const val USER_AGENT = "SonderKey/1.0"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000

    suspend fun download(
        context: Context,
        onProgress: ((Int) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = VoiceEngine.archiveUrl()
            ?: return@withContext Result.failure(Exception("Unsupported device architecture"))

        // kept out of cacheDir so Android cannot trim it out from under the download
        val tmp = File(context.filesDir, "voice-engine-download.tar.gz")
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK)
                return@withContext Result.failure(Exception("Download failed: HTTP ${connection.responseCode}"))

            val total = connection.contentLength.toLong()
            tmp.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
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

            if (!VoiceEngine.verifyArchive(tmp)) {
                tmp.delete()
                return@withContext Result.failure(Exception("Checksum mismatch, download discarded"))
            }

            val dir = VoiceEngine.libDir(context)
            dir.deleteRecursively()
            dir.mkdirs()
            GZIPInputStream(tmp.inputStream().buffered()).use { extractTar(it, dir) }
            tmp.delete()

            if (!VoiceEngine.areLibrariesPresent(context)) {
                dir.deleteRecursively()
                return@withContext Result.failure(Exception("Archive did not contain the expected libraries"))
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "voice engine download failed", t)
            tmp.delete()
            Result.failure(t)
        }
    }

    /**
     * Minimal reader for the flat, two-file archives published for this project.
     *
     * A full tar implementation is not warranted here and pulling in a compression library for it
     * would be heavier than the archive. Entries are read from the 512-byte headers, anything that
     * is not a plain file is skipped, and paths containing a separator are rejected so a crafted
     * archive cannot write outside the target directory.
     */
    private fun extractTar(input: InputStream, target: File) {
        val header = ByteArray(512)
        while (true) {
            if (!input.readFully(header)) return
            if (header.all { it == 0.toByte() }) return // end-of-archive padding

            val name = String(header, 0, 100, Charsets.US_ASCII).trimEnd('\u0000', ' ')
            if (name.isEmpty()) return
            val size = String(header, 124, 12, Charsets.US_ASCII)
                .trim('\u0000', ' ').ifEmpty { "0" }.toLong(8)
            val type = header[156].toInt().toChar()

            val padded = ((size + 511) / 512) * 512
            if (type == '0' || type == '\u0000') {
                if (name.contains('/') || name.contains('\\') || name.contains("..")) {
                    input.skipFully(padded)
                    continue
                }
                FileOutputStream(File(target, name)).use { out ->
                    val buffer = ByteArray(1 shl 16)
                    var left = size
                    while (left > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        left -= read
                    }
                }
                input.skipFully(padded - size)
            } else {
                input.skipFully(padded)
            }
        }
    }

    private fun InputStream.readFully(buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun InputStream.skipFully(count: Long) {
        var left = count
        val scratch = ByteArray(4096)
        while (left > 0) {
            val skipped = skip(left)
            if (skipped > 0) { left -= skipped; continue }
            val read = read(scratch, 0, minOf(scratch.size.toLong(), left).toInt())
            if (read <= 0) return
            left -= read
        }
    }
}
