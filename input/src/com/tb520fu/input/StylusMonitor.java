/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;

import java.util.List;

/**
 * Watches stylus events on the default display and feeds PenHaptics and the
 * dynamic palm rejection.
 *
 * Stock ZUI patched ViewRootImpl to forward every stylus event of an app to
 * system_server together with the caller's package. Here a gesture monitor
 * sees the same events and the app under the pen is looked up from the
 * visible tasks, only on DOWN and HOVER_ENTER.
 */
final class StylusMonitor {
    private static final String TAG = "TB520FUStylus";

    private final Context mContext;
    private final Handler mHandler;
    private final PenHaptics mHaptics;
    private final PalmController mPalm;
    private InputMonitor mMonitor;
    private Receiver mReceiver;

    StylusMonitor(Context context, Handler handler, PenHaptics haptics, PalmController palm) {
        mContext = context;
        mHandler = handler;
        mHaptics = haptics;
        mPalm = palm;
    }

    void start() {
        InputManager im = mContext.getSystemService(InputManager.class);
        mMonitor = im.monitorGestureInput("tb520fu-pen-haptics", Display.DEFAULT_DISPLAY);
        mReceiver = new Receiver(mMonitor);
        Log.i(TAG, "monitoring stylus input");
    }

    private final class Receiver extends InputEventReceiver {
        Receiver(InputMonitor monitor) {
            super(monitor.getInputChannel(), mHandler.getLooper());
        }

        @Override
        public void onInputEvent(InputEvent event) {
            try {
                if (event instanceof MotionEvent) onMotion((MotionEvent) event);
            } catch (Throwable t) {
                Log.e(TAG, "onInputEvent", t);
            } finally {
                finishInputEvent(event, false);
            }
        }
    }

    private void onMotion(MotionEvent ev) {
        int index = ev.getActionIndex();
        int tool = ev.getToolType(index);
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return;
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) mPalm.onStylusDown();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_HOVER_ENTER: {
                float distance = ev.getAxisValue(MotionEvent.AXIS_DISTANCE, index);
                String pkg = packageAt(ev.getRawX(index), ev.getRawY(index));
                mHaptics.onStylusEvent(action, tool, distance, pkg);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_HOVER_EXIT:
                mHaptics.onStylusEvent(action, tool,
                        ev.getAxisValue(MotionEvent.AXIS_DISTANCE, index), null);
                break;
        }
    }

    /** Package of the top-most visible task containing the point. */
    private String packageAt(float x, float y) {
        try {
            List<ActivityManager.RunningTaskInfo> tasks = ActivityTaskManager.getService()
                    .getTasks(20, false, false, Display.DEFAULT_DISPLAY);
            if (tasks == null) return null;
            for (ActivityManager.RunningTaskInfo task : tasks) {
                if (!task.isVisible || task.topActivity == null) continue;
                Rect bounds = task.configuration.windowConfiguration.getBounds();
                if (bounds.contains((int) x, (int) y)) {
                    return task.topActivity.getPackageName();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "packageAt", e);
        }
        return null;
    }
}
