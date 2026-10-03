/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lenovo.pen.cap.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/**
 * Compile-time stand-in; the real class comes from PenService own dex.
 *
 * Only the two helpers the compat classes call are declared here. The stock
 * implementation is:
 *   isSupportZuiNotes(ctx)    = DeviceUtils.isPackageEnabled(ctx, "com.zui.notes")
 *   isNotSupportZuiNotes(ctx) = !isSupportZuiNotes(ctx)
 */
public final class Features {
    public static final Features INSTANCE = new Features();

    private Features() {}

    public static boolean isSupportZuiNotes(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo("com.zui.notes", 0);
            return info.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isNotSupportZuiNotes(Context context) {
        return !isSupportZuiNotes(context);
    }
}
