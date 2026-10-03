/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package zui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CompoundButton;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class Switch extends CompoundButton {
    public Switch(Context context) {
        super(context);
    }

    public Switch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Switch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
