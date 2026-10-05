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
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;

/**
 * Folio case mode, ported from the stock ZuiPhoneWindowManager: closing the
 * folio cover turns the screen off and locks, opening it turns the screen
 * back on.
 *
 * TB710FU: the cover magnet is seen by hall_detect.ko (dt node hall_switch,
 * qcom,hall_detect, hall,gpio_irq1). Its "hall_irq" input device reports
 * scan code 252 (HALL_NEAR, cover close) and 253 (HALL_FAR, cover away) as
 * key presses; stock maps them to the ZUI keycodes 751/750 that
 * ZuiPhoneWindowManager turns into lid switch events. AOSP has no such
 * keycodes, so without a key layout they arrive as KEYCODE_UNKNOWN with the
 * scan code and are matched by device name and scan code here. The ADSP of
 * TB710FU has no hall effect sensor driver (unlike TB520FU, which uses
 * qti.sensor.hall_effect); that path is kept as a fallback.
 *
 * As on stock, a near event alone is not trusted: the ambient light sensor
 * has to confirm that the screen is covered (at most
 * config_light_sensor_min_value, 20 lux, on stock) before the screen goes
 * off, so a magnet near the back or a keyboard folded behind the tablet does
 * not turn the screen off.
 *
 * Stock setting: Settings.System zui_lid_enable, unset means on.
 */
final class FolioCover {
    private static final String TAG = "TB520FUFolio";

    static final String SETTING = "zui_lid_enable";
    private static final int TYPE_HALL = 33171002;
    private static final String HALL_DEVICE = "hall_irq";
    private static final int SCAN_NEAR = 252;
    private static final int SCAN_FAR = 253;
    private static final float COVERED_MAX_LUX = 20f;

    private final Context mContext;
    private final Handler mHandler;
    private SensorManager mSensors;
    private Sensor mLight;
    private boolean mLightRegistered;
    /**
     * The cover was closed on a dark screen: either the cover turned it off,
     * or it was already off (power key, double tap to sleep, timeout) when
     * the cover was closed. Opening the cover then wakes the screen.
     */
    private boolean mWakeOnOpen;

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

    /** Called on the input policy thread; must stay cheap. */
    boolean handle(KeyEvent event) {
        int scan = event.getScanCode();
        if (scan != SCAN_NEAR && scan != SCAN_FAR) return false;
        InputDevice device = event.getDevice();
        if (device == null || !HALL_DEVICE.equals(device.getName())) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            boolean away = scan == SCAN_FAR;
            Safe.post(mHandler, "folio hall key", () -> onHall(away));
        }
        return true;
    }

    void start() {
        mSensors = mContext.getSystemService(SensorManager.class);
        // The wake-up variant also reports while the screen is off.
        Sensor hall = mSensors.getDefaultSensor(TYPE_HALL, true);
        mLight = mSensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (hall == null) {
            Log.i(TAG, "no hall effect sensor, using " + HALL_DEVICE + " keys");
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
            if (mWakeOnOpen && enabled() && !pm.isInteractive()) {
                pm.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_LID,
                        "tb520fu:folio");
            }
            mWakeOnOpen = false;
            return;
        }
        if (!enabled()) return;
        if (!pm.isInteractive()) {
            // Closed on a screen that is already off: opening wakes it.
            mWakeOnOpen = true;
            return;
        }
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
        mWakeOnOpen = true;
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
