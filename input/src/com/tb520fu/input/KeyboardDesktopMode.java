/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.InputDevice;

/**
 * Keyboard desktop mode, like the stock Lenovo PC mode:
 *
 * With a keyboard and touchpad attached Android switches the screen to
 * desktop windowing (WM Shell DesktopDisplayModeController, see
 * frameworks_base-0004). The user can turn that off in the Lenovo keyboard
 * settings ("System mode when a keyboard is connected", stock key
 * enter_work_mode_from_keyboard) or leave it for the current attachment with
 * the button of an ongoing notification (stock: exit button in the taskbar).
 * Leaving is remembered until the keyboard is detached.
 */
final class KeyboardDesktopMode {
    private static final String TAG = "TB520FUDesktop";

    /** Settings.System, stock: 0 tablet mode, 1 PC mode (unset: tablet mode, like stock ZUI). */
    private static final String SYSTEM_MODE = "enter_work_mode_from_keyboard";
    /** Settings.Global, read by DesktopDisplayModeController. */
    private static final String EXITED = "tb520fu_keyboard_desktop_mode_exited";

    private static final String ACTION_EXIT = "com.tb520fu.input.EXIT_KEYBOARD_DESKTOP_MODE";
    private static final String CHANNEL = "keyboard_desktop_mode";
    private static final int NOTIFICATION_ID = 0x7b520;
    private static final String PARTS = "com.tb520fu.parts";

    private final Context mContext;
    private final Handler mHandler;
    private boolean mAttached;

    private final Runnable mUpdate = Safe.run("desktop mode update", this::update);

    KeyboardDesktopMode(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        mContext.getSystemService(InputManager.class).registerInputDeviceListener(
                new InputManager.InputDeviceListener() {
                    @Override
                    public void onInputDeviceAdded(int deviceId) {
                        schedule();
                    }

                    @Override
                    public void onInputDeviceRemoved(int deviceId) {
                        schedule();
                    }

                    @Override
                    public void onInputDeviceChanged(int deviceId) {
                        schedule();
                    }
                }, mHandler);

        ContentResolver cr = mContext.getContentResolver();
        ContentObserver modeObserver = Safe.observer(mHandler, "desktop mode setting", uri -> {
            // Picking a mode again starts over for the current attachment.
            setExited(false);
            update();
        });
        cr.registerContentObserver(Settings.System.getUriFor(SYSTEM_MODE), false, modeObserver,
                UserHandle.USER_ALL);
        cr.registerContentObserver(Settings.Global.getUriFor(EXITED), false,
                Safe.observer(mHandler, "desktop mode exited", uri -> update()));

        mContext.registerReceiver(Safe.receiver("desktop mode exit", (c, i) -> {
            setExited(true);
            update();
        }), new IntentFilter(ACTION_EXIT), null, mHandler, Context.RECEIVER_NOT_EXPORTED);

        // A stale "left" state must not survive a reboot without keyboard.
        mAttached = keyboardAndTouchpad();
        if (!mAttached) setExited(false);
        update();
    }

    private void schedule() {
        mHandler.removeCallbacks(mUpdate);
        mHandler.postDelayed(mUpdate, 300);
    }

    /** Same condition as DesktopDisplayModeController (full keyboard + touchpad). */
    private boolean keyboardAndTouchpad() {
        boolean keyboard = false;
        boolean touchpad = false;
        InputManager im = mContext.getSystemService(InputManager.class);
        for (int id : im.getInputDeviceIds()) {
            InputDevice d = im.getInputDevice(id);
            if (d == null || !d.isEnabled()) continue;
            if (!d.isVirtual() && d.isFullKeyboard()) keyboard = true;
            if (d.supportsSource(InputDevice.SOURCE_TOUCHPAD)) touchpad = true;
        }
        return keyboard && touchpad;
    }

    private boolean isExited() {
        return Settings.Global.getInt(mContext.getContentResolver(), EXITED, 0) != 0;
    }

    private void setExited(boolean exited) {
        if (isExited() != exited) {
            Settings.Global.putInt(mContext.getContentResolver(), EXITED, exited ? 1 : 0);
        }
    }

    private void update() {
        boolean attached = keyboardAndTouchpad();
        if (mAttached && !attached) setExited(false);
        mAttached = attached;
        boolean pcMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                SYSTEM_MODE, 0, UserHandle.USER_CURRENT) != 0;
        if (attached && pcMode && !isExited()) {
            showNotification();
        } else {
            mContext.getSystemService(NotificationManager.class).cancelAsUser(null,
                    NOTIFICATION_ID, UserHandle.ALL);
        }
    }

    /** Strings of the stock Lenovo settings, shipped (translated) in TB520FUParts. */
    private String partsString(String name, String fallback) {
        try {
            Resources res = mContext.getPackageManager().getResourcesForApplication(PARTS);
            int id = res.getIdentifier(name, "string", PARTS);
            if (id != 0) return res.getString(id);
        } catch (Exception e) {
            Log.w(TAG, "string " + name, e);
        }
        return fallback;
    }

    private void showNotification() {
        String pcMode = partsString("lkb_pc_mode_settings_title_name", "PC mode");
        String tabletMode = partsString("lkb_screen_cast_phone_mode_title", "Tablet mode");
        String summary = partsString("lkb_pc_mode_keyboard_connect_system_mode",
                "System mode when a keyboard is connected");

        NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, pcMode,
                NotificationManager.IMPORTANCE_LOW));

        PendingIntent exit = PendingIntent.getBroadcast(mContext, 0,
                new Intent(ACTION_EXIT).setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent settings = new Intent().setClassName(PARTS, PARTS + ".PartsActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivityAsUser(mContext, 0, settings,
                PendingIntent.FLAG_IMMUTABLE, null, UserHandle.CURRENT);

        Notification n = new Notification.Builder(mContext, CHANNEL)
                .setSmallIcon(com.android.internal.R.drawable.ic_settings_language)
                .setContentTitle(pcMode)
                .setContentText(summary)
                .setOngoing(true)
                .setLocalOnly(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, tabletMode, exit).build())
                .build();
        nm.notifyAsUser(null, NOTIFICATION_ID, n, UserHandle.ALL);
    }
}
