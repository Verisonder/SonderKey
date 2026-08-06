// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import kotlin.concurrent.thread

/**
 * Coordinates a single voice typing turn: record, transcribe, hand the text back.
 *
 * Kept deliberately small and defensive. Anything missing — permission, engine, model — ends the
 * turn with a message rather than an exception, because this runs inside the keyboard process.
 */
object VoiceTyping {
    private const val TAG = "VoiceTyping"

    private var recorder: VoiceRecorder? = null
    private val main = Handler(Looper.getMainLooper())

    val isRecording get() = recorder?.isRecording == true

    /** True when the engine and a model are both present, so the mic can do anything useful. */
    fun isReady(context: Context) =
        VoiceEngine.areLibrariesPresent(context) && VoiceModel.anyDownloaded(context)

    fun isPermissionGranted(context: Context) = VoiceRecorder.hasPermission(context)

    /**
     * Starts recording. Returns false if it could not start, having already told the user why.
     */
    fun start(context: Context, onLevel: ((Float) -> Unit)? = null): Boolean {
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
        if (!r.start(onLevel)) {
            toast(context, context.getString(R.string.voice_typing_mic_unavailable))
            return false
        }
        recorder = r
        return true
    }

    /**
     * Stops recording and transcribes off the main thread, delivering the text on the main thread.
     */
    fun stopAndTranscribe(context: Context, onResult: (String?) -> Unit) {
        val r = recorder ?: run { onResult(null); return }
        recorder = null
        val samples = r.stop()
        if (samples == null) { onResult(null); return }
        thread(name = "SonderKeyTranscribe") {
            val model = VoiceModel.ALL.firstOrNull { it.isDownloaded(context) }
            val text = if (model == null) null
                else VoiceRecognizer.get(context, model)?.transcribe(samples)
            main.post { onResult(text) }
        }
    }

    fun cancel() {
        recorder?.cancel()
        recorder = null
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
