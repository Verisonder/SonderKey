// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.Random;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.PointerTracker;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.settings.Settings;

/**
 * Scatters a short-lived burst of particles from a key as it is pressed.
 *
 * Registered on {@link DrawingPreviewPlacerView} alongside the gesture trail and the sliding key
 * indicator, so it draws over the whole keyboard rather than being clipped to the key it came
 * from — particles thrown sideways carry on across their neighbours, which is most of the effect.
 *
 * Everything here is written around one constraint: this runs on every keystroke of someone typing
 * quickly, on the main thread, on whatever phone they own. So particles live in a fixed array that
 * is never grown and never reallocated, the redraw loop stops itself the moment nothing is left
 * alive, and a burst that arrives with the array full overwrites the oldest particles instead of
 * queuing. A dropped particle is invisible; a dropped frame while typing is not.
 */
public final class KeyPressEffectDrawingPreview extends AbstractDrawingPreview {
    /**
     * Upper bound on particles alive at once. Around eight bursts' worth: enough that a fast
     * typist keeps a continuous scatter going, few enough that the per-frame cost stays flat
     * however hard the keyboard is hit.
     */
    private static final int MAX_PARTICLES = 96;
    private static final int PARTICLES_PER_PRESS = 12;

    /** Roughly one frame at 60Hz. */
    private static final long FRAME_MS = 16;
    private static final float LIFETIME_MS = 620f;

    /** Pixels per millisecond, before the display density is applied. */
    private static final float SPEED_MIN = 0.045f;
    private static final float SPEED_MAX = 0.14f;
    /** Downward pull, so particles arc and fall rather than drifting away evenly. */
    private static final float GRAVITY = 0.00035f;
    private static final float RADIUS_MIN = 1.6f;
    private static final float RADIUS_MAX = 3.4f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random mRandom = new Random();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final float[] mX = new float[MAX_PARTICLES];
    private final float[] mY = new float[MAX_PARTICLES];
    private final float[] mVelocityX = new float[MAX_PARTICLES];
    private final float[] mVelocityY = new float[MAX_PARTICLES];
    private final float[] mRadius = new float[MAX_PARTICLES];
    /** Milliseconds of life remaining; zero or less means the slot is free. */
    private final float[] mLife = new float[MAX_PARTICLES];

    /** Where the next burst starts writing, so a full array loses its oldest particles first. */
    private int mNextSlot;
    private int mColor;
    private float mDensity = 1f;
    private long mLastFrameAt;
    private boolean mRunning;

    private final Runnable mFrame = new Runnable() {
        @Override
        public void run() {
            final long now = System.currentTimeMillis();
            // Measured rather than assumed. A dropped frame would otherwise age particles by less
            // than the time that actually passed, and the burst would visibly stall under load.
            final float elapsed = Math.min(now - mLastFrameAt, 64);
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
     * the canvas by the keyboard origin before any preview draws — so the key's x and y need no
     * adjustment, and adding one would push every burst down the screen by that origin twice.
     */
    public void onKeyPressed(@NonNull final Key key) {
        if (!isPreviewEnabled()) return;
        mColor = Settings.getValues().mColors.get(ColorType.KEY_TEXT);
        final float originX = key.getX() + key.getWidth() / 2f;
        final float originY = key.getY() + key.getHeight() / 2f;
        for (int i = 0; i < PARTICLES_PER_PRESS; i++) {
            spawn(originX, originY);
        }
        if (!mRunning) {
            mRunning = true;
            mLastFrameAt = System.currentTimeMillis();
            mHandler.postDelayed(mFrame, FRAME_MS);
        }
        invalidateDrawingView();
    }

    private void spawn(final float originX, final float originY) {
        final int slot = mNextSlot;
        mNextSlot = (mNextSlot + 1) % MAX_PARTICLES;
        // Biased upward: a full circle reads as an explosion, while throwing most of it up and
        // letting gravity bring it back looks like something the key gave off.
        final double angle = -Math.PI / 2 + (mRandom.nextDouble() - 0.5) * Math.PI * 1.15;
        final float speed = (SPEED_MIN + mRandom.nextFloat() * (SPEED_MAX - SPEED_MIN)) * mDensity;
        mX[slot] = originX;
        mY[slot] = originY;
        mVelocityX[slot] = (float) Math.cos(angle) * speed;
        mVelocityY[slot] = (float) Math.sin(angle) * speed;
        mRadius[slot] = (RADIUS_MIN + mRandom.nextFloat() * (RADIUS_MAX - RADIUS_MIN)) * mDensity;
        mLife[slot] = LIFETIME_MS * (0.7f + mRandom.nextFloat() * 0.3f);
    }

    /** @return whether anything is still alive and worth another frame. */
    private boolean advance(final float elapsed) {
        boolean anyAlive = false;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (mLife[i] <= 0) continue;
            mLife[i] -= elapsed;
            if (mLife[i] <= 0) continue;
            mVelocityY[i] += GRAVITY * mDensity * elapsed;
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
        for (int i = 0; i < MAX_PARTICLES; i++) {
            final float life = mLife[i];
            if (life <= 0) continue;
            final float remaining = Math.min(life / LIFETIME_MS, 1f);
            // Fading and shrinking together reads as dissipating rather than as simply vanishing.
            mPaint.setColor(mColor);
            mPaint.setAlpha((int) (255 * remaining * remaining));
            canvas.drawCircle(mX[i], mY[i], mRadius[i] * remaining, mPaint);
        }
    }
}
