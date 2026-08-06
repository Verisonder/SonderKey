// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Derives a whole palette from a single seed colour, for both the settings UI and the keyboard.
 *
 * Written by hand rather than pulled from material-color-utilities: that library's HCT solver is
 * not public API in the Compose artifact, and vendoring it for one accent colour is not worth the
 * weight. What matters here is that tones are perceptually even and that text keeps its contrast,
 * so tones are placed on the CIE L* curve rather than on raw HSL lightness — a mid-tone HSL ramp
 * bunches up badly in the greens and yellows, which is exactly where a teal seed sits.
 */
object SonderPalette {

    const val DEFAULT_SEED = 0xFF2FB8A6.toInt()

    /** Suggested seeds shown as swatches. Chosen to stay legible as an accent on both plates. */
    val PRESETS = intArrayOf(
        0xFF2FB8A6.toInt(), // Sonder teal
        0xFF4C8DF6.toInt(), // blue
        0xFF7C6BF5.toInt(), // indigo
        0xFFB06AE8.toInt(), // violet
        0xFFE86A9E.toInt(), // pink
        0xFFE5645B.toInt(), // coral
        0xFFE8913C.toInt(), // amber
        0xFF6FBF4A.toInt(), // green
        0xFF3FB6C8.toInt(), // cyan
        0xFF8A8F98.toInt()  // graphite
    )

    // ---------------------------------------------------------------- sRGB <-> Lab

    private fun lin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun delin(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055

    private fun f(t: Double) = if (t > 216.0 / 24389.0) t.pow(1.0 / 3.0) else (841.0 / 108.0) * t + 4.0 / 29.0
    private fun fInv(t: Double) = if (t.pow(3) > 216.0 / 24389.0) t.pow(3) else (t - 4.0 / 29.0) * 108.0 / 841.0

    private fun toLab(color: Int): DoubleArray {
        val r = lin(Color.red(color) / 255.0)
        val g = lin(Color.green(color) / 255.0)
        val b = lin(Color.blue(color) / 255.0)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = (0.2126 * r + 0.7152 * g + 0.0722 * b)
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun fromLab(lab: DoubleArray): Int {
        val fy = (lab[0] + 16) / 116
        val fx = fy + lab[1] / 500
        val fz = fy - lab[2] / 200
        val x = fInv(fx) * 0.95047
        val y = fInv(fy)
        val z = fInv(fz) * 1.08883
        val r = delin(3.2406 * x - 1.5372 * y - 0.4986 * z)
        val g = delin(-0.9689 * x + 1.8758 * y + 0.0415 * z)
        val b = delin(0.0557 * x - 0.2040 * y + 1.0570 * z)
        fun ch(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt()
        return Color.rgb(ch(r), ch(g), ch(b))
    }

    /**
     * The seed's hue and chroma held constant, re-placed at lightness [tone] (0..100).
     * Chroma is trimmed near the ends of the range because fully saturated near-white and
     * near-black are not reproducible in sRGB and clip to something muddy.
     */
    fun tone(seed: Int, tone: Int): Int {
        val lab = toLab(seed)
        val t = tone.coerceIn(0, 100).toDouble()
        val edge = 1.0 - (abs(t - 55.0) / 55.0).pow(2.2)
        val k = max(0.15, edge)
        return fromLab(doubleArrayOf(t, lab[1] * k, lab[2] * k))
    }

    /** A near-neutral at [tone], carrying just enough of the seed's hue to feel related. */
    fun neutral(seed: Int, tone: Int): Int {
        val lab = toLab(seed)
        return fromLab(doubleArrayOf(tone.coerceIn(0, 100).toDouble(), lab[1] * 0.05, lab[2] * 0.05))
    }

    /** Perceived lightness of a colour, 0..100. */
    fun lightness(color: Int): Double = toLab(color)[0]

    fun isDark(color: Int) = lightness(color) < 50.0

    /** Black or white, whichever reads better on [background]. */
    fun on(background: Int): Int = if (lightness(background) > 60.0) Color.BLACK else Color.WHITE

    /**
     * Lifts a seed that is too dark or too bright to work as an accent on the given plate,
     * so a user picking near-black still gets something visible on the keyboard.
     */
    fun accentFor(seed: Int, dark: Boolean): Int {
        val l = lightness(seed)
        return when {
            dark && l < 45 -> tone(seed, 62)
            dark && l > 88 -> tone(seed, 80)
            !dark && l > 78 -> tone(seed, 52)
            !dark && l < 22 -> tone(seed, 38)
            else -> seed
        }
    }

    fun blend(a: Int, b: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        fun mix(x: Int, y: Int) = (x + (y - x) * r).roundToInt().coerceIn(0, 255)
        return Color.rgb(
            mix(Color.red(a), Color.red(b)),
            mix(Color.green(a), Color.green(b)),
            mix(Color.blue(a), Color.blue(b))
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(min(255, max(0, alpha)), Color.red(color), Color.green(color), Color.blue(color))

    // ---------------------------------------------------------------- keyboard plates

    const val DEFAULT_SURFACE = 0xFF111215.toInt()

    /** Suggested surface tints. Deliberately close to neutral — these are large areas. */
    val SURFACE_PRESETS = intArrayOf(
        0xFF111215.toInt(), // graphite (Sonder Dark)
        0xFF14171C.toInt(), // slate
        0xFF121A18.toInt(), // deep green
        0xFF151320.toInt(), // indigo
        0xFF1A1416.toInt(), // plum
        0xFF1A1712.toInt(), // umber
        0xFF0E0E0E.toInt(), // near black
        0xFF1C1C1E.toInt(), // charcoal
        0xFF101820.toInt(), // navy
        0xFF171717.toInt()  // neutral
    )

    /**
     * Keyboard surface colours.
     *
     * Two seeds rather than one: [accentSeed] drives everything that should stand out, and
     * [surfaceSeed] drives the plate the keys sit on. Deriving both from a single colour meant
     * that choosing a strong accent also tinted the whole keyboard, which is rarely what is wanted.
     */
    class Keyboard(accentSeed: Int, surfaceSeed: Int, dark: Boolean) {
        val accent: Int = accentFor(accentSeed, dark)
        val background: Int = if (dark) tone(surfaceSeed, 7) else tone(surfaceSeed, 95)
        val keyBackground: Int = if (dark) tone(surfaceSeed, 15) else tone(surfaceSeed, 100)
        val functionalKey: Int = if (dark) tone(surfaceSeed, 22) else tone(surfaceSeed, 88)
        val spaceBar: Int = keyBackground
        val keyText: Int = if (dark) tone(surfaceSeed, 93) else tone(surfaceSeed, 12)
        val keyHintText: Int = withAlpha(keyText, 0x80)
    }
}
