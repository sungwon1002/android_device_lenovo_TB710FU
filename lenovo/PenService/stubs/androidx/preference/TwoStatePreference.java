/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.preference;

import android.content.Context;
import android.util.AttributeSet;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public abstract class TwoStatePreference extends Preference {
    public TwoStatePreference(Context context) {
        super(context);
    }

    public TwoStatePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TwoStatePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public boolean isChecked() {
        throw new RuntimeException("stub");
    }

    public void setChecked(boolean checked) {}

    protected void onClick() {}
}
