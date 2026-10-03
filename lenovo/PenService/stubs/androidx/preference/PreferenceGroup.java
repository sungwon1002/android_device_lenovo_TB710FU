/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public abstract class PreferenceGroup extends Preference {
    public PreferenceGroup(Context context) {
        super(context);
    }

    public PreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public int getPreferenceCount() {
        throw new RuntimeException("stub");
    }

    public Preference getPreference(int index) {
        throw new RuntimeException("stub");
    }

    public Preference findPreference(CharSequence key) {
        throw new RuntimeException("stub");
    }

    public boolean addPreference(Preference preference) {
        throw new RuntimeException("stub");
    }

    public boolean removePreference(Preference preference) {
        throw new RuntimeException("stub");
    }

    public boolean removePreferenceRecursively(CharSequence key) {
        throw new RuntimeException("stub");
    }

    public void setInitialExpandedChildrenCount(int expandedCount) {}

    public boolean isAttached() {
        throw new RuntimeException("stub");
    }

    public void onAttached() {}

    public void onDetached() {}

    protected boolean isOnSameScreenAsChildren() {
        throw new RuntimeException("stub");
    }
}
