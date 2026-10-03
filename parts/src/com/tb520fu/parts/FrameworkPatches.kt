/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.content.res.Resources

/**
 * Marker resources of the framework patches in patches/frameworks_base-*.
 *
 * Lenovo features can be installed on a ROM built from a different tree, where
 * the framework half of a feature is missing. The UI parts that only work with
 * each patch are hidden behind these flags.
 */
object FrameworkPatches {
    /** frameworks_base-0002: Settings.Secure display_white_balance_strength. */
    val wbStrength = has("config_tb520fu_patch_wb_strength")

    /** frameworks_base-0003: LenovoKeyboardManager, read by the keyboard updater. */
    val keyboardManager = has("config_tb520fu_patch_keyboard_manager")

    /** frameworks_base-0004: keyboard desktop-mode opt-out and tb520fu_pc_mode. */
    val desktopOptOut = has("config_tb520fu_patch_desktop_opt_out")

    /** Whether the framework-res of the running ROM defines the marker bool [name]. */
    fun has(name: String) = Resources.getSystem().getIdentifier(name, "bool", "android") != 0
}
