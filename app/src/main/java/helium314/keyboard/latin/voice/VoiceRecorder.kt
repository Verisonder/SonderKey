// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.utils.Log
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Records microphone audio in the form the recogniser expects: mono, 16 kHz, floats in -1..1.
 *
 * Audio is held in memory and discarded once transcribed. Nothing is written to disk and nothing
 * leaves the device.
 */
class VoiceRecorder(private val context: Context) {

    companion object {
        private const val TAG = "VoiceRecorder"
        const val SAMPLE_RATE = 16000
        /** Recording stops here regardless, so a forgotten mic cannot fill memory. */
        const val MAX_SECONDS = 60

        fun hasPermission(context: Context) = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private var record: AudioRecord? = null
    @Volatile private var recording = false
    private val samples = ArrayList<Float>(SAMPLE_RATE * 10)

    val isRecording get() = recording

    /** [onLevel] receives a 0..1 loudness value for the waveform, on a background thread. */
    fun start(onLevel: ((Float) -> Unit)? = null, onAutoStop: (() -> Unit)? = null): Boolean {
        if (recording) return true
        if (!hasPermission(context)) return false
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val bufferSize = minBuffer * 2
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
            )
        } catch (t: Throwable) {
            Log.e(TAG, "could not open the microphone", t); return false
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release(); return false
        }

        record = audioRecord
        samples.clear()
        recording = true
        audioRecord.startRecording()

        thread(name = "SonderKeyVoiceRecorder") {
            val buffer = ShortArray(bufferSize / 2)
            val limit = SAMPLE_RATE * MAX_SECONDS
            while (recording) {
                val read = try { audioRecord.read(buffer, 0, buffer.size) } catch (t: Throwable) { -1 }
                if (read <= 0) continue
                var peak = 0f
                synchronized(samples) {
                    for (i in 0 until read) {
                        val v = buffer[i] / 32768f
                        samples.add(v)
                        val a = abs(v)
                        if (a > peak) peak = a
                    }
                }
                onLevel?.invoke(peak)
                if (samples.size >= limit) {
                    recording = false
                    onAutoStop?.invoke()
                }
            }
        }
        return true
    }

    /** Stops recording and returns what was captured, or null if it was too short to be speech. */
    fun stop(): FloatArray? {
        if (!recording && record == null) return null
        recording = false
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        val captured = synchronized(samples) { samples.toFloatArray() }
        samples.clear()
        // under a third of a second is a mis-tap rather than speech
        return if (captured.size < SAMPLE_RATE / 3) null else captured
    }

    fun cancel() {
        recording = false
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        samples.clear()
    }
}
