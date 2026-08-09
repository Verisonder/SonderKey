// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import helium314.keyboard.latin.utils.ChecksumCalculator
import java.io.File

/**
 * A speech-recognition model that can be downloaded and used for voice typing.
 *
 * Models are kept out of the app because they dwarf it - the smallest useful one is larger than
 * everything else SonderKey ships put together. Each is described here rather than hard-coded at
 * the call sites so adding another is a single entry.
 *
 * A model used to be exactly two files, a single `.onnx` and its tokens, because the one model
 * that existed was shaped that way. Most architectures are not: Whisper is an encoder and a
 * decoder, a transducer is three networks, and a streaming model adds more again. So a model is
 * now a list of [Asset]s with a [Role] each, and [Kind] says how sherpa should be configured
 * from them. Adding a model is still a single entry.
 */
data class VoiceModel(
    val id: String,
    val displayName: String,
    val languages: String,
    val releaseTag: String,
    /** how sherpa-onnx should be configured for this model */
    val kind: Kind,
    val assets: List<Asset>,
    /**
     * Mel bins the recogniser should extract. Eighty for everything here, but Whisper's large-v3
     * wants 128, and getting it wrong does not fail - it decodes quietly into nonsense.
     */
    val featureDim: Int = 80,
    /**
     * Licence to show beside the model when it is not the permissive one the app assumes. Some
     * published models carry attribution requirements that Parakeet does not, and someone about
     * to download one deserves to be told before it lands on their phone rather than after.
     */
    val licence: String? = null
) {
    /** What a file is to the recogniser. */
    enum class Role { MODEL, TOKENS, ENCODER, DECODER, JOINER }

    enum class Kind { NEMO_CTC, WHISPER, TRANSDUCER }

    /**
     * One downloadable file. [remoteName] is the release asset name and [localName] what it is
     * called on disk: release assets need names unique across the whole repository, while the
     * files on disk read better without the model id repeated in every one of them.
     */
    data class Asset(
        val role: Role,
        val remoteName: String,
        val localName: String,
        val checksum: String,
        /** approximate size, for the estimate shown up front and to weight download progress */
        val approximateMegabytes: Int
    )

    private val baseUrl get() = "https://github.com/Verisonder/SonderKey/releases/download/$releaseTag"

    /** approximate on-disk size, for showing before the download starts */
    val approximateMegabytes get() = assets.sumOf { it.approximateMegabytes }

    fun urlOf(asset: Asset) = "$baseUrl/${asset.remoteName}"

    fun dir(context: Context) = File(File(context.filesDir, "voice-models"), id)

    fun fileOf(context: Context, asset: Asset) = File(dir(context), asset.localName)

    /** The file filling [role], or null when this model has none. */
    fun fileFor(context: Context, role: Role): File? =
        assets.firstOrNull { it.role == role }?.let { fileOf(context, it) }

    fun isDownloaded(context: Context) = assets.all { fileOf(context, it).isFile }

    fun sizeOnDisk(context: Context): Long =
        if (isDownloaded(context)) assets.sumOf { fileOf(context, it).length() } else 0

    fun delete(context: Context) = dir(context).deleteRecursively()

    fun verify(context: Context): Boolean = assets.all {
        ChecksumCalculator.checksum(fileOf(context, it)).equals(it.checksum, true)
    }

    companion object {
        val PARAKEET_110M_EN = VoiceModel(
            id = "parakeet-110m-en",
            displayName = "Parakeet 110m",
            languages = "English",
            releaseTag = "voice-model-parakeet-110m-en",
            kind = Kind.NEMO_CTC,
            assets = listOf(
                Asset(
                    role = Role.MODEL,
                    remoteName = "parakeet-110m-en-model.int8.onnx",
                    // The names these files have always had on disk. Changing either would
                    // strand every existing install into re-downloading 126 MB for nothing.
                    localName = "model.onnx",
                    checksum = "9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a",
                    approximateMegabytes = 126
                ),
                Asset(
                    role = Role.TOKENS,
                    remoteName = "parakeet-110m-en-tokens.txt",
                    localName = "tokens.txt",
                    checksum = "450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10",
                    approximateMegabytes = 0
                )
            )
        )

        val ALL = listOf(PARAKEET_110M_EN)

        fun byId(id: String?) = ALL.firstOrNull { it.id == id } ?: PARAKEET_110M_EN

        fun anyDownloaded(context: Context) = ALL.any { it.isDownloaded(context) }

        /** The model a turn should use: the chosen one if it is present, else whatever is. */
        fun active(context: Context, preferredId: String?): VoiceModel? =
            ALL.firstOrNull { it.id == preferredId && it.isDownloaded(context) }
                ?: ALL.firstOrNull { it.isDownloaded(context) }
    }
}
