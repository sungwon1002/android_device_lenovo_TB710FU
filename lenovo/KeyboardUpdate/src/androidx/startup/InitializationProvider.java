/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.startup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.tb520fu.keyboardupdate.PixelStyle;

/**
 * Replacement of the stock androidx.startup.InitializationProvider of the
 * Lenovo keyboard updaters (ZuiKeyboardUpdate, ZuiKeyboardUpdateOlympia).
 * It runs the stock initializers exactly as before and additionally registers
 * PixelStyle, which gives the firmware update page the PixelOS Settings look.
 * The provider is the first app code that runs in the process, before any
 * activity, and both updaters declare it.
 */
public final class InitializationProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) {
            AppInitializer.getInstance(context).discoverAndInitialize();
            PixelStyle.install(context);
            return true;
        }
        throw new StartupException("Context cannot be null");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        throw new IllegalStateException("Not allowed.");
    }

    @Override
    public String getType(Uri uri) {
        throw new IllegalStateException("Not allowed.");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new IllegalStateException("Not allowed.");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new IllegalStateException("Not allowed.");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new IllegalStateException("Not allowed.");
    }
}
