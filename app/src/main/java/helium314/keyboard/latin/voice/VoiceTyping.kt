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
 * With live transcription on, the microphone stays open while finished phrases are transcribed on
 * a worker thread, so text lands as you speak rather than only when you stop. The recogniser is a
 * batch one - it needs a complete phrase before it can decode - so [VoiceRecorder] cuts the audio
 * at pauses and each piece is decoded on its own. That puts the text roughly a phrase behind the
 * speaker instead of a whole dictation behind, without needing a streaming model.
 *
 * Kept deliberately defensive. Anything missing - permission, engine, model - ends the turn with a
 * message rather than an exception, because this runs inside the keyboard process.
 */
object VoiceTyping {
    private const val TAG = "VoiceTyping"

    /** Marks the end of the queue, so the worker can finish rather than block forever. */
    private val END = FloatArray(0)

    private var recorder: VoiceRecorder? = null
    private val main = Handler(Looper.getMainLooper())

    /**
     * Serialises every use of the recogniser. The live worker and the closing tail can otherwise
     * decode at the same moment, and both go through the one cached sherpa recogniser.
     */
    private val decodeLock = Any()

    private var queue: LinkedBlockingQueue<FloatArray>? = null
    private var worker: Thread? = null
    @Volatile private var cancelled = false

    val isRecording get() = recorder?.isRecording == true

    /** True when the engine and a model are both present, so the mic can do anything useful. */
    fun isReady(context: Context) =
        VoiceEngine.areLibrariesPresent(context) && VoiceModel.anyDownloaded(context)

    fun isPermissionGranted(context: Context) = VoiceRecorder.hasPermission(context)

    fun isLiveEnabled(context: Context) = context.prefs()
        .getBoolean(Settings.PREF_VOICE_LIVE_TRANSCRIPTION, Defaults.PREF_VOICE_LIVE_TRANSCRIPTION)

    /**
     * Starts recording. Returns false if it could not start, having already told the user why.
     *
     * [onText] is called on the main thread for each phrase as it is transcribed, and only when
     * live transcription is on. Otherwise nothing arrives until [stopAndTranscribe].
     */
    fun start(
        context: Context,
        onLevel: ((Float) -> Unit)? = null,
        onText: ((String) -> Unit)? = null
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
        val r = VoiceRecorder(context)
        val live = onText != null && isLiveEnabled(context)
        if (live) startWorker(context, onText)
        if (!r.start(onLevel)) {
            toast(context, context.getString(R.string.voice_typing_mic_unavailable))
            stopWorker()
            return false
        }
        // Attached only once the microphone is confirmed open, so a failed start leaves nothing behind.
        if (live) r.setSegmentListener { segment -> queue?.offer(segment) }
        recorder = r
        return true
    }

    /**
     * Drains phrases as they arrive and transcribes them one at a time. A single thread keeps what
     * was said in order and stops several decodes competing for the same cores.
     */
    private fun startWorker(context: Context, onText: ((String) -> Unit)?) {
        val q = LinkedBlockingQueue<FloatArray>()
        queue = q
        cancelled = false
        worker = thread(name = "SonderKeyLiveTranscribe") {
            while (true) {
                val segment = try { q.take() } catch (_: InterruptedException) { break }
                if (segment === END) break
                if (cancelled) continue
                val text = transcribeSegment(context, segment) ?: continue
                if (cancelled) continue
                main.post { onText?.invoke(text) }
            }
        }
    }

    private fun stopWorker() {
        queue?.offer(END)
        queue = null
        worker = null
    }

    /** Transcribes one phrase. Returns null for silence or any failure; failures are logged once. */
    private fun transcribeSegment(context: Context, samples: FloatArray): String? {
        synchronized(decodeLock) {
            VoiceRecognizer.lastError = null
            val model = VoiceModel.ALL.firstOrNull { it.isDownloaded(context) } ?: return null
            val text = VoiceRecognizer.get(context, model)?.transcribe(samples)
            if (text == null) VoiceRecognizer.lastError?.let { Log.w(TAG, "segment failed: $it") }
            return text
        }
    }

    /**
     * Ends the turn. Any phrase not yet cut is transcribed off the main thread and delivered on the
     * main thread. With live transcription on this is only the tail, because everything before it
     * has already been handed over through the onText callback.
     */
    fun stopAndTranscribe(context: Context, onResult: (String?) -> Unit) {
        val r = recorder ?: run { onResult(null); return }
        recorder = null
        val live = queue != null
        // Take the tail before stop() clears the buffer, otherwise the last phrase is lost when the
        // speaker stops talking and releases the key in one motion.
        val tail = if (live) r.takeRemainder() else null
        val samples = r.stop()
        val audio = if (live) tail else samples
        stopWorker()
        if (audio == null) { onResult(null); return }
        thread(name = "SonderKeyTranscribe") {
            val text = synchronized(decodeLock) {
                VoiceRecognizer.lastError = null
                val model = VoiceModel.ALL.firstOrNull { it.isDownloaded(context) }
                if (model == null) VoiceRecognizer.lastError = "No model is installed"
                if (model == null) null else VoiceRecognizer.get(context, model)?.transcribe(audio)
            }
            // Report the reason rather than letting every failure read as silence. In a live turn
            // the earlier phrases already landed, so a failed tail is not a silent whole turn.
            val error = VoiceRecognizer.lastError
            if (text == null && error != null && !live) toast(context, error)
            main.post { onResult(text) }
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
