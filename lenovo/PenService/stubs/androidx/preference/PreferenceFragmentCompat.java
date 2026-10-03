/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.preference;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public abstract class PreferenceFragmentCompat {
    public abstract void onCreatePreferences(Bundle savedInstanceState, String rootKey);

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        throw new RuntimeException("stub");
    }

    public final RecyclerView getListView() {
        throw new RuntimeException("stub");
    }

    public void setDivider(Drawable divider) {}
}
