/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.util.ScreenshotHelper;
import com.android.internal.util.ScreenshotRequest;

/**
 * Handles the "screenshot" entry of the stock Lenovo stylus toolbox.
 *
 * <p>The toolbox (com.lenovo.penservice, kept as a prebuilt) broadcasts
 * {@code lenovo.styluspen.action.PEN_OPERATION} with {@code type=0} and
 * {@code source="styluspen"}. Stock ZUI picked that up inside services.jar and
 * forwarded it to ScreenshotHelper; here the same happens from system_server.
 *
 * <p>On Android 17 the framework helper talks to SystemUI's
 * TakeScreenshotService itself, so no zui-specific Messenger plumbing is
 * needed. {@link WindowManager#TAKE_SCREENSHOT_SELECTED_REGION} makes SystemUI
 * start the region selection UI, which is what the toolbox item offers.
 */
final class PenScreenshot {
    private static final String TAG = "TB520FUPenScreenshot";

    private static final String ACTION_PEN_OPERATION = "lenovo.styluspen.action.PEN_OPERATION";
    private static final String EXTRA_SOURCE = "source";
    private static final String EXTRA_TYPE = "type";
    private static final String SOURCE_STYLUSPEN = "styluspen";

    /** type 0 == screenshot; other values belong to toolbox items we do not handle. */
    private static final int TYPE_SCREENSHOT = 0;

    /**
     * The toolbox window is being torn down while the broadcast is in flight;
     * give it a moment so the region selector does not appear under a stale
     * floating window (stock waited 200 ms in its handler).
     */
    private static final long TOOLBOX_DISMISS_DELAY_MS = 300L;

    private final Context mContext;
    private final Handler mHandler;
    private final ScreenshotHelper mScreenshotHelper;

    PenScreenshot(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mScreenshotHelper = new ScreenshotHelper(context);
    }

    void start() {
        IntentFilter filter = new IntentFilter(ACTION_PEN_OPERATION);
        mContext.registerReceiver(Safe.receiver("pen operation", (c, i) -> onOperation(i)),
                filter, null, mHandler, Context.RECEIVER_EXPORTED);
        Log.i(TAG, "listening for " + ACTION_PEN_OPERATION);
    }

    private void onOperation(Intent intent) {
        if (intent == null) return;

        String source = intent.getStringExtra(EXTRA_SOURCE);
        if (source != null && !SOURCE_STYLUSPEN.equals(source)) {
            // Some other producer reusing the same action.
            return;
        }
        if (!intent.hasExtra(EXTRA_TYPE)) return;

        int type = intent.getIntExtra(EXTRA_TYPE, -1);
        if (type != TYPE_SCREENSHOT) {
            Log.i(TAG, "ignoring stylus operation type " + type);
            return;
        }

        Log.i(TAG, "stylus screenshot requested");
        Safe.postDelayed(mHandler, "stylus screenshot", this::takeScreenshot,
                TOOLBOX_DISMISS_DELAY_MS);
    }

    private void takeScreenshot() {
        ScreenshotRequest request = new ScreenshotRequest.Builder(
                WindowManager.TAKE_SCREENSHOT_SELECTED_REGION,
                WindowManager.ScreenshotSource.SCREENSHOT_OTHER).build();
        mScreenshotHelper.takeScreenshot(request, mHandler, null);
        Log.i(TAG, "screenshot request handed to " + "TakeScreenshotService");
    }
}
