/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class Preference {
    public static final int DEFAULT_ORDER = Integer.MAX_VALUE;

    public Preference(Context context) {}

    public Preference(Context context, AttributeSet attrs) {}

    public Preference(Context context, AttributeSet attrs, int defStyleAttr) {}

    public Preference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {}

    public Context getContext() {
        throw new RuntimeException("stub");
    }

    public String getKey() {
        throw new RuntimeException("stub");
    }

    public PreferenceGroup getParent() {
        throw new RuntimeException("stub");
    }

    public CharSequence getTitle() {
        throw new RuntimeException("stub");
    }

    public void setTitle(CharSequence title) {}

    public Drawable getIcon() {
        throw new RuntimeException("stub");
    }

    public void setIcon(Drawable icon) {}

    public boolean isEnabled() {
        throw new RuntimeException("stub");
    }

    public void setEnabled(boolean enabled) {}

    public final boolean isVisible() {
        throw new RuntimeException("stub");
    }

    public final void setVisible(boolean visible) {}

    public final boolean isShown() {
        throw new RuntimeException("stub");
    }

    public final int getLayoutResource() {
        throw new RuntimeException("stub");
    }

    public void setLayoutResource(int layoutResId) {}

    public void onAttached() {}

    public void onDetached() {}

    public void onBindViewHolder(PreferenceViewHolder holder) {}

    protected void notifyChanged() {}

    protected void onSetInitialValue(Object defaultValue) {}

    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {}

    protected TypedArray obtainStyledAttributes(AttributeSet attrs) {
        throw new RuntimeException("stub");
    }
}
