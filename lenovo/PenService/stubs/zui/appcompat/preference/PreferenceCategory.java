/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package zui.appcompat.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class PreferenceCategory extends androidx.preference.PreferenceCategory {
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

    public boolean isCardStyle() {
        throw new RuntimeException("stub");
    }

    @Override
    public void onAttached() {}

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {}

    @Override
    public boolean removePreference(androidx.preference.Preference preference) {
        throw new RuntimeException("stub");
    }

    public void setActivated(boolean activated) {}

    @Override
    public void setLayoutResource(int layoutResId) {}

    public void setPreferencePadding(int horizontal, int vertical) {}

    public void updateContent() {}
}
