/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screenshot;

import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.view.WindowManagerGlobal;
import android.window.ScreenCaptureInternal;

import kotlin.coroutines.Continuation;

/**
 * Replaces PenService's copy of the SystemUI screenshot helper: the stock one
 * uses android.window.ScreenCapture.CaptureArgs, which Android 17 moved to
 * ScreenCaptureInternal, and crashed the persistent PenService process with
 * NoClassDefFoundError (EasyJot screenshot notes).
 */
public final class ImageCaptureImpl implements ImageCapture {
    private static final String TAG = "PenServiceCompat";

    public static final ImageCaptureImpl INSTANCE = new ImageCaptureImpl();

    private ImageCaptureImpl() {}

    public static IActivityTaskManager access$getAtmService$p() {
        return ActivityTaskManager.getService();
    }

    @Override
    public Bitmap captureDisplay(int displayId, Rect crop) {
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            ScreenCaptureInternal.CaptureArgs args =
                    new ScreenCaptureInternal.CaptureArgs.Builder().setSourceCrop(crop).build();
            ScreenCaptureInternal.SynchronousScreenCaptureListener listener =
                    ScreenCaptureInternal.createSyncCaptureListener();
            WindowManagerGlobal.getWindowManagerService().captureDisplay(displayId, args, listener);
            ScreenCaptureInternal.ScreenshotHardwareBuffer buffer = listener.getBuffer();
            return buffer != null ? buffer.asBitmap() : null;
        } catch (Throwable t) {
            Log.w(TAG, "captureDisplay", t);
            return null;
        }
    }

    /** Task snapshots are not used by the pen features; report "no bitmap". */
    @Override
    public Object captureTask(int taskId, Continuation continuation) {
        return null;
    }
}
