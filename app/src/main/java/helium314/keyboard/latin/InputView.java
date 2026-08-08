/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.core.view.ViewKt;

import helium314.keyboard.accessibility.AccessibilityUtils;
import helium314.keyboard.keyboard.MainKeyboardView;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.settings.Defaults;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;
import helium314.keyboard.latin.suggestions.MoreSuggestionsView;
import helium314.keyboard.latin.suggestions.SuggestionStripView;
import kotlin.Unit;


public final class InputView extends FrameLayout {
    private final Rect mInputViewRect = new Rect();
    private MainKeyboardView mMainKeyboardView;
    private KeyboardTopPaddingForwarder mKeyboardTopPaddingForwarder;
    private MoreSuggestionsViewCanceler mMoreSuggestionsViewCanceler;
    private MotionEventForwarder<?, ?> mActiveForwarder;

    public InputView(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final SuggestionStripView suggestionStripView =
                findViewById(R.id.suggestion_strip_view);
        mMainKeyboardView = findViewById(R.id.keyboard_view);
        mKeyboardTopPaddingForwarder = new KeyboardTopPaddingForwarder(
                mMainKeyboardView, suggestionStripView);
        mMoreSuggestionsViewCanceler = new MoreSuggestionsViewCanceler(
                mMainKeyboardView, suggestionStripView);
        ViewKt.doOnNextLayout(this, this::onNextLayout);
    }

    public void setKeyboardTopPadding(final int keyboardTopPadding) {
        mKeyboardTopPaddingForwarder.setKeyboardTopPadding(keyboardTopPadding);
    }

    @Override
    protected boolean dispatchHoverEvent(final MotionEvent event) {
        if (AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled()
                && mMainKeyboardView.isShowingPopupKeysPanel()) {
            // With accessibility mode on, discard hover events while a popup keys keyboard is shown.
            // The {@link PopupKeysKeyboard} receives hover events directly from the platform.
            return true;
        }
        return super.dispatchHoverEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {
        final Rect rect = mInputViewRect;
        getGlobalVisibleRect(rect);
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index) + rect.left;
        final int y = (int)me.getY(index) + rect.top;

        // The touch events that hit the top padding of keyboard should be forwarded to
        // {@link SuggestionStripView}.
        if (mKeyboardTopPaddingForwarder.onInterceptTouchEvent(x, y, me)) {
            mActiveForwarder = mKeyboardTopPaddingForwarder;
            return true;
        }

        // To cancel {@link MoreSuggestionsView}, we should intercept a touch event to
        // {@link MainKeyboardView} and dismiss the {@link MoreSuggestionsView}.
        if (mMoreSuggestionsViewCanceler.onInterceptTouchEvent(x, y, me)) {
            mActiveForwarder = mMoreSuggestionsViewCanceler;
            return true;
        }

        mActiveForwarder = null;
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (mActiveForwarder == null) {
            return super.onTouchEvent(me);
        }

        final Rect rect = mInputViewRect;
        getGlobalVisibleRect(rect);
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index) + rect.left;
        final int y = (int)me.getY(index) + rect.top;
        return mActiveForwarder.onTouchEvent(x, y, me);
    }

    private Unit onNextLayout(View v) {
        final View frame = findViewById(R.id.main_keyboard_frame);
        Settings.getValues().mColors.setBackground(frame, ColorType.MAIN_BACKGROUND);
        applyRoundedTopCorners(frame);

        // Work around inset application being unreliable
        requestApplyInsets();
        return null;
    }

    /** Corner radius used by both the rounded and the inverted top shapes. */
    private static final float TOP_CORNER_RADIUS_DP = 18f;

    private static final String TOP_SHAPE_ROUNDED = "rounded";
    private static final String TOP_SHAPE_INVERTED = "inverted";

    /** Radius for the inverted shape, or 0 when the top is flat or rounded. */
    private float mInvertedRadius = 0f;
    private Path mInvertedFillPath;
    private int mInvertedWidth = -1, mInvertedTop = -1;
    private final Paint mCornerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Shapes the top of the keyboard: left flat, rounded off, or cut inwards.
     *
     * Rounded clips the frame to a rounded outline, which is cheap and antialiased.
     *
     * Inverted works the other way round and does not touch the keyboard's own edge, which stays
     * flat. A strip the height of one radius is added above the keyboard, and the two corners of
     * that strip are filled with the keyboard's background colour while the middle stays clear. The
     * app showing through the gap therefore appears to have rounded bottom corners, with the
     * keyboard curving up and around them.
     *
     * Clipping rather than giving the frame a shaped background, because the strip and the keyboard
     * view both paint their own opaque backgrounds across the full width, so a shaped drawable on
     * the parent is simply painted over by its children and never shows.
     */
    private void applyRoundedTopCorners(final View frame) {
        if (frame == null) return;
        final String shape = DeviceProtectedUtils.getSharedPreferences(getContext())
                .getString(Settings.PREF_SONDER_TOP_SHAPE, Defaults.PREF_SONDER_TOP_SHAPE);
        final View strip = findViewById(R.id.strip_container);
        final float r = TOP_CORNER_RADIUS_DP * getResources().getDisplayMetrics().density;

        // The view is reused across theme reloads, so every branch has to undo the others.
        frame.setClipToOutline(false);
        frame.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        mInvertedRadius = 0f;
        mInvertedFillPath = null;
        setPadding(0, 0, 0, 0);

        if (TOP_SHAPE_ROUNDED.equals(shape)) {
            // Taller than the view by one radius, which pushes the bottom corners past the edge
            // and leaves only the top two rounded.
            frame.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(final View view, final Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + (int) r, r);
                }
            });
            frame.setClipToOutline(true);
        } else if (TOP_SHAPE_INVERTED.equals(shape)) {
            mInvertedRadius = r;
            // Makes room for the fill above the keyboard. The window grows by this much, so the
            // app is pushed up rather than being covered.
            setPadding(0, Math.round(r), 0, 0);
        }

        // The toolbar row has the keyboard's own spacing below it and nothing at all above, so it
        // reads as pushed upwards against either shape. Half a radius on top evens it out.
        // Rounded also needs it at the sides, where the curve otherwise pinches the leftmost key;
        // inverted keeps a flat edge and would only look indented.
        if (strip != null) {
            final boolean shaped = TOP_SHAPE_ROUNDED.equals(shape) || TOP_SHAPE_INVERTED.equals(shape);
            final int top = shaped ? Math.round(r * 0.5f) : 0;
            final int sides = TOP_SHAPE_ROUNDED.equals(shape) ? Math.round(r * 0.5f) : 0;
            strip.setPadding(sides, top, sides, 0);
        }
        invalidate();
    }

    /**
     * The two shapes filled above the keyboard: a square corner with a quarter disc taken out of
     * it, so what remains hugs a rounded corner on the app above.
     *
     * Rebuilt from the frame's live position, because the frame moves as the toolbar expands and
     * as the keyboard is resized, and a path captured once ends up stranded.
     */
    private Path invertedFillPath(final View frame) {
        final int top = frame.getTop() - Math.round(mInvertedRadius);
        final int width = getWidth();
        if (width <= 0 || top < 0) return null;
        if (mInvertedFillPath != null && width == mInvertedWidth && top == mInvertedTop) {
            return mInvertedFillPath;
        }
        final float r = mInvertedRadius;
        final Path path = new Path();

        path.moveTo(0f, top);
        path.lineTo(0f, top + r);
        path.lineTo(r, top + r);
        path.arcTo(new RectF(0f, top - r, 2f * r, top + r), 90f, 90f);
        path.close();

        path.moveTo(width, top);
        path.lineTo(width, top + r);
        path.lineTo(width - r, top + r);
        path.arcTo(new RectF(width - 2f * r, top - r, width, top + r), 90f, -90f);
        path.close();

        mInvertedFillPath = path;
        mInvertedWidth = width;
        mInvertedTop = top;
        return path;
    }

    @Override
    protected void dispatchDraw(final Canvas canvas) {
        if (mInvertedRadius > 0f) {
            final View frame = findViewById(R.id.main_keyboard_frame);
            final Path fill = frame == null ? null : invertedFillPath(frame);
            if (fill != null) {
                mCornerFillPaint.setColor(Settings.getValues().mColors.get(ColorType.MAIN_BACKGROUND));
                canvas.drawPath(fill, mCornerFillPaint);
            }
        }
        super.dispatchDraw(canvas);
    }

    /**
     * This class forwards series of {@link MotionEvent}s from <code>SenderView</code> to
     * <code>ReceiverView</code>.
     *
     * @param <SenderView> a {@link View} that may send a {@link MotionEvent} to <ReceiverView>.
     * @param <ReceiverView> a {@link View} that receives forwarded {@link MotionEvent} from
     *     <SenderView>.
     */
    private static abstract class
            MotionEventForwarder<SenderView extends View, ReceiverView extends View> {
        protected final SenderView mSenderView;
        protected final ReceiverView mReceiverView;

        protected final Rect mEventSendingRect = new Rect();
        protected final Rect mEventReceivingRect = new Rect();

        public MotionEventForwarder(final SenderView senderView, final ReceiverView receiverView) {
            mSenderView = senderView;
            mReceiverView = receiverView;
        }

        // Return true if a touch event of global coordinate x, y needs to be forwarded.
        protected abstract boolean needsToForward(final int x, final int y);

        // Translate global x-coordinate to <code>ReceiverView</code> local coordinate.
        protected int translateX(final int x) {
            return x - mEventReceivingRect.left;
        }

        // Translate global y-coordinate to <code>ReceiverView</code> local coordinate.
        protected int translateY(final int y) {
            return y - mEventReceivingRect.top;
        }

        /**
         * Callback when a {@link MotionEvent} is forwarded.
         * @param me the motion event to be forwarded.
         */
        protected void onForwardingEvent(final MotionEvent me) {}

        // Returns true if a {@link MotionEvent} is needed to be forwarded to
        // <code>ReceiverView</code>. Otherwise returns false.
        public boolean onInterceptTouchEvent(final int x, final int y, final MotionEvent me) {
            // Forwards a {link MotionEvent} only if both <code>SenderView</code> and
            // <code>ReceiverView</code> are visible.
            if (mSenderView.getVisibility() != View.VISIBLE ||
                    mReceiverView.getVisibility() != View.VISIBLE) {
                return false;
            }
            mSenderView.getGlobalVisibleRect(mEventSendingRect);
            if (!mEventSendingRect.contains(x, y)) {
                return false;
            }

            if (me.getActionMasked() == MotionEvent.ACTION_DOWN) {
                // If the down event happens in the forwarding area, successive
                // {@link MotionEvent}s should be forwarded to <code>ReceiverView</code>.
                return needsToForward(x, y);
            }

            return false;
        }

        // Returns true if a {@link MotionEvent} is forwarded to <code>ReceiverView</code>.
        // Otherwise returns false.
        public boolean onTouchEvent(final int x, final int y, final MotionEvent me) {
            mReceiverView.getGlobalVisibleRect(mEventReceivingRect);
            // Translate global coordinates to <code>ReceiverView</code> local coordinates.
            me.setLocation(translateX(x), translateY(y));
            mReceiverView.dispatchTouchEvent(me);
            onForwardingEvent(me);
            return true;
        }
    }

    /**
     * This class forwards {@link MotionEvent}s happened in the top padding of
     * {@link MainKeyboardView} to {@link SuggestionStripView}.
     */
    private static class KeyboardTopPaddingForwarder
            extends MotionEventForwarder<MainKeyboardView, SuggestionStripView> {
        private int mKeyboardTopPadding;

        public KeyboardTopPaddingForwarder(final MainKeyboardView mainKeyboardView,
                final SuggestionStripView suggestionStripView) {
            super(mainKeyboardView, suggestionStripView);
        }

        public void setKeyboardTopPadding(final int keyboardTopPadding) {
            mKeyboardTopPadding = keyboardTopPadding;
        }

        private boolean isInKeyboardTopPadding(final int y) {
            return y < mEventSendingRect.top + mKeyboardTopPadding;
        }

        @Override
        protected boolean needsToForward(final int x, final int y) {
            // Forwarding an event only when {@link MainKeyboardView} is visible.
            // Because the visibility of {@link MainKeyboardView} is controlled by its parent
            // view in {@link KeyboardSwitcher#setMainKeyboardFrame()}, we should check the
            // visibility of the parent view.
            final View mainKeyboardFrame = (View)mSenderView.getParent();
            return mainKeyboardFrame.getVisibility() == View.VISIBLE && isInKeyboardTopPadding(y);
        }

        @Override
        protected int translateY(final int y) {
            final int translatedY = super.translateY(y);
            if (isInKeyboardTopPadding(y)) {
                // The forwarded event should have coordinates that are inside of the target.
                return Math.min(translatedY, mEventReceivingRect.height() - 1);
            }
            return translatedY;
        }
    }

    /**
     * This class forwards {@link MotionEvent}s happened in the {@link MainKeyboardView} to
     * {@link SuggestionStripView} when the {@link MoreSuggestionsView} is showing.
     * {@link SuggestionStripView} dismisses {@link MoreSuggestionsView} when it receives any event
     * outside of it.
     */
    private static class MoreSuggestionsViewCanceler
            extends MotionEventForwarder<MainKeyboardView, SuggestionStripView> {
        public MoreSuggestionsViewCanceler(final MainKeyboardView mainKeyboardView,
                final SuggestionStripView suggestionStripView) {
            super(mainKeyboardView, suggestionStripView);
        }

        @Override
        protected boolean needsToForward(final int x, final int y) {
            return mReceiverView.isShowingMoreSuggestionPanel() && mEventSendingRect.contains(x, y);
        }

        @Override
        protected void onForwardingEvent(final MotionEvent me) {
            if (me.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mReceiverView.dismissMoreSuggestionsPanel();
            }
        }
    }
}
