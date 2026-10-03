/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

/**
 * "zuinotification" service (zui.notification.INotificationManager). ZUI
 * SystemUI shows these as its own notification/island UI; PenService calls it
 * for the pen connect / battery notifications without a null check, so without
 * the service every pen broadcast crashed the persistent PenService process.
 * Here the calls become regular notifications posted as the calling package.
 *
 * ZUI fills in what its island UI does not need: PenService's pen connect /
 * battery popup has only a custom content view, no small icon and no channel,
 * which NotificationManager rejects. One-shot popups with a custom view are
 * shown as an island pill (PenIsland); other notifications get the app icon
 * and a high importance channel.
 */
final class ZuiNotificationBinder extends Binder {
    private static final String TAG = "TB520FUZuiNotif";
    static final String SERVICE = "zuinotification";
    static final String DESCRIPTOR = "zui.notification.INotificationManager";

    private static final int TRANSACTION_addNotification = 1;
    private static final int TRANSACTION_addPermanentNotification = 2;
    private static final int TRANSACTION_updatePermanentNotification = 3;
    private static final int TRANSACTION_cancelNotification = 4;
    private static final int TRANSACTION_cancelAllNotifications = 5;

    private static final String CHANNEL = "zui_notification";
    private static final long POPUP_TIMEOUT_MS = 5000;

    private final Context mContext;
    private final PenIsland mIsland;

    ZuiNotificationBinder(Context context, android.os.Handler handler) {
        mContext = context;
        mIsland = new PenIsland(context, handler);
        attachInterface(null, DESCRIPTOR);
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code < TRANSACTION_addNotification || code > TRANSACTION_cancelAllNotifications) {
            return super.onTransact(code, data, reply, flags);
        }
        data.enforceInterface(DESCRIPTOR);
        int uid = Binder.getCallingUid();
        String pkg = callerPackage(uid);
        long token = Binder.clearCallingIdentity();
        try {
            switch (code) {
                case TRANSACTION_addNotification:
                case TRANSACTION_addPermanentNotification:
                case TRANSACTION_updatePermanentNotification: {
                    int id = data.readInt();
                    Notification n = data.readInt() != 0
                            ? Notification.CREATOR.createFromParcel(data) : null;
                    data.readInt();
                    // One-shot popups with a custom view go to the island pill
                    // like on ZUI; anything else becomes a notification.
                    if (n != null && code == TRANSACTION_addNotification
                            && n.contentView != null && mIsland.show(n.contentView)) {
                        break;
                    }
                    if (n != null) {
                        Context c = packageContext(pkg, uid);
                        c.getSystemService(NotificationManager.class).notify(id,
                                complete(c, n, code == TRANSACTION_addNotification));
                    }
                    break;
                }
                case TRANSACTION_cancelNotification: {
                    data.readString();
                    int id = data.readInt();
                    manager(pkg, uid).cancel(id);
                    break;
                }
                case TRANSACTION_cancelAllNotifications:
                    data.readString();
                    manager(pkg, uid).cancelAll();
                    break;
            }
        } catch (Exception e) {
            // Never fail the caller: stock PenService does not handle errors here.
            Log.w(TAG, "call " + code + " from " + pkg + " failed", e);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        reply.writeNoException();
        return true;
    }

    private String callerPackage(int uid) {
        if (uid == android.os.Process.SYSTEM_UID) return PenHaptics.PKG_PENSERVICE;
        long token = Binder.clearCallingIdentity();
        try {
            String[] pkgs = mContext.getPackageManager().getPackagesForUid(uid);
            return pkgs != null && pkgs.length > 0 ? pkgs[0] : PenHaptics.PKG_PENSERVICE;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private Context packageContext(String pkg, int uid) throws PackageManager.NameNotFoundException {
        return mContext.createPackageContextAsUser(pkg, 0, UserHandle.getUserHandleForUid(uid));
    }

    private NotificationManager manager(String pkg, int uid) throws PackageManager.NameNotFoundException {
        return packageContext(pkg, uid).getSystemService(NotificationManager.class);
    }

    /** Adds the icon, channel and popup timeout that ZUI notifications leave out. */
    private static Notification complete(Context c, Notification n, boolean popup) {
        if (n.getSmallIcon() != null && n.getChannelId() != null && !popup) return n;
        Notification.Builder b = new Notification.Builder(c, n);
        if (n.getSmallIcon() == null) {
            int icon = c.getApplicationInfo().icon;
            b.setSmallIcon(icon != 0
                    ? Icon.createWithResource(c.getPackageName(), icon)
                    : Icon.createWithResource("android", android.R.drawable.stat_sys_data_bluetooth));
        }
        if (n.getChannelId() == null) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                        c.getApplicationInfo().loadLabel(c.getPackageManager()),
                        NotificationManager.IMPORTANCE_HIGH));
            }
            b.setChannelId(CHANNEL);
        }
        if (popup && n.getTimeoutAfter() == 0) b.setTimeoutAfter(POPUP_TIMEOUT_MS);
        return b.build();
    }
}
