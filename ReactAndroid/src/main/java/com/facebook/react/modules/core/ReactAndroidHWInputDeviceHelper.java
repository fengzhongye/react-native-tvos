/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.modules.core;

import android.view.KeyEvent;
import android.view.View;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.common.SystemClock;
import com.facebook.react.config.ReactFeatureFlags;

import java.util.Map;

/** Responsible for dispatching events specific for hardware inputs. */
public class ReactAndroidHWInputDeviceHelper {

  /**
   * Contains a mapping between handled KeyEvents and the corresponding navigation event that should
   * be fired when the KeyEvent is received.
   */
  private static final Map<Integer, String> KEY_EVENTS_ACTIONS =
      MapBuilder.<Integer, String>builder()
          .put(KeyEvent.KEYCODE_DPAD_CENTER, "select")
          .put(KeyEvent.KEYCODE_ENTER, "select")
          .put(KeyEvent.KEYCODE_NUMPAD_ENTER, "select")
          .put(KeyEvent.KEYCODE_BUTTON_SELECT, "select")
          .put(KeyEvent.KEYCODE_SPACE, "select")
          .put(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "playPause")
          .put(KeyEvent.KEYCODE_MEDIA_PLAY, "play")
          .put(KeyEvent.KEYCODE_MEDIA_PAUSE, "pause")
          .put(KeyEvent.KEYCODE_MEDIA_NEXT, "next")
          .put(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "previous")
          .put(KeyEvent.KEYCODE_MEDIA_REWIND, "rewind")
          .put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "fastForward")
          .put(KeyEvent.KEYCODE_MEDIA_STOP, "stop")
          .put(KeyEvent.KEYCODE_MEDIA_NEXT, "next")
          .put(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "previous")
          .put(KeyEvent.KEYCODE_DPAD_UP, "up")
          .put(KeyEvent.KEYCODE_DPAD_RIGHT, "right")
          .put(KeyEvent.KEYCODE_DPAD_DOWN, "down")
          .put(KeyEvent.KEYCODE_DPAD_LEFT, "left")
          .put(KeyEvent.KEYCODE_INFO, "info")
          .put(KeyEvent.KEYCODE_MENU, "menu")
          .put(KeyEvent.KEYCODE_0, "0")
          .put(KeyEvent.KEYCODE_1, "1")
          .put(KeyEvent.KEYCODE_2, "2")
          .put(KeyEvent.KEYCODE_3, "3")
          .put(KeyEvent.KEYCODE_4, "4")
          .put(KeyEvent.KEYCODE_5, "5")
          .put(KeyEvent.KEYCODE_6, "6")
          .put(KeyEvent.KEYCODE_7, "7")
          .put(KeyEvent.KEYCODE_8, "8")
          .put(KeyEvent.KEYCODE_9, "9")
          .put(KeyEvent.KEYCODE_CHANNEL_DOWN, "channelDown")
          .put(KeyEvent.KEYCODE_CHANNEL_UP, "channelUp")
          .build();

  private static final Map<Integer, String> KEY_EVENTS_LONG_PRESS_ACTIONS =
      MapBuilder.<Integer, String>builder()
          .put(KeyEvent.KEYCODE_DPAD_CENTER, "longSelect")
          .put(KeyEvent.KEYCODE_ENTER, "longSelect")
          .put(KeyEvent.KEYCODE_NUMPAD_ENTER, "longSelect")
          .put(KeyEvent.KEYCODE_BUTTON_SELECT, "longSelect")
          .put(KeyEvent.KEYCODE_DPAD_UP, "longUp")
          .put(KeyEvent.KEYCODE_DPAD_RIGHT, "longRight")
          .put(KeyEvent.KEYCODE_DPAD_DOWN, "longDown")
          .put(KeyEvent.KEYCODE_DPAD_LEFT, "longLeft")
          .build();

  /**
   * We keep a reference to the last focused view id so that we can send it as a target for key
   * events and be able to send a blur event when focus changes.
   */
  private int mLastFocusedViewId = View.NO_ID;

  private long mLastKeyDownTime = 0;
  private long mPressedDelta = 1000;

  public ReactAndroidHWInputDeviceHelper() {}

  private boolean isSelectEvent(int eventKeyCode) {
    return eventKeyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
      eventKeyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
      eventKeyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
      eventKeyCode == KeyEvent.KEYCODE_ENTER;
  }

  /** Only the up/right/down/left arrows. The dpad center button is not included */
  private boolean isDPadEvent(int eventKeyCode) {
    return eventKeyCode == KeyEvent.KEYCODE_DPAD_UP ||
      eventKeyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
      eventKeyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
      eventKeyCode == KeyEvent.KEYCODE_DPAD_LEFT;
  }

  /** Called from {@link com.facebook.react.ReactRootView}. This is the main place the key events are handled. */
  public void handleKeyEvent(KeyEvent ev, ReactContext context) {
    int eventKeyCode = ev.getKeyCode();
    int eventKeyAction = ev.getAction();
    long time = SystemClock.uptimeMillis();
    boolean isSelectOrDPadEvent = isDPadEvent(eventKeyCode) || isSelectEvent(eventKeyCode);

    // Simple implementation of long press detection
    if ((eventKeyAction == KeyEvent.ACTION_DOWN)
      && isSelectOrDPadEvent && mLastKeyDownTime == 0) {
      mLastKeyDownTime = time;
    }

    if (shouldDispatchEvent(eventKeyCode, eventKeyAction)) {
      long delta = time - mLastKeyDownTime;
      boolean isLongPress = delta > mPressedDelta;

      if(isLongPress && isSelectOrDPadEvent){
        dispatchEvent(KEY_EVENTS_LONG_PRESS_ACTIONS.get(eventKeyCode), mLastFocusedViewId, eventKeyAction, context);
      } else {
        dispatchEvent(KEY_EVENTS_ACTIONS.get(eventKeyCode), mLastFocusedViewId, eventKeyAction, context);
      }
      mLastKeyDownTime = 0;
    }
  }

  // Android TV: Only send key up actions, unless key down events are enabled
  private boolean shouldDispatchEvent(int eventKeyCode, int eventKeyAction) {
    return KEY_EVENTS_ACTIONS.containsKey(eventKeyCode) && (
      (eventKeyAction == KeyEvent.ACTION_UP) ||
      (eventKeyAction == KeyEvent.ACTION_DOWN && ReactFeatureFlags.enableKeyDownEvents)
    );
  }

  /** Called from {@link com.facebook.react.ReactRootView} when focused view changes. */
  public void onFocusChanged(View newFocusedView, ReactContext context) {
    if (mLastFocusedViewId == newFocusedView.getId()) {
      return;
    }
    if (mLastFocusedViewId != View.NO_ID) {
      dispatchEvent("blur", mLastFocusedViewId, context);
    }
    mLastFocusedViewId = newFocusedView.getId();
    dispatchEvent("focus", newFocusedView.getId(), context);
  }

  /** Called from {@link com.facebook.react.ReactRootView} when the whole view hierarchy looses focus. */
  public void clearFocus(ReactContext context) {
    if (mLastFocusedViewId != View.NO_ID) {
      dispatchEvent("blur", mLastFocusedViewId, context);
    }
    mLastFocusedViewId = View.NO_ID;
  }

  private void dispatchEvent(String eventType, int targetViewId, ReactContext context) {
    dispatchEvent(eventType, targetViewId, -1, context);
  }

  private void dispatchEvent(String eventType, int targetViewId, int eventKeyAction, ReactContext context) {
    WritableMap event = new WritableNativeMap();
    event.putString("eventType", eventType);
    event.putInt("eventKeyAction", eventKeyAction);
    if (targetViewId != View.NO_ID) {
      event.putInt("tag", targetViewId);
      event.putInt("target", targetViewId);
    }
    emitNamedEvent("onHWKeyEvent", event, context);
  }

  public void emitNamedEvent(String eventName, WritableMap event, ReactContext context) {
    if (context != null) {
      context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, event);
    }
  }

}
