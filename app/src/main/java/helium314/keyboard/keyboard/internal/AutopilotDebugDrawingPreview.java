// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.annotation.NonNull;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.keyboard.PointerTracker;

/**
 * Draws where autopilot has moved the key boundaries to.
 *
 * The whole point of autopilot is that nothing visible changes, which also makes it impossible to
 * tune by eye: a boundary that moved too far and one that did not move at all look identical. This
 * draws the outline each expected letter's touch target has grown to, so the effect of the strength
 * setting can actually be seen rather than guessed at.
 *
 * Diagnostic, not decoration, so it is drawn plainly and only when explicitly switched on.
 */
public final class AutopilotDebugDrawingPreview extends AbstractDrawingPreview {
    private static final int OUTLINE_COLOR = 0xFF4CD964;
    private static final float STROKE_WIDTH = 2f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Keyboard mKeyboard;
    private float mShiftRatio;

    public AutopilotDebugDrawingPreview() {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(STROKE_WIDTH);
        mPaint.setColor(OUTLINE_COLOR);
    }

    public void setKeyboard(final Keyboard keyboard) {
        mKeyboard = keyboard;
    }

    public void setShiftRatio(final float ratio) {
        mShiftRatio = ratio;
    }

    /**
     * Redraws after the expected letters change.
     *
     * Needed because this preview has no animation of its own: it is only ever out of date when
     * the dictionary's expectations move, which happens outside this class entirely.
     */
    public void invalidateFromOutside() {
        if (isPreviewEnabled()) invalidateDrawingView();
    }

    @Override
    public void setKeyboardViewGeometry(@NonNull final int[] originCoords, final int width,
            final int height) {
        // Marks the geometry valid; without it isPreviewEnabled() never returns true.
        super.setKeyboardViewGeometry(originCoords, width, height);
    }

    @Override
    public void setPreviewPosition(@NonNull final PointerTracker tracker) {
        // Driven by what the dictionary expects, not by where a finger is.
    }

    @Override
    public void drawPreview(@NonNull final Canvas canvas) {
        if (!isPreviewEnabled() || mKeyboard == null || mShiftRatio <= 0) return;
        final AutopilotHints hints = AutopilotHints.getInstance();
        if (!hints.isEnabled() || hints.isEmpty()) return;

        for (final Key key : mKeyboard.getSortedKeys()) {
            final int code = key.getCode();
            if (code <= 0 || !Character.isLetter(code)) continue;
            final float weight = hints.weightOf(code);
            if (weight <= 0f) continue;
            // The same figure KeyDetector allows this key to reach beyond its own edge, so what
            // is drawn is the boundary actually in force rather than an approximation of it.
            final float grown = key.getWidth() * mShiftRatio * weight;
            // Opacity tracks confidence, so the strongest candidate is obvious at a glance.
            mPaint.setColor(Color.argb(
                    (int) (90 + 165 * weight),
                    Color.red(OUTLINE_COLOR), Color.green(OUTLINE_COLOR), Color.blue(OUTLINE_COLOR)));
            canvas.drawRect(
                    key.getX() - grown,
                    key.getY() - grown,
                    key.getX() + key.getWidth() + grown,
                    key.getY() + key.getHeight() + grown,
                    mPaint);
        }
    }
}
