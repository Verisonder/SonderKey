/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

import helium314.keyboard.keyboard.internal.AutopilotHints;

/**
 * This class handles key detection.
 */
public class KeyDetector {
    private final int mKeyHysteresisDistanceSquared;
    private final int mKeyHysteresisDistanceForSlidingModifierSquared;

    private Keyboard mKeyboard;
    private int mCorrectionX;
    private int mCorrectionY;
    private float mAutopilotShiftRatio;

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
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

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
        return applyAutopilot(primaryKey, touchX, touchY);
    }

    /**
     * Lets a letter the dictionary expects claim ground from its neighbours.
     *
     * Nothing on screen moves and nothing already typed is revised. All that changes is where the
     * boundary between two keys sits, and only for touches that were already close to it.
     *
     * Deliberately timid, because a keyboard that overrides a clear press is unusable and gives
     * the user no way to see why. Three conditions must all hold before anything is overridden:
     * the touch must be within the shift distance of an edge, so a press anywhere near the middle
     * of a key is untouchable; the key that would win must be a plain letter, so space, backspace,
     * shift and enter can never be taken; and the newcomer must be more expected than what the
     * touch actually landed on, so ties leave the original alone.
     */
    private Key applyAutopilot(final Key primaryKey, final int touchX, final int touchY) {
        final AutopilotHints hints = AutopilotHints.getInstance();
        if (primaryKey == null || !hints.isEnabled() || hints.isEmpty()) return primaryKey;
        if (!isPlainLetter(primaryKey)) return primaryKey;

        final int maxShift = (int) (primaryKey.getWidth() * mAutopilotShiftRatio);
        if (maxShift <= 0) return primaryKey;
        // How far inside its own key the touch landed. A confident press is left alone entirely.
        if (depthInside(primaryKey, touchX, touchY) > maxShift) return primaryKey;

        final float pressedWeight = hints.weightOf(primaryKey.getCode());
        Key winner = primaryKey;
        float winnerWeight = pressedWeight;
        int winnerDistance = Integer.MAX_VALUE;
        for (final Key key : mKeyboard.getNearestKeys(touchX, touchY)) {
            if (key == primaryKey || !isPlainLetter(key)) continue;
            final float weight = hints.weightOf(key.getCode());
            if (weight <= winnerWeight) continue;
            // The distance this key's edge would have to grow by to reach the touch, against how
            // far it has earned the right to grow.
            final int reach = distanceOutside(key, touchX, touchY);
            if (reach > maxShift * weight) continue;
            if (reach < winnerDistance || weight > winnerWeight) {
                winner = key;
                winnerWeight = weight;
                winnerDistance = reach;
            }
        }
        return winner;
    }

    /** Letters only, and single-character ones: no shift, space, punctuation or action keys. */
    private static boolean isPlainLetter(final Key key) {
        final int code = key.getCode();
        return code > 0 && Character.isLetter(code);
    }

    /** Distance from the touch to the nearest edge of a key it is inside; 0 if outside. */
    private static int depthInside(final Key key, final int x, final int y) {
        final int left = x - key.getX();
        final int right = key.getX() + key.getWidth() - x;
        final int top = y - key.getY();
        final int bottom = key.getY() + key.getHeight() - y;
        if (left < 0 || right < 0 || top < 0 || bottom < 0) return 0;
        return Math.min(Math.min(left, right), Math.min(top, bottom));
    }

    /** Distance from the touch to a key it is outside of; 0 if inside. */
    private static int distanceOutside(final Key key, final int x, final int y) {
        final int dx = Math.max(0, Math.max(key.getX() - x, x - (key.getX() + key.getWidth())));
        final int dy = Math.max(0, Math.max(key.getY() - y, y - (key.getY() + key.getHeight())));
        return (int) Math.sqrt((double) dx * dx + (double) dy * dy);
    }

    /** Fraction of a key's width a boundary may move by, at full confidence. */
    public void setAutopilotShiftRatio(final float ratio) {
        mAutopilotShiftRatio = ratio;
    }
}
