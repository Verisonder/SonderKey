// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import kotlin.math.pow

/**
 * Which word this person tends to type after the words before it.
 *
 * Counts are kept for one word of context ("thank" -> "you") and for two ("see you" -> "soon"),
 * and every count fades with a half-life of [HALF_LIFE_DAYS], so a phrase used this week outranks
 * one used a lot last year. The two-word counts win when they have something to say, and fall
 * back on the one-word counts as they run thin.
 *
 * This sits beside the dictionary engine's own learning, which also remembers what was typed but
 * ranks it in ways that are hard to steer and does not favour recent use. Here both are explicit.
 *
 * No Android imports, so the arithmetic can be compiled and checked on its own.
 */
class PhraseModel {
    class Entry(var count: Float, var lastUsed: Long)

    /** context -> next word -> entry. A context is "w1" or "w2 w1", lowercase. */
    private val contexts = HashMap<String, HashMap<String, Entry>>()
    private var entryCount = 0

    fun size() = entryCount

    fun clear() {
        contexts.clear()
        entryCount = 0
    }

    /** Learns [word] after [prev1] (the word right before) and [prev2] (the one before that). */
    fun learn(prev2: String?, prev1: String?, word: String, now: Long) {
        val w = norm(word) ?: return
        val p1 = norm(prev1) ?: return
        bump(p1, w, now)
        val p2 = norm(prev2)
        if (p2 != null) bump("$p2 $p1", w, now)
        if (entryCount > MAX_ENTRIES) prune(now)
    }

    /** Takes back one use, for a word deleted straight after it was typed. */
    fun unlearn(prev2: String?, prev1: String?, word: String, now: Long) {
        val w = norm(word) ?: return
        val p1 = norm(prev1) ?: return
        drop(p1, w, now)
        val p2 = norm(prev2)
        if (p2 != null) drop("$p2 $p1", w, now)
    }

    private fun bump(ctx: String, w: String, now: Long) {
        val words = contexts.getOrPut(ctx) { HashMap() }
        val e = words[w]
        if (e == null) {
            words[w] = Entry(1f, now)
            entryCount++
        } else {
            e.count = e.count * fade(e.lastUsed, now) + 1f
            e.lastUsed = now
        }
    }

    private fun drop(ctx: String, w: String, now: Long) {
        val words = contexts[ctx] ?: return
        val e = words[w] ?: return
        e.count = e.count * fade(e.lastUsed, now) - 1f
        e.lastUsed = now
        if (e.count <= 0.05f) {
            words.remove(w)
            entryCount--
            if (words.isEmpty()) contexts.remove(ctx)
        }
    }

    /** Drops the faintest entries until well under the cap. */
    private fun prune(now: Long) {
        val all = ArrayList<Triple<String, String, Float>>(entryCount)
        for ((ctx, words) in contexts) for ((w, e) in words) all.add(Triple(ctx, w, e.count * fade(e.lastUsed, now)))
        all.sortBy { it.third }
        val target = (MAX_ENTRIES * 0.8f).toInt()
        var i = 0
        while (entryCount > target && i < all.size) {
            val (ctx, w, _) = all[i++]
            val words = contexts[ctx] ?: continue
            if (words.remove(w) != null) entryCount--
            if (words.isEmpty()) contexts.remove(ctx)
        }
    }

    /**
     * The likeliest next words after [prev2] [prev1], best first, as (word, probability, evidence).
     * Evidence is the faded count behind the word in the context that produced it, so callers can
     * ignore a guess made from a single sighting.
     */
    fun predict(prev2: String?, prev1: String?, now: Long, limit: Int = 8): List<Prediction> {
        val p1 = norm(prev1) ?: return emptyList()
        val p2 = norm(prev2)
        val one = contexts[p1]
        val two = if (p2 == null) null else contexts["$p2 $p1"]
        if (one == null && two == null) return emptyList()
        val oneTotal = one?.values?.sumOf { (it.count * fade(it.lastUsed, now)).toDouble() }?.toFloat() ?: 0f
        val twoTotal = two?.values?.sumOf { (it.count * fade(it.lastUsed, now)).toDouble() }?.toFloat() ?: 0f
        val candidates = HashSet<String>()
        one?.keys?.let { candidates.addAll(it) }
        two?.keys?.let { candidates.addAll(it) }
        val out = ArrayList<Prediction>(candidates.size)
        for (w in candidates) {
            val c1 = one?.get(w)?.let { it.count * fade(it.lastUsed, now) } ?: 0f
            val c2 = two?.get(w)?.let { it.count * fade(it.lastUsed, now) } ?: 0f
            // One-word estimate, shrunk while the context has been seen only a few times.
            val pOne = if (oneTotal > 0f) c1 / (oneTotal + SMOOTHING) else 0f
            // Two-word estimate, backing off to the one-word one in proportion to how little the
            // two-word context has been seen.
            val p = if (twoTotal > 0f) (c2 + SMOOTHING * pOne) / (twoTotal + SMOOTHING) else pOne
            if (p <= 0f) continue
            out.add(Prediction(w, p, if (c2 > 0f) c2 else c1))
        }
        out.sortWith(compareByDescending<Prediction> { it.probability }.thenBy { it.word })
        return if (out.size > limit) out.subList(0, limit) else out
    }

    data class Prediction(val word: String, val probability: Float, val evidence: Float)

    fun serialize(): String = buildString {
        for ((ctx, words) in contexts) for ((w, e) in words)
            append(ctx).append('\t').append(w).append('\t')
                .append(String.format(java.util.Locale.US, "%.3f", e.count)).append('\t').append(e.lastUsed).append('\n')
    }

    fun load(lines: List<String>) {
        clear()
        for (line in lines) {
            val p = line.split('\t')
            if (p.size < 4 || p[0].isEmpty() || p[1].isEmpty()) continue
            val count = p[2].toFloatOrNull() ?: continue
            if (count <= 0f || count.isNaN()) continue
            val words = contexts.getOrPut(p[0]) { HashMap() }
            if (words.put(p[1], Entry(count, p[3].toLongOrNull() ?: 0L)) == null) entryCount++
        }
    }

    companion object {
        const val HALF_LIFE_DAYS = 30.0
        const val MAX_ENTRIES = 30000
        /** How many sightings a context needs before its own counts outweigh the back-off. */
        const val SMOOTHING = 2f
        private const val MS_PER_DAY = 86_400_000.0
        const val SENTENCE_START = "<s>"

        fun fade(lastUsed: Long, now: Long): Float {
            val days = (now - lastUsed).coerceAtLeast(0L) / MS_PER_DAY
            return 0.5.pow(days / HALF_LIFE_DAYS).toFloat()
        }

        /** Lowercased and trimmed; null for anything that is not a plain word. */
        fun norm(word: String?): String? {
            if (word == null) return null
            if (word == SENTENCE_START) return word
            val w = word.trim().lowercase()
            if (w.isEmpty() || w.length > 40) return null
            if (w.any { it.isWhitespace() || it == '\t' }) return null
            if (!w.any { it.isLetter() }) return null
            return w
        }
    }
}
