// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Where this person lands on each key, learned from the words they type.
 *
 * Kept per keyboard shape - layout, portrait or landscape, split, one-handed - since the same
 * thumb hits a key differently when the key is somewhere else. Only offsets from key centres are
 * stored, in key sizes; never words, never text.
 *
 * Samples come from [TypingLearner][helium314.keyboard.latin.touch.TypingLearner] after a word has
 * been committed and not taken back, and are read by [helium314.keyboard.keyboard.KeyDetector] on
 * every touch and by the suggestion engine's key geometry when a keyboard is built.
 */
object TouchModel {
    private const val TAG = "TouchModel"
    private const val FILE_NAME = "touch_model.txt"
    /** Samples between writes; anything newer than that is lost only if the process dies. */
    private const val SAVE_EVERY = 25

    private val lock = Any()
    private var profiles: HashMap<String, HashMap<Int, TouchStats>>? = null
    private var dir: File? = null
    private var unsaved = 0
    private val writer = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        synchronized(lock) {
            if (dir == null) dir = DeviceProtectedUtils.getFilesDir(context)
        }
    }

    /** The shape a keyboard's learned touches are filed under. */
    fun profileOf(id: KeyboardId): String {
        val landscape = Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return buildString {
            append(id.mSubtype.mainLayoutName.replace('\t', ' ').replace('\n', ' '))
            append(if (landscape) "|l" else "|p")
            if (id.mIsSplitLayout) append("s")
            if (id.mOneHandedModeEnabled) append("o")
        }
    }

    private fun loaded(): HashMap<String, HashMap<Int, TouchStats>>? {
        profiles?.let { return it }
        val d = dir ?: return null
        val map = HashMap<String, HashMap<Int, TouchStats>>()
        try {
            val f = File(d, FILE_NAME)
            if (f.exists()) f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size < 7) return@forEachLine
                val code = p[1].toIntOrNull() ?: return@forEachLine
                val stats = TouchStats.parse(p.subList(2, 7)) ?: return@forEachLine
                map.getOrPut(p[0]) { HashMap() }[code] = stats
            }
        } catch (e: Exception) {
            Log.e(TAG, "could not read touch model", e)
        }
        profiles = map
        return map
    }

    /**
     * The live statistics for a keyboard, or null when there are none or it is not a letter
     * keyboard. The map is the one learning writes into, so a detector holding it sees new
     * samples as they arrive without the keyboard being rebuilt.
     */
    fun statsFor(id: KeyboardId?): Map<Int, TouchStats>? {
        if (id == null || !id.isAlphabetKeyboard) return null
        synchronized(lock) {
            return loaded()?.getOrPut(profileOf(id)) { HashMap() }
        }
    }

    /**
     * A copy of the usable offsets for a keyboard, code to (x, y) in key sizes, for building the
     * suggestion engine's key geometry. A copy because keyboards can be built off the main thread
     * while learning writes on it.
     */
    @JvmStatic
    fun offsetsSnapshot(id: KeyboardId?, enabled: Boolean): Map<Int, FloatArray>? {
        if (!enabled || id == null || !id.isAlphabetKeyboard) return null
        synchronized(lock) {
            val stats = loaded()?.get(profileOf(id)) ?: return null
            val out = HashMap<Int, FloatArray>()
            for ((code, s) in stats) if (s.isUseful()) out[code] = floatArrayOf(s.offsetX(), s.offsetY())
            return if (out.isEmpty()) null else out
        }
    }

    /** Offset of a key's learned centre from its drawn centre, in key sizes; null if unknown. */
    @JvmStatic
    fun learnedOffset(stats: Map<Int, TouchStats>?, code: Int): TouchStats? {
        if (stats == null || code <= 0) return null
        val s = stats[Character.toLowerCase(code)] ?: return null
        return if (s.isUseful()) s else null
    }

    /** One touch, already measured against the key it was meant for. */
    class Sample(val code: Int, val dx: Float, val dy: Float)

    /** Measures a touch at ([x], [y]) against the key for [code] on [keyboard]; null if none. */
    fun measure(keyboard: Keyboard, code: Int, x: Int, y: Int): Sample? {
        if (!Constants.isValidCoordinate(x) || !Constants.isValidCoordinate(y)) return null
        if (!Character.isLetter(code)) return null
        val key: Key = keyboard.getKey(code)
            ?: keyboard.getKey(Character.toLowerCase(code))
            ?: keyboard.getKey(Character.toUpperCase(code))
            ?: return null
        if (key.width <= 0 || key.height <= 0) return null
        val dx = (x - (key.x + key.width / 2f)) / key.width
        val dy = (y - (key.y + key.height / 2f)) / key.height
        return Sample(Character.toLowerCase(code), dx, dy)
    }

    fun record(profile: String, samples: List<Sample>) {
        if (samples.isEmpty()) return
        synchronized(lock) {
            val map = loaded() ?: return
            val stats = map.getOrPut(profile) { HashMap() }
            for (s in samples) stats.getOrPut(s.code) { TouchStats() }.add(s.dx, s.dy)
            unsaved += samples.size
            if (unsaved >= SAVE_EVERY) saveLocked()
        }
    }

    /** Writes anything not yet written, for when the keyboard closes. */
    fun flush() {
        synchronized(lock) {
            if (unsaved > 0) saveLocked()
        }
    }

    /** Forgets everything learned, on every keyboard shape. */
    fun reset(context: Context) {
        init(context)
        synchronized(lock) {
            // Cleared in place: detectors hold these maps and must see them empty at once.
            loaded()?.values?.forEach { it.clear() }
            unsaved = 0
            saveLocked()
        }
    }

    /** Number of keys with enough samples to be used, across all shapes; for the settings row. */
    fun learnedKeyCount(context: Context): Int {
        init(context)
        synchronized(lock) {
            return loaded()?.values?.sumOf { m -> m.values.count { it.isUseful() } } ?: 0
        }
    }

    private fun saveLocked() {
        val d = dir ?: return
        val map = profiles ?: return
        unsaved = 0
        val snapshot = buildString {
            for ((profile, stats) in map) for ((code, s) in stats)
                append(profile).append('\t').append(code).append('\t').append(s.serialize()).append('\n')
        }
        writer.execute {
            try {
                val tmp = File(d, "$FILE_NAME.tmp")
                tmp.writeText(snapshot)
                val f = File(d, FILE_NAME)
                if (!tmp.renameTo(f)) { f.writeText(snapshot); tmp.delete() }
            } catch (e: Exception) {
                Log.e(TAG, "could not write touch model", e)
            }
        }
    }
}
