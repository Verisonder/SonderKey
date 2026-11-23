// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.lm

/**
 * Byte-level BPE, as used by the language model's tokenizer.json (GPT-2 family).
 *
 * Text is first split into pieces - every digit on its own, then the GPT-2 pattern (contractions,
 * runs of letters, of digits, of other symbols, with one leading space attached) - each piece is
 * mapped byte by byte onto printable characters, and then merged pair by pair in the order the
 * merges list gives, exactly as the reference implementation does.
 *
 * Only what the language model needs: encoding the text before the cursor, and turning single
 * tokens back into text to see which of them are whole words.
 *
 * No Android imports, so it can be compiled and checked against the reference tokenizer in CI.
 */
class LmTokenizer(
    private val vocab: Map<String, Int>,
    merges: List<Pair<String, String>>,
    private val ignoreMerges: Boolean,
    private val splitDigits: Boolean,
) {
    private val ranks = HashMap<Pair<String, String>, Int>(merges.size * 2).apply {
        merges.forEachIndexed { i, m -> put(m, i) }
    }
    private val idToToken: Array<String?> = arrayOfNulls<String>((vocab.values.maxOrNull() ?: -1) + 1).also { a ->
        for ((t, i) in vocab) if (i >= 0) a[i] = t
    }
    private val cache = object : LinkedHashMap<String, IntArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > 4096
    }

    val vocabSize get() = idToToken.size

    fun idOf(token: String): Int? = vocab[token]

    fun encode(text: String): IntArray {
        val out = ArrayList<Int>(text.length / 3 + 4)
        for (piece in pretokenize(text)) {
            val mapped = buildString { for (b in piece.toByteArray(Charsets.UTF_8)) append(BYTE_TO_CHAR[b.toInt() and 0xFF]) }
            val ids = synchronized(cache) { cache[mapped] } ?: bpe(mapped).also { synchronized(cache) { cache[mapped] = it } }
            for (id in ids) out.add(id)
        }
        return out.toIntArray()
    }

    /** The text a single token stands for, or null if it is not valid UTF-8 on its own. */
    fun decodeToken(id: Int): String? {
        val t = idToToken.getOrNull(id) ?: return null
        val bytes = ByteArray(t.length)
        for (i in t.indices) {
            val b = CHAR_TO_BYTE[t[i]] ?: return null
            bytes[i] = b.toByte()
        }
        val s = String(bytes, Charsets.UTF_8)
        return if (s.contains('�')) null else s
    }

    private fun bpe(word: String): IntArray {
        if (ignoreMerges) vocab[word]?.let { return intArrayOf(it) }
        var parts = ArrayList<String>(word.length)
        var i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            val n = Character.charCount(cp)
            parts.add(word.substring(i, i + n))
            i += n
        }
        while (parts.size > 1) {
            var best = Int.MAX_VALUE
            var bestIndex = -1
            for (k in 0 until parts.size - 1) {
                val r = ranks[parts[k] to parts[k + 1]] ?: continue
                if (r < best) { best = r; bestIndex = k }
            }
            if (bestIndex < 0) break
            val a = parts[bestIndex]
            val b = parts[bestIndex + 1]
            // Merge every occurrence of this pair in one pass, left to right, like the reference.
            val merged = ArrayList<String>(parts.size)
            var k = 0
            while (k < parts.size) {
                if (k < parts.size - 1 && parts[k] == a && parts[k + 1] == b) {
                    merged.add(a + b); k += 2
                } else {
                    merged.add(parts[k]); k++
                }
            }
            parts = merged
        }
        val ids = IntArray(parts.size)
        for ((k, p) in parts.withIndex()) {
            ids[k] = vocab[p] ?: vocab[UNKNOWN] ?: 0
        }
        return ids
    }

    private fun pretokenize(text: String): List<String> {
        val segments = ArrayList<String>()
        if (splitDigits) {
            val sb = StringBuilder()
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val n = Character.charCount(cp)
                if (Character.isDigit(cp)) {
                    if (sb.isNotEmpty()) { segments.add(sb.toString()); sb.setLength(0) }
                    segments.add(text.substring(i, i + n))
                } else sb.append(text, i, i + n)
                i += n
            }
            if (sb.isNotEmpty()) segments.add(sb.toString())
        } else segments.add(text)
        val out = ArrayList<String>()
        for (s in segments) {
            val m = PATTERN.matcher(s)
            while (m.find()) out.add(m.group())
        }
        return out
    }

    companion object {
        private const val UNKNOWN = "<|endoftext|>"
        const val END_OF_TEXT = UNKNOWN

        /**
         * Reads the plain-text form the model release carries instead of tokenizer.json, so no
         * JSON parser is needed on the phone or in the check:
         * a line `ignore_merges=0|1 split_digits=0|1`, then `V<TAB>count` and that many
         * `token<TAB>id` lines, then `M<TAB>count` and that many `left right` lines. Byte-level
         * tokens never contain whitespace, so neither separator can be ambiguous.
         */
        fun parse(lines: Iterator<String>): LmTokenizer {
            val header = lines.next()
            val flags = header.split(' ').associate { it.substringBefore('=') to it.substringAfter('=') }
            val vocabHeader = lines.next().split('\t')
            require(vocabHeader[0] == "V") { "bad tokenizer file" }
            val vocabCount = vocabHeader[1].toInt()
            val vocab = HashMap<String, Int>(vocabCount * 2)
            repeat(vocabCount) {
                val line = lines.next()
                val tab = line.lastIndexOf('\t')
                vocab[line.substring(0, tab)] = line.substring(tab + 1).toInt()
            }
            val mergeHeader = lines.next().split('\t')
            require(mergeHeader[0] == "M") { "bad tokenizer file" }
            val mergeCount = mergeHeader[1].toInt()
            val merges = ArrayList<Pair<String, String>>(mergeCount)
            repeat(mergeCount) {
                val line = lines.next()
                val space = line.indexOf(' ')
                merges.add(line.substring(0, space) to line.substring(space + 1))
            }
            return LmTokenizer(vocab, merges, flags["ignore_merges"] == "1", flags["split_digits"] == "1")
        }

        private val PATTERN = java.util.regex.Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+"
        )

        /** GPT-2's mapping of every byte onto a printable character. */
        val BYTE_TO_CHAR: CharArray = CharArray(256).also { table ->
            val direct = ArrayList<Int>()
            for (b in '!'.code..'~'.code) direct.add(b)
            for (b in '¡'.code..'¬'.code) direct.add(b)
            for (b in '®'.code..'ÿ'.code) direct.add(b)
            val set = direct.toHashSet()
            var extra = 0
            for (b in 0 until 256) {
                table[b] = if (b in set) b.toChar() else (256 + extra++).toChar()
            }
        }

        val CHAR_TO_BYTE: Map<Char, Int> = HashMap<Char, Int>(512).also { m ->
            for (b in 0 until 256) m[BYTE_TO_CHAR[b]] = b
        }
    }
}
