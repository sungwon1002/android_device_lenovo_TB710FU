/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.keyboardupdate;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * PixelOS Settings look for the firmware update page of the Lenovo keyboard
 * updaters. Both apps build the page from the same stock layout
 * (activity_main: a back/title bar, a card with the keyboard picture, the
 * versions and the download progress, and the action buttons below it), and
 * their activities only toggle visibility and text afterwards, so the views
 * are restyled once, right after the activity created them:
 *
 * - page and system bars on the Settings background (surface container),
 *   which replaces the old resource overlays,
 * - the SettingsLib expressive back button (40dp circle) and a 22sp title,
 * - the information card as a surface-bright card with 20dp corners at the
 *   full pane width (stock: 80% wide, which was cramped in the right pane),
 * - Material 3 progress bar and pill buttons in the dynamic colours.
 *
 * Views are looked up by resource name, so a view that a variant of the page
 * lacks is skipped. Activities without the page (dialogs, the device picker)
 * are left alone.
 */
public final class PixelStyle implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "TB520FUKeyboardUpdate";

    /** Called once per process from the startup provider. */
    public static void install(Context context) {
        Context app = context.getApplicationContext();
        if (app instanceof Application) {
            ((Application) app).registerActivityLifecycleCallbacks(new PixelStyle());
        }
    }

    @Override
    public void onActivityPostCreated(Activity activity, Bundle savedInstanceState) {
        try {
            if (view(activity, "update_rl") != null) {
                stylePage(activity, new Palette(activity));
            }
        } catch (RuntimeException e) {
            // Never break the updater because of the look.
            Log.w(TAG, "Cannot restyle " + activity.getComponentName(), e);
        }
    }

    private static void stylePage(Activity activity, Palette p) {
        Window window = activity.getWindow();
        window.setStatusBarColor(p.background);
        window.setNavigationBarColor(p.background);
        window.setNavigationBarContrastEnforced(false);
        window.getDecorView().setBackgroundColor(p.background);
        WindowInsetsController insets = window.getInsetsController();
        if (insets != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            insets.setSystemBarsAppearance(p.night ? 0 : light, light);
        }

        styleTitleBar(activity, p);

        // Content: full width cards with the Settings side margin.
        View content = view(activity, "update_rl");
        content.setPadding(p.dp(16), content.getPaddingTop(), p.dp(16), p.dp(24));
        View card = view(activity, "update_rl_background");
        if (card != null) {
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            if (lp instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams clp = (ConstraintLayout.LayoutParams) lp;
                clp.matchConstraintPercentWidth = 1f;
                clp.topMargin = p.dp(8);
                card.setLayoutParams(clp);
            }
            card.setBackground(rounded(p.card, p.dp(20)));
            card.setPadding(p.dp(24), p.dp(8), p.dp(24), p.dp(24));
        }
        ImageView picture = (ImageView) view(activity, "start_download");
        if (picture != null) {
            picture.setAdjustViewBounds(true);
        }
        styleVersions(activity, p);
        styleProgress(activity, p);

        filledButton(text(activity, "check_for_update"), p);
        filledButton(text(activity, "download_cancel"), p);
        disabledButton(text(activity, "check_for_cancel"), p);
        // xml_parse is the row around the "Checking for updates..." label.
        tonalLabel(firstText(view(activity, "xml_parse")), p);

        TextView updating = text(activity, "updating_content");
        if (updating != null) {
            body(updating, p.onSurfaceVariant, 14);
        }
    }

    /** Back button and title like the SettingsLib expressive toolbar. */
    private static void styleTitleBar(Activity activity, Palette p) {
        View back = view(activity, "back");
        if (back == null || !(back.getParent() instanceof ViewGroup)) {
            return;
        }
        ViewGroup bar = (ViewGroup) back.getParent();
        if (bar.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bar.getLayoutParams();
            lp.setMarginStart(p.dp(16));
            lp.topMargin = p.dp(16);
            lp.bottomMargin = p.dp(8);
            bar.setLayoutParams(lp);
        }
        bar.setMinimumHeight(p.dp(56));

        if (back instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) back;
            for (int i = 0; i < group.getChildCount(); i++) {
                group.getChildAt(i).setVisibility(View.GONE);
            }
        }
        ViewGroup.LayoutParams blp = back.getLayoutParams();
        blp.width = p.dp(40);
        blp.height = p.dp(40);
        back.setLayoutParams(blp);
        back.setBackground(backButton(activity, p));
        back.setClickable(true);
        back.setFocusable(true);

        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof TextView) {
                TextView title = (TextView) child;
                title.setTextColor(p.onSurface);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                title.setTypeface(Typeface.create("variable-title-large-emphasized",
                        Typeface.NORMAL));
                if (title.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams tlp =
                            (ViewGroup.MarginLayoutParams) title.getLayoutParams();
                    tlp.setMarginStart(p.dp(16));
                    title.setLayoutParams(tlp);
                }
            }
        }
    }

    /** "Current version" / "Last update" row in the card. */
    private static void styleVersions(Activity activity, Palette p) {
        TextView version = text(activity, "current_version");
        if (version == null || !(version.getParent() instanceof ViewGroup)) {
            return;
        }
        ViewGroup row = (ViewGroup) version.getParent();
        row.setPadding(0, p.dp(16), 0, 0);
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof TextView) {
                body((TextView) child, p.onSurfaceVariant, 14);
            }
        }
        body(version, p.onSurface, 14);
        TextView time = text(activity, "update_time");
        if (time != null) {
            body(time, p.onSurface, 14);
        }
    }

    private static void styleProgress(Activity activity, Palette p) {
        View area = view(activity, "download_area");
        if (area != null && area.getLayoutParams() != null) {
            // Stock 350dp; follow the card instead.
            ViewGroup.LayoutParams lp = area.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            area.setLayoutParams(lp);
        }
        View bar = view(activity, "update_progress");
        if (bar instanceof ProgressBar) {
            ProgressBar progress = (ProgressBar) bar;
            GradientDrawable track = new GradientDrawable();
            track.setColor(p.track);
            track.setCornerRadius(p.dp(4));
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(p.primary);
            fill.setCornerRadius(p.dp(4));
            LayerDrawable layers = new LayerDrawable(new Drawable[] {
                    track, new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL) });
            layers.setId(0, android.R.id.background);
            layers.setId(1, android.R.id.progress);
            progress.setProgressTintList(null);
            progress.setProgressBackgroundTintList(null);
            progress.setProgressDrawable(layers);
            ViewGroup.LayoutParams lp = progress.getLayoutParams();
            lp.height = p.dp(8);
            progress.setLayoutParams(lp);
        }
        TextView title = text(activity, "update_title");
        if (title != null) {
            body(title, p.onSurfaceVariant, 14);
        }
    }

    private static void filledButton(TextView button, Palette p) {
        if (button == null) {
            return;
        }
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(p.ripple),
                rounded(p.primary, p.dp(100)), null));
        label(button, p.onPrimary);
    }

    private static void disabledButton(TextView button, Palette p) {
        if (button == null) {
            return;
        }
        button.setBackground(rounded(withAlpha(p.onSurface, 0.12f), p.dp(100)));
        label(button, withAlpha(p.onSurface, 0.38f));
    }

    /** "Checking for updates..." status pill. */
    private static void tonalLabel(TextView status, Palette p) {
        if (status == null) {
            return;
        }
        status.setBackground(rounded(p.secondaryContainer, p.dp(100)));
        label(status, p.onSecondaryContainer);
    }

    private static void label(TextView button, int color) {
        button.setTextColor(color);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("variable-label-large", Typeface.NORMAL));
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(Math.round(48 * button.getResources().getDisplayMetrics().density));
        button.setPadding(button.getPaddingLeft(), 0, button.getPaddingRight(), 0);
    }

    private static void body(TextView text, int color, int sp) {
        text.setTextColor(color);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTypeface(Typeface.create("variable-body-medium", Typeface.NORMAL));
    }

    private static Drawable backButton(Context context, Palette p) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(p.backButton);
        Drawable arrow = null;
        int id = Resources.getSystem().getIdentifier("ic_ab_back_material", "drawable", "android");
        if (id != 0) {
            arrow = context.getDrawable(id).mutate();
            arrow.setTint(p.onSurfaceVariant);
            arrow.setAutoMirrored(true);
        }
        Drawable content = circle;
        if (arrow != null) {
            // The framework arrow fills 16 of its 24dp, the SettingsLib one is 16dp.
            LayerDrawable layers = new LayerDrawable(new Drawable[] { circle, arrow });
            layers.setLayerGravity(1, Gravity.CENTER);
            layers.setLayerSize(1, p.dp(24), p.dp(24));
            content = layers;
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(p.ripple), content, mask);
    }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00ffffff) | (Math.round(alpha * 255) << 24);
    }

    private static View view(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    private static TextView text(Activity activity, String name) {
        View v = view(activity, name);
        return v instanceof TextView ? (TextView) v : null;
    }

    private static TextView firstText(View v) {
        if (v instanceof TextView) {
            return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView t = firstText(group.getChildAt(i));
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    /** The PixelOS Settings colour roles (SettingsLib materialColor*). */
    private static final class Palette {
        final boolean night;
        final int background;
        final int card;
        final int backButton;
        final int track;
        final int onSurface;
        final int onSurfaceVariant;
        final int primary;
        final int onPrimary;
        final int secondaryContainer;
        final int onSecondaryContainer;
        final int ripple;
        private final float density;

        Palette(Context context) {
            Resources res = context.getResources();
            night = (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            density = res.getDisplayMetrics().density;
            background = color(context, android.R.color.system_surface_container_light,
                    android.R.color.system_surface_container_dark);
            card = color(context, android.R.color.system_surface_bright_light,
                    android.R.color.system_surface_bright_dark);
            backButton = color(context, android.R.color.system_surface_container_highest_light,
                    android.R.color.system_surface_container_highest_dark);
            track = backButton;
            onSurface = color(context, android.R.color.system_on_surface_light,
                    android.R.color.system_on_surface_dark);
            onSurfaceVariant = color(context, android.R.color.system_on_surface_variant_light,
                    android.R.color.system_on_surface_variant_dark);
            primary = color(context, android.R.color.system_primary_light,
                    android.R.color.system_primary_dark);
            onPrimary = color(context, android.R.color.system_on_primary_light,
                    android.R.color.system_on_primary_dark);
            secondaryContainer = color(context, android.R.color.system_secondary_container_light,
                    android.R.color.system_secondary_container_dark);
            onSecondaryContainer = color(context,
                    android.R.color.system_on_secondary_container_light,
                    android.R.color.system_on_secondary_container_dark);
            TypedArray a = context.obtainStyledAttributes(
                    new int[] { android.R.attr.colorControlHighlight });
            ripple = a.getColor(0, 0x1f000000);
            a.recycle();
        }

        private int color(Context context, int light, int dark) {
            return context.getColor(night ? dark : light);
        }

        int dp(int value) {
            return Math.round(value * density);
        }
    }

    // Unused callbacks.
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityResumed(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}
}
