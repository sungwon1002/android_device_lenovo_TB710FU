/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lenovo.pen.cap.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.lenovo.pen.cap.util.Features;

/**
 * Faithful replacement of the stock
 * com.lenovo.pen.cap.widget.ClickableWhenDisabledSwitchPreference (built from
 * ClickableWhenDisabledSwitchPreference.kt) that additionally hides itself when
 * ZUI Notes is missing.
 *
 * Only three stock preferences use this class and all three are Notes features:
 *   pref_key_quick_create_note, pref_key_screen_off_quick_create_note,
 *   pref_key_parker_quick_note
 * so gating on those keys leaves every other user of the class alone.
 *
 * androidx.preference.Preference.setVisible(boolean) is PUBLIC FINAL in the
 * bundled dex, so onAttached() (PUBLIC, non-final) is the hook: it runs for
 * every child of every PreferenceGroup from PreferenceScreen.onAttached() in
 * PreferenceFragmentCompat.bindPreferences(), i.e. while the adapter is being
 * built and before the rows are laid out. No stock code ever calls
 * setVisible(true) on these three keys, so the hide cannot be undone.
 */
public final class ClickableWhenDisabledSwitchPreference
        extends zui.appcompat.preference.SwitchPreference {

    private static final String KEY_QUICK_CREATE_NOTE = "pref_key_quick_create_note";
    private static final String KEY_SCREEN_OFF_QUICK_CREATE_NOTE =
            "pref_key_screen_off_quick_create_note";
    private static final String KEY_PARKER_QUICK_NOTE = "pref_key_parker_quick_note";

    private boolean clickableWhenDisabled;
    private View itemView;
    private OnClickWhenDisabledListener onClickWhenDisabledListener;

    public ClickableWhenDisabledSwitchPreference(Context context) {
        this(context, null);
    }

    public ClickableWhenDisabledSwitchPreference(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ClickableWhenDisabledSwitchPreference(Context context, AttributeSet attributeSet,
            int defStyleAttr) {
        this(context, attributeSet, defStyleAttr, 0);
    }

    public ClickableWhenDisabledSwitchPreference(Context context, AttributeSet attributeSet,
            int defStyleAttr, int defStyleRes) {
        super(context, attributeSet, defStyleAttr, defStyleRes);
    }

    /** Byte-code shape of the Kotlin default-arguments constructor. */
    public ClickableWhenDisabledSwitchPreference(Context context, AttributeSet attributeSet,
            int defStyleAttr, int defStyleRes, int mask,
            kotlin.jvm.internal.DefaultConstructorMarker marker) {
        this(context, attributeSet,
                (mask & 2) != 0 ? 0 : defStyleAttr,
                (mask & 4) != 0 ? 0 : defStyleRes);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        if (isNotesKey() && Features.isNotSupportZuiNotes(getContext())) {
            setVisible(false);
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        itemView = holder.itemView;
        itemView.setAllowClickWhenDisabled(clickableWhenDisabled);
        itemView.setOnClickListener(new OnItemClickListener(this));
    }

    public final void setAllowClickWhenDisabled(boolean clickableWhenDisabled) {
        this.clickableWhenDisabled = clickableWhenDisabled;
        View view = itemView;
        if (view != null) {
            view.setAllowClickWhenDisabled(clickableWhenDisabled);
        }
    }

    public final void setOnClickWhenDisabledListener(OnClickWhenDisabledListener listener) {
        this.onClickWhenDisabledListener = listener;
    }

    private boolean isNotesKey() {
        String key = getKey();
        return KEY_QUICK_CREATE_NOTE.equals(key)
                || KEY_SCREEN_OFF_QUICK_CREATE_NOTE.equals(key)
                || KEY_PARKER_QUICK_NOTE.equals(key);
    }

    /** Usual enabled -> stock click, disabled -> the extra listener. */
    // Not private: called from OnItemClickListener. The compat dex is built with d8
    // --no-desugaring, so a private member reached from another class (javac's
    // nestmate access) is not rewritten and ART throws IllegalAccessError.
    static void onItemClick(ClickableWhenDisabledSwitchPreference preference, View view) {
        if (preference.isEnabled()) {
            preference.setOnClickWhenDisabledSuperCall();
        } else if (preference.onClickWhenDisabledListener != null) {
            preference.onClickWhenDisabledListener.onClickWhenDisabled(preference);
        }
    }

    /** Isolated so the accessibility modifier of zui Preference.onClick is checked once. */
    private void setOnClickWhenDisabledSuperCall() {
        super.onClick();
    }

    public interface OnClickWhenDisabledListener {
        void onClickWhenDisabled(Preference preference);
    }

    /** Stand-in for the stock $$ExternalSyntheticLambda0. */
    private static final class OnItemClickListener implements View.OnClickListener {
        private final ClickableWhenDisabledSwitchPreference f$0;

        OnItemClickListener(ClickableWhenDisabledSwitchPreference preference) {
            this.f$0 = preference;
        }

        @Override
        public void onClick(View view) {
            onItemClick(f$0, view);
        }
    }
}
