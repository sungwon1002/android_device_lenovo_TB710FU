/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class SwitchPreference extends TwoStatePreference {
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

    protected void performClick(View view) {}

    public CharSequence getSwitchTextOn() {
        throw new RuntimeException("stub");
    }

    public CharSequence getSwitchTextOff() {
        throw new RuntimeException("stub");
    }

    public void setSwitchTextOn(CharSequence text) {}

    public void setSwitchTextOn(int resId) {}

    public void setSwitchTextOff(CharSequence text) {}

    public void setSwitchTextOff(int resId) {}
}
