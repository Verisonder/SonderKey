// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import android.content.Context
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** A word the keyboard learned from typing, as listed on the learned words screen. */
data class LearnedWord(val word: String, val frequency: Int, val locale: Locale, val inDictionary: Boolean)

/**
 * Words on their way into the personal dictionary.
 *
 * A word that is in none of the dictionaries is counted each time it is typed. Once it reaches the
 * user's threshold it is either added straight away or, with review on, held as pending until the
 * user approves or dismisses it. A dismissed word is remembered so it is not offered again.
 *
 * Kept in a small text file next to the blocked words, one entry per line:
 * `languageTag \t word \t uses \t state \t lastUsed`.
 */
object PersonalDictCandidates {
    enum class State { COUNTING, PENDING, REJECTED }

    data class Entry(val word: String, val locale: Locale, var uses: Int, var state: State, var lastUsed: Long)

    private const val TAG = "PersonalDictCandidates"
    private const val FILE_NAME = "personal_dict_candidates.txt"
    // Only words still being counted are evicted; pending and dismissed ones are the user's decisions.
    private const val MAX_COUNTING = 400

    private val lock = Any()
    private var entries: LinkedHashMap<String, Entry>? = null
    private val writer = Executors.newSingleThreadExecutor()

    private fun key(word: String, locale: Locale) = locale.toLanguageTag() + "\u0000" + word

    private fun file(context: Context) = File(DeviceProtectedUtils.getFilesDir(context), FILE_NAME)

    private fun loaded(context: Context): LinkedHashMap<String, Entry> {
        entries?.let { return it }
        val map = LinkedHashMap<String, Entry>()
        try {
            val f = file(context)
            if (f.exists()) f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size < 5) return@forEachLine
                val state = runCatching { State.valueOf(p[3]) }.getOrNull() ?: return@forEachLine
                val locale = Locale.forLanguageTag(p[0])
                map[key(p[1], locale)] = Entry(p[1], locale, p[2].toIntOrNull() ?: 0, state, p[4].toLongOrNull() ?: 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "could not read candidates", e)
        }
        entries = map
        return map
    }

    /** Writes a snapshot on a background thread; call with [lock] held. */
    private fun saveLocked(context: Context) {
        val snapshot = loaded(context).values.joinToString("") {
            "${it.locale.toLanguageTag()}\t${it.word}\t${it.uses}\t${it.state.name}\t${it.lastUsed}\n"
        }
        val f = file(context)
        writer.execute {
            try {
                val tmp = File(f.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(snapshot)
                if (!tmp.renameTo(f)) { f.writeText(snapshot); tmp.delete() }
            } catch (e: Exception) {
                Log.e(TAG, "could not write candidates", e)
            }
        }
    }

    /**
     * Counts one use of [word]. Returns true when the word should be added to the personal
     * dictionary now. With [review] on, a word reaching [threshold] becomes pending instead.
     */
    fun recordUse(context: Context, word: String, locale: Locale, threshold: Int, review: Boolean): Boolean {
        synchronized(lock) {
            val map = loaded(context)
            val k = key(word, locale)
            val now = System.currentTimeMillis()
            val entry = map.getOrPut(k) { Entry(word, locale, 0, State.COUNTING, now) }
            if (entry.state != State.COUNTING) return false
            entry.uses++
            entry.lastUsed = now
            var addNow = false
            if (entry.uses >= threshold) {
                if (review) entry.state = State.PENDING
                else { map.remove(k); addNow = true }
            }
            val counting = map.values.filter { it.state == State.COUNTING }
            if (counting.size > MAX_COUNTING)
                counting.sortedBy { it.lastUsed }.take(counting.size - MAX_COUNTING)
                    .forEach { map.remove(key(it.word, it.locale)) }
            saveLocked(context)
            return addNow
        }
    }

    fun pending(context: Context): List<Entry> = synchronized(lock) {
        loaded(context).values.filter { it.state == State.PENDING }.map { it.copy() }
    }

    /** Forgets the entry; the caller adds the word to the personal dictionary. */
    fun approve(context: Context, word: String, locale: Locale) {
        synchronized(lock) {
            if (loaded(context).remove(key(word, locale)) != null) saveLocked(context)
        }
    }

    /** Dismisses the word for good: it will not be counted or offered again. */
    fun reject(context: Context, word: String, locale: Locale) {
        synchronized(lock) {
            val entry = loaded(context).getOrPut(key(word, locale)) { Entry(word, locale, 0, State.REJECTED, 0L) }
            entry.state = State.REJECTED
            saveLocked(context)
        }
    }

    fun clearDismissed(context: Context) {
        synchronized(lock) {
            if (loaded(context).values.removeAll { it.state == State.REJECTED }) saveLocked(context)
        }
    }
}
