// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import android.content.Context
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Corrections the user has undone and kept undone.
 *
 * Backspacing straight after an auto-correction puts the typed word back. Before, that only held
 * for the one occasion: the next time the same word was typed it was corrected again, and the
 * time after that. Once a correction has been undone and the typed word then kept, the pair is
 * remembered here and the same correction is not made again. The correction is still offered in
 * the suggestion strip, and picking it from there by hand forgets the refusal.
 *
 * Words are compared ignoring case. Only the pair is refused - the same typed word can still be
 * corrected to something else, and the correction can still be made from other typed words.
 */
object RefusedCorrections {
    private const val TAG = "RefusedCorrections"
    private const val FILE_NAME = "refused_corrections.txt"

    private val lock = Any()
    private var set: RefusalSet? = null
    private var dir: File? = null
    private val writer = Executors.newSingleThreadExecutor()

    /** Called once the keyboard has a context; until then nothing is refused. */
    fun init(context: Context) {
        synchronized(lock) {
            if (dir == null) dir = DeviceProtectedUtils.getFilesDir(context)
        }
    }

    private fun loaded(): RefusalSet? {
        set?.let { return it }
        val d = dir ?: return null
        val s = RefusalSet()
        try {
            val f = File(d, FILE_NAME)
            if (f.exists()) s.load(f.readLines())
        } catch (e: Exception) {
            Log.e(TAG, "could not read refused corrections", e)
        }
        set = s
        return s
    }

    fun isRefused(typed: String?, correction: String?): Boolean {
        if (typed.isNullOrEmpty() || correction.isNullOrEmpty()) return false
        synchronized(lock) {
            return loaded()?.contains(typed, correction) == true
        }
    }

    fun refuse(typed: String, correction: String) {
        synchronized(lock) {
            val s = loaded() ?: return
            if (s.add(typed, correction, System.currentTimeMillis())) saveLocked(s)
        }
    }

    fun allow(typed: String, correction: String) {
        synchronized(lock) {
            val s = loaded() ?: return
            if (s.remove(typed, correction)) saveLocked(s)
        }
    }

    fun count(): Int = synchronized(lock) { loaded()?.size() ?: 0 }

    fun clear() {
        synchronized(lock) {
            val s = loaded() ?: return
            s.clear()
            saveLocked(s)
        }
    }

    private fun saveLocked(s: RefusalSet) {
        val d = dir ?: return
        val snapshot = s.serialize()
        writer.execute {
            try {
                val tmp = File(d, "$FILE_NAME.tmp")
                tmp.writeText(snapshot)
                val f = File(d, FILE_NAME)
                if (!tmp.renameTo(f)) { f.writeText(snapshot); tmp.delete() }
            } catch (e: Exception) {
                Log.e(TAG, "could not write refused corrections", e)
            }
        }
    }
}

/**
 * The refused pairs themselves, oldest first, capped at [MAX]. No Android imports, so it can be
 * compiled and checked on its own.
 */
class RefusalSet {
    private val entries = LinkedHashMap<String, Long>()

    private fun key(typed: String, correction: String) =
        typed.lowercase() + "\t" + correction.lowercase()

    fun contains(typed: String, correction: String) = entries.containsKey(key(typed, correction))

    /** @return true when something changed. */
    fun add(typed: String, correction: String, time: Long): Boolean {
        if (typed.isBlank() || correction.isBlank()) return false
        if (typed.equals(correction, ignoreCase = true)) return false
        if (typed.contains('\t') || correction.contains('\t') || typed.contains('\n') || correction.contains('\n')) return false
        val k = key(typed, correction)
        entries.remove(k)
        entries[k] = time
        while (entries.size > MAX) entries.remove(entries.keys.first())
        return true
    }

    fun remove(typed: String, correction: String) = entries.remove(key(typed, correction)) != null

    fun clear() = entries.clear()

    fun size() = entries.size

    fun serialize(): String = entries.entries.joinToString("") { "${it.key}\t${it.value}\n" }

    fun load(lines: List<String>) {
        entries.clear()
        for (line in lines) {
            val p = line.split('\t')
            if (p.size < 3 || p[0].isEmpty() || p[1].isEmpty()) continue
            entries[p[0] + "\t" + p[1]] = p[2].toLongOrNull() ?: 0L
        }
        while (entries.size > MAX) entries.remove(entries.keys.first())
    }

    companion object {
        const val MAX = 500
    }
}
