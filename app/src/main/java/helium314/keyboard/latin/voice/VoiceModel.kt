// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import helium314.keyboard.latin.utils.ChecksumCalculator
import java.io.File

/**
 * A speech-recognition model that can be downloaded and used for voice typing.
 *
 * Models are kept out of the app because they dwarf it — the smallest useful one is larger than
 * everything else SonderKey ships put together. Each is described here rather than hard-coded at
 * the call sites so adding another is a single entry.
 */
data class VoiceModel(
    val id: String,
    val displayName: String,
    val languages: String,
    /** approximate on-disk size, for showing before the download starts */
    val approximateMegabytes: Int,
    val modelFileName: String,
    val modelChecksum: String,
    val tokensFileName: String,
    val tokensChecksum: String,
    val releaseTag: String,
    /** how sherpa-onnx should be configured for this model */
    val kind: Kind
) {
    enum class Kind { NEMO_CTC, TRANSDUCER }

    private val baseUrl get() = "https://github.com/Verisonder/SonderKey/releases/download/$releaseTag"
    val modelUrl get() = "$baseUrl/$modelFileName"
    val tokensUrl get() = "$baseUrl/$tokensFileName"

    fun dir(context: Context) = File(File(context.filesDir, "voice-models"), id)
    fun modelFile(context: Context) = File(dir(context), "model.onnx")
    fun tokensFile(context: Context) = File(dir(context), "tokens.txt")

    fun isDownloaded(context: Context) =
        modelFile(context).isFile && tokensFile(context).isFile

    fun sizeOnDisk(context: Context): Long =
        if (isDownloaded(context)) modelFile(context).length() + tokensFile(context).length() else 0

    fun delete(context: Context) = dir(context).deleteRecursively()

    fun verify(context: Context): Boolean =
        ChecksumCalculator.checksum(modelFile(context)).equals(modelChecksum, true) &&
            ChecksumCalculator.checksum(tokensFile(context)).equals(tokensChecksum, true)

    companion object {
        val PARAKEET_110M_EN = VoiceModel(
            id = "parakeet-110m-en",
            displayName = "Parakeet 110m",
            languages = "English",
            approximateMegabytes = 126,
            modelFileName = "parakeet-110m-en-model.int8.onnx",
            modelChecksum = "9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a",
            tokensFileName = "parakeet-110m-en-tokens.txt",
            tokensChecksum = "450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10",
            releaseTag = "voice-model-parakeet-110m-en",
            kind = Kind.NEMO_CTC
        )

        val ALL = listOf(PARAKEET_110M_EN)

        fun byId(id: String?) = ALL.firstOrNull { it.id == id } ?: PARAKEET_110M_EN

        fun anyDownloaded(context: Context) = ALL.any { it.isDownloaded(context) }
    }
}
