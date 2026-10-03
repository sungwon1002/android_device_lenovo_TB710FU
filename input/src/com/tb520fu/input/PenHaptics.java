/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Writing haptics of the Lenovo Tab Pen Pro, ported from the stock
 * ZuiPenHapticService / ZuiPenHapticPolicy / ZuiPenHapticUtils.
 *
 * The motor sits in the pen: the tablet only tells the pen which waveform to
 * play while its tip is pressed ("continuous" haptic) or asks for a one shot
 * ("impact"). Stock arms the continuous waveform when the stylus hovers over
 * or touches an app listed in pen_haptic_packages, which PenService's
 * haptic settings maintain.
 */
final class PenHaptics {
    private static final String TAG = "TB520FUHaptics";

    // Settings.Global keys written by PenService's haptic settings
    static final String PEN_HAPTIC_FEEDBACK = "pen_haptic_feedback";
    static final String PEN_HAPTIC_SOUND = "pen_haptic_sound";
    static final String PEN_HAPTIC_BRUSH = "pen_haptic_brush";
    static final String PEN_HAPTIC_LEVEL = "pen_haptic_level";
    static final String PEN_HAPTIC_PACKAGES = "pen_haptic_packages";
    static final String PEN_HAPTIC_USED = "pen_haptic_use_first";
    static final String PEN_HOVER_HAPTIC = "pen_hover_haptic";

    static final String PKG_PENSERVICE = "com.lenovo.penservice";
    private static final String PKG_WPS = "cn.wps.moffice";

    // Waveform ids
    static final int WAVE_STOP = 0;
    static final int WAVE_BALLPEN = 32;
    static final int WAVE_ERASER = 35;
    static final int WAVE_ERASER_NS = 40;

    private static final int TOOL_TYPE_STYLUS = MotionEvent.TOOL_TYPE_STYLUS;
    private static final int TOOL_TYPE_ERASER = MotionEvent.TOOL_TYPE_ERASER;

    private final Context mContext;
    private final Handler mHandler;

    // settings
    private boolean mFeedbackOn = true;
    private boolean mSoundOn = true;
    private int mBrush = WAVE_BALLPEN;
    private int mLevel = 5;
    private String mPackages;
    private boolean mHoverMode = true;
    private boolean mNeedNotifyUsed;

    // pen state
    private PenGatt mGatt;
    private int mPenType;
    private boolean mTpReady;

    // haptic state
    private int mCurrentId = -1;
    private int mCurrentLevel = -1;
    private int mToolType;
    private boolean mArmed;

    PenHaptics(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        ContentResolver cr = mContext.getContentResolver();
        ContentObserver observer = Safe.observer(mHandler, "haptic settings", uri -> {
            boolean wasOn = mFeedbackOn;
            readSettings();
            if (wasOn != mFeedbackOn) applyToggle();
        });
        for (String key : new String[] {PEN_HAPTIC_FEEDBACK, PEN_HAPTIC_SOUND, PEN_HAPTIC_BRUSH,
                PEN_HAPTIC_LEVEL, PEN_HAPTIC_PACKAGES, PEN_HOVER_HAPTIC}) {
            cr.registerContentObserver(Settings.Global.getUriFor(key), false, observer);
        }
        readSettings();
    }

    private void readSettings() {
        ContentResolver cr = mContext.getContentResolver();
        mFeedbackOn = Settings.Global.getInt(cr, PEN_HAPTIC_FEEDBACK, 1) != 0;
        mSoundOn = Settings.Global.getInt(cr, PEN_HAPTIC_SOUND, 1) == 1;
        mBrush = Settings.Global.getInt(cr, PEN_HAPTIC_BRUSH, WAVE_BALLPEN);
        mLevel = Settings.Global.getInt(cr, PEN_HAPTIC_LEVEL, 5);
        mPackages = Settings.Global.getString(cr, PEN_HAPTIC_PACKAGES);
        mHoverMode = Settings.Global.getInt(cr, PEN_HOVER_HAPTIC, 1) != 0;
        mNeedNotifyUsed = Settings.Global.getInt(cr, PEN_HAPTIC_USED, 0) != 1;
        // A setting change invalidates the armed waveform.
        mCurrentId = -1;
        mCurrentLevel = -1;
        mArmed = false;
    }

    // ---- pen connection (PenController, on mHandler) ----

    void onGattReady(PenGatt gatt, int penType) {
        mGatt = gatt;
        mPenType = penType;
        mCurrentId = -1;
        mArmed = false;
        if (!gatt.has(PenGatt.HAPTIC_SERVICE, PenGatt.HAPTIC_SWITCH)) {
            Log.d(TAG, "pen has no haptic service");
            return;
        }
        gatt.setNotify(PenGatt.HAPTIC_SERVICE, PenGatt.HAPTIC_INFO_NOTIFY, true);
        gatt.write(PenGatt.HAPTIC_SERVICE, PenGatt.HAPTIC_REQ_INFO, new byte[] {1});
        applyToggle();
    }

    void onPenDisconnected() {
        mGatt = null;
        mArmed = false;
        mCurrentId = -1;
        setTpHaptics(false);
    }

    private void applyToggle() {
        setTpHaptics(mFeedbackOn && mGatt != null);
        if (mGatt != null && mGatt.isReady()) {
            mGatt.write(PenGatt.HAPTIC_SERVICE, PenGatt.HAPTIC_SWITCH,
                    new byte[] {(byte) (mFeedbackOn ? 1 : 0)}, "haptic switch");
        }
        mCurrentId = -1;
        mArmed = false;
    }

    private void setTpHaptics(boolean on) {
        int want = on ? 1 : 0;
        int cur = LenovoHal.getHaptics();
        if (cur != want) LenovoHal.setHaptics(want);
        mTpReady = LenovoHal.getHaptics() != 0;
        Log.d(TAG, "tp haptics " + cur + " -> " + want + ", ready " + mTpReady);
    }

    boolean isReady() {
        return mFeedbackOn && mTpReady && mGatt != null && mGatt.isReady()
                && mGatt.has(PenGatt.HAPTIC_SERVICE, PenGatt.HAPTIC_CONTINUOUS);
    }

    // ---- package list ----

    boolean isHapticPackage(String pkg) {
        if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(mPackages)) return false;
        if (pkg.contains(PKG_WPS)) return mPackages.contains(PKG_WPS);
        for (String p : mPackages.split("[;,]")) {
            if (pkg.equals(p.trim())) return true;
        }
        return false;
    }

    // ---- stylus events (StylusMonitor, on mHandler) ----

    void onStylusEvent(int action, int toolType, float distance, String pkg) {
        mToolType = toolType;
        if (!isReady()) return;
        boolean match = isHapticPackage(pkg);
        if (mHoverMode) {
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_DOWN) {
                if (!match) {
                    if (mArmed) {
                        stop(WAVE_STOP);
                        mArmed = false;
                    }
                    return;
                }
                if (!mArmed || distance > 30f) {
                    setContinuous(mBrush, mLevel, 1);
                    mArmed = true;
                }
            } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
                if (match && distance > 65f) {
                    stop(WAVE_STOP);
                    mArmed = false;
                }
            }
        } else {
            if (action == MotionEvent.ACTION_DOWN && match) {
                setContinuous(mBrush, mLevel, 1);
            } else if (action == MotionEvent.ACTION_UP && match) {
                stop(WAVE_STOP);
            }
        }
    }

    // ---- haptic commands (also used by HapticBinder) ----

    static int noSoundId(int id) {
        return id >= 32 && id <= 36 ? id + 5 : id;
    }

    synchronized boolean setContinuous(int id, int level, int friction) {
        if (!isReady()) return false;
        if (mToolType == TOOL_TYPE_ERASER) {
            id = mSoundOn ? WAVE_ERASER : WAVE_ERASER_NS;
        } else if (!mSoundOn) {
            id = noSoundId(id);
        }
        if (id < 32 || id > 41 || level < 0 || level > 5 || (friction != 0 && friction != 1)) {
            Log.d(TAG, "continuous params invalid " + id + "/" + level + "/" + friction);
            return false;
        }
        notifyUsed();
        if (id == mCurrentId && level == mCurrentLevel) return true;
        mCurrentId = id;
        mCurrentLevel = level;
        byte extra = 0;
        if (mPenType == PenController.TYPE_PARKER_2 && mToolType == TOOL_TYPE_STYLUS) extra = 1;
        mGatt.write(PenGatt.HAPTIC_SERVICE, PenGatt.HAPTIC_CONTINUOUS,
                new byte[] {(byte) id, (byte) level, (byte) friction, extra}, "haptic continuous");
        return true;
    }

    synchronized boolean stop(int id) {
        mCurrentId = id;
        mCurrentLevel = 0;
        if (mGatt == null || !mGatt.isReady()) return false;
        mGatt.write(PenGatt.HAPTIC_SERVICE, PenGatt.HAPTIC_CONTINUOUS, new byte[] {0, 0, 0, 0},
                "haptic continuous");
        return true;
    }

    synchronized boolean impact(int id, int level, int repeat, int cutoffMs) {
        if (!mFeedbackOn || mGatt == null || !mGatt.isReady()) return false;
        boolean valid = id == 42 || (id >= 1 && id <= 7 && level >= 0 && level <= 5
                && repeat >= 1 && repeat <= 10 && cutoffMs >= 0 && cutoffMs <= 300);
        if (!valid) return false;
        mGatt.write(PenGatt.HAPTIC_SERVICE, PenGatt.HAPTIC_IMPACT, new byte[] {(byte) id,
                (byte) level, (byte) (repeat & 0xff), (byte) ((repeat >> 8) & 0xff), 0, 0});
        return true;
    }

    boolean isFeedbackOn() {
        return mFeedbackOn;
    }

    /** Adds or removes a package from pen_haptic_packages (IZuiPenHapticManager.setUseHaptic). */
    void setUseHaptic(String pkg, boolean use) {
        if (TextUtils.isEmpty(pkg) || mPackages == null) return;
        String list = mPackages;
        if (use && !isHapticPackage(pkg)) {
            list = list.isEmpty() ? pkg : list + ";" + pkg;
        } else if (!use && isHapticPackage(pkg)) {
            StringBuilder sb = new StringBuilder();
            for (String p : list.split(";")) {
                if (p.isEmpty() || p.equals(pkg)) continue;
                if (sb.length() > 0) sb.append(';');
                sb.append(p);
            }
            list = sb.toString();
        }
        Settings.Global.putString(mContext.getContentResolver(), PEN_HAPTIC_PACKAGES, list);
    }

    private void notifyUsed() {
        if (!mNeedNotifyUsed) return;
        mNeedNotifyUsed = false;
        Settings.Global.putInt(mContext.getContentResolver(), PEN_HAPTIC_USED, 1);
    }
}
