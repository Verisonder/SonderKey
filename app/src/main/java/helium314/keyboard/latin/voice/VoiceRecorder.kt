// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
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

        /** Plenty for any dictation, and a hard stop on the list growing without bound. */
        private const val MAX_QUIET_POINTS = 512

        /**
         * Audio kept ahead of a phrase when the window slides forward through silence. A fifth of
         * a second is comfortably longer than one read, so no word can begin in the gap.
         */
        private const val PRE_ROLL_SAMPLES = SAMPLE_RATE / 5

        fun hasPermission(context: Context) = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private var record: AudioRecord? = null
    @Volatile private var recording = false
    private val samples = ArrayList<Float>(SAMPLE_RATE * 10)

    /**
     * Quiet time that ends the turn on its own, or 0 to keep listening until told otherwise.
     * The clock starts when recording does, so a microphone opened by accident closes itself
     * rather than staying open on a turn that never had any speech in it.
     */
    @Volatile var silenceTimeoutMs: Long = 0
    private var lastLoudAt = 0L

    /** Emits finished phrases while recording continues, when live transcription is on. */
    @Volatile private var onSegment: ((FloatArray) -> Unit)? = null
    private var quietFrames = 0
    private var quietRun = 0
    private var heardSpeechForQuietPoints = false
    private val quietPoints = ArrayList<Int>()
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
        synchronized(quietPoints) { quietPoints.clear() }
        quietRun = 0
        heardSpeechForQuietPoints = false
        recording = true
        lastLoudAt = SystemClock.elapsedRealtime()
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
                trackQuietPoints(peak)
                if (onSegment != null) checkForPause(peak)
                val now = SystemClock.elapsedRealtime()
                if (peak >= SPEECH_THRESHOLD) lastLoudAt = now
                val timeout = silenceTimeoutMs
                if (timeout > 0 && now - lastLoudAt >= timeout) {
                    // Stop rather than cancel: whatever was said still deserves transcribing.
                    recording = false
                    onAutoStop?.invoke()
                } else if (samples.size >= limit) {
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
    /**
     * Remembers where the speaker stopped for breath, in every mode.
     *
     * Rolling mode needs somewhere safe to stop re-decoding: audio can be frozen behind a silence
     * without cutting a word in half, and cannot be frozen anywhere else. The pause detection
     * below only runs when a segment listener is attached, which is pause mode alone, so this
     * keeps its own count. Deliberately cheap - a comparison and an integer per read.
     */
    private fun trackQuietPoints(peak: Float) {
        if (peak >= SPEECH_THRESHOLD) {
            quietRun = 0
            heardSpeechForQuietPoints = true
            return
        }
        if (!heardSpeechForQuietPoints) return
        quietRun++
        if (quietRun != QUIET_FRAMES_TO_CUT) return
        synchronized(quietPoints) {
            quietPoints.add(synchronized(samples) { samples.size })
            if (quietPoints.size > MAX_QUIET_POINTS) quietPoints.removeAt(0)
        }
    }

    /**
     * The latest pause that still leaves [minTailSamples] of audio after it, or -1 if there is
     * none. The tail requirement matters: freezing right up to the newest silence would settle
     * the words the recogniser has only just heard, which is where it is least sure of them.
     */
    fun freezePoint(after: Int, minTailSamples: Int): Int {
        val end = synchronized(samples) { samples.size }
        synchronized(quietPoints) {
            for (i in quietPoints.indices.reversed()) {
                val point = quietPoints[i]
                if (point > after && end - point >= minTailSamples) return point
            }
        }
        return -1
    }

    /** The audio between two points, for transcribing a stretch that is about to be frozen. */
    fun snapshotRange(start: Int, end: Int): FloatArray? = synchronized(samples) {
        if (start < 0 || end > samples.size || end - start < SAMPLE_RATE / 3) return@synchronized null
        val copy = FloatArray(end - start)
        for (i in copy.indices) copy[i] = samples[start + i]
        copy
    }

    /** Everything captured from [start] onwards, for re-decoding only the unfrozen tail. */
    fun snapshotFrom(start: Int): FloatArray? = synchronized(samples) {
        if (start < 0 || samples.size - start < SAMPLE_RATE / 3) return@synchronized null
        val copy = FloatArray(samples.size - start)
        for (i in copy.indices) copy[i] = samples[start + i]
        copy
    }

    private fun checkForPause(peak: Float) {
        if (peak >= SPEECH_THRESHOLD) {
            heardSpeechInSegment = true
            quietFrames = 0
            return
        }
        if (!heardSpeechInSegment) {
            // Nothing said yet: keep the window sliding forward so leading silence is not
            // prepended to the first phrase.
            //
            // The window used to jump the whole way to the current end of the buffer, which threw
            // away everything captured since the last check - including the start of a word if the
            // speaker began talking part way through that read. That is where "test" arrived as
            // "est". Leaving a short pre-roll behind costs a fraction of a second of silence at
            // the front of a phrase, which the recogniser ignores, and guarantees the onset of
            // whatever comes next is inside the segment.
            synchronized(samples) {
                if (samples.size - segmentStart > SAMPLE_RATE) {
                    segmentStart = (samples.size - PRE_ROLL_SAMPLES).coerceAtLeast(segmentStart)
                }
            }
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
