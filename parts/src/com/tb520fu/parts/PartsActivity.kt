/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class PartsActivity :
    CollapsingToolbarBaseActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    PartsFragment(),
                    TAG,
                )
                .commit()
        }
    }

    /**
     * Sub screens opened by a preference go into the content frame and onto
     * the fragment back stack, like the app's own transitions. The library's
     * fallback would use the activity's content root instead and lose the
     * toolbar.
     */
    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val name = pref.fragment ?: return false
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, name)
        fragment.arguments = pref.extras
        supportFragmentManager.beginTransaction()
            .replace(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                fragment,
            )
            .addToBackStack(null)
            .commit()
        return true
    }

    /**
     * Back pops one level at a time: sub screen, then main screen, and only
     * the main screen leaves the app. The base activity finishes as soon as
     * the fragment back stack is empty, which skipped the main screen.
     */
    override fun onNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate()
            return true
        }
        return super.onNavigateUp()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate()
            return
        }
        super.onBackPressed()
    }

    private companion object {
        const val TAG = "PartsActivity"
    }
}
