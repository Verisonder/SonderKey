// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.KeyDetector;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.keyboard.PointerTracker;

/**
 * Draws where touches actually land: the real boundaries between letters, as the key detector
 * decides them right now.
 *
 * The whole point of autopilot is that nothing visible changes, which also makes it impossible to
 * tune by eye: a boundary that moved too far and one that did not move at all look identical. The
 * boundaries are no longer rectangles - they bend towards expected letters and follow where this
 * person lands on each key - so instead of drawing a shape, the keyboard is sampled on a fine grid
 * through the detector itself and the edges between different answers are drawn. What is shown is
 * therefore exactly what a touch would get, learned touch included.
 *
 * Boundaries next to an expected letter are drawn strongly, the rest faintly.
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

    /** Opacity steps the edges are sorted into, so each step is one drawLines call. */
    private static final int ALPHA_STEPS = 6;
    /** Opacity of a boundary between two letters neither of which is expected. */
    private static final float FAINT_ALPHA = 60f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private Keyboard mKeyboard;
    private KeyDetector mDetector;
    // Edges (lines) or cells (filled) per opacity step, rebuilt whenever expectations change.
    private final float[][] mShapes = new float[ALPHA_STEPS][];
    private final int[] mShapeLengths = new int[ALPHA_STEPS];
    private int mColor = 0xFF4CD964;
    private String mShape = SHAPE_RECT;

    public AutopilotDebugDrawingPreview() {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(STROKE_WIDTH);
        mPaint.setColor(mColor);
    }

    /**
     * Colour and line style of the outline. Every style draws the same boundary; "oval" is kept as
     * the stored value of what is now the dotted line, so nobody's choice resets.
     */
    public void setStyle(final int color, final String shape) {
        mColor = color;
        mShape = shape == null ? SHAPE_RECT : shape;
        mPaint.setStyle(SHAPE_FILLED.equals(mShape) ? Paint.Style.FILL : Paint.Style.STROKE);
        mPaint.setPathEffect(SHAPE_DASHED.equals(mShape)
                ? new DashPathEffect(new float[] { 10f, 6f }, 0f)
                : SHAPE_OVAL.equals(mShape) ? new DashPathEffect(new float[] { 1f, 6f }, 0f) : null);
        mPaint.setStrokeCap(SHAPE_ROUNDED.equals(mShape) || SHAPE_OVAL.equals(mShape)
                ? Paint.Cap.ROUND : Paint.Cap.BUTT);
    }

    public void setKeyboard(final Keyboard keyboard, final KeyDetector detector) {
        mKeyboard = keyboard;
        mDetector = detector;
        rebuild();
    }

    /**
     * Redraws after the expected letters change.
     *
     * Needed because this preview has no animation of its own: it is only ever out of date when
     * the dictionary's expectations move, which happens outside this class entirely.
     */
    public void invalidateFromOutside() {
        if (!isPreviewEnabled()) return;
        rebuild();
        invalidateDrawingView();
    }

    /**
     * Samples the letter area through the detector and collects the edges between cells that
     * resolve to different keys. Debug only, and only on keystrokes: a few thousand detections.
     */
    private void rebuild() {
        java.util.Arrays.fill(mShapeLengths, 0);
        final Keyboard keyboard = mKeyboard;
        final KeyDetector detector = mDetector;
        if (keyboard == null || detector == null || !isPreviewEnabled()) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (final Key key : keyboard.getSortedKeys()) {
            if (!isLetter(key)) continue;
            minX = Math.min(minX, key.getX());
            minY = Math.min(minY, key.getY());
            maxX = Math.max(maxX, key.getX() + key.getWidth());
            maxY = Math.max(maxY, key.getY() + key.getHeight());
        }
        if (minX >= maxX || minY >= maxY) return;
        final int step = Math.max(4, keyboard.mMostCommonKeyWidth / 12);
        final int cols = (maxX - minX) / step + 1;
        final int rows = (maxY - minY) / step + 1;
        final Key[] grid = new Key[cols * rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r * cols + c] = detector.detectHitKeyInKeyboard(
                        minX + c * step + step / 2, minY + r * step + step / 2);
            }
        }
        final AutopilotHints hints = AutopilotHints.getInstance();
        final boolean filled = SHAPE_FILLED.equals(mShape);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                final Key key = grid[r * cols + c];
                final float x = minX + c * step;
                final float y = minY + r * step;
                if (filled) {
                    if (!isLetter(key)) continue;
                    final float weight = hints.weightOf(key.getCode());
                    if (weight <= 0f) continue;
                    add(stepFor(weight), x, y, x + step, y + step);
                    continue;
                }
                if (c + 1 < cols) {
                    final Key right = grid[r * cols + c + 1];
                    if (right != key && (isLetter(key) || isLetter(right))) {
                        add(stepFor(Math.max(weightOf(hints, key), weightOf(hints, right))),
                                x + step, y, x + step, y + step);
                    }
                }
                if (r + 1 < rows) {
                    final Key below = grid[(r + 1) * cols + c];
                    if (below != key && (isLetter(key) || isLetter(below))) {
                        add(stepFor(Math.max(weightOf(hints, key), weightOf(hints, below))),
                                x, y + step, x + step, y + step);
                    }
                }
            }
        }
    }

    private static boolean isLetter(final Key key) {
        if (key == null) return false;
        final int code = key.getCode();
        return code > 0 && Character.isLetter(code);
    }

    private static float weightOf(final AutopilotHints hints, final Key key) {
        return isLetter(key) && hints.isEnabled() ? hints.weightOf(key.getCode()) : 0f;
    }

    /** Step 0 is for unexpected letters; 1.. for increasing expectation. */
    private static int stepFor(final float weight) {
        if (weight <= 0f) return 0;
        return Math.min(ALPHA_STEPS - 1, 1 + (int) (weight * (ALPHA_STEPS - 2) + 0.5f));
    }

    private void add(final int step, final float a, final float b, final float c, final float d) {
        float[] shapes = mShapes[step];
        int length = mShapeLengths[step];
        if (shapes == null || length + 4 > shapes.length) {
            final float[] grown = new float[Math.max(256, (shapes == null ? 0 : shapes.length) * 2)];
            if (shapes != null) System.arraycopy(shapes, 0, grown, 0, length);
            shapes = grown;
            mShapes[step] = shapes;
        }
        shapes[length] = a;
        shapes[length + 1] = b;
        shapes[length + 2] = c;
        shapes[length + 3] = d;
        mShapeLengths[step] = length + 4;
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
        // Holds references to the keyboard and its detector, released here so a torn down
        // keyboard is not kept alive by a preview that outlives it.
        mKeyboard = null;
        mDetector = null;
        java.util.Arrays.fill(mShapeLengths, 0);
    }

    @Override
    public void drawPreview(@NonNull final Canvas canvas) {
        if (!isPreviewEnabled() || mKeyboard == null) return;
        final boolean filled = SHAPE_FILLED.equals(mShape);
        for (int step = 0; step < ALPHA_STEPS; step++) {
            final int length = mShapeLengths[step];
            if (length == 0) continue;
            // Opacity tracks confidence, so the strongest candidate is obvious at a glance; it is
            // scaled by the chosen colour's own alpha so a translucent pick stays translucent.
            final float confidence = step == 0 ? 0f : (step - 1) / (float) (ALPHA_STEPS - 2);
            float alpha = (step == 0 ? FAINT_ALPHA : 90 + 165 * confidence) * (Color.alpha(mColor) / 255f);
            if (filled) alpha *= FILL_ALPHA_FACTOR;
            mPaint.setColor(Color.argb((int) alpha,
                    Color.red(mColor), Color.green(mColor), Color.blue(mColor)));
            final float[] shapes = mShapes[step];
            if (filled) {
                for (int i = 0; i < length; i += 4) {
                    mRect.set(shapes[i], shapes[i + 1], shapes[i + 2], shapes[i + 3]);
                    canvas.drawRect(mRect, mPaint);
                }
            } else {
                canvas.drawLines(shapes, 0, length, mPaint);
            }
        }
    }
}
