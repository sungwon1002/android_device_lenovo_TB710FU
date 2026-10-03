/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Dialog listing the launchable apps (icon and name), loaded off the main thread. */
object AppPicker {

    private class AppItem(val pkg: String, val label: CharSequence, val icon: Drawable)

    fun pick(fragment: Fragment, title: CharSequence, exclude: Set<String>, onPicked: (String) -> Unit) {
        val ctx = fragment.requireContext().applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val pm = ctx.packageManager
            val items = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.ResolveInfoFlags.of(0),
            )
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName !in exclude && it.packageName != ctx.packageName }
                .map { AppItem(it.packageName, it.loadLabel(pm), it.loadIcon(pm)) }
                .sortedBy { it.label.toString().lowercase() }
            main.post { if (fragment.isAdded) show(fragment, title, items, onPicked) }
        }.start()
    }

    private fun show(fragment: Fragment, title: CharSequence, items: List<AppItem>,
            onPicked: (String) -> Unit) {
        val ctx = fragment.requireContext()
        val inflater = LayoutInflater.from(ctx)
        val adapter = object : ArrayAdapter<AppItem>(ctx, R.layout.app_picker_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: inflater.inflate(R.layout.app_picker_item, parent, false)
                val item = getItem(position)!!
                view.findViewById<ImageView>(R.id.icon).setImageDrawable(item.icon)
                view.findViewById<TextView>(R.id.label).text = item.label
                return view
            }
        }
        // A ListView of its own scrolls inside the dialog body instead of
        // drawing over the title and the buttons.
        val list = ListView(ctx).apply {
            this.adapter = adapter
            divider = null
            clipToPadding = true
            val pad = resources.getDimensionPixelSize(R.dimen.app_picker_vertical_padding)
            setPadding(0, pad, 0, pad)
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(list)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, which, _ ->
            dialog.dismiss()
            onPicked(items[which].pkg)
        }
        dialog.show()
    }
}
