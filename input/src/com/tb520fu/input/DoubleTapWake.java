/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.app.ActivityManager;
import android.app.UserSwitchObserver;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

/**
 * Double tap to wake (stock ZuiDoubleWakeUpScreenInputPolicy): the NVT touch
 * firmware detects the double tap while the screen is off and reports
 * KEY_WAKEUP; the Lenovo touchscreen HAL switches the gesture on and off.
 * Driven by AOSP's Settings > Display > "Tap to wake"
 * (Settings.Secure.DOUBLE_TAP_TO_WAKE, config_supportDoubleTapWake).
 */
final class DoubleTapWake {
    private static final String TAG = "TB520FUDt2w";

    private final Context mContext;
    private final Handler mHandler;
    private int mRetries;

    DoubleTapWake(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DOUBLE_TAP_TO_WAKE), false,
                Safe.observer(mHandler, "dt2w setting", uri -> apply()), UserHandle.USER_ALL);
        try {
            ActivityManager.getService().registerUserSwitchObserver(new UserSwitchObserver() {
                @Override
                public void onUserSwitchComplete(int newUserId) {
                    Safe.post(mHandler, "dt2w user switch", DoubleTapWake.this::apply);
                }
            }, TAG);
        } catch (Exception e) {
            Log.w(TAG, "user switch observer", e);
        }
        apply();
    }

    private void apply() {
        boolean on = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.DOUBLE_TAP_TO_WAKE, 0, UserHandle.USER_CURRENT) != 0;
        boolean ok = LenovoHal.setDoubleGesture(on);
        Log.i(TAG, "double tap to wake " + on + (ok ? "" : " (HAL call failed)"));
        if (!ok && !LenovoHal.available(LenovoHal.TOUCH) && mRetries++ < 12) {
            // Touch HAL not up yet this early: try again shortly.
            Safe.postDelayed(mHandler, "dt2w retry", this::apply, 5000);
        }
    }
}
