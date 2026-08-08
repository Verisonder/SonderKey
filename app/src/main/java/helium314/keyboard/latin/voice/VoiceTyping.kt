// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Coordinates a voice typing turn: record, transcribe, hand the text back.
 *
 * The recogniser is a batch one - it needs a complete stretch of audio before it can decode - so
 * anything resembling live typing has to be built out of repeated whole decodes. [Mode] picks how.
 *
 * Kept deliberately defensive. Anything missing - permission, engine, model - ends the turn with a
 * message rather than an exception, because this runs inside the keyboard process.
 */
object VoiceTyping {
    private const val TAG = "VoiceTyping"

    /** How text is produced during a turn. */
    enum class Mode {
        /** Nothing appears until the mic key is pressed again. The original behaviour. */
        ON_STOP,

        /**
         * The audio is cut at pauses and each phrase decoded once. Text lands a phrase behind the
         * speaker, and the cost of a turn stays flat however long it runs.
         */
        PAUSES,

        /**
         * Everything captured so far is re-decoded on a timer and the text replaced. Words correct
         * themselves as later context arrives, but each pass is longer than the last, so a long
         * dictation gets progressively slower.
         */
        ROLLING;

        val isLive get() = this != ON_STOP

        companion object {
            fun from(value: String?) = when (value) {
                "on_stop" -> ON_STOP
                "rolling" -> ROLLING
                else -> PAUSES
            }
        }
    }

    /** Marks the end of the queue, so the worker can finish rather than block forever. */
    private val END = FloatArray(0)

    /** How often rolling mode re-decodes. Short enough to feel live, long enough to finish a pass. */
    private const val ROLLING_INTERVAL_MS = 1000L

    /**
     * Serialises every use of the recogniser. A live pass and the closing tail can otherwise decode
     * at the same moment, and both go through the one cached sherpa recogniser.
     */
    private val decodeLock = Any()

    private var recorder: VoiceRecorder? = null
    private val main = Handler(Looper.getMainLooper())

    private var queue: LinkedBlockingQueue<FloatArray>? = null
    private var worker: Thread? = null
    private var roller: Thread? = null
    @Volatile private var cancelled = false
    @Volatile private var mode = Mode.ON_STOP

    val isRecording get() = recorder?.isRecording == true

    /**
     * True while a turn is running that writes text as it goes. On-stop turns are excluded: they
     * have written nothing yet, so ending one early would throw the whole dictation away.
     */
    val isLiveTurn get() = isRecording && mode.isLive

    /** True when the engine and a model are both present, so the mic can do anything useful. */
    fun isReady(context: Context) =
        VoiceEngine.areLibrariesPresent(context) && VoiceModel.anyDownloaded(context)

    fun isPermissionGranted(context: Context) = VoiceRecorder.hasPermission(context)

    fun modeOf(context: Context) = Mode.from(
        context.prefs().getString(Settings.PREF_VOICE_TRANSCRIPTION_MODE, Defaults.PREF_VOICE_TRANSCRIPTION_MODE)
    )

    /** Quiet time that ends a turn on its own, in milliseconds, or 0 when the option is off. */
    private fun silenceTimeoutMs(context: Context): Long {
        val prefs = context.prefs()
        if (!prefs.getBoolean(Settings.PREF_VOICE_SILENCE_STOP, Defaults.PREF_VOICE_SILENCE_STOP)) return 0
        val seconds = prefs.getInt(Settings.PREF_VOICE_SILENCE_SECONDS, Defaults.PREF_VOICE_SILENCE_SECONDS)
        return seconds.coerceAtLeast(1) * 1000L
    }

    /**
     * Starts recording. Returns false if it could not start, having already told the user why.
     *
     * [onText] is called on the main thread as text becomes available, and only in a live mode.
     * The Boolean says whether this text replaces what was delivered before it (rolling) or is
     * appended after it (pauses).
     */
    fun start(
        context: Context,
        onLevel: ((Float) -> Unit)? = null,
        onText: ((String, Boolean) -> Unit)? = null,
        onAutoStop: (() -> Unit)? = null
    ): Boolean {
        if (isRecording) return true
        if (!isReady(context)) {
            toast(context, context.getString(R.string.voice_typing_not_set_up))
            openSettings(context)
            return false
        }
        if (!isPermissionGranted(context)) {
            toast(context, context.getString(R.string.voice_typing_needs_permission))
            openSettings(context)
            return false
        }
        mode = if (onText == null) Mode.ON_STOP else modeOf(context)
        val r = VoiceRecorder(context)
        r.silenceTimeoutMs = silenceTimeoutMs(context)
        cancelled = false
        if (mode == Mode.PAUSES) startWorker(context, onText)
        // Fires on the recorder thread when it stops itself, either on the silence timeout or at
        // the hard length cap. Nothing used to listen for it, so a turn that hit the cap left the
        // indicator up and never transcribed.
        if (!r.start(onLevel) { main.post { onAutoStop?.invoke() } }) {
            toast(context, context.getString(R.string.voice_typing_mic_unavailable))
            stopWorker()
            return false
        }
        // Attached only once the microphone is confirmed open, so a failed start leaves nothing behind.
        when (mode) {
            Mode.PAUSES -> r.setSegmentListener { segment -> queue?.offer(segment) }
            Mode.ROLLING -> startRoller(context, r, onText)
            Mode.ON_STOP -> {}
        }
        recorder = r
        return true
    }

    /**
     * Drains phrases as they arrive and transcribes them one at a time. A single thread keeps what
     * was said in order and stops several decodes competing for the same cores.
     */
    private fun startWorker(context: Context, onText: ((String, Boolean) -> Unit)?) {
        val q = LinkedBlockingQueue<FloatArray>()
        queue = q
        worker = thread(name = "SonderKeyLiveTranscribe") {
            while (true) {
                val segment = try { q.take() } catch (_: InterruptedException) { break }
                if (segment === END) break
                if (cancelled) continue
                val text = transcribeLocked(context, segment) ?: continue
                if (cancelled) continue
                main.post { onText?.invoke(text, false) }
            }
        }
    }

    /**
     * Re-decodes the whole turn on a timer. Each result replaces the last, so the editor always
     * shows one consistent transcription rather than phrases glued together.
     */
    private fun startRoller(context: Context, r: VoiceRecorder, onText: ((String, Boolean) -> Unit)?) {
        roller = thread(name = "SonderKeyRollingTranscribe") {
            while (!cancelled && r.isRecording) {
                try { Thread.sleep(ROLLING_INTERVAL_MS) } catch (_: InterruptedException) { break }
                if (cancelled || !r.isRecording) break
                val audio = r.snapshot() ?: continue
                val text = transcribeLocked(context, audio) ?: continue
                if (cancelled) break
                main.post { onText?.invoke(text, true) }
            }
        }
    }

    private fun stopWorker() {
        queue?.offer(END)
        queue = null
        worker = null
        roller = null
    }

    /** Transcribes one stretch of audio. Returns null for silence or any failure. */
    private fun transcribeLocked(context: Context, samples: FloatArray): String? {
        synchronized(decodeLock) {
            VoiceRecognizer.lastError = null
            val model = VoiceModel.ALL.firstOrNull { it.isDownloaded(context) } ?: return null
            val text = VoiceRecognizer.get(context, model)?.transcribe(samples)
            if (text == null) VoiceRecognizer.lastError?.let { Log.w(TAG, "pass failed: $it") }
            return text
        }
    }

    /**
     * Ends the turn and delivers the closing text on the main thread. The Boolean says whether it
     * replaces what came before: rolling always replaces, pauses appends a tail, and on-stop is the
     * whole dictation and so has nothing to replace.
     */
    fun stopAndTranscribe(context: Context, onResult: (String?, Boolean) -> Unit) {
        val r = recorder ?: run { onResult(null, false); return }
        recorder = null
        val turnMode = mode
        val replaces = turnMode == Mode.ROLLING
        // Take the audio before stop() clears the buffer, otherwise the last phrase is lost when
        // the speaker stops talking and releases the key in one motion.
        val audio = when (turnMode) {
            Mode.PAUSES -> r.takeRemainder().also { r.stop() }
            Mode.ROLLING -> r.snapshot().also { r.stop() }
            Mode.ON_STOP -> r.stop()
        }
        stopWorker()
        if (audio == null) { onResult(null, replaces); return }
        thread(name = "SonderKeyTranscribe") {
            val text = transcribeLocked(context, audio)
            // Report the reason rather than letting every failure read as silence. In a live turn
            // earlier text already landed, so a failed tail is not a silent whole turn.
            val error = VoiceRecognizer.lastError
            if (text == null && error != null && !turnMode.isLive) toast(context, error)
            main.post { onResult(text, replaces) }
        }
    }

    fun cancel() {
        cancelled = true
        recorder?.cancel()
        recorder = null
        stopWorker()
    }

    private fun toast(context: Context, message: String) =
        main.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }

    private fun openSettings(context: Context) {
        try {
            val intent = Intent(context, Class.forName("helium314.keyboard.settings.SettingsActivity")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "could not open settings", t)
        }
    }
}
