// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.Random;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.PointerTracker;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.settings.SettingsValues;

/**
 * Scatters a short-lived burst of particles from a key as it is pressed.
 *
 * Registered on {@link DrawingPreviewPlacerView} alongside the gesture trail and the sliding key
 * indicator, so it draws over the whole keyboard rather than being clipped to the key it came
 * from - particles thrown sideways carry on across their neighbours, which is most of the effect.
 *
 * Everything here is written around one constraint: this runs on every keystroke of someone typing
 * quickly, on the main thread, on whatever phone they own. So particles live in fixed arrays that
 * are never grown and never reallocated, the redraw loop stops itself the moment nothing is left
 * alive, and a burst arriving with the arrays full overwrites the oldest particles rather than
 * queueing. A dropped particle is invisible; a dropped frame while typing is not.
 */
public final class KeyPressEffectDrawingPreview extends AbstractDrawingPreview {
    /**
     * Hard ceiling on particles alive at once, independent of what the user asks for. The count
     * per press is theirs to choose; the cost per frame is not, so a generous burst simply
     * recycles its own oldest particles rather than letting the frame time grow without limit.
     */
    private static final int MAX_PARTICLES = 200;

    /** Roughly one frame at 60Hz. */
    private static final long FRAME_MS = 16;
    /** Longest step a single frame may age particles by, so a stall does not teleport them. */
    private static final float MAX_FRAME_MS = 64f;

    private static final float SPEED_MIN = 0.045f;
    private static final float SPEED_MAX = 0.14f;
    private static final float GRAVITY = 0.00035f;
    private static final float RADIUS_MIN = 1.6f;
    private static final float RADIUS_MAX = 3.4f;

    public static final String SHAPE_CIRCLE = "circle";
    public static final String SHAPE_RING = "ring";
    public static final String SHAPE_SQUARE = "square";
    public static final String SHAPE_STAR = "star";

    public static final String COLOR_KEY_TEXT = "key_text";
    public static final String COLOR_ACCENT = "accent";
    public static final String COLOR_GESTURE_TRAIL = "gesture_trail";
    public static final String COLOR_CUSTOM = "custom";
    public static final String COLOR_RANDOM = "random";

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random mRandom = new Random();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** A unit star built once, then scaled per particle rather than recomputed. */
    private final Path mStarPath = new Path();

    private final float[] mX = new float[MAX_PARTICLES];
    private final float[] mY = new float[MAX_PARTICLES];
    private final float[] mVelocityX = new float[MAX_PARTICLES];
    private final float[] mVelocityY = new float[MAX_PARTICLES];
    private final float[] mRadius = new float[MAX_PARTICLES];
    /** Milliseconds of life remaining; zero or less means the slot is free. */
    private final float[] mLife = new float[MAX_PARTICLES];
    /** Full lifetime, kept per particle so a duration change mid-flight cannot distort a fade. */
    private final float[] mLifespan = new float[MAX_PARTICLES];
    private final int[] mColor = new int[MAX_PARTICLES];

    /** Where the next burst starts writing, so full arrays lose their oldest particles first. */
    private int mNextSlot;
    private float mDensity = 1f;
    private float mGravity;
    private String mShape = SHAPE_CIRCLE;
    private long mLastFrameAt;
    private boolean mRunning;

    public KeyPressEffectDrawingPreview() {
        buildStar();
    }

    private final Runnable mFrame = new Runnable() {
        @Override
        public void run() {
            final long now = System.currentTimeMillis();
            // Measured rather than assumed. A dropped frame would otherwise age particles by less
            // than the time that actually passed, and a burst would visibly stall under load.
            final float elapsed = Math.min(now - mLastFrameAt, MAX_FRAME_MS);
            mLastFrameAt = now;
            if (advance(elapsed)) {
                mHandler.postDelayed(this, FRAME_MS);
            } else {
                mRunning = false;
            }
            invalidateDrawingView();
        }
    };

    public void setDensity(final float density) {
        mDensity = density > 0 ? density : 1f;
    }

    /**
     * Throws a burst from the centre of {@code key}.
     *
     * Coordinates are the keyboard view's own, because {@link DrawingPreviewPlacerView} translates
     * the canvas by the keyboard origin before any preview draws - so the key's x and y need no
     * adjustment, and adding one would push every burst down the screen by that origin twice.
     */
    public void onKeyPressed(@NonNull final Key key) {
        if (!isPreviewEnabled()) return;
        final SettingsValues settings = Settings.getValues();
        if (settings == null || !settings.mKeyPressEffect) return;

        mShape = settings.mKeyPressEffectShape;
        mGravity = GRAVITY * settings.mKeyPressEffectGravity;
        final float originX = key.getX() + key.getWidth() / 2f;
        final float originY = key.getY() + key.getHeight() / 2f;
        final int count = Math.min(settings.mKeyPressEffectCount, MAX_PARTICLES);
        for (int i = 0; i < count; i++) {
            spawn(originX, originY, settings);
        }
        if (!mRunning) {
            mRunning = true;
            mLastFrameAt = System.currentTimeMillis();
            mHandler.postDelayed(mFrame, FRAME_MS);
        }
        invalidateDrawingView();
    }

    private void spawn(final float originX, final float originY, final SettingsValues settings) {
        final int slot = mNextSlot;
        mNextSlot = (mNextSlot + 1) % MAX_PARTICLES;
        // Spread runs from a narrow upward jet to a full circle. Biased upward at anything less
        // than full, because throwing particles up and letting gravity bring them back reads as
        // something the key gave off, where an even ring reads as an explosion.
        final double arc = Math.PI * 2 * Math.max(0f, Math.min(settings.mKeyPressEffectSpread, 1f));
        final double angle = -Math.PI / 2 + (mRandom.nextDouble() - 0.5) * arc;
        final float speed = (SPEED_MIN + mRandom.nextFloat() * (SPEED_MAX - SPEED_MIN))
                * mDensity * settings.mKeyPressEffectSpeed;
        final float lifespan = settings.mKeyPressEffectDuration * (0.7f + mRandom.nextFloat() * 0.3f);
        mX[slot] = originX;
        mY[slot] = originY;
        mVelocityX[slot] = (float) Math.cos(angle) * speed;
        mVelocityY[slot] = (float) Math.sin(angle) * speed;
        mRadius[slot] = (RADIUS_MIN + mRandom.nextFloat() * (RADIUS_MAX - RADIUS_MIN))
                * mDensity * settings.mKeyPressEffectSize;
        mLifespan[slot] = lifespan;
        mLife[slot] = lifespan;
        mColor[slot] = pickColor(settings);
    }

    private int pickColor(final SettingsValues settings) {
        final String choice = settings.mKeyPressEffectColor;
        if (COLOR_CUSTOM.equals(choice)) return settings.mKeyPressEffectCustomColor;
        if (COLOR_RANDOM.equals(choice)) {
            // Full saturation at high value: anything less turns to grey against a dark keyboard.
            return Color.HSVToColor(new float[] { mRandom.nextFloat() * 360f, 0.75f, 1f });
        }
        if (COLOR_ACCENT.equals(choice)) return settings.mColors.get(ColorType.ACTION_KEY_BACKGROUND);
        if (COLOR_GESTURE_TRAIL.equals(choice)) return settings.mColors.get(ColorType.GESTURE_TRAIL);
        return settings.mColors.get(ColorType.KEY_TEXT);
    }

    /** @return whether anything is still alive and worth another frame. */
    private boolean advance(final float elapsed) {
        boolean anyAlive = false;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (mLife[i] <= 0) continue;
            mLife[i] -= elapsed;
            if (mLife[i] <= 0) continue;
            mVelocityY[i] += mGravity * mDensity * elapsed;
            mX[i] += mVelocityX[i] * elapsed;
            mY[i] += mVelocityY[i] * elapsed;
            anyAlive = true;
        }
        return anyAlive;
    }

    @Override
    public void onDeallocateMemory() {
        stop();
    }

    /** Clears everything at once, for a keyboard being hidden or torn down. */
    public void stop() {
        mHandler.removeCallbacks(mFrame);
        mRunning = false;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            mLife[i] = 0;
        }
    }

    @Override
    public void setKeyboardViewGeometry(@NonNull final int[] originCoords, final int width,
            final int height) {
        // The super call is what marks the geometry valid; without it isPreviewEnabled() is
        // permanently false and nothing is ever drawn. Particles need no layout of their own,
        // since each is positioned from the key that spawned it.
        super.setKeyboardViewGeometry(originCoords, width, height);
    }

    @Override
    public void setPreviewPosition(@NonNull final PointerTracker tracker) {
        // Driven by key presses rather than by pointer movement, so there is nothing to follow.
    }

    @Override
    public void drawPreview(@NonNull final Canvas canvas) {
        if (!isPreviewEnabled()) return;
        final boolean ring = SHAPE_RING.equals(mShape);
        final boolean square = SHAPE_SQUARE.equals(mShape);
        final boolean star = SHAPE_STAR.equals(mShape);
        mPaint.setStyle(ring ? Paint.Style.STROKE : Paint.Style.FILL);
        for (int i = 0; i < MAX_PARTICLES; i++) {
            final float life = mLife[i];
            if (life <= 0) continue;
            final float remaining = Math.min(life / mLifespan[i], 1f);
            // Fading and shrinking together reads as dissipating rather than simply vanishing.
            // Squaring the fade keeps particles solid most of their flight then drops them away.
            final float size = mRadius[i] * remaining;
            mPaint.setColor(mColor[i]);
            mPaint.setAlpha((int) (255 * remaining * remaining));
            if (star) {
                canvas.save();
                canvas.translate(mX[i], mY[i]);
                canvas.scale(size, size);
                canvas.drawPath(mStarPath, mPaint);
                canvas.restore();
            } else if (square) {
                canvas.drawRect(mX[i] - size, mY[i] - size, mX[i] + size, mY[i] + size, mPaint);
            } else if (ring) {
                mPaint.setStrokeWidth(Math.max(size * 0.4f, 1f));
                canvas.drawCircle(mX[i], mY[i], size, mPaint);
            } else {
                canvas.drawCircle(mX[i], mY[i], size, mPaint);
            }
        }
    }

    /** A five-pointed star of radius one, built once so no path is allocated while drawing. */
    private void buildStar() {
        final int points = 5;
        final float innerRatio = 0.42f;
        for (int i = 0; i < points * 2; i++) {
            final double angle = -Math.PI / 2 + i * Math.PI / points;
            final float radius = (i % 2 == 0) ? 1f : innerRatio;
            final float x = (float) Math.cos(angle) * radius;
            final float y = (float) Math.sin(angle) * radius;
            if (i == 0) mStarPath.moveTo(x, y);
            else mStarPath.lineTo(x, y);
        }
        mStarPath.close();
    }
}
