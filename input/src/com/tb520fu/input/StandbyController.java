/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

/**
 * Standby saver, the counterpart of the PRC "夜间省电" (night power saving,
 * com.zui.server.power.ZuiNightPowerSave). ZUI restricts alarms and jobs of
 * idle apps through its own framework hooks; the AOSP mechanism for that is
 * Doze, so this makes the device reach deep Doze within minutes of the screen
 * turning off instead of after the default hour-long sensing phases.
 *
 * Settings.Global tb520fu_standby_saver (TB520FUParts, on by default) toggles
 * Settings.Global.DEVICE_IDLE_CONSTANTS, which DeviceIdleController reads
 * with priority over DeviceConfig. A previous user value is not touched
 * unless it was written by this class.
 */
final class StandbyController {
    private static final String TAG = "TB520FUStandby";

    static final String SETTING = "tb520fu_standby_saver";

    /** Marker so we only ever remove constants that we wrote. */
    private static final String MARKER = "tb520fu=1";
    private static final String CONSTANTS = MARKER
            // light doze shortly after the screen goes off
            + ",light_after_inactive_to=30000"
            + ",light_idle_to=600000"
            // skip the motion/location sensing phases of deep doze
            + ",inactive_to=120000"
            + ",sensing_to=0"
            + ",locating_to=0"
            + ",motion_inactive_to=0"
            + ",idle_after_inactive_to=0"
            // keep deep idle windows long, with short maintenance
            + ",idle_pending_to=60000"
            + ",idle_to=3600000"
            + ",max_idle_to=21600000";

    private final Context mContext;
    private final Handler mHandler;

    StandbyController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(SETTING),
                false, Safe.observer(mHandler, "standby setting", uri -> apply()));
        apply();
    }

    private void apply() {
        ContentResolver cr = mContext.getContentResolver();
        boolean on = Settings.Global.getInt(cr, SETTING, 1) != 0;
        String current = Settings.Global.getString(cr, Settings.Global.DEVICE_IDLE_CONSTANTS);
        boolean ours = current != null && current.startsWith(MARKER);
        if (on && !CONSTANTS.equals(current)) {
            if (current != null && !ours) {
                Log.w(TAG, "replacing user device_idle_constants: " + current);
            }
            Settings.Global.putString(cr, Settings.Global.DEVICE_IDLE_CONSTANTS, CONSTANTS);
        } else if (!on && ours) {
            Settings.Global.putString(cr, Settings.Global.DEVICE_IDLE_CONSTANTS, null);
        }
        Log.i(TAG, "standby saver " + on);
    }
}
