/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lenovo.pen.bt.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.lenovo.pen.cap.util.Features;

/**
 * Faithful replacement of the stock com.lenovo.pen.bt.widget.ItemFloatingExpand
 * (built from ItemFloatingExpand.kt) that additionally hides the toolbox
 * "new note" item when the ZUI Notes package is missing.
 *
 * Stock only ever conveys the Notes state to this view through
 * FloatingExpandView.onZuiNotesStateChanged() ->
 *     mNewNote.setEnabled(Features.isSupportZuiNotes(context))
 * and FloatingMenuLayout only measures/sizes children whose visibility is not
 * GONE. Driving the visibility from setEnabled() therefore keeps every other
 * item (screenshot, magnifier, ...) untouched and makes the row disappear from
 * the measured height, so the toolbox window shrinks on its own.
 *
 * Resource ids cannot be referenced at compile time (the stock R is not on the
 * classpath), so the layout/id lookups go through Resources.getIdentifier().
 */
public final class ItemFloatingExpand extends ConstraintLayout {

    private Drawable icon;
    private ImageView iconView;
    private String title;
    private TextView titleView;

    /** Cached once per instance; 0 means "no such resource in this package". */
    private int itemNewNoteId = -1;

    public ItemFloatingExpand(Context context) {
        this(context, null);
    }

    public ItemFloatingExpand(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ItemFloatingExpand(Context context, AttributeSet attributeSet, int defStyleAttr) {
        this(context, attributeSet, defStyleAttr, 0);
    }

    public ItemFloatingExpand(Context context, AttributeSet attributeSet,
            int defStyleAttr, int defStyleRes) {
        super(context, attributeSet, defStyleAttr, defStyleRes);

        title = "";
        // Same styleable as stock: android.R.attr.src / android.R.attr.text.
        TypedArray typedArray = context.obtainStyledAttributes(
                attributeSet, new int[] {android.R.attr.src, android.R.attr.text},
                defStyleAttr, 0);
        try {
            icon = typedArray.getDrawable(0);
            String string = typedArray.getString(1);
            title = string != null ? string : "";
        } finally {
            typedArray.recycle();
        }

        Context themed = context;
        LayoutInflater.from(themed).inflate(
                resolve(themed, "layout", "item_floating_expand"), this, true);
        iconView = (ImageView) findViewById(resolve(themed, "id", "item_floating_expand_icon"));
        titleView = (TextView) findViewById(resolve(themed, "id", "item_floating_expand_title"));
        iconView.setImageDrawable(icon);
        titleView.setText(title);
        setContentDescription(title);
    }

    /** Byte-code shape of the Kotlin default-arguments constructor. */
    public ItemFloatingExpand(Context context, AttributeSet attributeSet, int defStyleAttr,
            int defStyleRes, int mask, kotlin.jvm.internal.DefaultConstructorMarker marker) {
        this(context, attributeSet,
                (mask & 2) != 0 ? 0 : defStyleAttr,
                (mask & 4) != 0 ? 0 : defStyleRes);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        iconView.setEnabled(enabled);
        titleView.setEnabled(enabled);
        applyNotesVisibility(enabled);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // The inflated default is VISIBLE, so re-assert the rule for the case
        // where the stock caller never reached setEnabled().
        applyNotesVisibility(isEnabled());
    }

    public final void setImageResource(int resId) {
        iconView.setImageResource(resId);
    }

    public final void setText(int resId) {
        titleView.setText(resId);
    }

    public final void setTextColor(ColorStateList colors) {
        titleView.setTextColor(colors);
    }

    /**
     * Notes present  -> VISIBLE, i.e. exactly what the layout declares and what
     * stock leaves in place; Notes missing -> GONE, so the item is not drawn and
     * contributes no height.
     */
    private void applyNotesVisibility(boolean enabled) {
        if (getId() != itemNewNoteId()) {
            return;
        }
        setVisibility(Features.isSupportZuiNotes(getContext()) ? View.VISIBLE : View.GONE);
    }

    private int itemNewNoteId() {
        if (itemNewNoteId == -1) {
            itemNewNoteId = resolve(getContext(), "id", "item_new_note");
        }
        return itemNewNoteId;
    }

    private static int resolve(Context context, String type, String name) {
        return context.getResources().getIdentifier(
                name, type, context.getPackageName());
    }
}
