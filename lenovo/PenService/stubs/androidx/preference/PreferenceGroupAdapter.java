/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.preference;

import androidx.recyclerview.widget.RecyclerView;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class PreferenceGroupAdapter extends RecyclerView.Adapter<PreferenceViewHolder> {
    @Override
    public int getItemCount() {
        throw new RuntimeException("stub");
    }

    public Preference getItem(int position) {
        throw new RuntimeException("stub");
    }
}
