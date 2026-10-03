/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Handler;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * "zui_pen_haptic" service: android.app.haptic.IZuiPenHapticManager as
 * implemented by the stock ZuiPenHapticService, hand-rolled so the jar does
 * not depend on the framework patch that adds the client class.
 */
final class HapticBinder extends Binder {
    private static final String TAG = "TB520FUHaptics";
    static final String DESCRIPTOR = "android.app.haptic.IZuiPenHapticManager";

    private static final int TRANSACTION_isHapticReady = FIRST_CALL_TRANSACTION;
    private static final int TRANSACTION_setHapticImpactParams = FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_setHapticContinuousParams = FIRST_CALL_TRANSACTION + 2;
    private static final int TRANSACTION_stopHaptic = FIRST_CALL_TRANSACTION + 3;
    private static final int TRANSACTION_setHapticSdkPackage = FIRST_CALL_TRANSACTION + 4;
    private static final int TRANSACTION_setUseHaptic = FIRST_CALL_TRANSACTION + 5;
    private static final int TRANSACTION_setEraserRect = FIRST_CALL_TRANSACTION + 6;

    private final Context mContext;
    private final PenHaptics mHaptics;
    private final Handler mHandler;

    HapticBinder(Context context, Handler handler, PenHaptics haptics) {
        mContext = context;
        mHaptics = haptics;
        mHandler = handler;
        attachInterface(null, DESCRIPTOR);
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code < TRANSACTION_isHapticReady || code > TRANSACTION_setEraserRect) {
            return super.onTransact(code, data, reply, flags);
        }
        data.enforceInterface(DESCRIPTOR);
        String caller = callerPackage();
        boolean result = false;
        switch (code) {
            case TRANSACTION_isHapticReady:
                result = onHandler(mHaptics::isReady);
                break;
            case TRANSACTION_setHapticImpactParams: {
                int id = data.readInt(), level = data.readInt();
                int repeat = data.readInt(), cutoff = data.readInt();
                result = allowed(caller) && onHandler(() -> mHaptics.impact(id, level, repeat, cutoff));
                break;
            }
            case TRANSACTION_setHapticContinuousParams: {
                int id = data.readInt(), level = data.readInt(), friction = data.readInt();
                if (allowed(caller)) {
                    result = onHandler(() -> mHaptics.setContinuous(id, level, friction));
                } else {
                    onHandler(() -> mHaptics.stop(PenHaptics.WAVE_STOP));
                }
                break;
            }
            case TRANSACTION_stopHaptic: {
                int id = data.readInt();
                result = onHandler(() -> mHaptics.stop(id));
                break;
            }
            case TRANSACTION_setHapticSdkPackage:
                data.readInt();
                break;
            case TRANSACTION_setUseHaptic: {
                boolean use = data.readInt() != 0;
                String pkg = caller;
                onHandler(() -> {
                    mHaptics.setUseHaptic(pkg, use);
                    return true;
                });
                break;
            }
            case TRANSACTION_setEraserRect:
                // Only used for the app-defined eraser areas of the Lenovo SDK.
                data.createTypedArrayList(Rect.CREATOR);
                data.createTypedArrayList(Rect.CREATOR);
                break;
        }
        reply.writeNoException();
        if (code <= TRANSACTION_stopHaptic) reply.writeInt(result ? 1 : 0);
        return true;
    }

    private String callerPackage() {
        int uid = Binder.getCallingUid();
        if (uid == Process.SYSTEM_UID) return PenHaptics.PKG_PENSERVICE;
        long token = Binder.clearCallingIdentity();
        try {
            String[] pkgs = mContext.getPackageManager().getPackagesForUid(uid);
            return pkgs != null && pkgs.length > 0 ? pkgs[0] : "";
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private boolean allowed(String pkg) {
        return PenHaptics.PKG_PENSERVICE.equals(pkg) || mHaptics.isHapticPackage(pkg);
    }

    private boolean onHandler(BooleanSupplier call) {
        if (mHandler == null) return false;
        AtomicBoolean result = new AtomicBoolean();
        long token = Binder.clearCallingIdentity();
        try {
            mHandler.runWithScissors(Safe.run("haptic binder", () -> result.set(call.getAsBoolean())),
                    500);
        } catch (Exception e) {
            Log.w(TAG, "haptic call failed", e);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return result.get();
    }
}
