/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.UserHandle
import android.provider.Settings

/**
 * Lenovo keyboard settings: the stock Settings.System keys of the ZuiSettings
 * physical keyboard page, applied by tb520fu-input in system_server
 * (input/src/com/tb520fu/input/KeyboardController.java).
 */
object LenovoKeyboard {
    const val AUTO_BACKLIGHT = "keyboard_auto_backlight"
    const val TAP_WAKE = "keyboard_touch_bright_screen"
    const val SINGLE_FINGER_TAP = "physical_keyboard_single_finger_touch"
    /** Stock "System mode when a keyboard is connected": 0 tablet, 1 PC mode. */
    const val SYSTEM_MODE = "enter_work_mode_from_keyboard"
    /** Stock "Folio case mode": the cover turns the screen off and on, 1 (unset) on. */
    const val FOLIO_CASE_MODE = "zui_lid_enable"
    const val APP1 = "keyboard_shortcut_value_app1"
    const val APP2 = "keyboard_shortcut_value_app2"

    const val DEFAULT_APP1 = "screenshot"
    const val DEFAULT_APP2 = "notifications"
    const val ACTION_LAUNCH = "launch:"
    const val ACTION_URL = "url:"

    /** Stock "Shortcuts setting" list: key, title and key combination strings. */
    data class Shortcut(val key: String, val title: Int, val keys: Int)

    val SHORTCUTS = listOf(
        Shortcut("keyboard_combo_win_d", R.string.lkb_keyboard_shortcuts_switch_title_1,
            R.string.lkb_keyboard_shortcuts_switch_summary_1),
        Shortcut("keyboard_combo_win_n", R.string.lkb_keyboard_shortcuts_switch_title_2,
            R.string.lkb_keyboard_shortcuts_switch_summary_2),
        Shortcut("keyboard_combo_win_l", R.string.lkb_keyboard_shortcuts_switch_title_3,
            R.string.lkb_keyboard_shortcuts_switch_summary_3),
        Shortcut("keyboard_combo_win_w", R.string.lkb_keyboard_shortcuts_switch_title_4,
            R.string.lkb_keyboard_shortcuts_switch_summary_4),
        Shortcut("keyboard_combo_alt_tab", R.string.lkb_keyboard_shortcuts_switch_title_5,
            R.string.lkb_keyboard_shortcuts_switch_summary_5),
        Shortcut("keyboard_combo_win_s", R.string.lkb_keyboard_shortcuts_switch_title_8,
            R.string.lkb_keyboard_shortcuts_switch_summary_7),
        Shortcut("keyboard_combo_win_a", R.string.lkb_keyboard_shortcuts_switch_title_9,
            R.string.lkb_keyboard_shortcuts_switch_summary_8),
        Shortcut("keyboard_combo_win_e", R.string.lkb_keyboard_shortcuts_switch_title_10,
            R.string.lkb_keyboard_shortcuts_switch_summary_9),
        Shortcut("keyboard_combo_win_i", R.string.lkb_keyboard_shortcuts_switch_title_11,
            R.string.lkb_keyboard_shortcuts_switch_summary_10),
        Shortcut("keyboard_combo_win_m", R.string.lkb_keyboard_shortcuts_switch_title_12,
            R.string.lkb_keyboard_shortcuts_switch_summary_11),
        Shortcut("keyboard_combo_win_back", R.string.lkb_keyboard_shortcuts_switch_title_21,
            R.string.lkb_keyboard_shortcuts_switch_summary_12),
        Shortcut("keyboard_combo_win_number", R.string.lkb_keyboard_shortcuts_switch_title_14,
            R.string.lkb_keyboard_shortcuts_switch_summary_13),
        Shortcut("keyboard_combo_lr_arrow", R.string.lkb_keyboard_shortcuts_switch_title_15,
            R.string.lkb_keyboard_shortcuts_switch_summary_14),
        Shortcut("keyboard_combo_ud_arrow", R.string.lkb_keyboard_shortcuts_switch_title_16,
            R.string.lkb_keyboard_shortcuts_switch_summary_15),
        Shortcut("keyboard_combo_alt_shift", R.string.lkb_keyboard_shortcuts_switch_title_17,
            R.string.lkb_keyboard_shortcuts_switch_summary_16),
        Shortcut("keyboard_combo_ctrl_shift", R.string.lkb_keyboard_shortcuts_switch_title_18,
            R.string.lkb_keyboard_shortcuts_switch_summary_17),
        Shortcut("keyboard_combo_ctrl_space", R.string.lkb_keyboard_shortcuts_switch_title_19,
            R.string.lkb_keyboard_shortcuts_switch_summary_18),
        Shortcut("keyboard_combo_ctrl_3", R.string.lkb_keyboard_shortcuts_helper,
            R.string.lkb_keyboard_shortcuts_switch_summary_20),
    )

    // Keyboard firmware update apps (stock ZuiKeyboardUpdate / ZuiKeyboardUpdateOlympia)
    private const val UPDATE_PACKAGE = "com.zui.keyboardupdate"
    private const val UPDATE_ACTIVITY = "com.zui.keyboardupdate.MainActivity"
    private const val UPDATE_OLYMPIA_PACKAGE = "com.zui.keyboardupdate.olympia"
    private const val UPDATE_OLYMPIA_ACTIVITY =
        "com.zui.keyboardupdate.olympia.DeviceEntryMainActivity"
    private const val VENDOR_LENOVO = 0x17ef
    private const val PRODUCT_OLYMPIA = 0x619e

    fun getInt(ctx: Context, key: String, def: Int) =
        Settings.System.getIntForUser(ctx.contentResolver, key, def, UserHandle.USER_CURRENT)

    fun putInt(ctx: Context, key: String, value: Int) =
        Settings.System.putIntForUser(ctx.contentResolver, key, value, UserHandle.USER_CURRENT)

    fun getString(ctx: Context, key: String): String? =
        Settings.System.getStringForUser(ctx.contentResolver, key, UserHandle.USER_CURRENT)

    fun putString(ctx: Context, key: String, value: String) =
        Settings.System.putStringForUser(ctx.contentResolver, key, value, UserHandle.USER_CURRENT)

    /**
     * Firmware update screen (stock PhysicalKeyboardFragment.startKeyboardUpdateActivity):
     * the Olympia updater for a connected Olympia keyboard, otherwise the pogo pin keyboard
     * updater, also with no keyboard connected (it then shows the last known version and
     * waits for the keyboard). Null when the updater is missing.
     */
    fun firmwareUpdateIntent(ctx: Context): Intent? {
        val im = ctx.getSystemService(InputManager::class.java)
        val keyboards = im.inputDeviceIds.toList().mapNotNull { im.getInputDevice(it) }
            .filter { !it.isVirtual && it.isFullKeyboard && it.vendorId == VENDOR_LENOVO }
        val olympia = keyboards.any { it.productId == PRODUCT_OLYMPIA }
        val intent = if (olympia) {
            Intent().setClassName(UPDATE_OLYMPIA_PACKAGE, UPDATE_OLYMPIA_ACTIVITY)
        } else {
            Intent().setClassName(UPDATE_PACKAGE, UPDATE_ACTIVITY)
        }
        return intent.takeIf { ctx.packageManager.resolveActivity(it, 0) != null }
    }
}
