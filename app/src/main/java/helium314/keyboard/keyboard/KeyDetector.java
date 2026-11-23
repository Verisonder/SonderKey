/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

import java.util.Map;

import helium314.keyboard.keyboard.internal.AutopilotHints;
import helium314.keyboard.keyboard.internal.TouchModel;
import helium314.keyboard.keyboard.internal.TouchScoring;
import helium314.keyboard.keyboard.internal.TouchStats;

/**
 * This class handles key detection.
 */
public class KeyDetector {
    private final int mKeyHysteresisDistanceSquared;
    private final int mKeyHysteresisDistanceForSlidingModifierSquared;

    private Keyboard mKeyboard;
    private int mCorrectionX;
    private int mCorrectionY;
    private float mAutopilotLambda;
    private Map<Integer, TouchStats> mTouchStats;

    /** How far past its own edge a key may claim a touch, in key widths. */
    private static final float MAX_REACH = 0.65f;

    public KeyDetector() {
        this(0.0f /* keyHysteresisDistance */, 0.0f /* keyHysteresisDistanceForSlidingModifier */);
    }

    /**
     * Key detection object constructor with key hysteresis distances.
     *
     * @param keyHysteresisDistance if the pointer movement distance is smaller than this, the
     * movement will not be handled as meaningful movement. The unit is pixel.
     * @param keyHysteresisDistanceForSlidingModifier the same parameter for sliding input that
     * starts from a modifier key such as shift and symbols key.
     */
    public KeyDetector(final float keyHysteresisDistance,
            final float keyHysteresisDistanceForSlidingModifier) {
        mKeyHysteresisDistanceSquared = (int)(keyHysteresisDistance * keyHysteresisDistance);
        mKeyHysteresisDistanceForSlidingModifierSquared = (int)(
                keyHysteresisDistanceForSlidingModifier * keyHysteresisDistanceForSlidingModifier);
    }

    public void setKeyboard(final Keyboard keyboard, final float correctionX,
            final float correctionY) {
        if (keyboard == null) {
            throw new NullPointerException();
        }
        mCorrectionX = (int)correctionX;
        mCorrectionY = (int)correctionY;
        mKeyboard = keyboard;
    }

    public int getKeyHysteresisDistanceSquared(final boolean isSlidingFromModifier) {
        return isSlidingFromModifier
                ? mKeyHysteresisDistanceForSlidingModifierSquared : mKeyHysteresisDistanceSquared;
    }

    public int getTouchX(final int x) {
        return x + mCorrectionX;
    }

    // TODO: Remove vertical correction.
    public int getTouchY(final int y) {
        return y + mCorrectionY;
    }

    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    public boolean alwaysAllowsKeySelectionByDraggingFinger() {
        return false;
    }

    /**
     * Detect the key whose hitbox the touch point is in.
     *
     * @param x The x-coordinate of a touch point
     * @param y The y-coordinate of a touch point
     * @return the key that the touch point hits.
     */
    public Key detectHitKey(final int x, final int y) {
        if (mKeyboard == null) {
            return null;
        }
        return detectHitKeyInKeyboard(getTouchX(x), getTouchY(y));
    }

    /**
     * The same decision as {@link #detectHitKey}, for a point already in the keyboard's own
     * coordinates. The touch-area outline uses this so it draws exactly what a touch would get.
     */
    public Key detectHitKeyInKeyboard(final int touchX, final int touchY) {
        if (mKeyboard == null) {
            return null;
        }
        int minDistance = Integer.MAX_VALUE;
        Key primaryKey = null;
        for (final Key key: mKeyboard.getNearestKeys(touchX, touchY)) {
            // An edge key always has its enlarged hitbox to respond to an event that occurred in
            // the empty area around the key. (@see Key#markAsLeftEdge(KeyboardParams)} etc.)
            if (!key.isOnKey(touchX, touchY)) {
                continue;
            }
            final int distance = key.squaredDistanceToEdge(touchX, touchY);
            if (distance > minDistance) {
                continue;
            }
            // To take care of hitbox overlaps, we compare key's code here too.
            if (primaryKey == null || distance < minDistance
                    || key.getCode() > primaryKey.getCode()) {
                minDistance = distance;
                primaryKey = key;
            }
        }
        return refineLetter(primaryKey, touchX, touchY);
    }

    /**
     * Decides which letter a touch near a boundary most likely meant.
     *
     * Two things can move a boundary, and both go through the same score (see TouchScoring): where
     * this person actually lands on each key, once enough of their typing has been seen, and -
     * with Autopilot on - which letters the dictionary expects next. Nothing on screen moves and
     * nothing already typed is revised; only which key a touch belongs to, decided at contact.
     *
     * Kept deliberately modest. Only letters can win or lose ground, so space, backspace, shift
     * and enter are never taken. A touch close to the centre of the key it landed on always stays
     * there, however unlikely the letter. A key can only claim a touch within a bounded distance
     * of its own edge. And where neither key has anything learned, a neighbour only wins by being
     * more expected than the key that was hit, so with nothing learned and Autopilot off every
     * touch lands exactly where it always did.
     */
    private Key refineLetter(final Key primaryKey, final int touchX, final int touchY) {
        if (primaryKey == null || !isPlainLetter(primaryKey)) return primaryKey;
        final AutopilotHints hints = AutopilotHints.getInstance();
        final boolean usePrior = mAutopilotLambda > 0f && hints.isEnabled() && !hints.isEmpty();
        final Map<Integer, TouchStats> stats = mTouchStats;
        final boolean useStats = stats != null && !stats.isEmpty();
        if (!usePrior && !useStats) return primaryKey;

        final TouchStats primaryStats = TouchModel.learnedOffset(stats, primaryKey.getCode());
        final float primaryOffX = primaryStats == null ? 0f : primaryStats.offsetX();
        final float primaryOffY = primaryStats == null ? 0f : primaryStats.offsetY();
        if (Math.abs(dxOf(primaryKey, touchX) - primaryOffX) < TouchScoring.CENTRE_GUARD
                && Math.abs(dyOf(primaryKey, touchY) - primaryOffY) < TouchScoring.CENTRE_GUARD) {
            return primaryKey;
        }
        final float primaryPrior = usePrior ? priorOf(primaryKey, hints) : 0f;
        float best = fitOf(primaryKey, primaryStats, touchX, touchY) + primaryPrior;
        Key winner = primaryKey;
        for (final Key key : mKeyboard.getNearestKeys(touchX, touchY)) {
            if (key == primaryKey || !isPlainLetter(key)) continue;
            if (distanceOutside(key, touchX, touchY) > key.getWidth() * MAX_REACH) continue;
            final TouchStats keyStats = TouchModel.learnedOffset(stats, key.getCode());
            final float prior = usePrior ? priorOf(key, hints) : 0f;
            // Without anything learned on either side, the drawn boundary is the evidence and only
            // a more expected letter may move it.
            if (keyStats == null && primaryStats == null && prior <= primaryPrior) continue;
            final float score = fitOf(key, keyStats, touchX, touchY) + prior;
            if (score > best + 1e-4f) {
                best = score;
                winner = key;
            }
        }
        return winner;
    }

    private float priorOf(final Key key, final AutopilotHints hints) {
        return mAutopilotLambda * TouchScoring.INSTANCE.logPrior(hints.weightOf(key.getCode()), hints.getFloor());
    }

    private static float fitOf(final Key key, final TouchStats stats, final int x, final int y) {
        if (stats == null) {
            return TouchScoring.INSTANCE.fit(dxOf(key, x), dyOf(key, y), 0f, 0f, 1f, 1f);
        }
        return TouchScoring.INSTANCE.fit(dxOf(key, x), dyOf(key, y),
                stats.offsetX(), stats.offsetY(), 1f, 1f);
    }

    /** Horizontal distance from the key's drawn centre, in key widths. */
    private static float dxOf(final Key key, final int x) {
        return (x - (key.getX() + key.getWidth() / 2f)) / Math.max(1, key.getWidth());
    }

    /** Vertical distance from the key's drawn centre, in key heights. */
    private static float dyOf(final Key key, final int y) {
        return (y - (key.getY() + key.getHeight() / 2f)) / Math.max(1, key.getHeight());
    }

    /** Letters only, and single-character ones: no shift, space, punctuation or action keys. */
    private static boolean isPlainLetter(final Key key) {
        final int code = key.getCode();
        return code > 0 && Character.isLetter(code);
    }

    /** Distance from the touch to a key it is outside of; 0 if inside. */
    private static int distanceOutside(final Key key, final int x, final int y) {
        final int dx = Math.max(0, Math.max(key.getX() - x, x - (key.getX() + key.getWidth())));
        final int dy = Math.max(0, Math.max(key.getY() - y, y - (key.getY() + key.getHeight())));
        return (int) Math.sqrt((double) dx * dx + (double) dy * dy);
    }

    /** How strongly Autopilot's expectations count; 0 leaves them out entirely. */
    public void setAutopilotLambda(final float lambda) {
        mAutopilotLambda = lambda;
    }

    /** Where this person lands on each key of the current keyboard; null for nothing learned. */
    public void setTouchStats(final Map<Integer, TouchStats> stats) {
        mTouchStats = stats;
    }
}
