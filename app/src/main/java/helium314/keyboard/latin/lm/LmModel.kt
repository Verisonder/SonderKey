// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.lm

import android.content.Context
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Runs the model; supplied by the build flavour, absent where there is no runtime. */
interface LmBackend {
    /** Scores for the token that would follow [ids], one per vocabulary entry; null on failure. */
    fun nextTokenLogits(ids: IntArray): FloatArray?
    fun close()
}

/**
 * The on-device language model: where it comes from, where it lives, and its download.
 *
 * SmolLM2-135M (Apache-2.0), exported without a key/value cache and quantised to 8 bits, published
 * as a pre-release of this repository so the app never fetches from a third party. The checksums
 * are pinned here; a release that does not match them is refused.
 */
object LmModel {
    private const val TAG = "LmModel"
    const val RELEASE_TAG = "lm-smollm2-135m-en"
    private const val BASE_URL = "https://github.com/Verisonder/SonderKey/releases/download/$RELEASE_TAG"
    private const val USER_AGENT = "SonderKey/1.0"

    class Asset(val remoteName: String, val localName: String, val sha256: String, val approximateMegabytes: Int)

    // Checksums and sizes come from the release itself; until they are filled in nothing downloads.
    val MODEL = Asset("smollm2-135m-int8.onnx", "model.onnx", "", 140)
    val TOKENIZER = Asset("smollm2-135m-tokenizer.txt", "tokenizer.txt", "", 2)
    private val ASSETS = listOf(MODEL, TOKENIZER)

    val approximateMegabytes get() = ASSETS.sumOf { it.approximateMegabytes }

    /** False until the release's checksums have been pinned in this file. */
    val isPublished get() = ASSETS.all { it.sha256.isNotEmpty() }

    fun dir(context: Context) = File(context.filesDir, "language-model")

    fun fileOf(context: Context, asset: Asset) = File(dir(context), asset.localName)

    fun isDownloaded(context: Context) = ASSETS.all { fileOf(context, it).isFile }

    fun delete(context: Context) {
        LmPredictor.release()
        dir(context).deleteRecursively()
    }

    sealed class State {
        object Idle : State()
        class Downloading(val percent: Int) : State()
        class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts the download unless one is running; progress and failure arrive through [state]. */
    fun startDownload(context: Context) {
        if (_state.value is State.Downloading || !isPublished) return
        val app = context.applicationContext
        _state.value = State.Downloading(0)
        scope.launch {
            val dir = dir(app)
            try {
                dir.deleteRecursively()
                if (!dir.mkdirs() && !dir.isDirectory) throw Exception("Could not create the model folder")
                val total = approximateMegabytes.coerceAtLeast(1)
                var done = 0
                val parts = ASSETS.map { asset ->
                    val part = File(dir, asset.localName + ".part")
                    fetch("$BASE_URL/${asset.remoteName}", part) { percent ->
                        _state.value = State.Downloading(((done + asset.approximateMegabytes * percent / 100f) * 100 / total).toInt().coerceIn(0, 99))
                    }
                    done += asset.approximateMegabytes
                    if (!asset.sha256.equals(ChecksumCalculator.checksum(part), ignoreCase = true))
                        throw Exception("Checksum mismatch, download discarded")
                    asset to part
                }
                // Renamed only once every file is down and checked: presence is what marks it ready.
                for ((asset, part) in parts)
                    if (!part.renameTo(fileOf(app, asset))) throw Exception("Could not store ${asset.localName}")
                _state.value = State.Idle
            } catch (t: Throwable) {
                Log.e(TAG, "language model download failed", t)
                dir.deleteRecursively()
                _state.value = State.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private fun fetch(url: String, target: File, onProgress: (Int) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK)
            throw Exception("Download failed: HTTP ${connection.responseCode}")
        val total = connection.contentLength.toLong()
        connection.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(1 shl 16)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) onProgress((done * 100 / total).toInt())
                }
            }
        }
    }
}
