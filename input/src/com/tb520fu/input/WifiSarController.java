/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.SystemProperties;
import android.util.Log;

/**
 * WLAN SAR power limits, ported from the stock com.android.server.exwifi
 * .ExWifiManagerService (lapis). ROW boards follow the Awinic SAR sensor:
 * far from the body uses power index 1 (setwlansardsi1), near or while the
 * sensor calibrates index 2 (setwlansardsi2); a board without the sensor
 * stays at index 2. PRC boards keep the firmware default, as stock.
 *
 * The vendor init services run vendor_cmd_tool; they are started through
 * sys.tb520fu.wlan_sar (init.target.rc). Unlike stock, the state is applied
 * again whenever Wi-Fi is turned on, since the firmware forgets it.
 */
final class WifiSarController {
    private static final String TAG = "TB520FUWifiSar";

    private static final String SENSOR_NAME = "aw963xx0 SAR Sensor Wakeup";
    private static final String PROP = "sys.tb520fu.wlan_sar";

    private static final int SENSOR_NEAR = 0;
    private static final int SENSOR_FAR = 1;
    private static final int SENSOR_CALIBRATION = 2;

    private static final int POWER_FAR = 1;
    private static final int POWER_NEAR = 2;

    /** Wi-Fi needs a moment after WIFI_STATE_ENABLED before wlan0 takes commands. */
    private static final long WIFI_ON_DELAY_MS = 2000;

    private final Context mContext;
    private final Handler mHandler;
    private int mSensorState = SENSOR_CALIBRATION;

    WifiSarController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        String board = SystemProperties.get("ro.boot.pcbaidinfo", "");
        if (board.toLowerCase().startsWith("prc")) {
            Log.i(TAG, "PRC board " + board + ", keeping the firmware SAR default");
            return;
        }

        SensorManager sm = mContext.getSystemService(SensorManager.class);
        Sensor sar = null;
        for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {
            if (s.getName().contains(SENSOR_NAME)) {
                sar = s;
                break;
            }
        }
        if (sar != null) {
            sm.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    int state = (int) event.values[0];
                    if (state != mSensorState) {
                        mSensorState = state;
                        Safe.run("wifi sar", WifiSarController.this::apply).run();
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            }, sar, SensorManager.SENSOR_DELAY_NORMAL, mHandler);
        } else {
            Log.w(TAG, "no SAR sensor, using the near power limits");
        }

        mContext.registerReceiver(Safe.receiver("wifi state", (c, i) -> {
            if (i.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    == WifiManager.WIFI_STATE_ENABLED) {
                Safe.postDelayed(mHandler, "wifi sar", this::apply, WIFI_ON_DELAY_MS);
            }
        }), new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION), null, mHandler,
                Context.RECEIVER_EXPORTED);
        apply();
    }

    private void apply() {
        WifiManager wm = mContext.getSystemService(WifiManager.class);
        if (wm == null || wm.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
            return;
        }
        int power = mSensorState == SENSOR_FAR ? POWER_FAR : POWER_NEAR;
        Log.i(TAG, "sensor " + mSensorState + ", power index " + power);
        SystemProperties.set(PROP, Integer.toString(power));
    }
}
