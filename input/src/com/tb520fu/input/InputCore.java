/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.ServiceManager;
import android.util.Log;
import android.view.KeyEvent;

import dalvik.system.PathClassLoader;

import java.io.File;

/**
 * Owns the worker thread and the feature controllers. Constructed from
 * PhoneWindowManager.init(); the controllers are started once the system has
 * finished booting (LOCKED_BOOT_COMPLETED), so nothing here can slow down or
 * break early boot.
 *
 * The optional customizations repository (vendor/lenovo/TB520FU-custom) installs
 * /system_ext/framework/tb520fu-input-custom.jar with
 * com.tb520fu.input.custom.CustomInput. When that jar is present it is loaded
 * into a PathClassLoader and driven through [InputExtension] (game performance
 * enforcement); a plain build has no jar and loads nothing.
 */
public final class InputCore {
    private static final String TAG = "TB520FUInput";

    /** Optional customizations jar, from vendor/lenovo/TB520FU-custom. */
    private static final String CUSTOM_JAR = "/system_ext/framework/tb520fu-input-custom.jar";
    private static final String CUSTOM_CLASS = "com.tb520fu.input.custom.CustomInput";

    /** Name the Lenovo apps use with getSystemService() for the pen haptic manager. */
    public static final String HAPTIC_SERVICE = "zui_pen_haptic";

    private static volatile InputCore sInstance;

    final Context mContext;
    final Handler mHandler;

    final PenHaptics mHaptics;
    final PenController mPen;
    final PenKeys mPenKeys;
    final KeyboardController mKeyboard;
    final BatteryController mBattery;
    final DoubleTapWake mDoubleTapWake;
    final FolioCover mFolio;
    final StandbyController mStandby;
    final PalmController mPalm;
    final KeyboardDesktopMode mDesktopMode;
    final WifiSarController mWifiSar;
    final PenScreenshot mPenScreenshot;
    final PenMagnifier mPenMagnifier;
    private InputExtension mExtension;
    private StylusMonitor mStylusMonitor;
    private boolean mStarted;

    static void start(Context context) {
        if (sInstance == null) {
            sInstance = new InputCore(context);
        }
    }

    static InputCore get() {
        return sInstance;
    }

    private InputCore(Context context) {
        mContext = context;
        HandlerThread thread = new HandlerThread("tb520fu-input", Process.THREAD_PRIORITY_FOREGROUND);
        thread.start();
        mHandler = new Handler(thread.getLooper());

        mHaptics = new PenHaptics(context, mHandler);
        mPen = new PenController(context, mHandler, mHaptics);
        mPenKeys = new PenKeys(context, mHandler, mPen);
        mKeyboard = new KeyboardController(context, mHandler);
        mBattery = new BatteryController(context, mHandler);
        mDoubleTapWake = new DoubleTapWake(context, mHandler);
        mFolio = new FolioCover(context, mHandler);
        mStandby = new StandbyController(context, mHandler);
        mPalm = new PalmController(context, mHandler);
        mDesktopMode = new KeyboardDesktopMode(context, mHandler);
        mWifiSar = new WifiSarController(context, mHandler);
        mPenScreenshot = new PenScreenshot(context, mHandler);
        mPenMagnifier = new PenMagnifier(context, mHandler);
        mExtension = loadExtension(context, mHandler);

        // The binder can be published right away; it only answers "not ready"
        // until a pen is connected.
        Safe.post(mHandler, "publish " + HAPTIC_SERVICE, () ->
                ServiceManager.addService(HAPTIC_SERVICE, new HapticBinder(context, mHandler, mHaptics)));
        Safe.post(mHandler, "publish " + KeyboardServiceBinder.SERVICE, () ->
                ServiceManager.addService(KeyboardServiceBinder.SERVICE,
                        new KeyboardServiceBinder(context)));
        Safe.post(mHandler, "publish " + ZuiNotificationBinder.SERVICE, () ->
                ServiceManager.addService(ZuiNotificationBinder.SERVICE,
                        new ZuiNotificationBinder(context, mHandler)));

        IntentFilter filter = new IntentFilter(Intent.ACTION_LOCKED_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(Safe.receiver("boot", (c, i) -> onBoot()), filter, null, mHandler,
                Context.RECEIVER_EXPORTED);
    }

    /**
     * Loads the optional customizations jar. Nothing here may throw: a broken
     * jar must not take system_server down at boot.
     */
    private static InputExtension loadExtension(Context context, Handler handler) {
        if (!new File(CUSTOM_JAR).isFile()) {
            Log.i(TAG, "no " + CUSTOM_JAR + ", running without customizations");
            return null;
        }
        try {
            PathClassLoader loader = new PathClassLoader(CUSTOM_JAR,
                    InputCore.class.getClassLoader());
            InputExtension extension = (InputExtension) loader.loadClass(CUSTOM_CLASS)
                    .getDeclaredConstructor().newInstance();
            extension.init(context, handler);
            Log.i(TAG, "loaded " + CUSTOM_CLASS + " from " + CUSTOM_JAR);
            return extension;
        } catch (Throwable t) {
            Log.e(TAG, "failed to load " + CUSTOM_JAR, t);
            return null;
        }
    }

    private void onBoot() {
        if (mStarted) return;
        mStarted = true;
        Log.i(TAG, "starting");
        Safe.run("battery", mBattery::start).run();
        Safe.run("keyboard", mKeyboard::start).run();
        Safe.run("double tap wake", mDoubleTapWake::start).run();
        Safe.run("folio cover", mFolio::start).run();
        Safe.run("standby saver", mStandby::start).run();
        Safe.run("palm rejection", mPalm::start).run();
        Safe.run("keyboard desktop mode", mDesktopMode::start).run();
        Safe.run("wifi sar", mWifiSar::start).run();
        Safe.run("haptics", mHaptics::start).run();
        Safe.run("pen", mPen::start).run();
        Safe.run("stylus screenshot", mPenScreenshot::start).run();
        Safe.run("stylus magnifier", mPenMagnifier::start).run();
        if (mExtension != null) {
            Safe.run("custom", mExtension::start).run();
        }
        Safe.run("stylus monitor", () -> {
            mStylusMonitor = new StylusMonitor(mContext, mHandler, mHaptics, mPalm);
            mStylusMonitor.start();
        }).run();
    }

    /** Called on the input dispatcher's policy thread; must stay cheap. */
    boolean handleKey(KeyEvent event) {
        if (!mStarted) return false;
        // Folio cover keys (hall_irq scan 252/253) are mapped to SLEEP/WAKEUP
        // by keylayout/hall_irq.kl and handled natively, so they must not be
        // consumed here (consuming would stop the power keycode from reaching
        // PhoneWindowManager).
        if (mPenKeys.handle(event) || mKeyboard.handle(event)) {
            return true;
        }
        return mExtension != null && mExtension.handleKey(event);
    }
}
