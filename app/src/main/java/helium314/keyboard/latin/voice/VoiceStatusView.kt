// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import kotlin.math.max
import kotlin.math.min

/**
 * Shown in the suggestion strip while voice typing is active.
 *
 * Speaking into a keyboard with no feedback is unnerving — there is no way to tell whether it is
 * listening, whether it heard anything, or whether it has finished. This gives all three: a label,
 * and a level meter that moves with your voice so it is obvious the microphone is live.
 */
@SuppressLint("ViewConstructor")
class VoiceStatusView(context: Context) : LinearLayout(context) {

    private val label = TextView(context)
    private val meter = LevelMeter(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val colors = Settings.getValues().mColors
        val pad = (10 * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)

        label.text = context.getString(R.string.voice_typing_listening_short)
        label.setTextColor(colors.get(helium314.keyboard.latin.common.ColorType.KEY_TEXT))
        label.textSize = 15f
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        val meterParams = LayoutParams(0, (18 * resources.displayMetrics.density).toInt(), 1f)
        meterParams.leftMargin = pad
        addView(meter, meterParams)
    }

    fun setLevel(level: Float) = meter.setLevel(level)

    fun showTranscribing() {
        label.text = context.getString(R.string.voice_typing_working)
        meter.setIndeterminate()
    }

    /** A row of bars that rise and fall with the microphone level. */
    private class LevelMeter(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Settings.getValues().mColors.get(helium314.keyboard.latin.common.ColorType.KEY_TEXT)
        }
        private val bars = FloatArray(24)
        private var cursor = 0
        private var indeterminate = false

        fun setLevel(level: Float) {
            indeterminate = false
            bars[cursor] = min(1f, max(0.05f, level * 2.2f))
            cursor = (cursor + 1) % bars.size
            postInvalidateOnAnimation()
        }

        fun setIndeterminate() {
            indeterminate = true
            postInvalidateOnAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val slot = w / bars.size
            val barWidth = slot * 0.5f
            for (i in bars.indices) {
                // draw oldest-first so the trace scrolls left as you speak
                val value = if (indeterminate) 0.25f else bars[(cursor + i) % bars.size]
                val barHeight = max(h * 0.12f, h * value)
                val left = i * slot + (slot - barWidth) / 2f
                val top = (h - barHeight) / 2f
                paint.alpha = if (indeterminate) 90 else 200
                canvas.drawRoundRect(left, top, left + barWidth, top + barHeight, barWidth / 2, barWidth / 2, paint)
            }
        }
    }
}
