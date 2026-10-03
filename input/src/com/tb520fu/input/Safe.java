/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

/** Wrappers that keep exceptions out of system_server threads. */
public final class Safe {
    public static final String TAG = "TB520FUInput";

    private Safe() {}

    public static Runnable run(String what, Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Throwable t) {
                Log.e(TAG, what, t);
            }
        };
    }

    public static void post(Handler h, String what, Runnable r) {
        h.post(run(what, r));
    }

    public static void postDelayed(Handler h, String what, Runnable r, long delayMs) {
        h.postDelayed(run(what, r), delayMs);
    }

    public interface IntentCallback {
        void onReceive(Context context, Intent intent);
    }

    public static BroadcastReceiver receiver(String what, IntentCallback cb) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    cb.onReceive(context, intent);
                } catch (Throwable t) {
                    Log.e(TAG, what + " " + intent, t);
                }
            }
        };
    }

    public interface UriCallback {
        void onChange(Uri uri);
    }

    public static ContentObserver observer(Handler h, String what, UriCallback cb) {
        return new ContentObserver(h) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                try {
                    cb.onChange(uri);
                } catch (Throwable t) {
                    Log.e(TAG, what + " " + uri, t);
                }
            }
        };
    }
}
