/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.android.internal.graphics.drawable.BackgroundBlurDrawable;

/**
 * Stand-in for the ZUI "island": ZUI SystemUI shows the one-shot notifications
 * of PenService (pen connected, battery level) as a pill at the top of the
 * screen instead of a notification. Like the stock one it shows the pen name
 * above PenService's battery view and a close-up of the pen tip, on a frosted
 * light or dark background, for a few seconds.
 */
final class PenIsland {
    private static final String TAG = "TB520FUPenIsland";
    private static final long SHOW_MS = 3000;
    private static final long FADE_MS = 250;
    private static final int PILL_COLOR_LIGHT = 0xD9FFFFFF;
    private static final int PILL_COLOR_DARK = 0xCC1E1E1E;
    /**
     * Stock ZUI island size factor: the whole popup is this much smaller than
     * the sizes the constants below were taken from. Applied to every size, so
     * the layout stays proportional (nothing is scaled after rendering).
     */
    private static final float SCALE = 0.85f;
    private static final int PILL_HEIGHT_DP = 68;
    private static final int PEN_HEIGHT_DP = 34;
    private static final int TIP_WIDTH_DP = 120;
    private static final int TOP_MARGIN_DP = 14;
    private static final int BLUR_DP = 24;
    private static final String PEN_PACKAGE = "com.lenovo.penservice";

    private final Context mContext;
    private final Handler mHandler;
    private View mShown;
    private WindowManager mShownWm;
    private final Runnable mHide = Safe.run("pen island hide", this::hide);

    PenIsland(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    /** Shows the content view; returns false if it cannot be shown (post a notification then). */
    boolean show(RemoteViews content) {
        if (content == null) return false;
        mHandler.post(Safe.run("pen island show", () -> showNow(content)));
        return true;
    }

    private void showNow(RemoteViews content) {
        hide();
        Display display = mContext.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        int type = WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
        Context window = mContext.createDisplayContext(display).createWindowContext(type, null);
        boolean night = (window.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Context ui = new ContextThemeWrapper(window, android.R.style.Theme_DeviceDefault_DayNight);
        float dp = ui.getResources().getDisplayMetrics().density;
        int height = (int) (PILL_HEIGHT_DP * SCALE * dp);
        int bgColor = night ? PILL_COLOR_DARK : PILL_COLOR_LIGHT;

        // Like the ZUI island: name and battery on the left, a close-up of the
        // pen tip on the right, cut off by the rounded edge of the pill.
        LinearLayout pill = new LinearLayout(ui);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(height / 2f);
        pill.setBackground(bg);
        pill.setClipToOutline(true);
        pill.setPaddingRelative((int) (26 * SCALE * dp), 0, 0, 0);

        LinearLayout text = new LinearLayout(ui);
        text.setOrientation(LinearLayout.VERTICAL);
        String name = PenController.sLastPenName;
        int nameWidth = 0;
        if (!TextUtils.isEmpty(name)) {
            TextView label = new TextView(ui);
            label.setText(name);
            label.setTextColor(night ? 0xFFF2F2F2 : 0xFF1F1F1F);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * SCALE);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setMaxLines(1);
            nameWidth = (int) Math.ceil(label.getPaint().measureText(name));
            text.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        FrameLayout battery = new FrameLayout(ui);
        View view = content.apply(ui, battery);
        scaleSizes(view, SCALE);
        battery.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.START));
        LinearLayout.LayoutParams lpBattery = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBattery.topMargin = text.getChildCount() > 0 ? (int) (4 * SCALE * dp) : 0;
        text.addView(battery, lpBattery);
        pill.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Drawable picture = penPicture(ui, PenController.sLastPenType);
        int imageWidth = 0;
        if (picture != null && picture.getIntrinsicHeight() > 0) {
            ImageView image = new ImageView(ui);
            image.setImageDrawable(picture);
            image.setScaleType(ImageView.ScaleType.MATRIX);
            int penHeight = (int) (PEN_HEIGHT_DP * SCALE * dp);
            float penScale = (float) penHeight / picture.getIntrinsicHeight();
            Matrix m = new Matrix();
            m.setScale(penScale, penScale);
            m.postTranslate(0, (height - penHeight) / 2f);
            image.setImageMatrix(m);
            LinearLayout.LayoutParams lpImage =
                    new LinearLayout.LayoutParams((int) (TIP_WIDTH_DP * SCALE * dp), height);
            lpImage.setMarginStart((int) (28 * SCALE * dp));
            pill.addView(image, lpImage);
            imageWidth = lpImage.width + lpImage.getMarginStart();
        } else {
            pill.setPaddingRelative((int) (26 * SCALE * dp), 0,
                    (int) (26 * SCALE * dp), 0);
        }

        // A WRAP_CONTENT window is first measured at the preferred dialog width,
        // which cut the pen name off; size the window to the content instead.
        pill.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        int textWidth = Math.max(nameWidth, battery.getMeasuredWidth());
        int width = Math.max(pill.getMeasuredWidth(), pill.getPaddingStart()
                + textWidth + pill.getPaddingEnd() + imageWidth);
        text.setMinimumWidth(textWidth);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = (int) (TOP_MARGIN_DP * SCALE * dp);
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("PenIsland");
        lp.setFitInsetsTypes(0);

        pill.setAlpha(0f);
        pill.setScaleX(0.8f);
        pill.setScaleY(0.8f);
        WindowManager wm = window.getSystemService(WindowManager.class);
        wm.addView(pill, lp);
        blurBackground(wm, pill, bgColor, height / 2f, dp);
        mShownWm = wm;
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(FADE_MS).start();
        mShown = pill;
        mHandler.removeCallbacks(mHide);
        mHandler.postDelayed(mHide, SHOW_MS);
        Log.i(TAG, "shown");
    }

    /** Frosted background like the ZUI island when cross-window blur is available. */
    private static void blurBackground(WindowManager wm, View pill, int color, float radius,
            float dp) {
        try {
            ViewRootImpl root = pill.getViewRootImpl();
            if (root == null || !wm.isCrossWindowBlurEnabled()) return;
            BackgroundBlurDrawable blur = root.createBackgroundBlurDrawable();
            blur.setBlurRadius((int) (BLUR_DP * SCALE * dp));
            blur.setCornerRadius(radius);
            blur.setColor(color);
            pill.setBackground(blur);
        } catch (RuntimeException e) {
            Log.w(TAG, "blur", e);
        }
    }

    /**
     * Shrinks the inflated PenService battery view to match the pill. The layout
     * of PenService (pen_pair_layout_for_notification_new) uses real dp and sp
     * sizes, so they are rewritten instead of scaling the rendered view: that
     * keeps the battery percentage text crisp.
     */
    private static void scaleSizes(View view, float scale) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            if (lp.width > 0) lp.width = Math.round(lp.width * scale);
            if (lp.height > 0) lp.height = Math.round(lp.height * scale);
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                int top = Math.round(mlp.topMargin * scale);
                int bottom = Math.round(mlp.bottomMargin * scale);
                if (mlp.isMarginRelative()) {
                    // layout_marginStart/End (what the PenService layout uses,
                    // resolved by the inflater): keep them relative.
                    mlp.setMarginsRelative(Math.round(mlp.getMarginStart() * scale), top,
                            Math.round(mlp.getMarginEnd() * scale), bottom);
                } else {
                    mlp.setMargins(Math.round(mlp.leftMargin * scale), top,
                            Math.round(mlp.rightMargin * scale), bottom);
                }
            }
        }
        view.setPadding(Math.round(view.getPaddingLeft() * scale),
                Math.round(view.getPaddingTop() * scale),
                Math.round(view.getPaddingRight() * scale),
                Math.round(view.getPaddingBottom() * scale));
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() * scale);
            tv.setMinWidth(Math.round(tv.getMinimumWidth() * scale));
            tv.setMinHeight(Math.round(tv.getMinimumHeight() * scale));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scaleSizes(group.getChildAt(i), scale);
            }
        }
    }

    /** Pen picture from PenService by pen type (PenController.TYPE_*), or null. */
    private static Drawable penPicture(Context ui, int type) {
        String name;
        switch (type) {
            case PenController.TYPE_PICASSO: name = "ic_bt_pen_picasso"; break;
            case PenController.TYPE_PARKER: name = "ic_bt_pen_parker_v1"; break;
            case PenController.TYPE_PARKER_2: name = "ic_bt_pen_omas"; break;
            case PenController.TYPE_SHEAFFER: name = "ic_bt_pen_sheaffer"; break;
            default: name = "ic_pen_stylus"; break;
        }
        try {
            Context pen = ui.createPackageContext(PEN_PACKAGE, 0);
            int id = pen.getResources().getIdentifier(name, "drawable", PEN_PACKAGE);
            return id != 0 ? pen.getDrawable(id) : null;
        } catch (Exception e) {
            Log.w(TAG, "pen picture", e);
            return null;
        }
    }

    private void hide() {
        View view = mShown;
        WindowManager wm = mShownWm;
        if (view == null || wm == null) return;
        mShown = null;
        mShownWm = null;
        view.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            try {
                wm.removeView(view);
            } catch (RuntimeException e) {
                Log.w(TAG, "remove", e);
            }
        }).start();
    }
}
