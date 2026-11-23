// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.lm

import kotlin.math.exp

/**
 * Turns the model's scores for the next token into scores for whole next words.
 *
 * A token that is a space followed by letters (" the", " Monday", " don't") is a word on its own.
 * Most common words are single tokens, so one pass of the model over the text before the cursor
 * gives a probability for every one of them at once. Longer words split into several tokens are
 * simply not offered by the model; the dictionary engine still covers them.
 *
 * No Android imports, so it can be compiled and checked on its own.
 */
class LmWords(tokenizer: LmTokenizer) {
    private val ids: IntArray
    private val words: Array<String>

    init {
        val idList = ArrayList<Int>()
        val wordList = ArrayList<String>()
        for (id in 0 until tokenizer.vocabSize) {
            val text = tokenizer.decodeToken(id) ?: continue
            if (text.length < 2 || text[0] != ' ') continue
            val word = text.substring(1)
            if (!word.all { it.isLetter() || it == '\'' } || !word[0].isLetter()) continue
            idList.add(id)
            wordList.add(word)
        }
        ids = idList.toIntArray()
        words = wordList.toTypedArray()
    }

    val size get() = ids.size

    /** A next word with its probability, in the casing the model liked best. */
    data class Scored(val word: String, val probability: Float)

    /**
     * Probability of each whole next word, best first, after a softmax over the full vocabulary.
     * Forms that differ only in case are added together and shown in the likelier casing.
     */
    fun rank(logits: FloatArray, limit: Int = 200): List<Scored> {
        var max = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > max) max = v
        var sum = 0.0
        for (v in logits) sum += exp((v - max).toDouble())
        val byLower = HashMap<String, FloatArray>() // lower -> [total, best single]
        val display = HashMap<String, String>()
        for (k in ids.indices) {
            val id = ids[k]
            if (id >= logits.size) continue
            val p = (exp((logits[id] - max).toDouble()) / sum).toFloat()
            if (p < MIN_PROBABILITY) continue
            val lower = words[k].lowercase()
            val acc = byLower.getOrPut(lower) { floatArrayOf(0f, 0f) }
            acc[0] += p
            if (p > acc[1]) { acc[1] = p; display[lower] = words[k] }
        }
        return byLower.entries
            .map { Scored(display[it.key] ?: it.key, it.value[0]) }
            .sortedWith(compareByDescending<Scored> { it.probability }.thenBy { it.word })
            .take(limit)
    }

    companion object {
        /** Anything less likely than this is noise and not worth carrying around. */
        const val MIN_PROBABILITY = 1e-5f
    }
}
