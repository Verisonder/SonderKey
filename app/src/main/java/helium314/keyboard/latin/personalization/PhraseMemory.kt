// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import android.content.Context
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SuggestionResults
import java.io.File
import java.util.concurrent.Executors

/**
 * Stores a [PhraseModel] for this device and applies it to suggestions.
 *
 * Learning follows the keyboard's own: nothing is learned in private mode, in fields that ask not
 * to learn, or with "use personalized dictionaries" off. What is stored is pairs and triples of
 * lowercased words with a count and a date, on this device only.
 */
object PhraseMemory {
    private const val TAG = "PhraseMemory"
    private const val FILE_NAME = "phrase_memory.txt"
    private const val SAVE_EVERY = 20

    /** A next-word guess needs this much faded use behind it, and this probability. */
    private const val MIN_EVIDENCE_PREDICTION = 1.5f
    private const val MIN_PROBABILITY_PREDICTION = 0.15f
    /** Mid-word, where reordering also changes what auto-correction picks, the bar is higher. */
    private const val MIN_EVIDENCE_COMPLETION = 2.5f
    private const val MIN_PROBABILITY_COMPLETION = 0.3f
    /** At most this many learned words are put in front of the engine's own. */
    private const val MAX_PROMOTED = 3

    private val lock = Any()
    private var model: PhraseModel? = null
    private var dir: File? = null
    private var unsaved = 0
    private val writer = Executors.newSingleThreadExecutor()

    /** Changes whenever something is learned, so cached predictions know to go stale. */
    @Volatile
    var version = 0
        private set

    fun init(context: Context) {
        synchronized(lock) {
            if (dir == null) dir = DeviceProtectedUtils.getFilesDir(context)
        }
    }

    private fun loaded(): PhraseModel? {
        model?.let { return it }
        val d = dir ?: return null
        val m = PhraseModel()
        try {
            val f = File(d, FILE_NAME)
            if (f.exists()) m.load(f.readLines())
        } catch (e: Exception) {
            Log.e(TAG, "could not read phrases", e)
        }
        model = m
        return m
    }

    /** (word two back, word right before), with sentence starts marked; null if there is none. */
    private fun contextOf(ngramContext: NgramContext?): Pair<String?, String>? {
        if (ngramContext == null || ngramContext.prevWordCount == 0) return null
        if (ngramContext.isNthPrevWordBeginningOfSentence(1)) return null to PhraseModel.SENTENCE_START
        val p1 = ngramContext.getNthPrevWord(1)?.toString() ?: return null
        val p2 = when {
            ngramContext.isNthPrevWordBeginningOfSentence(2) -> PhraseModel.SENTENCE_START
            else -> ngramContext.getNthPrevWord(2)?.toString()
        }
        return p2 to p1
    }

    @JvmStatic
    fun learn(ngramContext: NgramContext?, word: String) {
        val (p2, p1) = contextOf(ngramContext) ?: return
        synchronized(lock) {
            val m = loaded() ?: return
            m.learn(p2, p1, word, System.currentTimeMillis())
            version++
            if (++unsaved >= SAVE_EVERY) saveLocked()
        }
    }

    @JvmStatic
    fun unlearn(ngramContext: NgramContext?, word: String) {
        val (p2, p1) = contextOf(ngramContext) ?: return
        synchronized(lock) {
            val m = loaded() ?: return
            m.unlearn(p2, p1, word, System.currentTimeMillis())
            version++
            unsaved++
        }
    }

    @JvmStatic
    fun flush() {
        synchronized(lock) { if (unsaved > 0) saveLocked() }
    }

    fun count(): Int = synchronized(lock) { loaded()?.size() ?: 0 }

    fun clear() {
        synchronized(lock) {
            val m = loaded() ?: return
            m.clear()
            version++
            saveLocked()
        }
    }

    /**
     * Moves the words this person usually types next to the front of [results].
     *
     * With nothing typed yet, up to [MAX_PROMOTED] learned next words go ahead of the engine's own
     * predictions, and are added if the engine did not think of them. Mid-word, only words that
     * continue what has been typed are moved, and only on stronger evidence, since the first
     * suggestion there is also what auto-correction would pick.
     *
     * @param fallbackDict the dictionary a word the engine did not offer is credited to.
     */
    @JvmStatic
    fun promote(results: SuggestionResults, ngramContext: NgramContext?, typedWord: String,
                fallbackDict: Dictionary?) {
        val (p2, p1) = contextOf(ngramContext) ?: return
        val predictions = synchronized(lock) {
            loaded()?.predict(p2, p1, System.currentTimeMillis()) ?: return
        }
        val typed = typedWord.lowercase()
        val prediction = typed.isEmpty()
        val chosen = predictions.filter {
            if (prediction) it.evidence >= MIN_EVIDENCE_PREDICTION && it.probability >= MIN_PROBABILITY_PREDICTION
            else it.word.startsWith(typed) && it.evidence >= MIN_EVIDENCE_COMPLETION
                    && it.probability >= MIN_PROBABILITY_COMPLETION
        }.take(MAX_PROMOTED)
        if (chosen.isEmpty()) return

        val top = results.maxOfOrNull { it.mScore } ?: 0
        // Highest first: each promoted word sits just above the engine's best, in learned order.
        for ((i, p) in chosen.withIndex()) {
            val existing = results.firstOrNull { it.mWord.equals(p.word, ignoreCase = true) }
            val score = top + 1 + (chosen.size - i) * 10
            if (existing != null) {
                results.remove(existing)
                results.add(SuggestedWordInfo(existing.mWord, existing.mPrevWordsContext, score,
                    existing.mKindAndFlags, existing.mSourceDict, existing.mIndexOfTouchPointOfSecondWord,
                    existing.mAutoCommitFirstWordConfidence))
            } else if (fallbackDict != null) {
                // Words the engine did not offer are only added for a clean prefix match mid-word;
                // anything else would be guessing past a possible typo.
                results.add(SuggestedWordInfo(p.word, "", score,
                    if (prediction) SuggestedWordInfo.KIND_PREDICTION else SuggestedWordInfo.KIND_COMPLETION,
                    fallbackDict, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
            }
        }
    }

    private fun saveLocked() {
        val d = dir ?: return
        val m = model ?: return
        unsaved = 0
        val snapshot = m.serialize()
        writer.execute {
            try {
                val tmp = File(d, "$FILE_NAME.tmp")
                tmp.writeText(snapshot)
                val f = File(d, FILE_NAME)
                if (!tmp.renameTo(f)) { f.writeText(snapshot); tmp.delete() }
            } catch (e: Exception) {
                Log.e(TAG, "could not write phrases", e)
            }
        }
    }
}
