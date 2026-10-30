/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Per-app "smooth video motion" (MEMC). The chosen packages are stored in
 * Settings.Secure "per_app_memc" as "pkg;pkg2" and read by the framework
 * (MediaCodec) at configure time, where it turns on the QTI VPP frame-rate
 * converter for that app's video decoders. Apps not in the list are untouched.
 */
class MemcFragment : SettingsBasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.memc_title)
    }

    private fun rebuild() {
        val ctx = requireContext()
        val screen = preferenceScreen
        screen.removeAll()

        screen.addPreference(Preference(ctx).apply {
            title = getString(R.string.memc_add)
            summary = getString(R.string.memc_summary)
            setIcon(R.drawable.ic_add)
            isPersistent = false
            setOnPreferenceClickListener { addEntry(); true }
        })

        val apps = read(ctx)
        if (apps.isEmpty()) return
        val cat = PreferenceCategory(ctx).apply {
            key = "entries"
            title = getString(R.string.memc_entries)
        }
        screen.addPreference(cat)
        val pm = ctx.packageManager
        apps.sorted().forEach { pkg ->
            val label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm) }.getOrDefault(pkg)
            cat.addPreference(Preference(ctx).apply {
                title = label
                summary = pkg
                isPersistent = false
                setOnPreferenceClickListener { remove(pkg); true }
            })
        }
    }

    private fun addEntry() {
        AppPicker.pick(this, getString(R.string.memc_add), read(requireContext())) { pkg ->
            val ctx = requireContext()
            val set = read(ctx)
            set.add(pkg)
            write(ctx, set)
            rebuild()
        }
    }

    private fun remove(pkg: String) {
        val ctx = requireContext()
        val set = read(ctx)
        set.remove(pkg)
        write(ctx, set)
        rebuild()
    }

    companion object {
        // Matches the key read by android.media.MediaCodec on this device.
        private const val KEY = "per_app_memc"

        private fun read(ctx: Context): LinkedHashSet<String> {
            val set = LinkedHashSet<String>()
            val raw = Settings.Secure.getString(ctx.contentResolver, KEY)
            if (!raw.isNullOrEmpty()) for (e in raw.split(";")) {
                val p = e.trim()
                if (p.isNotEmpty()) set.add(p)
            }
            return set
        }

        private fun write(ctx: Context, set: Set<String>) {
            Settings.Secure.putString(ctx.contentResolver, KEY, set.joinToString(";"))
        }
    }
}
