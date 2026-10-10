// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dyslexia

/**
 * Finds the word someone meant when they spelled it the way it sounds: "fone" for "phone",
 * "becos" for "because", "nite" for "night".
 *
 * Ordinary correction compares key presses, so it only catches a letter or two of slip. A word
 * spelled by sound can share almost no letters with the real one, so instead every word is reduced
 * to a rough code of how it sounds, and words with the same code are candidates. Vowels are kept
 * only as "a vowel goes here", because vowel spelling is the least reliable part of English.
 *
 * English only: the sound rules are English rules.
 */
object SoundSpelling {
    private const val MAX_KEYS = 4

    private fun isVowelChar(c: Char) = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'

    /** Sound codes for [word]. Usually one; a few where English spelling is ambiguous (gh, ch, soft g). */
    fun keys(word: String): Set<String> {
        var w = buildString { for (c in word.lowercase()) if (c in 'a'..'z') append(c) }
        if (w.isEmpty()) return emptySet()
        if (w.length < 2) return setOf(w.uppercase())
        for ((p, r) in PREFIXES) if (w.startsWith(p)) { w = r + w.substring(p.length); break }
        if (w.startsWith("x")) w = "s" + w.substring(1)
        if (w.endsWith("mb")) w = w.dropLast(1)
        if (w.length > 2 && w.last() == 'e' && !isVowelChar(w[w.length - 2])) w = w.dropLast(1)
        // doubled letters sound like one
        w = buildString { for (c in w) if (isEmpty() || last() != c) append(c) }

        val s = w
        fun isVowel(i: Int): Boolean {
            if (i < 0 || i >= s.length) return false
            val c = s[i]
            if (isVowelChar(c)) return true
            // y is a vowel unless it starts the word or starts a syllable
            if (c == 'y') return i > 0 && !(i + 1 < s.length && isVowelChar(s[i + 1]))
            return false
        }

        var outs = listOf("")
        fun add(vararg options: String) {
            val next = LinkedHashSet<String>()
            for (o in outs) for (x in options) next.add(o + x)
            outs = next.take(MAX_KEYS)
        }

        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            val nx = if (i + 1 < n) s[i + 1] else ' '
            val nx2 = if (i + 2 < n) s[i + 2] else ' '
            when {
                isVowel(i) -> { add("A"); i++ }
                c == 'w' -> {
                    // after a vowel and not before one, w is part of the vowel: "now", "saw"
                    if (!(i > 0 && isVowel(i - 1) && !isVowel(i + 1))) add("W")
                    i++
                }
                c == 'y' -> { add("Y"); i++ }
                (c == 't' || c == 's' || c == 'c') && nx == 'i' && (nx2 == 'a' || nx2 == 'o' || nx2 == 'u') && i > 0 -> {
                    add("X"); i += 2 // nation, mission, special
                }
                c == 's' && nx == 'h' -> { add("X"); i += 2 }
                c == 't' && nx == 'h' -> { add("0"); i += 2 }
                c == 'p' && nx == 'h' -> { add("F"); i += 2 }
                c == 'c' && nx == 'h' -> { add("C", "K"); i += 2 }
                c == 'c' && nx == 'k' -> { add("K"); i += 2 }
                c == 'g' && nx == 'h' -> {
                    if (i == 0) add("G") else add("", "F") // night / enough
                    i += 2
                }
                c == 'd' && nx == 'g' -> { add("J"); i += 2 }
                c == 'c' -> { add(if (nx == 'e' || nx == 'i' || nx == 'y') "S" else "K"); i++ }
                c == 'g' -> { if (nx == 'e' || nx == 'i' || nx == 'y') add("J", "G") else add("G"); i++ }
                c == 'q' -> {
                    add("K"); i++
                    if (i < n && s[i] == 'u') { add("W"); i++ }
                }
                c == 'x' -> { add("KS"); i++ }
                c == 'z' -> { add("S"); i++ }
                c == 'h' -> {
                    // silent after a consonant, and after a vowel unless another vowel follows
                    val silent = (i > 0 && !isVowel(i - 1)) || (i > 0 && isVowel(i - 1) && !isVowel(i + 1))
                    if (!silent) add("H")
                    i++
                }
                c == 'l' && i > 0 && isVowel(i - 1) && (nx == 'd' || nx == 'k' || nx == 'm' || nx == 'f') -> {
                    add("L", ""); i++ // could, walk, calm, half
                }
                else -> { add(c.uppercaseChar().toString()); i++ }
            }
        }
        return outs.mapTo(LinkedHashSet()) { o -> buildString { for (ch in o) if (isEmpty() || last() != ch) append(ch) } }
    }

    private val PREFIXES = listOf("kn" to "n", "wr" to "r", "gn" to "n", "ps" to "s", "pn" to "n", "wh" to "w")

    private val NORMALIZE = listOf(
        "ph" to "f", "ck" to "k", "wr" to "r", "kn" to "n", "gh" to "", "wh" to "w", "qu" to "kw",
        "x" to "ks", "z" to "s", "ce" to "se", "ci" to "si", "cy" to "sy", "c" to "k", "y" to "i",
    )

    /** A light spelling-level normalisation, for judging which same-sounding word looks closest. */
    fun normalize(word: String): String {
        var w = word.lowercase()
        for ((a, b) in NORMALIZE) w = w.replace(a, b)
        w = buildString { for (c in w) if (isEmpty() || last() != c) append(c) }
        if (w.length > 2 && w.endsWith("e")) w = w.dropLast(1)
        return w
    }

    fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}

/**
 * Words of one dictionary, findable by sound. Held as one sorted array of (code hash, word) pairs
 * rather than a map of lists, because a main dictionary has well over a hundred thousand words and
 * a map of that size costs tens of megabytes in a keyboard that should stay small.
 */
class SoundIndex private constructor(
    private val words: Array<String>,
    private val frequencies: IntArray,
    private val entries: LongArray,
) {
    /** A same-sounding word and how good a match it is; higher is better. */
    class Candidate(val word: String, val score: Float, val exact: Boolean)

    private fun idsFor(key: String, into: MutableMap<Int, Boolean>, exact: Boolean) {
        val h = key.hashCode().toLong() shl 32
        var lo = 0
        var hi = entries.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid] < h) lo = mid + 1 else hi = mid
        }
        var i = lo
        while (i < entries.size && (entries[i] and HIGH) == h) {
            val id = (entries[i] and LOW).toInt()
            // an exact match is never downgraded by also turning up as a near one
            if (exact) into[id] = true else into.putIfAbsent(id, false)
            i++
        }
    }

    /**
     * Same-sounding words for [typed], best first. Exact sound matches, then words one sound away
     * (a dropped or extra sound, as in "diffrent" or "langwij"). Ranked by how common each word is,
     * held back by how different it looks from what was typed.
     */
    fun lookup(typed: String, limit: Int = 5): List<Candidate> {
        val lower = typed.lowercase()
        val found = HashMap<Int, Boolean>()
        for (key in SoundSpelling.keys(lower)) {
            idsFor(key, found, true)
            idsFor(VOWEL_DROP_PREFIX + key, found, false)
            if (key.length >= 4) for (i in key.indices) idsFor(key.removeRange(i, i + 1), found, false)
        }
        if (found.isEmpty()) return emptyList()
        val normTyped = SoundSpelling.normalize(lower)
        return found.mapNotNull { (id, exact) ->
            val w = words[id]
            if (w == lower) return@mapNotNull null
            // dictionary frequencies run 0..255; /36 puts them on roughly the same 0..7 scale
            // the weights below were tuned against
            val score = frequencies[id] / 36f -
                1.3f * SoundSpelling.editDistance(normTyped, SoundSpelling.normalize(w)) -
                (if (exact) 0f else 1f)
            Candidate(w, score, exact)
        }.sortedByDescending { it.score }.take(limit)
    }

    companion object {
        private const val HIGH = -0x100000000L
        private const val LOW = 0xFFFFFFFFL
        // near-match codes live in the same array, marked so they never collide with exact ones
        private const val VOWEL_DROP_PREFIX = "~"

        /** Builds from (word, frequency) pairs. Skips anything that is not a plain lowercase word. */
        fun build(source: Sequence<Pair<String, Int>>): SoundIndex {
            val words = ArrayList<String>()
            val freqs = ArrayList<Int>()
            var entries = LongArray(1 shl 16)
            var count = 0
            fun put(key: String, id: Int) {
                if (count == entries.size) entries = entries.copyOf(entries.size * 2)
                entries[count++] = (key.hashCode().toLong() shl 32) or id.toLong()
            }
            for ((word, freq) in source) {
                if (word.length < 2 || word.length > 24 || word.any { it !in 'a'..'z' }) continue
                val id = words.size
                words.add(word)
                freqs.add(freq)
                for (key in SoundSpelling.keys(word)) {
                    put(key, id)
                    // the same word with one vowel sound dropped, so "diffrent" finds "different"
                    if (key.length >= 4) for (i in key.indices) if (key[i] == 'A')
                        put(VOWEL_DROP_PREFIX + key.removeRange(i, i + 1), id)
                }
            }
            val sorted = entries.copyOf(count).also { it.sort() }
            return SoundIndex(words.toTypedArray(), freqs.toIntArray(), sorted)
        }
    }
}
