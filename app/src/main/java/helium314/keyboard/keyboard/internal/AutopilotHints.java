// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import helium314.keyboard.latin.SuggestedWords;

/**
 * Which letters the dictionary expects next, and how strongly.
 *
 * Autopilot widens the touch target of a likely letter at the expense of its neighbours. Nothing
 * moves on screen and nothing already typed is ever revised - the only thing that changes is where
 * the boundary between two keys sits at the moment a finger lands, which is the same latitude a
 * person's own aim already has.
 *
 * The likelihoods come free. Suggestions are computed after every keystroke anyway, so the letters
 * that could follow what has been typed are sitting in that list: after "th" the candidates are
 * "the", "this", "that", and the characters at position two are exactly the answer. That also
 * means the hints always describe the keystroke about to happen rather than the one just made.
 *
 * A singleton because {@link helium314.keyboard.keyboard.KeyDetector} is reached through static
 * plumbing in PointerTracker and has no route back to the service that owns the dictionaries.
 * Written from the main thread and read from touch dispatch on the same thread.
 */
public final class AutopilotHints {
    private static final AutopilotHints INSTANCE = new AutopilotHints();

    /** How many suggestions to read. Beyond a handful the scores are noise. */
    private static final int MAX_SUGGESTIONS_READ = 8;

    /** Weight of the first suggestion; each subsequent one counts for less. */
    private static final float RANK_DECAY = 0.72f;

    /** Weights by code point, always normalised so the strongest is exactly 1. */
    private final SparseArray<Float> mWeights = new SparseArray<>();
    private boolean mEnabled;

    private AutopilotHints() {
    }

    @NonNull
    public static AutopilotHints getInstance() {
        return INSTANCE;
    }

    public void setEnabled(final boolean enabled) {
        mEnabled = enabled;
        if (!enabled) clear();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public boolean isEmpty() {
        return mWeights.size() == 0;
    }

    public void clear() {
        mWeights.clear();
    }

    /** @return 0 when nothing is expected of this code point, up to 1 for the likeliest. */
    public float weightOf(final int codePoint) {
        final Float weight = mWeights.get(Character.toLowerCase(codePoint));
        return weight == null ? 0f : weight;
    }

    /**
     * Rebuilds the hints from the suggestions for {@code typedWord}.
     *
     * Only words that actually continue what has been typed count, and only the single character
     * that would come next. Anything shorter than the typed word, or diverging from it, says
     * nothing about the next keystroke.
     */
    public void update(@Nullable final SuggestedWords suggestedWords, @Nullable final String typedWord) {
        if (!mEnabled) return;
        mWeights.clear();
        if (suggestedWords == null || typedWord == null || typedWord.isEmpty()) return;

        final String prefix = typedWord.toLowerCase();
        final int prefixLength = prefix.length();
        final int count = Math.min(suggestedWords.size(), MAX_SUGGESTIONS_READ);
        float rankWeight = 1f;
        float strongest = 0f;
        for (int i = 0; i < count; i++) {
            final String word = suggestedWords.getWord(i);
            rankWeight *= RANK_DECAY;
            if (word == null || word.length() <= prefixLength) continue;
            final String candidate = word.toLowerCase();
            if (!candidate.startsWith(prefix)) continue;
            final int next = candidate.codePointAt(prefixLength);
            if (!Character.isLetter(next)) continue;
            final Float existing = mWeights.get(next);
            final float updated = (existing == null ? 0f : existing) + rankWeight;
            mWeights.put(next, updated);
            if (updated > strongest) strongest = updated;
        }
        if (strongest <= 0f) {
            mWeights.clear();
            return;
        }
        // Normalised so the strongest candidate is always 1, whatever the raw scores were. The
        // strength setting then means the same thing whether two words matched or eight.
        for (int i = 0; i < mWeights.size(); i++) {
            mWeights.setValueAt(i, mWeights.valueAt(i) / strongest);
        }
    }

    /** The code points currently expected, for the debug overlay to draw. */
    @NonNull
    public List<Integer> expectedCodePoints() {
        final java.util.ArrayList<Integer> codes = new java.util.ArrayList<>(mWeights.size());
        for (int i = 0; i < mWeights.size(); i++) {
            codes.add(mWeights.keyAt(i));
        }
        return codes;
    }
}
