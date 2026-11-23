// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.touch

import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.TouchModel
import helium314.keyboard.latin.LastComposedWord
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.StringUtils

/**
 * Turns committed words into touch samples for [TouchModel].
 *
 * A touch only teaches something once it is known which key it was meant for. That is known when
 * a word is committed: either the word was typed as meant (a real word, committed as typed) or it
 * was corrected to a word of the same length, in which case each touch belongs to the corrected
 * letter - including the ones that missed, which are the most useful samples there are.
 *
 * Samples are held back for one word. If the user deletes back into what was just committed, or
 * undoes the correction, they are thrown away; they reach the model only once the next word is
 * committed and the previous one has evidently stood.
 *
 * Never used when learning is off, in private mode, in fields that ask not to learn, or for
 * gesture typing, whose points are a path rather than taps.
 */
object TypingLearner {
    /** A word corrected in more places than this was not a slip of the finger. */
    private const val MAX_CHANGED_LETTERS = 2

    private var pending: List<TouchModel.Sample>? = null
    private var pendingProfile: String? = null

    /**
     * Called just before [WordComposer.commitWord], while the composer still holds the touches.
     *
     * @param isKnownWord whether the committed word is in a dictionary; a typed word that is not
     *        may well be a typo, and its touches would teach the wrong keys.
     */
    @JvmStatic
    fun onWordCommitted(
        composer: WordComposer, keyboard: Keyboard?, chosenWord: String, commitType: Int,
        learningAllowed: Boolean, isKnownWord: Boolean
    ) {
        // Whatever was pending has stood: a further word is being committed after it.
        flushPending()
        if (!learningAllowed || keyboard == null || !keyboard.mId.isAlphabetKeyboard) return
        if (composer.isBatchMode || composer.isResumed) return
        if (commitType == LastComposedWord.COMMIT_TYPE_CANCEL_AUTO_CORRECT) return

        val typed = composer.typedWord
        val typedCodes = StringUtils.toCodePointArray(typed)
        val chosenCodes = StringUtils.toCodePointArray(chosenWord)
        if (typedCodes.isEmpty() || typedCodes.size != chosenCodes.size) return
        val pointers = composer.inputPointers
        if (pointers.pointerSize < typedCodes.size) return

        var changed = 0
        for (i in typedCodes.indices) {
            if (Character.toLowerCase(typedCodes[i]) != Character.toLowerCase(chosenCodes[i])) changed++
        }
        if (changed == 0 && !isKnownWord) return
        if (changed > MAX_CHANGED_LETTERS) return
        if (changed > 0 && commitType != LastComposedWord.COMMIT_TYPE_DECIDED_WORD
            && commitType != LastComposedWord.COMMIT_TYPE_MANUAL_PICK) return

        val xs = pointers.xCoordinates
        val ys = pointers.yCoordinates
        val samples = ArrayList<TouchModel.Sample>(typedCodes.size)
        for (i in chosenCodes.indices) {
            val sample = TouchModel.measure(keyboard, chosenCodes[i], xs[i], ys[i]) ?: continue
            samples.add(sample)
        }
        if (samples.isEmpty()) return
        pending = samples
        pendingProfile = TouchModel.profileOf(keyboard.mId)
    }

    /** The word just committed is being deleted into or taken back: its touches prove nothing. */
    @JvmStatic
    fun discardPending() {
        pending = null
        pendingProfile = null
    }

    private fun flushPending() {
        val samples = pending
        val profile = pendingProfile
        pending = null
        pendingProfile = null
        if (samples != null && profile != null) TouchModel.record(profile, samples)
    }
}
