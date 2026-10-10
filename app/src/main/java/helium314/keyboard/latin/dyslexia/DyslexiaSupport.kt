// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dyslexia

import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.utils.Log
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds the sound index for the main dictionary, built once in the background the first time
 * dyslexia mode asks for it. Until it is ready, lookups return nothing and typing carries on as
 * normal; nothing waits on it.
 */
object DyslexiaSupport {
    const val MODE_OFF = "off"
    const val MODE_SUGGEST = "suggest"
    const val MODE_AUTOCORRECT = "autocorrect"

    private const val TAG = "DyslexiaSupport"

    @Volatile private var index: SoundIndex? = null
    @Volatile private var indexLocale: Locale? = null
    private val building = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()

    /** The sound rules are English; other languages get nothing rather than wrong guesses. */
    fun supports(locale: Locale) = locale.language == "en"

    /** The index for [facilitator]'s main dictionary, or null while it is being built. */
    fun indexFor(facilitator: DictionaryFacilitator): SoundIndex? {
        val locale = facilitator.mainLocale
        if (!supports(locale)) return null
        val current = index
        if (current != null && indexLocale == locale) return current
        if (!facilitator.hasAtLeastOneInitializedMainDictionary()) return null
        if (building.compareAndSet(false, true)) {
            executor.execute {
                try {
                    val pairs = ArrayList<Pair<String, Int>>()
                    facilitator.forEachWordInMainDictionaryOnly { word: String, freq: Int -> pairs.add(word to freq) }
                    if (pairs.isNotEmpty()) {
                        index = SoundIndex.build(pairs.asSequence())
                        indexLocale = locale
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "could not build sound index", e)
                } finally {
                    building.set(false)
                }
            }
        }
        return null
    }

    /** Drops the index, for when the main dictionary changes. Rebuilt on next use. */
    fun invalidate() {
        index = null
        indexLocale = null
    }
}
