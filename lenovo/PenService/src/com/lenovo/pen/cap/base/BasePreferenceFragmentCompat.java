/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.lenovo.pen.cap.base;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.lenovo.pen.cap.util.ZuiVersions;

/**
 * Replacement of the stock com.lenovo.pen.cap.base.BasePreferenceFragmentCompat
 * (BasePreferenceFragmentCompat.kt), the base class of every pen settings page.
 *
 * Stock behaviour is kept (list side padding from pen_pref_padding[_zui14_5],
 * no scroll bar). On top of it the rows get the PixelOS Settings look
 * (SettingsLib expressive, settingslib_round_background_*): consecutive rows
 * between two categories form one group of surface-bright cards, 20dp corners
 * on the outside of the group, 4dp inside, 2dp apart. Categories and the
 * stock bottom spacer rows (pen_settings_preference_space) stay outside the
 * cards and end a group. The backgrounds are set per adapter position from an
 * item decoration, so rows hidden or shown later regroup on the next layout.
 */
public class BasePreferenceFragmentCompat extends zui.appcompat.preference.PreferenceFragmentCompat {

    private static final int OUTER_RADIUS_DP = 20;
    private static final int INNER_RADIUS_DP = 4;
    private static final int GAP_DP = 2;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        RecyclerView list = getListView();
        Context context = list.getContext();
        Resources res = context.getResources();
        int padding = (int) res.getDimension(res.getIdentifier(
                ZuiVersions.isZui14_5OrLater() ? "pen_pref_padding_zui14_5" : "pen_pref_padding",
                "dimen", context.getPackageName()));
        list.setPadding(padding, list.getPaddingTop(), padding, list.getPaddingBottom());
        list.setVerticalScrollBarEnabled(false);
        // The cards replace the ZUI dividers between rows.
        setDivider(null);
        list.addItemDecoration(new CardDecoration(context));
        return view;
    }

    private static final class CardDecoration extends RecyclerView.ItemDecoration {
        private static final int SINGLE = 0;
        private static final int TOP = 1;
        private static final int MIDDLE = 2;
        private static final int BOTTOM = 3;

        private final float mOuterRadius;
        private final float mInnerRadius;
        private final int mGap;
        private final int mCardColor;
        private final ColorStateList mRippleColor;
        private final int mSpacerLayout;
        private RecyclerView.Adapter mObserved;

        CardDecoration(Context context) {
            Resources res = context.getResources();
            float density = res.getDisplayMetrics().density;
            mOuterRadius = OUTER_RADIUS_DP * density;
            mInnerRadius = INNER_RADIUS_DP * density;
            mGap = Math.round(GAP_DP * density);
            boolean night = (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            mCardColor = context.getColor(night
                    ? android.R.color.system_surface_bright_dark
                    : android.R.color.system_surface_bright_light);
            TypedArray a = context.obtainStyledAttributes(
                    new int[] { android.R.attr.colorControlHighlight });
            ColorStateList ripple = a.getColorStateList(0);
            a.recycle();
            mRippleColor = ripple != null ? ripple : ColorStateList.valueOf(0x1f000000);
            // Empty row at the end of the stock pen pages (bottom padding).
            mSpacerLayout = res.getIdentifier("pen_settings_preference_space", "layout",
                    context.getPackageName());
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            outRect.setEmpty();
            RecyclerView.Adapter adapter = parent.getAdapter();
            if (!(adapter instanceof PreferenceGroupAdapter)) {
                return;
            }
            observe(parent, adapter);
            PreferenceGroupAdapter prefs = (PreferenceGroupAdapter) adapter;
            int position = parent.getChildAdapterPosition(view);
            Preference preference = item(prefs, position);
            if (isGroupEdge(preference)) {
                return;
            }
            boolean first = isGroupEdge(item(prefs, position - 1));
            boolean last = isGroupEdge(item(prefs, position + 1));
            int shape = first ? (last ? SINGLE : TOP) : (last ? BOTTOM : MIDDLE);
            if (!first) {
                outRect.top = mGap;
            }
            Drawable background = view.getBackground();
            if (!(background instanceof Card) || ((Card) background).mShape != shape) {
                view.setBackground(card(shape));
            }
        }

        /** Regroup when rows are added, removed or hidden. */
        private void observe(RecyclerView parent, RecyclerView.Adapter adapter) {
            if (mObserved == adapter) {
                return;
            }
            mObserved = adapter;
            adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    invalidate(parent);
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    invalidate(parent);
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    invalidate(parent);
                }

                @Override
                public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                    invalidate(parent);
                }
            });
        }

        // Not private: called from the adapter observer in observe(). The compat dex is built with d8
        // --no-desugaring, so a private member reached from another class (javac's
        // nestmate access) is not rewritten and ART throws IllegalAccessError.
        static void invalidate(RecyclerView parent) {
            // No lambdas: the compat dex is built without desugaring.
            parent.post(new Runnable() {
                @Override
                public void run() {
                    if (!parent.isComputingLayout()) {
                        parent.invalidateItemDecorations();
                    }
                }
            });
        }

        private static Preference item(PreferenceGroupAdapter adapter, int position) {
            if (position < 0 || position >= adapter.getItemCount()) {
                return null;
            }
            return adapter.getItem(position);
        }

        private boolean isGroupEdge(Preference preference) {
            return preference == null || preference instanceof PreferenceCategory
                    || (mSpacerLayout != 0 && preference.getLayoutResource() == mSpacerLayout);
        }

        private Drawable card(int shape) {
            float top = shape == SINGLE || shape == TOP ? mOuterRadius : mInnerRadius;
            float bottom = shape == SINGLE || shape == BOTTOM ? mOuterRadius : mInnerRadius;
            GradientDrawable card = new GradientDrawable();
            card.setColor(mCardColor);
            card.setCornerRadii(new float[] {
                    top, top, top, top, bottom, bottom, bottom, bottom });
            return new Card(mRippleColor, card, shape);
        }
    }

    /** Card background of one row; remembers its shape to skip rebuilding. */
    private static final class Card extends RippleDrawable {
        final int mShape;

        Card(ColorStateList color, Drawable content, int shape) {
            super(color, content, null);
            mShape = shape;
        }
    }
}
