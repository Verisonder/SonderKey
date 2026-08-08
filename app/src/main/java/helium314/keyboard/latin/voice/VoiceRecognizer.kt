// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import helium314.keyboard.latin.utils.Log

/**
 * Turns recorded audio into text, entirely on the device.
 *
 * The recogniser is expensive to build and cheap to keep, so it is created once per model and
 * reused. Everything here is guarded: if the engine or model is missing, transcription simply
 * returns null rather than throwing into the keyboard.
 */
class VoiceRecognizer private constructor(private val recognizer: OfflineRecognizer) {

    /** [samples] must be mono 16 kHz floats in -1..1, which is what [VoiceRecorder] produces. */
    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): String? = try {
        lastError = null
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, sampleRate)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text
        stream.release()
        text.trim().ifEmpty { null }
    } catch (t: Throwable) {
        Log.e(TAG, "transcription failed", t)
        lastError = "Transcription failed: ${t.message ?: t.javaClass.simpleName}"
        null
    }

    fun release() = runCatching { recognizer.release() }

    companion object {
        private const val TAG = "VoiceRecognizer"

        /** Two keeps a turn responsive without starving the rest of the keyboard process. */
        private const val NUM_THREADS = 2

        /**
         * Why the last attempt produced nothing. Without this, a missing library, a bad model and
         * genuine silence are indistinguishable to the user — all three just say nothing was heard.
         */
        @Volatile var lastError: String? = null
            internal set

        @Volatile private var cached: Pair<String, VoiceRecognizer>? = null

        /** Returns a recogniser for [model], or null if the engine or files are unavailable. */
        @Synchronized
        fun get(context: Context, model: VoiceModel): VoiceRecognizer? {
            cached?.let { (id, recognizer) -> if (id == model.id) return recognizer }
            if (!VoiceEngine.ensureLoaded(context)) {
                lastError = "Speech engine did not load: ${VoiceEngine.loadError ?: "unknown"}"
                return null
            }
            if (!model.isDownloaded(context)) {
                lastError = "The model files are missing"
                return null
            }
            val modelConfig = modelConfigFor(context, model)
            if (modelConfig == null) {
                lastError = "The model is described wrongly and cannot be started"
                return null
            }
            return try {
                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = model.featureDim),
                    modelConfig = modelConfig
                )
                cached?.second?.release()
                val recognizer = VoiceRecognizer(OfflineRecognizer(config = config))
                cached = model.id to recognizer
                recognizer
            } catch (t: Throwable) {
                Log.e(TAG, "could not create recognizer", t)
                lastError = "Could not start the recogniser: ${t.message ?: t.javaClass.simpleName}"
                null
            }
        }

        /**
         * Builds the sherpa configuration a model's shape calls for.
         *
         * Each architecture reads its files from a different field, and sherpa picks which by
         * seeing which one is filled in - so this is a translation from our [VoiceModel.Kind] to
         * whichever config object sherpa expects. Returns null when the model is missing a file
         * its own kind requires, which is a mistake in the entry rather than anything the user
         * did, and is worth failing loudly on rather than handing sherpa an empty path.
         */
        private fun modelConfigFor(context: Context, model: VoiceModel): OfflineModelConfig? {
            val tokens = model.fileFor(context, VoiceModel.Role.TOKENS)?.absolutePath ?: return null
            return when (model.kind) {
                VoiceModel.Kind.NEMO_CTC -> {
                    val file = model.fileFor(context, VoiceModel.Role.MODEL) ?: return null
                    OfflineModelConfig(
                        nemo = OfflineNemoEncDecCtcModelConfig(model = file.absolutePath),
                        tokens = tokens,
                        numThreads = NUM_THREADS,
                        modelType = "nemo_ctc"
                    )
                }
                VoiceModel.Kind.WHISPER -> {
                    val encoder = model.fileFor(context, VoiceModel.Role.ENCODER) ?: return null
                    val decoder = model.fileFor(context, VoiceModel.Role.DECODER) ?: return null
                    OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = encoder.absolutePath,
                            decoder = decoder.absolutePath
                        ),
                        tokens = tokens,
                        numThreads = NUM_THREADS,
                        modelType = "whisper"
                    )
                }
                VoiceModel.Kind.TRANSDUCER -> {
                    val encoder = model.fileFor(context, VoiceModel.Role.ENCODER) ?: return null
                    val decoder = model.fileFor(context, VoiceModel.Role.DECODER) ?: return null
                    val joiner = model.fileFor(context, VoiceModel.Role.JOINER) ?: return null
                    OfflineModelConfig(
                        transducer = OfflineTransducerModelConfig(
                            encoder = encoder.absolutePath,
                            decoder = decoder.absolutePath,
                            joiner = joiner.absolutePath
                        ),
                        tokens = tokens,
                        numThreads = NUM_THREADS,
                        modelType = "transducer"
                    )
                }
            }
        }

        @Synchronized
        fun releaseCached() {
            cached?.second?.release()
            cached = null
        }
    }
}
