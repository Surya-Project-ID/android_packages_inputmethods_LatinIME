/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.accessibility;

import android.content.Context;
import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.SparseIntArray;
import android.view.MotionEvent;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

public final class MainKeyboardAccessibilityDelegate
        extends KeyboardAccessibilityDelegate<MainKeyboardView> {
    /** Map of keyboard modes to resource IDs. */
    private static final SparseIntArray KEYBOARD_MODE_RES_IDS = new SparseIntArray();

    static {
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_DATE, R.string.keyboard_mode_date);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_DATETIME, R.string.keyboard_mode_date_time);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_EMAIL, R.string.keyboard_mode_email);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_IM, R.string.keyboard_mode_im);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_NUMBER, R.string.keyboard_mode_number);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_PHONE, R.string.keyboard_mode_phone);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_TEXT, R.string.keyboard_mode_text);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_TIME, R.string.keyboard_mode_time);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_URL, R.string.keyboard_mode_url);
    }

    /** The most recently set keyboard mode. */
    private int mLastKeyboardMode = KEYBOARD_IS_HIDDEN;
    private static final int KEYBOARD_IS_HIDDEN = -1;

    private Key mLastHoverKey = null;

    /**
     * Inset in pixels to look for keys when the user's finger exits the keyboard area.
     */
    private final int mEdgeSlop;

    public MainKeyboardAccessibilityDelegate(final MainKeyboardView mainKeyboardView,
            final KeyDetector keyDetector) {
        super(mainKeyboardView, keyDetector);
        final Context context = mainKeyboardView.getContext();
        mEdgeSlop = context.getResources().getDimensionPixelSize(
                R.dimen.config_accessibility_edge_slop);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKeyboard(final Keyboard keyboard) {
        if (keyboard == null) {
            return;
        }
        final Keyboard lastKeyboard = getKeyboard();
        super.setKeyboard(keyboard);
        final int lastKeyboardMode = mLastKeyboardMode;
        mLastKeyboardMode = keyboard.mId.mMode;

        // Since this method is called even when accessibility is off, make sure
        // to check the state before announcing anything.
        if (!AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            return;
        }
        // Announce the language name only when the language is changed.
        if (lastKeyboard == null || !keyboard.mId.mSubtype.equals(lastKeyboard.mId.mSubtype)) {
            announceKeyboardLanguage(keyboard);
            return;
        }
        // Announce the mode only when the mode is changed.
        if (keyboard.mId.mMode != lastKeyboardMode) {
            announceKeyboardMode(keyboard);
            return;
        }
        // Announce the keyboard type only when the type is changed.
        if (keyboard.mId.mElementId != lastKeyboard.mId.mElementId) {
            announceKeyboardType(keyboard, lastKeyboard);
            return;
        }
    }

    /**
     * Called when the keyboard is hidden and accessibility is enabled.
     */
    public void onHideWindow() {
        announceKeyboardHidden();
        mLastKeyboardMode = KEYBOARD_IS_HIDDEN;
    }

    /**
     * Announces which language of keyboard is being displayed.
     *
     * @param keyboard The new keyboard.
     */
    private void announceKeyboardLanguage(final Keyboard keyboard) {
        final String languageText = SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(
                keyboard.mId.mSubtype);
        sendWindowStateChanged(languageText);
    }

    /**
     * Announces which type of keyboard is being displayed.
     * If the keyboard type is unknown, no announcement is made.
     *
     * @param keyboard The new keyboard.
     */
    private void announceKeyboardMode(final Keyboard keyboard) {
        final Context context = mKeyboardView.getContext();
        final int modeTextResId = KEYBOARD_MODE_RES_IDS.get(keyboard.mId.mMode);
        if (modeTextResId == 0) {
            return;
        }
        final String modeText = context.getString(modeTextResId);
        final String text = context.getString(R.string.announce_keyboard_mode, modeText);
        sendWindowStateChanged(text);
    }

    /**
     * Announces which type of keyboard is being displayed.
     *
     * @param keyboard The new keyboard.
     * @param lastKeyboard The last keyboard.
     */
    private void announceKeyboardType(final Keyboard keyboard, final Keyboard lastKeyboard) {
        final int lastElementId = lastKeyboard.mId.mElementId;
        final int resId;
        switch (keyboard.mId.mElementId) {
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET:
            if (lastElementId == KeyboardId.ELEMENT_ALPHABET
                    || lastElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) {
                return;
            }
            resId = R.string.spoken_description_mode_alpha;
            break;
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
            resId = R.string.spoken_description_shiftmode_on;
            break;
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
            resId = R.string.spoken_description_shiftmode_locked;
            break;
        case KeyboardId.ELEMENT_SYMBOLS:
            resId = R.string.spoken_description_mode_symbol;
            break;
        case KeyboardId.ELEMENT_SYMBOLS_SHIFTED:
            resId = R.string.spoken_description_mode_symbol_shift;
            break;
        case KeyboardId.ELEMENT_PHONE:
            resId = R.string.spoken_description_mode_phone;
            break;
        case KeyboardId.ELEMENT_PHONE_SYMBOLS:
            resId = R.string.spoken_description_mode_phone_shift;
            break;
        default:
            return;
        }
        final String text = mKeyboardView.getContext().getString(resId);
        sendWindowStateChanged(text);
    }

    /**
     * Announces that the keyboard has been hidden.
     */
    private void announceKeyboardHidden() {
        final Context context = mKeyboardView.getContext();
        final String text = context.getString(R.string.announce_keyboard_hidden);

        sendWindowStateChanged(text);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchHoverEvent(final MotionEvent event) {
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        final Key previousKey = mLastHoverKey;
        final Key key;

        if (pointInView(x, y)) {
            key = mKeyDetector.detectHitKey(x, y);
        } else {
            key = null;
        }
        mLastHoverKey = key;

        switch (event.getAction()) {
        case MotionEvent.ACTION_HOVER_EXIT:
            // Make sure we're not getting an EXIT event because the user slid
            // off the keyboard area, then force a key press.
            if (key != null) {
                final long downTime = simulateKeyPress(key);
                simulateKeyRelease(key, downTime);
            }
            //$FALL-THROUGH$
        case MotionEvent.ACTION_HOVER_ENTER:
            return onHoverKey(key, event);
        case MotionEvent.ACTION_HOVER_MOVE:
            if (key != previousKey) {
                return onTransitionKey(key, previousKey, event);
            }
            return onHoverKey(key, event);
        }
        return false;
    }

    /**
     * Utility method to determine whether the given point, in local coordinates, is inside the
     * view, where the area of the view is contracted by the edge slop factor.
     *
     * @param localX The local x-coordinate.
     * @param localY The local y-coordinate.
     */
    private boolean pointInView(final int localX, final int localY) {
        return (localX >= mEdgeSlop) && (localY >= mEdgeSlop)
                && (localX < (mKeyboardView.getWidth() - mEdgeSlop))
                && (localY < (mKeyboardView.getHeight() - mEdgeSlop));
    }

    /**
     * Simulates a key press by injecting touch an event into the keyboard view.
     * This avoids the complexity of trackers and listeners within the keyboard.
     *
     * @param key The key to press.
     */
    private long simulateKeyPress(final Key key) {
        final int x = key.getHitBox().centerX();
        final int y = key.getHitBox().centerY();
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        mKeyboardView.onTouchEvent(downEvent);
        downEvent.recycle();
        return downTime;
    }

    /**
     * Simulates a key release by injecting touch an event into the keyboard view.
     * This avoids the complexity of trackers and listeners within the keyboard.
     *
     * @param key The key to release.
     */
    private void simulateKeyRelease(final Key key, final long downTime) {
        final int x = key.getHitBox().centerX();
        final int y = key.getHitBox().centerY();
        final MotionEvent upEvent = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        mKeyboardView.onTouchEvent(upEvent);
        upEvent.recycle();
    }

    /**
     * Simulates a transition between two {@link Key}s by sending a HOVER_EXIT on the previous key,
     * a HOVER_ENTER on the current key, and a HOVER_MOVE on the current key.
     *
     * @param currentKey The currently hovered key.
     * @param previousKey The previously hovered key.
     * @param event The event that triggered the transition.
     * @return {@code true} if the event was handled.
     */
    private boolean onTransitionKey(final Key currentKey, final Key previousKey,
            final MotionEvent event) {
        final int savedAction = event.getAction();
        event.setAction(MotionEvent.ACTION_HOVER_EXIT);
        onHoverKey(previousKey, event);
        event.setAction(MotionEvent.ACTION_HOVER_ENTER);
        onHoverKey(currentKey, event);
        event.setAction(MotionEvent.ACTION_HOVER_MOVE);
        final boolean handled = onHoverKey(currentKey, event);
        event.setAction(savedAction);
        return handled;
    }

    /**
     * Handles a hover event on a key. If {@link Key} extended View, this would be analogous to
     * calling View.onHoverEvent(MotionEvent).
     *
     * @param key The currently hovered key.
     * @param event The hover event.
     * @return {@code true} if the event was handled.
     */
    private boolean onHoverKey(final Key key, final MotionEvent event) {
        // Null keys can't receive events.
        if (key == null) {
            return false;
        }
        final KeyboardAccessibilityNodeProvider provider = getAccessibilityNodeProvider();

        switch (event.getAction()) {
        case MotionEvent.ACTION_HOVER_ENTER:
            provider.sendAccessibilityEventForKey(
                    key, AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER);
            provider.performActionForKey(
                    key, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, null);
            break;
        case MotionEvent.ACTION_HOVER_EXIT:
            provider.sendAccessibilityEventForKey(
                    key, AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT);
            break;
        }
        return true;
    }
}
