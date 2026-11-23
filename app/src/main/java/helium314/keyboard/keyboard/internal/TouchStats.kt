// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal

import kotlin.math.ln

/**
 * Where one person actually lands on one key.
 *
 * Offsets are in key sizes: x in fractions of the key's width, y in fractions of its height, both
 * measured from the key's centre. Someone who hits every "e" a little low and to the right ends up
 * with a positive mean on both axes, and the key's centre is moved there for them.
 *
 * The mean is a running average that forgets: the first samples count fully, and once [MAX_N] have
 * been seen every new one counts for 1/[MAX_N]. A habit that changes (new phone, new case, typing
 * one-handed for a week) is followed rather than outvoted by years of old samples.
 *
 * Nothing here is ever trusted early or far. Below [MIN_N] samples the key is left exactly where it
 * is, the learned shift is shrunk towards zero until there is enough evidence for it, and it can
 * never move a centre by more than [MAX_OFFSET] of a key. Even a bad model is a small nudge.
 *
 * No Android imports, so the arithmetic can be compiled and checked on its own.
 */
class TouchStats(
    var n: Float = 0f,
    var meanX: Float = 0f,
    var meanY: Float = 0f,
    // How scattered the touches are. Recorded so the history is there to use, not used yet: a
    // key whose catchment grows because it is hit sloppily moves boundaries in ways that are
    // hard to predict, and the offset alone already follows a hand that lands off-centre.
    var varX: Float = BASE_VARIANCE,
    var varY: Float = BASE_VARIANCE,
) {
    fun add(dx: Float, dy: Float) {
        if (dx.isNaN() || dy.isNaN()) return
        if (dx < -OUTLIER || dx > OUTLIER || dy < -OUTLIER || dy > OUTLIER) return
        n = (n + 1f).coerceAtMost(MAX_N)
        val a = 1f / n
        val ex = dx - meanX
        val ey = dy - meanY
        meanX += a * ex
        meanY += a * ey
        // Running variance with the same forgetting as the mean.
        varX = (1f - a) * (varX + a * ex * ex)
        varY = (1f - a) * (varY + a * ey * ey)
    }

    /** How far the evidence is trusted, from 0 (none) towards 1. */
    private fun trust(): Float = if (n < MIN_N) 0f else n / (n + PRIOR_N)

    fun offsetX(): Float = (meanX * trust()).coerceIn(-MAX_OFFSET, MAX_OFFSET)

    fun offsetY(): Float = (meanY * trust()).coerceIn(-MAX_OFFSET, MAX_OFFSET)

    fun isUseful(): Boolean = n >= MIN_N

    fun serialize(): String = "${fmt(n)}\t${fmt(meanX)}\t${fmt(meanY)}\t${fmt(varX)}\t${fmt(varY)}"

    companion object {
        const val MAX_N = 60f
        const val MIN_N = 5f
        const val PRIOR_N = 10f
        const val MAX_OFFSET = 0.3f
        /** A touch more than this many key sizes from the centre is not an attempt at the key. */
        const val OUTLIER = 1.2f
        const val BASE_SIGMA = 0.25f
        const val BASE_VARIANCE = BASE_SIGMA * BASE_SIGMA

        private fun fmt(f: Float) = String.format(java.util.Locale.US, "%.5f", f)

        /** Reads what [serialize] wrote; null if the fields are not five numbers. */
        fun parse(fields: List<String>): TouchStats? {
            if (fields.size < 5) return null
            val v = fields.take(5).map { it.toFloatOrNull() ?: return null }
            if (v.any { it.isNaN() || it.isInfinite() }) return null
            return TouchStats(v[0].coerceIn(0f, MAX_N), v[1], v[2], v[3].coerceAtLeast(0f), v[4].coerceAtLeast(0f))
        }
    }
}

/**
 * The decision both learned touch and Autopilot feed: which letter did this touch most likely mean.
 *
 * Every candidate key gets a score made of two parts. The first is how well the touch fits where
 * this person lands on that key - a bell curve around the key's (learned) centre, measured in key
 * sizes so that tall and wide keys are treated alike. The second, only while Autopilot is on, is
 * how likely the letter is to come next. The key with the best total wins.
 *
 * With nothing learned and Autopilot off, every key is a bell curve of the same width around its
 * own centre, and the best fit is simply the nearest centre: on a regular grid that is exactly the
 * key that was drawn under the finger, so nothing changes until there is something to change it.
 */
object TouchScoring {
    /** Width of the bell curve, in key sizes. */
    const val SIGMA = 0.4f

    /** Rough distance from one key's centre to the next, in key widths (key plus gap). */
    const val PITCH = 1.25f

    /** A touch this close to a key's (learned) centre, in key sizes, always stays on that key. */
    const val CENTRE_GUARD = 0.15f

    /**
     * Autopilot strength 1..10 maps to this many units of prior per step. Chosen so that 10 is
     * where a fully expected letter reaches its unexpected neighbour's centre guard; beyond that
     * the slider would do nothing, so the whole range is usable.
     */
    const val LAMBDA_PER_STEP = 0.128f

    fun lambdaForStrength(strength: Int): Float = strength.coerceIn(1, 10) * LAMBDA_PER_STEP

    /**
     * Fit of a touch at ([dx], [dy]) - measured from the key's drawn centre, in key sizes - to a
     * key whose learned centre is at ([offX], [offY]) with spread factors [sx], [sy].
     */
    fun fit(dx: Float, dy: Float, offX: Float, offY: Float, sx: Float, sy: Float): Float {
        val ux = (dx - offX) / (SIGMA * sx)
        val uy = (dy - offY) / (SIGMA * sy)
        return -0.5f * (ux * ux + uy * uy)
    }

    /** Log of how expected a letter is, given its weight 0..1 and the floor for unexpected ones. */
    fun logPrior(weight: Float, floor: Float): Float = ln(floor + (1f - floor) * weight.coerceIn(0f, 1f))

    /**
     * How far, in key widths, an expected letter's catchment reaches into a neighbour that is not
     * expected at all, for two keys of the same size side by side. Derived from the scoring above:
     * where the two scores are equal is shifted from the midpoint by lambda * prior gap * SIGMA² /
     * PITCH. It stops where the neighbour's centre guard begins, as the decision does. Used only
     * to draw the effect, which must describe the same thing the decision does.
     */
    fun reach(lambda: Float, weight: Float, floor: Float): Float {
        if (lambda <= 0f || weight <= 0f) return 0f
        val gap = logPrior(weight, floor) - logPrior(0f, floor)
        return (lambda * gap * SIGMA * SIGMA / PITCH).coerceAtMost(PITCH / 2f - CENTRE_GUARD)
    }
}
