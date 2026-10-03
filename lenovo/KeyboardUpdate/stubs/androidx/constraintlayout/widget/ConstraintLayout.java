/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.constraintlayout.widget;

import android.content.Context;
import android.view.ViewGroup;

/** Compile-time stand-in; the real class comes from the updater dex. */
public class ConstraintLayout extends ViewGroup {
    public ConstraintLayout(Context context) {
        super(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {}

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public float matchConstraintPercentWidth;

        public LayoutParams(int width, int height) {
            super(width, height);
        }
    }
}
