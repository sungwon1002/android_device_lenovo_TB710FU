/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class PreferenceCategory extends PreferenceGroup {
    public PreferenceCategory(Context context) {
        super(context);
    }

    public PreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
}
