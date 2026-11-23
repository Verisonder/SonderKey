// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.lm

import android.content.Context
import android.os.Handler
import android.os.Looper
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SuggestionResults
import java.util.concurrent.Executors

/**
 * Next-word predictions from the on-device language model, alongside the dictionary engine's.
 *
 * The model reads the text before the current word - well beyond the two or three words the
 * dictionary looks at - and scores every whole word that could come next. That takes a moment,
 * so it runs on its own thread: suggestions are shown straight away from the dictionaries as
 * before, and redrawn once the model's answer for the same text is in. One pass per word is
 * enough, since the same answer also ranks completions while the word is being typed.
 *
 * Nothing leaves the phone. The model is loaded on first use and released after the keyboard
 * has been closed for a while, since it takes a fair amount of memory.
 */
object LmPredictor {
    private const val TAG = "LmPredictor"
    /** Characters of text before the cursor handed to the model. */
    const val CONTEXT_CHARS = 400
    private const val MAX_TOKENS = 48
    private const val IDLE_RELEASE_MS = 90_000L

    /** With nothing typed, a model word needs this probability to be put first. */
    private const val MIN_PREDICTION = 0.03f
    private const val MAX_PREDICTIONS = 3
    /** Mid-word, a completion needs this share of everything the model expects with that start. */
    private const val MIN_COMPLETION_SHARE = 0.35f
    private const val MAX_COMPLETIONS = 2

    private var app: Context? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var backend: LmBackend? = null
    private var words: LmWords? = null
    private var tokenizer: LmTokenizer? = null
    private var endOfText = 0

    @Volatile private var requested: String? = null
    @Volatile private var resultKey: String? = null
    @Volatile private var result: List<LmWords.Scored>? = null

    /** Called on the main thread when an answer arrives for the text last asked about. */
    @JvmStatic
    var onReady: Runnable? = null

    private val releaseRunnable = Runnable { release() }

    @JvmStatic
    fun init(context: Context) {
        if (app == null) app = context.applicationContext
    }

    @JvmStatic
    fun isAvailable(context: Context): Boolean =
        LmBackendFactory.isAvailable && LmModel.isPublished && LmModel.isDownloaded(context)

    /** Asks for predictions after [textBeforeWord]; does nothing if that is what was last asked. */
    @JvmStatic
    fun request(textBeforeWord: CharSequence) {
        val key = contextKey(textBeforeWord)
        main.removeCallbacks(releaseRunnable)
        if (key == requested) return
        requested = key
        executor.execute {
            if (requested != key) return@execute
            val answer = try {
                compute(key)
            } catch (t: Throwable) {
                Log.e(TAG, "prediction failed", t)
                null
            }
            if (answer != null && requested == key) {
                result = answer
                resultKey = key
                main.post { if (requested == key) onReady?.run() }
            }
        }
    }

    /** Nothing applies until the next request: the text or language no longer fits the answer. */
    @JvmStatic
    fun forget() {
        requested = null
    }

    /** Lets the model go after the keyboard has been away for a while. */
    @JvmStatic
    fun scheduleRelease() {
        main.removeCallbacks(releaseRunnable)
        main.postDelayed(releaseRunnable, IDLE_RELEASE_MS)
    }

    @JvmStatic
    fun release() {
        requested = null
        executor.execute {
            backend?.close()
            backend = null
            words = null
            tokenizer = null
            result = null
            resultKey = null
        }
    }

    /** The answer for the text last asked about, or null while it is still being worked out. */
    private fun current(): List<LmWords.Scored>? {
        val key = requested ?: return null
        return if (resultKey == key) result else null
    }

    private fun contextKey(text: CharSequence): String {
        var s = text.toString()
        if (s.length > CONTEXT_CHARS) {
            s = s.substring(s.length - CONTEXT_CHARS)
            // Do not start in the middle of a word.
            val space = s.indexOfFirst { it.isWhitespace() }
            if (space >= 0) s = s.substring(space + 1)
        }
        // The next word's own leading space belongs to its token; leaving it on the context would
        // make the model predict what follows a space rather than a word.
        return s.trimEnd()
    }

    private fun compute(key: String): List<LmWords.Scored>? {
        if (backend == null) {
            val context = app ?: return null
            if (!isAvailable(context)) return null
            val t = LmModel.fileOf(context, LmModel.TOKENIZER).bufferedReader().useLines { LmTokenizer.parse(it.iterator()) }
            val b = LmBackendFactory.open(LmModel.fileOf(context, LmModel.MODEL)) ?: return null
            tokenizer = t
            words = LmWords(t)
            endOfText = t.idOf(LmTokenizer.END_OF_TEXT) ?: 0
            backend = b
        }
        val t = tokenizer ?: return null
        val encoded = if (key.isEmpty()) IntArray(0) else t.encode(key)
        val tail = if (encoded.size > MAX_TOKENS - 1) encoded.copyOfRange(encoded.size - (MAX_TOKENS - 1), encoded.size) else encoded
        val ids = IntArray(tail.size + 1)
        ids[0] = endOfText
        System.arraycopy(tail, 0, ids, 1, tail.size)
        val logits = backend?.nextTokenLogits(ids) ?: return null
        return words?.rank(logits)
    }

    /**
     * Folds the model's answer into [results], if it has one for the current text.
     *
     * With nothing typed, its likeliest words go first. Mid-word, likely completions of what has
     * been typed are moved up to just below the first suggestion and never above it, since the
     * first suggestion is also what auto-correction would commit.
     *
     * @param isWord whether a word is in the dictionaries; the model also scores word fragments
     *        and names, and only real words are offered.
     */
    @JvmStatic
    fun apply(results: SuggestionResults, typedWord: String, fallbackDict: Dictionary?, isWord: (String) -> Boolean) {
        val answer = current() ?: return
        val typed = typedWord.lowercase()
        if (typed.isEmpty()) {
            val chosen = answer.asSequence().filter { it.probability >= MIN_PREDICTION && isWord(it.word) }
                .take(MAX_PREDICTIONS).toList()
            if (chosen.isEmpty()) return
            val top = results.maxOfOrNull { it.mScore } ?: 0
            for ((i, p) in chosen.withIndex()) place(results, p.word, top + 1 + (chosen.size - i) * 10,
                SuggestedWordInfo.KIND_PREDICTION, fallbackDict, raiseOnly = false)
            return
        }
        val startingWith = answer.filter { it.word.lowercase().startsWith(typed) }
        val total = startingWith.sumOf { it.probability.toDouble() }.toFloat()
        if (total <= 0f) return
        val chosen = startingWith.asSequence()
            .filter { it.word.length > typed.length && it.probability / total >= MIN_COMPLETION_SHARE && isWord(it.word) }
            .take(MAX_COMPLETIONS).toList()
        if (chosen.isEmpty()) return
        val first = results.firstOrNull()
        for ((i, p) in chosen.withIndex()) {
            if (first != null && first.mWord.equals(p.word, ignoreCase = true)) continue
            val score = if (first == null) chosen.size - i else first.mScore - 1 - i
            place(results, p.word, score, SuggestedWordInfo.KIND_COMPLETION, fallbackDict, raiseOnly = true)
        }
    }

    /** Puts [word] at [score]; with [raiseOnly], an entry already ranked higher is left alone. */
    private fun place(results: SuggestionResults, word: String, score: Int, kind: Int, fallbackDict: Dictionary?, raiseOnly: Boolean) {
        val existing = results.firstOrNull { it.mWord.equals(word, ignoreCase = true) }
        if (existing != null) {
            if (raiseOnly && existing.mScore >= score) return
            results.remove(existing)
            results.add(SuggestedWordInfo(existing.mWord, existing.mPrevWordsContext, score,
                existing.mKindAndFlags, existing.mSourceDict, existing.mIndexOfTouchPointOfSecondWord,
                existing.mAutoCommitFirstWordConfidence))
        } else if (fallbackDict != null) {
            results.add(SuggestedWordInfo(word, "", score, kind, fallbackDict,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
    }
}
