// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

/**
 * Turns spoken numbers into digits: "twenty five" becomes "25".
 *
 * Parakeet is trained to write numbers out as words, so dictating a phone number or a price gives
 * a paragraph of English rather than something you can use. Nothing downstream converts them, so
 * it has to happen here.
 *
 * The work is entirely in knowing when *not* to convert. Number words are ordinary English words
 * as well, and a converter that cannot tell the two apart does more damage than it repairs -
 * "one of them" turning into "1 of them" is worse than "twenty five" staying as words, because it
 * corrupts sentences the user never thought of as being about numbers.
 */
object SpokenNumbers {
    private val UNITS = mapOf(
        "zero" to 0L, "oh" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L,
        "five" to 5L, "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L,
        "eleven" to 11L, "twelve" to 12L, "thirteen" to 13L, "fourteen" to 14L,
        "fifteen" to 15L, "sixteen" to 16L, "seventeen" to 17L, "eighteen" to 18L,
        "nineteen" to 19L
    )
    private val TENS = mapOf(
        "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L,
        "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L
    )
    private val SCALES = mapOf("hundred" to 100L, "thousand" to 1_000L,
        "million" to 1_000_000L, "billion" to 1_000_000_000L)

    /**
     * Words that are left alone when they stand by themselves.
     *
     * Each is far more often a determiner or a pronoun than a quantity. "one" is the worst
     * offender by a distance - "one of them", "one more time", "the one I meant" - but "oh" is
     * only a digit inside a spoken sequence, never on its own.
     */
    private val AMBIGUOUS_ALONE = setOf("a", "oh")

    /**
     * "one" is a number far more often than not, so it converts like every other word - but it is
     * also the one number word that doubles as a pronoun, and "1 of them" is worse damage than
     * leaving a quantity spelled out. Rather than refusing it outright, which made it the only
     * number that never converted, it is judged by the company it keeps.
     */
    private val PRONOUN_BEFORE_ONE = setOf("the", "this", "that", "which", "any", "every", "each", "no", "only")
    private val PRONOUN_AFTER_ONE = setOf("of", "more", "another", "or")

    /** Joins parts of a single number without ending it: "one hundred and two". */
    private const val FILLER = "and"

    // Ordered by magnitude, so one word may only follow a strictly larger one.
    private const val KIND_NONE = 0
    private const val KIND_TENS = 1
    private const val KIND_TEEN = 2
    private const val KIND_UNIT = 3
    private const val KIND_SCALE = 4

    /** True when a lone "one" is being used as a pronoun rather than as a quantity. */
    private fun isPronounOne(pieces: List<String>, index: Int): Boolean {
        val word = pieces[index].trim(',', '.', '?', '!', ';', ':').lowercase()
        if (word != "one") return false
        val before = pieces.getOrNull(index - 1)?.trim(',', '.', '?', '!', ';', ':')?.lowercase()
        if (before in PRONOUN_BEFORE_ONE) return true
        val after = pieces.getOrNull(index + 1)?.trim(',', '.', '?', '!', ';', ':')?.lowercase()
        return after in PRONOUN_AFTER_ONE
    }

    fun toDigits(text: String): String {
        if (text.isEmpty()) return text
        // Split on whitespace and hyphens while keeping every separator, so the text can be
        // rebuilt exactly as it came in wherever no conversion applies.
        val pieces = Regex("([\\s-]+)").split(text)
        val separators = Regex("([\\s-]+)").findAll(text).map { it.value }.toList()
        if (pieces.size < 2) return text

        val out = StringBuilder()
        var i = 0
        while (i < pieces.size) {
            val runStart = i
            var value = 0L
            var current = 0L
            var sawNumber = false
            var lastWasFiller = false
            var lastKind = KIND_NONE
            var j = i
            while (j < pieces.size) {
                val word = pieces[j].trim(',', '.', '?', '!', ';', ':').lowercase()
                val units = UNITS[word]
                val tens = TENS[word]
                val scale = SCALES[word]
                if (word == FILLER) {
                    // Only meaningful between two parts of one number, never at either end.
                    if (!sawNumber || lastWasFiller) break
                    lastWasFiller = true
                    j++
                    continue
                }
                // Two words of the same magnitude are two numbers, not one. Without this,
                // "five five five" adds up to fifteen and a dictated phone number becomes a
                // single meaningless total. Only a descending sequence - tens then units, or
                // anything after a scale word - is one number.
                val kind = when {
                    units != null && units < 10L -> KIND_UNIT
                    units != null -> KIND_TEEN
                    tens != null -> KIND_TENS
                    scale != null -> KIND_SCALE
                    else -> KIND_NONE
                }
                if (kind == KIND_NONE) break
                // The constants below rank by descending magnitude, so a larger constant means a
                // smaller word: tens may be followed by a unit, but never by another tens.
                if (kind != KIND_SCALE && lastKind != KIND_NONE && lastKind != KIND_SCALE
                    && kind <= lastKind) {
                    break
                }
                when {
                    units != null -> current += units
                    tens != null -> current += tens
                    scale == 100L -> current = if (current == 0L) 100L else current * 100L
                    else -> { value += (if (current == 0L) 1L else current) * scale!!; current = 0L }
                }
                lastKind = kind
                sawNumber = true
                lastWasFiller = false
                // A word carrying trailing punctuation ends the number as well as the phrase.
                if (pieces[j] != pieces[j].trimEnd(',', '.', '?', '!', ';', ':')) { j++; break }
                j++
            }
            // Trailing "and" belongs to the sentence, not to the number.
            var runEnd = j
            if (lastWasFiller) runEnd--

            val wordCount = runEnd - runStart
            val loneWord = pieces[runStart].trim(',', '.', '?', '!', ';', ':').lowercase()
            val lone = wordCount == 1 && (loneWord in AMBIGUOUS_ALONE || isPronounOne(pieces, runStart))
            if (sawNumber && wordCount > 0 && !lone) {
                // The whole run collapses to one token, so only the separator that followed the
                // last word of it survives - the spaces inside the run are gone with the words.
                val total = value + current
                val tail = pieces[runEnd - 1].takeLastWhile { it in ",.?!;:" }
                out.append(total).append(tail)
                out.append(separators.getOrElse(runEnd - 1) { "" })
                i = runEnd
            } else {
                out.append(pieces[i])
                out.append(separators.getOrElse(i) { "" })
                i++
            }
        }
        return out.toString()
    }
}
