/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ServiceManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal clients for the stock Lenovo vendor AIDL HALs. Only the calls the
 * bridge needs are implemented, as raw transactions (codes from the stock
 * framework.jar stubs), so nothing has to be generated from the vendor AIDL.
 */
final class LenovoHal {
    private static final String TAG = "TB520FUInput";

    static final String BATTERY = "vendor.lenovo.hardware.battery.IBattery";
    static final String TOUCH = "vendor.lenovo.hardware.touchscreen.ITouchscreen";
    static final String KEYBOARD = "vendor.lenovo.hardware.keyboard.IKeyboard";

    // IBattery
    private static final int BAT_IS_MAINTENANCE_ENABLED = 3;
    private static final int BAT_SET_CHARGE_DISABLED = 4;
    private static final int BAT_SET_MAINTENANCE_ENABLED = 5;
    private static final int BAT_SET_PROTECTED_LEVEL = 12;
    private static final int BAT_SET_MAX_CHARGING_LEVEL = 14;
    private static final int BAT_SET_RECHARGING_PERCENT = 16;
    private static final int BAT_SET_STYLUS_QI_COMMAND = 25;

    // ITouchscreen
    private static final int TS_SET_BIG_PALM = 7;
    private static final int TS_SET_DOUBLE_GESTURE = 8;
    private static final int TS_SET_EDGE_INHIBITION = 10;
    private static final int TS_SET_PEN_MODE = 11;
    private static final int TS_SET_PEN_BLE = 12;
    private static final int TS_IOCTL = 13;
    private static final int TS_SET_HAPTICS = 14;
    private static final int TS_GET_HAPTICS = 15;

    // IKeyboard
    private static final int KB_SET_KEYBOARD_STATUS = 1;
    private static final int KB_OPEN = 2;
    private static final int KB_READ = 3;
    private static final int KB_READ_DATA = 4;
    private static final int KB_WRITE_DATA = 5;
    private static final int KB_CLOSE = 6;
    private static final int KB_GETFEATURE = 7;
    private static final int KB_SETFEATURE = 8;
    private static final int KB_GETRAWNAME = 9;
    private static final int KB_GETRAWINFO = 10;

    // ITouchscreen.ioctl commands
    static final int IOCTL_QUICK_NOTE = 1;
    static final int IOCTL_ERASER = 2;

    private static final Map<String, IBinder> sBinders = new HashMap<>();

    private interface Args {
        void write(Parcel p);
    }

    private LenovoHal() {}

    private static synchronized IBinder binder(String descriptor) {
        IBinder b = sBinders.get(descriptor);
        if (b == null || !b.isBinderAlive()) {
            b = ServiceManager.checkService(descriptor + "/default");
            if (b != null) {
                sBinders.put(descriptor, b);
            } else {
                sBinders.remove(descriptor);
            }
        }
        return b;
    }

    static boolean available(String descriptor) {
        return binder(descriptor) != null;
    }

    /** Returns the reply positioned after the exception header, or null. */
    private static Parcel call(String descriptor, int code, Args args) {
        IBinder b = binder(descriptor);
        if (b == null) {
            Log.w(TAG, descriptor + " not available (code " + code + ")");
            return null;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            if (args != null) args.write(data);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply;
        } catch (Exception e) {
            Log.w(TAG, descriptor + " call " + code + " failed", e);
            reply.recycle();
            return null;
        } finally {
            data.recycle();
        }
    }

    private static boolean callBool(String d, int code, Args args) {
        Parcel r = call(d, code, args);
        if (r == null) return false;
        try {
            return r.readInt() != 0;
        } finally {
            r.recycle();
        }
    }

    private static int callInt(String d, int code, Args args, int def) {
        Parcel r = call(d, code, args);
        if (r == null) return def;
        try {
            return r.readInt();
        } finally {
            r.recycle();
        }
    }

    private static byte[] callBytes(String d, int code, Args args) {
        Parcel r = call(d, code, args);
        if (r == null) return null;
        try {
            return r.createByteArray();
        } finally {
            r.recycle();
        }
    }

    private static String callString(String d, int code, Args args) {
        Parcel r = call(d, code, args);
        if (r == null) return null;
        try {
            return r.readString();
        } finally {
            r.recycle();
        }
    }

    // ---- battery ----

    static boolean setBatteryProtectedLevel(int value) {
        return callBool(BATTERY, BAT_SET_PROTECTED_LEVEL, p -> p.writeInt(value));
    }

    static boolean setMaxBatteryChargingLevel(int value) {
        return callBool(BATTERY, BAT_SET_MAX_CHARGING_LEVEL, p -> p.writeInt(value));
    }

    static boolean setBatteryRechargingPercent(int value) {
        return callBool(BATTERY, BAT_SET_RECHARGING_PERCENT, p -> p.writeInt(value));
    }

    /** Stops charging the battery; the charger keeps powering the device (bypass). */
    static boolean setBatteryChargeDisabled(boolean disable) {
        return callBool(BATTERY, BAT_SET_CHARGE_DISABLED, p -> p.writeInt(disable ? 1 : 0));
    }

    static boolean setBatteryMaintenanceEnabled(boolean enable) {
        return callBool(BATTERY, BAT_SET_MAINTENANCE_ENABLED, p -> p.writeInt(enable ? 1 : 0));
    }

    static boolean isBatteryMaintenanceEnabled() {
        return callBool(BATTERY, BAT_IS_MAINTENANCE_ENABLED, null);
    }

    static boolean setStylusQiCommand(int cmd) {
        return callBool(BATTERY, BAT_SET_STYLUS_QI_COMMAND, p -> p.writeInt(cmd));
    }

    // ---- touchscreen ----

    /** Double tap on the panel wakes the screen (NVT reports KEY_WAKEUP). */
    static boolean setDoubleGesture(boolean enable) {
        return callBool(TOUCH, TS_SET_DOUBLE_GESTURE, p -> p.writeInt(enable ? 1 : 0));
    }

    /** Palm rejection profile: 0 normal, 1 writing (reject palms), 2 game. */
    static boolean setBigPalm(int mode) {
        return callBool(TOUCH, TS_SET_BIG_PALM, p -> p.writeInt(mode));
    }

    /** Display rotation (Surface.ROTATION_*), for the edge rejection of the panel. */
    static boolean setEdgeInhibition(int rotation) {
        return callBool(TOUCH, TS_SET_EDGE_INHIBITION, p -> p.writeInt(rotation));
    }

    static boolean setPenMode(boolean enable) {
        return callBool(TOUCH, TS_SET_PEN_MODE, p -> p.writeInt(enable ? 1 : 0));
    }

    static boolean setPenBle(boolean enable) {
        return callBool(TOUCH, TS_SET_PEN_BLE, p -> p.writeInt(enable ? 1 : 0));
    }

    static boolean setHaptics(int mode) {
        return callBool(TOUCH, TS_SET_HAPTICS, p -> p.writeInt(mode));
    }

    static int getHaptics() {
        return callInt(TOUCH, TS_GET_HAPTICS, null, 0);
    }

    static String ioctl(int cmd, boolean enable) {
        return callString(TOUCH, TS_IOCTL, p -> {
            p.writeInt(cmd);
            p.writeString(enable ? "1" : "0");
        });
    }

    // ---- keyboard ----

    /** target: 1 fn-lock led, 2 speaker mute led, 3 mic mute led, 4 backlight level,
     *  6 touchpad tap-to-wake, 7 backlight after sleep. */
    static boolean setKeyboardStatus(int target, int op, int data) {
        return callBool(KEYBOARD, KB_SET_KEYBOARD_STATUS, p -> {
            p.writeInt(target);
            p.writeInt(op);
            p.writeInt(data);
        });
    }

    // ---- raw keyboard access (lenovokeyboard service, firmware updater) ----

    static int kbOpen(String path) {
        return callInt(KEYBOARD, KB_OPEN, p -> p.writeString(path), -4);
    }

    static String kbRead(int fd, int size) {
        return callString(KEYBOARD, KB_READ, p -> {
            p.writeInt(fd);
            p.writeInt(size);
        });
    }

    static byte[] kbReadData(int fd, int size) {
        return callBytes(KEYBOARD, KB_READ_DATA, p -> {
            p.writeInt(fd);
            p.writeInt(size);
        });
    }

    static int kbWriteData(int fd, int size, byte[] data) {
        return callInt(KEYBOARD, KB_WRITE_DATA, p -> {
            p.writeInt(fd);
            p.writeInt(size);
            p.writeByteArray(data);
        }, 0);
    }

    static void kbClose(int fd) {
        Parcel r = call(KEYBOARD, KB_CLOSE, p -> p.writeInt(fd));
        if (r != null) r.recycle();
    }

    static byte[] kbGetFeature(int fd, int size, byte[] data) {
        return callBytes(KEYBOARD, KB_GETFEATURE, p -> {
            p.writeInt(fd);
            p.writeInt(size);
            p.writeByteArray(data);
        });
    }

    static int kbSetFeature(int fd, byte[] data, int size) {
        return callInt(KEYBOARD, KB_SETFEATURE, p -> {
            p.writeInt(fd);
            p.writeByteArray(data);
            p.writeInt(size);
        }, 0);
    }

    static String kbGetRawName(int fd) {
        return callString(KEYBOARD, KB_GETRAWNAME, p -> p.writeInt(fd));
    }

    static byte[] kbGetRawInfo(int fd) {
        return callBytes(KEYBOARD, KB_GETRAWINFO, p -> p.writeInt(fd));
    }
}
