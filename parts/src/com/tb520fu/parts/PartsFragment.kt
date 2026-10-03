/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.app.AlertDialog
import android.content.Intent
import android.hardware.display.ColorDisplayManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

class PartsFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    private lateinit var vramPref: ListPreference
    private lateinit var statusPref: Preference
    private lateinit var chargingPrefs: List<SelectorWithWidgetPreference>
    private lateinit var bypassPref: SwitchPreferenceCompat
    private lateinit var standbyPref: SwitchPreferenceCompat
    private lateinit var maintenancePref: SwitchPreferenceCompat
    private lateinit var batteryInfoPref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.parts_settings, rootKey)
        val ctx = requireContext()

        // Charging mode: tb520fu_battery_mode 0 normal, 1 stop at 80%, 2 protection 40-60%
        chargingPrefs = CHARGING_KEYS.map { findPreference<SelectorWithWidgetPreference>(it)!! }
        chargingPrefs.forEachIndexed { mode, pref ->
            pref.setOnClickListener {
                LenovoSettings.putInt(ctx, LenovoSettings.BATTERY_MODE, mode)
                updateChargingMode()
            }
        }

        bypassPref = switch(KEY_BYPASS, LenovoSettings.BYPASS_CHARGING, 0)
        standbyPref = switch(KEY_STANDBY, LenovoSettings.STANDBY_SAVER, 1)
        maintenancePref = switch(KEY_MAINTENANCE, LenovoSettings.BATTERY_MAINTENANCE, 1)
        batteryInfoPref = findPreference(KEY_BATTERY_INFO)!!

        // Folio case mode (stock Settings.System key, per user), applied by
        // tb520fu-input (FolioCover) when the hall sensor reports the cover.
        findPreference<SwitchPreferenceCompat>(KEY_FOLIO)!!.apply {
            isChecked = LenovoKeyboard.getInt(ctx, LenovoKeyboard.FOLIO_CASE_MODE, 1) != 0
            setOnPreferenceChangeListener { _, value ->
                LenovoKeyboard.putInt(requireContext(), LenovoKeyboard.FOLIO_CASE_MODE,
                    if (value as Boolean) 1 else 0)
                true
            }
        }

        // Adaptive white balance (the Settings > Display switch is hidden) and how
        // strongly it follows the ambient light (patches/frameworks_base-0002)
        findPreference<SwitchPreferenceCompat>(KEY_WHITE_BALANCE)!!.apply {
            // Unset means the framework default (config_displayWhiteBalanceEnabledDefault)
            isChecked = ctx.getSystemService(ColorDisplayManager::class.java)!!
                .isDisplayWhiteBalanceEnabled
            setOnPreferenceChangeListener { _, newValue ->
                Settings.Secure.putInt(requireContext().contentResolver,
                    LenovoSettings.DISPLAY_WHITE_BALANCE, if (newValue as Boolean) 1 else 0)
                true
            }
        }
        val strengthPref: SeekBarPreference = findPreference(KEY_WHITE_BALANCE_STRENGTH)!!
        // The strength setting is only read by the patched ColorDisplayService
        // (patches/frameworks_base-0002); on a ROM without it the slider would
        // store a value nothing reads.
        strengthPref.isVisible = FrameworkPatches.wbStrength
        strengthPref.apply {
            value = Settings.Secure.getInt(ctx.contentResolver,
                LenovoSettings.WHITE_BALANCE_STRENGTH, LenovoSettings.DEFAULT_WHITE_BALANCE_STRENGTH)
            setOnPreferenceChangeListener { _, newValue ->
                Settings.Secure.putInt(requireContext().contentResolver,
                    LenovoSettings.WHITE_BALANCE_STRENGTH, newValue as Int)
                true
            }
        }

        val penPref: Preference = findPreference(KEY_PEN_SETTINGS)!!
        // No own task, so the stock pen page opens in the Settings two-pane
        // right pane like the other sub pages. An overlay
        // (PenServiceResTB710FU) shrinks its pen picture and battery row to
        // fit the narrow pane.
        val penIntent = Intent(LenovoSettings.ACTION_PEN_SETTINGS)
            .setClassName(LenovoSettings.PEN_PACKAGE, LenovoSettings.PEN_SETTINGS_ACTIVITY)
        if (ctx.packageManager.resolveActivity(penIntent, 0) != null) {
            penPref.intent = penIntent
        } else {
            findPreference<PreferenceCategory>(KEY_PEN_CATEGORY)?.isVisible = false
        }

        vramPref = findPreference(KEY_VRAM)!!
        vramPref.entries = MemoryExtension.SIZES_GB.map { sizeLabel(it) }.toTypedArray()
        vramPref.entryValues = MemoryExtension.SIZES_GB.map { it.toString() }.toTypedArray()
        vramPref.value = MemoryExtension.selectedGb.toString()
        vramPref.onPreferenceChangeListener = this

        statusPref = findPreference(KEY_STATUS)!!
    }

    /** A switch backed by a Settings.Global int of [LenovoSettings]. */
    private fun switch(key: String, setting: String, def: Int): SwitchPreferenceCompat {
        val pref: SwitchPreferenceCompat = findPreference(key)!!
        pref.isChecked = LenovoSettings.getInt(requireContext(), setting, def) != 0
        pref.setOnPreferenceChangeListener { _, value ->
            LenovoSettings.putInt(requireContext(), setting, if (value as Boolean) 1 else 0)
            true
        }
        return pref
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.app_name)
        val ctx = requireContext()
        updateChargingMode()
        updateBatteryInfo()
        updateVramSummary()
        updateStatus()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when (preference) {
            vramPref -> {
                val gb = (newValue as String).toInt()
                MemoryExtension.selectedGb = gb
                vramPref.value = newValue
                updateVramSummary()
                if (gb != MemoryExtension.activeGb) askReboot(R.string.reboot_message)
            }
            else -> return false
        }
        return true
    }

    private fun updateChargingMode() {
        val mode = LenovoSettings.getInt(requireContext(), LenovoSettings.BATTERY_MODE, 0)
            .coerceIn(0, chargingPrefs.size - 1)
        chargingPrefs.forEachIndexed { i, pref -> pref.isChecked = i == mode }
    }

    private fun updateBatteryInfo() {
        val info = LenovoSettings.readBatteryInfo(requireContext())
        val cycles = info.cycles?.toString() ?: "-"
        val health = info.healthPercent?.let { "$it%" } ?: "-"
        batteryInfoPref.summary = getString(R.string.battery_info_summary, cycles, health)
    }

    private fun sizeLabel(gb: Int) =
        if (gb == 0) getString(R.string.vram_off) else getString(R.string.vram_size, gb)

    private fun updateVramSummary() {
        val selected = MemoryExtension.selectedGb
        val active = MemoryExtension.activeGb
        vramPref.summary = if (active == null || active == selected) {
            getString(R.string.vram_summary, sizeLabel(selected))
        } else {
            getString(R.string.vram_summary_pending, sizeLabel(selected), sizeLabel(active))
        }
    }

    private fun updateStatus() {
        val status = MemoryExtension.readStatus() ?: return
        val ctx = requireContext()
        val ram = Formatter.formatShortFileSize(ctx, status.ramBytes)
        val used = Formatter.formatShortFileSize(
            ctx,
            status.ramBytes - status.availableBytes + status.writebackBytes,
        )
        val active = MemoryExtension.activeGb ?: 0
        statusPref.summary = if (active > 0) {
            getString(R.string.memory_status_summary, ram, sizeLabel(active), used)
        } else {
            getString(R.string.memory_status_summary_off, ram, used)
        }
    }

    private fun askReboot(message: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reboot_title)
            .setMessage(message)
            .setPositiveButton(R.string.reboot_now) { _, _ ->
                requireContext().getSystemService(PowerManager::class.java).reboot(null)
            }
            .setNegativeButton(R.string.reboot_later, null)
            .show()
    }

    private companion object {
        val CHARGING_KEYS = listOf("charging_normal", "charging_limit", "charging_protect")
        const val KEY_BYPASS = "bypass_charging"
        const val KEY_STANDBY = "standby_saver"
        const val KEY_MAINTENANCE = "battery_maintenance"
        const val KEY_BATTERY_INFO = "battery_info"
        const val KEY_FOLIO = "folio_case_mode"
        const val KEY_WHITE_BALANCE = "white_balance"
        const val KEY_WHITE_BALANCE_STRENGTH = "white_balance_strength"
        const val KEY_PEN_CATEGORY = "pen"
        const val KEY_PEN_SETTINGS = "pen_settings"
        const val KEY_VRAM = "vram_gb"
        const val KEY_STATUS = "memory_status"
    }
}
