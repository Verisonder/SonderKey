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

        /** Peak below this counts as silence. Set above room noise but below quiet speech. */
        private const val SPEECH_THRESHOLD = 0.04f

        /**
         * Consecutive quiet reads before a phrase is cut. A read is roughly 50-100 ms, so this
         * lands near half a second: long enough not to cut mid-sentence at a breath, short enough
         * that text still feels like it is keeping up.
         */
        private const val QUIET_FRAMES_TO_CUT = 6

        fun hasPermission(context: Context) = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private var record: AudioRecord? = null
    @Volatile private var recording = false
    private val samples = ArrayList<Float>(SAMPLE_RATE * 10)

    /** Emits finished phrases while recording continues, when live transcription is on. */
    @Volatile private var onSegment: ((FloatArray) -> Unit)? = null
    private var quietFrames = 0
    private var segmentStart = 0
    private var heardSpeechInSegment = false

    val isRecording get() = recording

    /**
     * Splits the audio at pauses and hands each finished phrase to [onSegment] without stopping
     * the microphone, so a phrase can be transcribed while the next one is still being spoken.
     *
     * Cutting at pauses rather than re-transcribing everything keeps the cost of each turn flat:
     * a phrase is decoded once, no matter how long the whole dictation runs.
     */
    fun setSegmentListener(listener: (FloatArray) -> Unit) { onSegment = listener }

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
                if (onSegment != null) checkForPause(peak)
                if (samples.size >= limit) {
                    recording = false
                    onAutoStop?.invoke()
                }
            }
        }
        return true
    }

    /**
     * Cuts a phrase once the level has stayed low for [QUIET_FRAMES_TO_CUT] reads following actual
     * speech. Requiring speech first stops a silent lead-in being emitted as an empty phrase, and
     * requiring a minimum length stops a cough or a door closing being sent off for transcription.
     */
    private fun checkForPause(peak: Float) {
        if (peak >= SPEECH_THRESHOLD) {
            heardSpeechInSegment = true
            quietFrames = 0
            return
        }
        if (!heardSpeechInSegment) {
            // Nothing said yet: keep the window sliding forward so leading silence is not
            // prepended to the first phrase.
            synchronized(samples) { if (samples.size - segmentStart > SAMPLE_RATE) segmentStart = samples.size }
            return
        }
        quietFrames++
        if (quietFrames < QUIET_FRAMES_TO_CUT) return

        val segment = synchronized(samples) {
            val end = samples.size
            if (end - segmentStart < SAMPLE_RATE / 2) return@synchronized null
            val slice = FloatArray(end - segmentStart)
            for (i in slice.indices) slice[i] = samples[segmentStart + i]
            segmentStart = end
            slice
        }
        quietFrames = 0
        heardSpeechInSegment = false
        if (segment != null) onSegment?.invoke(segment)
    }

    /**
     * Everything captured since recording began, without consuming it.
     *
     * Rolling mode re-transcribes the whole dictation each tick so later words can correct earlier
     * ones, which means the cost grows with the length of the turn — unlike the pause cutting
     * above, where each phrase is decoded exactly once.
     */
    fun snapshot(): FloatArray? = synchronized(samples) {
        if (samples.size < SAMPLE_RATE / 3) return@synchronized null
        val copy = FloatArray(samples.size)
        for (i in copy.indices) copy[i] = samples[i]
        copy
    }

    /**
     * Everything captured since the last cut. Used when recording ends, so a final phrase that
     * never got a trailing pause is not silently dropped.
     */
    fun takeRemainder(): FloatArray? = synchronized(samples) {
        val end = samples.size
        if (end - segmentStart < SAMPLE_RATE / 3) return@synchronized null
        val slice = FloatArray(end - segmentStart)
        for (i in slice.indices) slice[i] = samples[segmentStart + i]
        segmentStart = end
        slice
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
        onSegment = null
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        synchronized(samples) { samples.clear() }
        segmentStart = 0
        quietFrames = 0
        heardSpeechInSegment = false
    }
}
