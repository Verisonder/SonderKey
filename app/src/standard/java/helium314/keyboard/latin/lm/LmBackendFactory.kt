// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.lm

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.nio.LongBuffer

/** Runs the language model with ONNX Runtime. */
object LmBackendFactory {
    private const val TAG = "LmBackend"

    /** onnxruntime needs Android 7; the keyboard itself still runs on 6, without the model. */
    val isAvailable = android.os.Build.VERSION.SDK_INT >= 24

    fun open(model: File): LmBackend? = try {
        OrtBackend(model)
    } catch (t: Throwable) {
        Log.e(TAG, "could not load the language model", t)
        null
    }

    private class OrtBackend(model: File) : LmBackend {
        private val env = OrtEnvironment.getEnvironment()
        private val session: OrtSession
        private val wantsPositions: Boolean

        init {
            val options = OrtSession.SessionOptions().apply {
                // Two threads, as voice typing uses: enough to be quick without starving the app
                // being typed into.
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = env.createSession(model.absolutePath, options)
            wantsPositions = "position_ids" in session.inputNames
        }

        override fun nextTokenLogits(ids: IntArray): FloatArray? {
            val n = ids.size
            if (n == 0) return null
            val shape = longArrayOf(1, n.toLong())
            val tensors = ArrayList<OnnxTensor>(3)
            try {
                val inputs = HashMap<String, OnnxTensor>()
                inputs["input_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { ids[it].toLong() }), shape).also { tensors.add(it) }
                inputs["attention_mask"] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { 1L }), shape).also { tensors.add(it) }
                if (wantsPositions)
                    inputs["position_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { it.toLong() }), shape).also { tensors.add(it) }
                session.run(inputs).use { result ->
                    val logits = result.get(0) as OnnxTensor
                    val dims = logits.info.shape // [1, n, vocab]
                    val vocab = dims[dims.size - 1].toInt()
                    val buffer = logits.floatBuffer
                    val out = FloatArray(vocab)
                    buffer.position((n - 1) * vocab)
                    buffer.get(out)
                    return out
                }
            } finally {
                tensors.forEach { it.close() }
            }
        }

        override fun close() {
            try { session.close() } catch (_: Throwable) {}
        }
    }
}
