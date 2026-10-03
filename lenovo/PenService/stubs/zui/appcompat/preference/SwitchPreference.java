/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package zui.appcompat.preference;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class SwitchPreference extends androidx.preference.SwitchPreference {
    public SwitchPreference(Context context) {
        super(context);
    }

    public SwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {}

    public void setActivated(boolean activated) {}

    public void setBackGround(Drawable drawable) {}

    @Override
    public void setChecked(boolean checked) {}

    public void setChecked(boolean checked, boolean animate) {}

    public void setPreferencePadding(int horizontal, int vertical) {}
}
