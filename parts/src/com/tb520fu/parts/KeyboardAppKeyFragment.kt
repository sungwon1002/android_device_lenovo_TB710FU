/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.FrameLayout
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

/**
 * Action of the APP1 / APP2 key, like the stock AppFunctionOptionsFragment:
 * launch an app, open a web page, or one of the system actions.
 */
class KeyboardAppKeyFragment : SettingsBasePreferenceFragment() {

    private val index get() = requireArguments().getInt(ARG_INDEX)
    private val setting get() = settingFor(index)
    private val prefs = mutableListOf<Pair<SelectorWithWidgetPreference, String>>()
    private lateinit var launchPref: SelectorWithWidgetPreference
    private lateinit var urlPref: SelectorWithWidgetPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx)
        val category = PreferenceCategory(ctx).apply { key = "actions" }
        preferenceScreen.addPreference(category)

        launchPref = selector(ctx, "launch",
            getString(R.string.lkb_keyboard_app_shortcut_option_launch_app_or_shortcut)).apply {
            setOnClickListener { pickApp() }
        }
        category.addPreference(launchPref)
        urlPref = selector(ctx, "url", getString(R.string.lkb_keyboard_app_shortcut_option_open_url))
            .apply { setOnClickListener { askUrl() } }
        category.addPreference(urlPref)

        val entries = resources.getStringArray(R.array.app_key_entries)
        val values = resources.getStringArray(R.array.app_key_values)
        values.forEachIndexed { i, value ->
            val pref = selector(ctx, "action_$value", entries[i]).apply {
                setOnClickListener {
                    LenovoKeyboard.putString(requireContext(), setting, value)
                    updateChecked()
                }
            }
            category.addPreference(pref)
            prefs += pref to value
        }
    }

    private fun selector(ctx: Context, key: String, title: CharSequence) =
        SelectorWithWidgetPreference(ctx).apply {
            this.key = key
            this.title = title
            isPersistent = false
        }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(if (index == 1) R.string.lkb_keyboard_shortcut_application_key_app1
            else R.string.lkb_keyboard_shortcut_application_key_app2)
        updateChecked()
    }

    private fun updateChecked() {
        val ctx = requireContext()
        val action = current(ctx, index)
        prefs.forEach { (pref, value) -> pref.isChecked = value == action }
        launchPref.isChecked = action.startsWith(LenovoKeyboard.ACTION_LAUNCH)
        launchPref.summary = if (launchPref.isChecked) describe(ctx, index)
            else getString(R.string.lkb_keyboard_app_shortcut_option_launch_app_or_shortcut_summary)
        urlPref.isChecked = action.startsWith(LenovoKeyboard.ACTION_URL)
        urlPref.summary = if (urlPref.isChecked) action.removePrefix(LenovoKeyboard.ACTION_URL)
            else getString(R.string.lkb_keyboard_app_shortcut_option_open_url_summary)
    }

    private fun pickApp() {
        AppPicker.pick(this, getString(R.string.lkb_keyboard_app_shortcut_option_launch_app_or_shortcut),
            emptySet()) { pkg ->
            LenovoKeyboard.putString(requireContext(), setting, LenovoKeyboard.ACTION_LAUNCH + pkg)
            updateChecked()
        }
    }

    private fun askUrl() {
        val ctx = requireContext()
        val current = current(ctx, index)
        val edit = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.lkb_keyboard_app_shortcut_option_set_url_hint)
            setText(current.removePrefix(LenovoKeyboard.ACTION_URL)
                .takeIf { current.startsWith(LenovoKeyboard.ACTION_URL) } ?: "https://")
            setSelection(text.length)
        }
        val pad = resources.getDimensionPixelSize(R.dimen.app_picker_vertical_padding) * 3
        val frame = FrameLayout(ctx).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(edit)
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.lkb_keyboard_app_shortcut_option_set_url)
            .setView(frame)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                var url = edit.text.toString().trim()
                if (!url.contains("://")) url = "https://$url"
                if (Patterns.WEB_URL.matcher(url).matches()) {
                    LenovoKeyboard.putString(requireContext(), setting, LenovoKeyboard.ACTION_URL + url)
                    updateChecked()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val ARG_INDEX = "index"

        fun newInstance(index: Int) = KeyboardAppKeyFragment().apply {
            arguments = Bundle().apply { putInt(ARG_INDEX, index) }
        }

        private fun settingFor(index: Int) = if (index == 1) LenovoKeyboard.APP1 else LenovoKeyboard.APP2

        private fun current(ctx: Context, index: Int): String =
            LenovoKeyboard.getString(ctx, settingFor(index)).takeUnless { it.isNullOrEmpty() }
                ?: if (index == 1) LenovoKeyboard.DEFAULT_APP1 else LenovoKeyboard.DEFAULT_APP2

        /** Summary of the APP key action for the keyboard page. */
        fun describe(ctx: Context, index: Int): CharSequence {
            val action = current(ctx, index)
            if (action.startsWith(LenovoKeyboard.ACTION_LAUNCH)) {
                val pkg = action.removePrefix(LenovoKeyboard.ACTION_LAUNCH)
                val pm = ctx.packageManager
                return runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm) }.getOrDefault(pkg)
            }
            if (action.startsWith(LenovoKeyboard.ACTION_URL)) {
                return action.removePrefix(LenovoKeyboard.ACTION_URL)
            }
            val values = ctx.resources.getStringArray(R.array.app_key_values)
            val entries = ctx.resources.getStringArray(R.array.app_key_entries)
            return entries.getOrNull(values.indexOf(action)) ?: action
        }
    }
}
