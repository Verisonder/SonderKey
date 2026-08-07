// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
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
            return try {
                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        nemo = OfflineNemoEncDecCtcModelConfig(
                            model = model.modelFile(context).absolutePath
                        ),
                        tokens = model.tokensFile(context).absolutePath,
                        numThreads = 2,
                        modelType = "nemo_ctc"
                    )
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

        @Synchronized
        fun releaseCached() {
            cached?.second?.release()
            cached = null
        }
    }
}
