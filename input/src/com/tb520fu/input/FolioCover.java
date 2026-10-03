/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManagerGlobal;

/**
 * Folio case mode, ported from the stock ZuiPhoneWindowManager: closing the
 * folio cover turns the screen off and locks, opening it turns the screen
 * back on.
 *
 * The cover magnet is seen by the ROHM BU52053NVX hall sensor of the sensor
 * HAL (qti.sensor.hall_effect, type 33171002; value 1 = cover away, anything
 * else = cover near). The hall_irq input device of hall_mod.ko never reports
 * events, its two hall sensors only serve the pen charger. As on stock, a
 * near event alone is not trusted: the ambient light sensor has to confirm
 * that the screen is covered (at most config_light_sensor_min_value, 20 lux,
 * on stock) before the screen goes off, so a magnet near the back or a
 * keyboard folded behind the tablet does not turn the screen off.
 *
 * Stock setting: Settings.System zui_lid_enable, unset means on.
 */
final class FolioCover {
    private static final String TAG = "TB520FUFolio";

    static final String SETTING = "zui_lid_enable";
    private static final int TYPE_HALL = 33171002;
    private static final float COVERED_MAX_LUX = 20f;

    private final Context mContext;
    private final Handler mHandler;
    private SensorManager mSensors;
    private Sensor mLight;
    private boolean mLightRegistered;
    /** The screen was turned off by the cover; opening it wakes the screen. */
    private boolean mSleptByCover;

    private final SensorEventListener mHallListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            boolean away = (int) event.values[0] == 1;
            Safe.run("folio hall", () -> onHall(away)).run();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final SensorEventListener mLightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0];
            Safe.run("folio light", () -> onLight(lux)).run();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    FolioCover(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        mSensors = mContext.getSystemService(SensorManager.class);
        // The wake-up variant also reports while the screen is off.
        Sensor hall = mSensors.getDefaultSensor(TYPE_HALL, true);
        mLight = mSensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (hall == null) {
            Log.w(TAG, "no hall effect sensor, folio case mode unavailable");
            return;
        }
        mSensors.registerListener(mHallListener, hall, SensorManager.SENSOR_DELAY_NORMAL,
                mHandler);
        Log.i(TAG, "watching " + hall.getName());
    }

    private void onHall(boolean away) {
        Log.d(TAG, "cover " + (away ? "away" : "near"));
        PowerManager pm = mContext.getSystemService(PowerManager.class);
        if (away) {
            unregisterLight();
            if (mSleptByCover && enabled() && !pm.isInteractive()) {
                pm.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_LID,
                        "tb520fu:folio");
            }
            mSleptByCover = false;
            return;
        }
        if (!enabled() || !pm.isInteractive()) return;
        if (mLight == null) {
            coverClosed(pm);
        } else if (!mLightRegistered) {
            // Wait for the light sensor to confirm the screen is covered.
            mLightRegistered = mSensors.registerListener(mLightListener, mLight,
                    SensorManager.SENSOR_DELAY_NORMAL, mHandler);
        }
    }

    private void onLight(float lux) {
        if (lux > COVERED_MAX_LUX) return;
        unregisterLight();
        PowerManager pm = mContext.getSystemService(PowerManager.class);
        if (enabled() && pm.isInteractive()) {
            coverClosed(pm);
        }
    }

    private void coverClosed(PowerManager pm) {
        Log.i(TAG, "cover closed, screen off");
        mSleptByCover = true;
        pm.goToSleep(SystemClock.uptimeMillis(), PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH, 0);
        // Stock: "turn the screen off and lock automatically".
        try {
            WindowManagerGlobal.getWindowManagerService().lockNow(null);
        } catch (RemoteException e) {
            Log.w(TAG, "lockNow failed", e);
        }
    }

    private void unregisterLight() {
        if (mLightRegistered) {
            mSensors.unregisterListener(mLightListener);
            mLightRegistered = false;
        }
    }

    private boolean enabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(), SETTING, 1,
                UserHandle.USER_CURRENT) != 0;
    }
}
