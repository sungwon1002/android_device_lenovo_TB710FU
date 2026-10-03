/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.os.Bundle
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/** Stock "Shortcuts setting": one switch per Lenovo keyboard shortcut, all on by default. */
class KeyboardShortcutsFragment : SettingsBasePreferenceFragment() {

    private val prefs = mutableListOf<Pair<SwitchPreferenceCompat, String>>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx)
        LenovoKeyboard.SHORTCUTS.forEach { shortcut ->
            val pref = SwitchPreferenceCompat(ctx).apply {
                key = shortcut.key
                title = getString(shortcut.title)
                summary = getString(shortcut.keys)
                isPersistent = false
                setOnPreferenceChangeListener { _, value ->
                    LenovoKeyboard.putInt(requireContext(), shortcut.key, if (value as Boolean) 1 else 0)
                    true
                }
            }
            preferenceScreen.addPreference(pref)
            prefs += pref to shortcut.key
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.lkb_keyboard_shortcuts_switch)
        prefs.forEach { (pref, key) ->
            pref.isChecked = LenovoKeyboard.getInt(requireContext(), key, 1) != 0
        }
    }
}
