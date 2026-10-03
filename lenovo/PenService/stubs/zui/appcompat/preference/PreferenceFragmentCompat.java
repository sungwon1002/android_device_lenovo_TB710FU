/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package zui.appcompat.preference;

import android.os.Bundle;

/** Compile-time stand-in; the real class comes from the PenService dex. */
public class PreferenceFragmentCompat extends androidx.preference.PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {}
}
