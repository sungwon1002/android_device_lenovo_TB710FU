/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.preference;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class PreferenceViewHolder extends RecyclerView.ViewHolder {
    public PreferenceViewHolder(View itemView) {
        super(itemView);
    }

    public static PreferenceViewHolder createInstanceForTests(View itemView) {
        throw new RuntimeException("stub");
    }

    public View findViewById(int id) {
        throw new RuntimeException("stub");
    }
}
