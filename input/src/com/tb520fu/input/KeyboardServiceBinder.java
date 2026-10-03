/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Parcel;
import android.os.Process;
import android.util.Log;

/**
 * "lenovokeyboard": the service behind android.app.keyboard.LenovoKeyboardManager
 * (framework patch 0003), which the Lenovo keyboard firmware updater uses to
 * talk to the keyboard over the pogo pin serial port (/dev/ttyHS1) or hidraw.
 * Stock ZUI forwards every call to the vendor keyboard HAL, and so does this.
 *
 * Implemented as a raw Binder (transaction codes and parcel layout of the
 * stock ILenovoKeyboardService, including the inout byte arrays) so the jar
 * does not depend on the framework patch.
 */
final class KeyboardServiceBinder extends Binder {
    private static final String TAG = "TB520FUKeyboard";

    static final String SERVICE = "lenovokeyboard";
    private static final String DESCRIPTOR = "android.app.keyboard.ILenovoKeyboardService";

    private static final int KB_OPEN = 1;
    private static final int KB_READ = 2;
    private static final int KB_READ_DATA = 3;
    private static final int KB_WRITE_DATA = 4;
    private static final int KB_CLOSE = 5;
    private static final int KB_GETFEATURE = 6;
    private static final int KB_SETFEATURE = 7;
    private static final int KB_GETRAWNAME = 8;
    private static final int KB_GETRAWINFO = 9;

    // Bound allocations in system_server and reject empty arrays before the
    // stock NDK HAL unmarshals them (an empty vector can return NO_MEMORY).
    private static final int MAX_IO_SIZE = 64 * 1024;

    private static void checkFd(int fd) {
        if (fd < 0) throw new IllegalArgumentException("Invalid keyboard fd");
    }

    private static void checkRead(int fd, int size) {
        checkFd(fd);
        if (size <= 0 || size > MAX_IO_SIZE) {
            throw new IllegalArgumentException("Invalid keyboard transfer size");
        }
    }

    private static void checkBuffer(int fd, int size, byte[] buffer) {
        checkRead(fd, size);
        if (buffer == null || buffer.length == 0 || buffer.length > MAX_IO_SIZE
                || size > buffer.length) {
            throw new IllegalArgumentException("Invalid keyboard buffer");
        }
    }

    private final Context mContext;

    KeyboardServiceBinder(Context context) {
        mContext = context;
    }

    /** Only the system and apps signed with the platform key (the updater) get through. */
    private boolean callerAllowed() {
        int uid = Binder.getCallingUid();
        if (uid == Process.SYSTEM_UID || uid == Process.ROOT_UID) return true;
        final long token = Binder.clearCallingIdentity();
        try {
            return mContext.getPackageManager().checkSignatures(uid, Process.SYSTEM_UID)
                    == PackageManager.SIGNATURE_MATCH;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        if (code < KB_OPEN || code > KB_GETRAWINFO) {
            try {
                return super.onTransact(code, data, reply, flags);
            } catch (Exception e) {
                return false;
            }
        }
        data.enforceInterface(DESCRIPTOR);
        if (!callerAllowed()) {
            reply.writeException(new SecurityException(SERVICE + ": uid "
                    + Binder.getCallingUid() + " not allowed"));
            return true;
        }
        final long token = Binder.clearCallingIdentity();
        try {
            switch (code) {
                case KB_OPEN: {
                    int fd = LenovoHal.kbOpen(data.readString());
                    reply.writeNoException();
                    reply.writeInt(fd);
                    return true;
                }
                case KB_READ: {
                    int fd = data.readInt();
                    int size = data.readInt();
                    checkRead(fd, size);
                    String r = LenovoHal.kbRead(fd, size);
                    reply.writeNoException();
                    reply.writeString(r);
                    return true;
                }
                case KB_READ_DATA: {
                    int fd = data.readInt();
                    int size = data.readInt();
                    checkRead(fd, size);
                    byte[] r = LenovoHal.kbReadData(fd, size);
                    reply.writeNoException();
                    reply.writeByteArray(r != null ? r : new byte[Math.max(size, 0)]);
                    return true;
                }
                case KB_WRITE_DATA: {
                    int fd = data.readInt();
                    int size = data.readInt();
                    byte[] buf = data.createByteArray();
                    checkBuffer(fd, size, buf);
                    int r = LenovoHal.kbWriteData(fd, size, buf);
                    reply.writeNoException();
                    reply.writeInt(r);
                    reply.writeByteArray(buf);
                    return true;
                }
                case KB_CLOSE: {
                    int fd = data.readInt();
                    checkFd(fd);
                    LenovoHal.kbClose(fd);
                    reply.writeNoException();
                    return true;
                }
                case KB_GETFEATURE: {
                    int fd = data.readInt();
                    int size = data.readInt();
                    byte[] buf = data.createByteArray();
                    checkBuffer(fd, size, buf);
                    byte[] r = LenovoHal.kbGetFeature(fd, size, buf);
                    reply.writeNoException();
                    reply.writeByteArray(r != null ? r : new byte[Math.max(size, 0)]);
                    reply.writeByteArray(buf);
                    return true;
                }
                case KB_SETFEATURE: {
                    int fd = data.readInt();
                    byte[] buf = data.createByteArray();
                    int size = data.readInt();
                    checkBuffer(fd, size, buf);
                    int r = LenovoHal.kbSetFeature(fd, buf, size);
                    reply.writeNoException();
                    reply.writeInt(r);
                    reply.writeByteArray(buf);
                    return true;
                }
                case KB_GETRAWNAME: {
                    int fd = data.readInt();
                    checkFd(fd);
                    String r = LenovoHal.kbGetRawName(fd);
                    reply.writeNoException();
                    reply.writeString(r);
                    return true;
                }
                case KB_GETRAWINFO: {
                    int fd = data.readInt();
                    checkFd(fd);
                    byte[] r = LenovoHal.kbGetRawInfo(fd);
                    reply.writeNoException();
                    reply.writeByteArray(r != null ? r : new byte[8]);
                    return true;
                }
            }
        } catch (Exception | OutOfMemoryError e) {
            // Binder maps a vendor STATUS_NO_MEMORY reply to OutOfMemoryError.
            // A failed keyboard request must not terminate system_server.
            Log.e(TAG, "transaction " + code, e);
            reply.setDataPosition(0);
            reply.setDataSize(0);
            reply.writeException(new IllegalStateException(e.toString()));
            return true;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        return false;
    }
}
