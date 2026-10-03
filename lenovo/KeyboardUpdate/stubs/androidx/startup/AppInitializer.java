/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.startup;

import android.content.Context;

/** Compile-time stand-in; the real class comes from the updater dex. */
public final class AppInitializer {
    public static AppInitializer getInstance(Context context) {
        throw new RuntimeException("stub");
    }

    void discoverAndInitialize() {}
}
