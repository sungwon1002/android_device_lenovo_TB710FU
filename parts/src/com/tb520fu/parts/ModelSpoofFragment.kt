/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Per-app Build.MODEL spoofing. The package -> model map is stored in
 * Settings.Secure "per_app_model_spoof" as "pkg=model;pkg2=model2" and applied
 * by the framework (PixelPropsUtils) at each app's startup, so the chosen apps
 * report the given model. Apps are restarted to pick up changes.
 */
class ModelSpoofFragment : SettingsBasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.model_spoof_title)
    }

    private fun rebuild() {
        val ctx = requireContext()
        val screen = preferenceScreen
        screen.removeAll()

        screen.addPreference(Preference(ctx).apply {
            title = getString(R.string.model_spoof_add)
            setIcon(R.drawable.ic_add)
            isPersistent = false
            setOnPreferenceClickListener { addEntry(); true }
        })

        val map = read(ctx)
        if (map.isEmpty()) return
        val cat = PreferenceCategory(ctx).apply {
            key = "entries"
            title = getString(R.string.model_spoof_entries)
        }
        screen.addPreference(cat)
        val pm = ctx.packageManager
        map.toSortedMap().forEach { (pkg, model) ->
            val label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm) }.getOrDefault(pkg)
            cat.addPreference(Preference(ctx).apply {
                title = label
                summary = model
                isPersistent = false
                setOnPreferenceClickListener { editEntry(pkg); true }
            })
        }
    }

    private fun addEntry() {
        AppPicker.pick(this, getString(R.string.model_spoof_add), read(requireContext()).keys) { pkg ->
            askModel(pkg, "")
        }
    }

    private fun editEntry(pkg: String) {
        val ctx = requireContext()
        val label = runCatching {
            ctx.packageManager.getApplicationInfo(pkg, 0).loadLabel(ctx.packageManager)
        }.getOrDefault(pkg)
        AlertDialog.Builder(ctx)
            .setTitle(label)
            .setItems(arrayOf(getString(R.string.model_spoof_edit),
                    getString(R.string.model_spoof_remove))) { _, which ->
                if (which == 0) askModel(pkg, read(ctx)[pkg] ?: "")
                else { val m = read(ctx); m.remove(pkg); write(ctx, m); rebuild() }
            }
            .show()
    }

    private fun askModel(pkg: String, current: String) {
        val ctx = requireContext()
        val edit = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.model_spoof_hint)
            setText(current)
            setSelection(text.length)
        }
        val pad = resources.getDimensionPixelSize(R.dimen.app_picker_vertical_padding) * 3
        val frame = FrameLayout(ctx).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(edit)
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.model_spoof_set)
            .setView(frame)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val model = edit.text.toString().trim()
                val m = read(ctx)
                if (model.isEmpty()) m.remove(pkg) else m[pkg] = model
                write(ctx, m)
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        // Matches Settings.Secure.PER_APP_MODEL_SPOOF (@Readable so the
        // framework hook can read it from every app process).
        private const val KEY = "per_app_model_spoof"

        private fun read(ctx: Context): LinkedHashMap<String, String> {
            val map = LinkedHashMap<String, String>()
            val raw = Settings.Secure.getString(ctx.contentResolver, KEY)
            if (!raw.isNullOrEmpty()) for (e in raw.split(";")) {
                val eq = e.indexOf('=')
                if (eq in 1 until e.length - 1) {
                    map[e.substring(0, eq).trim()] = e.substring(eq + 1).trim()
                }
            }
            return map
        }

        private fun write(ctx: Context, map: Map<String, String>) {
            val raw = map.entries.joinToString(";") { "${it.key}=${it.value}" }
            Settings.Secure.putString(ctx.contentResolver, KEY, raw)
        }
    }
}
