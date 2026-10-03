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
public class Preference extends androidx.preference.Preference {
    public Preference(Context context) {
        super(context);
    }

    public Preference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Preference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public Preference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {}

    public void setActivated(boolean activated) {}

    public void setBackGround(Drawable drawable) {}

    public void setPreferencePadding(int horizontal, int vertical) {}
}
