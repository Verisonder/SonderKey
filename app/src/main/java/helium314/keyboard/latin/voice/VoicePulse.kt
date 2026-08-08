// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import java.util.WeakHashMap

/**
 * Pulses a microphone key while a dictation turn is running.
 *
 * [VoiceStatusView] has to hold the entire suggestion strip for the length of a turn, so it
 * competes with suggestions for the same row and disappears the moment the user types — exactly
 * when a "still recording" signal matters most. Pulsing the microphone the user already has on
 * screen coexists with suggestions instead of displacing them.
 */
object VoicePulse {

    /** Red reads as recording everywhere else, so it does here too. */
    private const val COLOR = 0xFFE53935.toInt()

    /** One breath. Slow enough not to be a strobe, fast enough to read as live. */
    private const val PERIOD_MS = 750L

    private const val DIM_TO = 0.3f

    // Weak so a key removed by a toolbar rebuild during a turn does not keep its animator alive.
    private val animators = WeakHashMap<View, ValueAnimator>()

    fun start(view: View) {
        stop(view)
        if (view is ImageView) view.setColorFilter(COLOR)
        val animator = ValueAnimator.ofFloat(1f, DIM_TO).apply {
            duration = PERIOD_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { view.alpha = it.animatedValue as Float }
        }
        animators[view] = animator
        animator.start()
    }

    fun stop(view: View) {
        animators.remove(view)?.cancel()
        view.alpha = 1f
        // The key's own colour comes from a tint on the drawable, which this filter sits on top of,
        // so clearing the filter is enough to put it back.
        if (view is ImageView) view.clearColorFilter()
    }
}
