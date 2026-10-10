// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;

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
    public static final String SHAPE_RECT = "rect";
    public static final String SHAPE_ROUNDED = "rounded";
    public static final String SHAPE_OVAL = "oval";
    public static final String SHAPE_DASHED = "dashed";
    public static final String SHAPE_FILLED = "filled";

    private static final float STROKE_WIDTH = 2f;
    // a fill covers the letter beneath it, so it is drawn much fainter than a line
    private static final float FILL_ALPHA_FACTOR = 0.35f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private Keyboard mKeyboard;
    private float mShiftRatio;
    private int mColor = 0xFF4CD964;
    private String mShape = SHAPE_RECT;

    public AutopilotDebugDrawingPreview() {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(STROKE_WIDTH);
        mPaint.setColor(mColor);
    }

    /**
     * Colour and shape of the outline. Only rectangle and dashed show the boundary exactly; the
     * touch area is always a rectangle, so rounded and oval are a look, not a different reach.
     */
    public void setStyle(final int color, final String shape) {
        mColor = color;
        mShape = shape == null ? SHAPE_RECT : shape;
        mPaint.setStyle(SHAPE_FILLED.equals(mShape) ? Paint.Style.FILL : Paint.Style.STROKE);
        mPaint.setPathEffect(SHAPE_DASHED.equals(mShape)
                ? new DashPathEffect(new float[] { 10f, 6f }, 0f) : null);
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
    public void onDeallocateMemory() {
        // Holds nothing but a reference to the keyboard, which is released here so a torn down
        // keyboard is not kept alive by a preview that outlives it.
        mKeyboard = null;
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
            // Opacity tracks confidence, so the strongest candidate is obvious at a glance; it is
            // scaled by the chosen colour's own alpha so a translucent pick stays translucent.
            float alpha = (90 + 165 * weight) * (Color.alpha(mColor) / 255f);
            if (SHAPE_FILLED.equals(mShape)) alpha *= FILL_ALPHA_FACTOR;
            mPaint.setColor(Color.argb((int) alpha,
                    Color.red(mColor), Color.green(mColor), Color.blue(mColor)));
            mRect.set(
                    key.getX() - grown,
                    key.getY() - grown,
                    key.getX() + key.getWidth() + grown,
                    key.getY() + key.getHeight() + grown);
            if (SHAPE_OVAL.equals(mShape)) {
                canvas.drawOval(mRect, mPaint);
            } else if (SHAPE_ROUNDED.equals(mShape)) {
                final float radius = Math.min(mRect.width(), mRect.height()) * 0.25f;
                canvas.drawRoundRect(mRect, radius, radius, mPaint);
            } else {
                canvas.drawRect(mRect, mPaint);
            }
        }
    }
}
