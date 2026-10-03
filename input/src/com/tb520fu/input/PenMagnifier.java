/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.InputManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/**
 * Reproduces the "magnifier" entry of the stock Lenovo stylus toolbox.
 *
 * <p>The toolbox only flips {@code Settings.Global.magnifier_status} to 1 (and
 * back to 0 when the entry is closed); stock ZUI drew the lens from
 * MagnifierDragDropController inside services.jar. This class is a clean-room
 * reimplementation of the user visible behaviour:
 *
 * <ul>
 *   <li>a 200x200 round lens that follows the pen while it is down (2x zoom),
 *       disappearing shortly after the pen is lifted;</li>
 *   <li>the content comes from a full-screen virtual display that mirrors the
 *       default display into an ImageReader;</li>
 *   <li>the lens window itself is excluded from that mirror, otherwise the
 *       lens would magnify its own picture.
 * </ul>
 *
 * <p><b>How the lens moves:</b> a gesture input monitor sees every stylus
 * DOWN/MOVE/UP on the default display. On every event the window position is
 * updated with {@link SurfaceControl.Transaction#setPosition} (a cheap surface
 * transaction, not a WindowManager relayout) so the lens stays glued to the
 * pen. New frames are only read from the mirror when the pen actually moved.
 *
 * <p><b>Self exclusion:</b> unlike stock, the lens surface is flagged with
 * {@link SurfaceControl.Transaction#setSkipScreenshot}. SurfaceFlinger skips
 * such layers when building a mirrored hierarchy
 * (frameworks/native/services/surfaceflinger/FrontEnd/LayerSnapshotBuilder.cpp,
 * {@code handleSkipScreenshotFlag}), which is exactly our case
 * (AUTO_MIRROR virtual display).
 */
final class PenMagnifier {
    private static final String TAG = "TB520FUPenMagnifier";

    /** Settings.Global key written by the Lenovo pen app. */
    private static final String SETTING_MAGNIFIER_STATUS = "magnifier_status";

    /** Lens diameter in pixels (stock used 200 and a fixed -200 px offset). */
    private static final int LENS_SIZE = 200;
    private static final int LENS_HALF = LENS_SIZE / 2;
    private static final float ZOOM = 2.0f;
    /** Source pixels shown in the lens before zooming. */
    private static final int SOURCE_SIZE = (int) (LENS_SIZE / ZOOM);
    private static final int SOURCE_HALF = SOURCE_SIZE / 2;

    /** Stock waited 30 ms before hiding after the pen went up. */
    private static final long HIDE_DELAY_MS = 30L;
    /** Re-mirroring after a rotation needs the old session gone first. */
    private static final long ROTATION_DELAY_MS = 300L;

    private final Context mContext;
    private final Handler mHandler;
    private final Point mDisplaySize = new Point();
    private final ContentResolver mContentResolver;
    private DisplayManager mDisplayManager;
    private Display mDefaultDisplay;

    private SurfaceControl.Transaction mTransaction;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private int mLastRotation = -1;

    private Context mWindowContext;
    private WindowManager mWindowManager;
    private MagnifierView mLens;
    private SurfaceControl mLensSurface;
    /** Bumped on every show: window removal is deferred, so reusing the
     * same title too soon can throw (multiple roots with the same name). */
    private int mLensSerial;
    private WindowManager.LayoutParams mLensParams;

    private InputMonitor mMonitor;
    private InputReceiver mReceiver;

    private Bitmap mBand;
    private int mBandWidth;
    private int mCursorX;
    private int mCursorY;
    private int mRenderedX = Integer.MIN_VALUE;
    private int mRenderedY = Integer.MIN_VALUE;
    private boolean mNeedFreshFrame;
    private boolean mShown;
    private boolean mActive;

    private final Runnable mHide = () -> Safe.run("lens hide", this::hideLens).run();

    PenMagnifier(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mContentResolver = context.getContentResolver();
    }

    void start() {
        // Nothing here may throw: this runs inside system_server.
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        if (mDisplayManager == null) {
            Log.w(TAG, "no DisplayManager, magnifier unavailable");
            return;
        }
        mDefaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (mDefaultDisplay == null) {
            Log.w(TAG, "no default display, magnifier unavailable");
            return;
        }
        mTransaction = new SurfaceControl.Transaction();

        mContentResolver.registerContentObserver(
                Settings.Global.getUriFor(SETTING_MAGNIFIER_STATUS), false,
                Safe.observer(mHandler, "magnifier setting", uri -> syncFromSettings()));

        Safe.run("magnifier broadcasts", () -> {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            mContext.registerReceiverAsUser(Safe.receiver("magnifier "
                            + "screen/user change", (c, i) -> onScreenOrUserChanged(i)),
                    UserHandle.ALL, filter, null, mHandler, Context.RECEIVER_EXPORTED);
        }).run();

        mDisplayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int displayId) { }
            @Override public void onDisplayRemoved(int displayId) { }
            @Override public void onDisplayChanged(int displayId) {
                if (displayId != Display.DEFAULT_DISPLAY) return;
                if (mActive) {
                    syncRotation();
                } else {
                    // The lens may have been requested while the screen was
                    // off; pick it up as soon as the display comes back.
                    syncFromSettings();
                }
            }
        }, mHandler);

        syncFromSettings();
    }

    private void syncFromSettings() {
        boolean on;
        try {
            on = Settings.Global.getInt(mContentResolver, SETTING_MAGNIFIER_STATUS, 0) != 0;
        } catch (Throwable t) {
            Log.e(TAG, "read " + SETTING_MAGNIFIER_STATUS, t);
            return;
        }
        if (on) {
            enable();
        } else {
            disable();
        }
    }

    private void onScreenOrUserChanged(Intent intent) {
        if (!mActive) return;
        // Only the screen going off needs the explicit reset; a user switch is
        // already reflected by the setting being per-user.
        Log.i(TAG, "stopping for " + (intent != null ? intent.getAction() : "?"));
        disable();
        // Keep the pen app in sync: it owns the UI state and would otherwise
        // think the lens is still up.
        try {
            Settings.Global.putInt(mContentResolver, SETTING_MAGNIFIER_STATUS, 0);
        } catch (Throwable t) {
            Log.e(TAG, "reset " + SETTING_MAGNIFIER_STATUS, t);
        }
    }

    // ---------------------------------------------------------------- enable

    private void enable() {
        if (mActive || mDefaultDisplay == null) return;
        if (mDefaultDisplay.getState() != Display.STATE_ON) {
            Log.i(TAG, "display not on, not enabling");
            return;
        }
        Log.i(TAG, "enabling magnifier");
        mActive = true;
        mNeedFreshFrame = false;
        mShown = false;
        mRenderedX = Integer.MIN_VALUE;
        mRenderedY = Integer.MIN_VALUE;
        try {
            createLensWindow();
            createMirror();
            registerInput();
        } catch (Throwable t) {
            Log.e(TAG, "enable", t);
            disable();
        }
    }

    private void disable() {
        if (!mActive) return;
        Log.i(TAG, "disabling magnifier");
        mActive = false;
        unregisterInput();
        destroyLensWindow();
        releaseMirror();
    }

    // ------------------------------------------------------------------ lens

    private void createLensWindow() {
        int type = WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY;
        mWindowContext = mContext.createDisplayContext(mDefaultDisplay)
                .createWindowContext(type, null);
        mWindowManager = mWindowContext.getSystemService(WindowManager.class);

        mLens = new MagnifierView(mWindowContext);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // Stock used privateFlags 16 == SYSTEM_FLAG_SHOW_FOR_ALL_USERS.
        lp.privateFlags = WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        lp.setTitle("tb520fu-magnifier-" + (mLensSerial + 1));
        lp.x = 0;
        lp.y = 0;
        mLensParams = lp;

        mLensSerial += 1;
        mLens.setVisibility(View.INVISIBLE);
        mWindowManager.addView(mLens, lp);

        // The surface only exists once the view is attached, so grab it from a
        // posted runnable (View.post defers to the view handler thread).
        mLens.post(Safe.run("lens skip screenshot", this::applyLensSurface));
    }

    /**
     * Flags the lens surface with SKIP_SCREENSHOT so SurfaceFlinger leaves it
     * out when it builds the mirrored hierarchy for the lens own virtual
     * display (LayerSnapshotBuilder keeps handleSkipScreenshotFlag on mirrored
     * layers).
     */
    private void applyLensSurface() {
        View lens = mLens;
        if (lens == null) return;
        ViewRootImpl root = lens.getViewRootImpl();
        SurfaceControl surface = root != null ? root.getSurfaceControl() : null;
        if (surface == null || !surface.isValid()) {
            Log.w(TAG, "no lens surface, the lens may appear in its own mirror");
            return;
        }
        mLensSurface = surface;
        mTransaction.setSkipScreenshot(surface, true).apply();
        // Position it right away: the first stylus events may have arrived
        // while the surface was not available yet.
        moveLens();
    }

    private void destroyLensWindow() {
        mHandler.removeCallbacks(mHide);
        if (mWindowManager != null && mLens != null) {
            try {
                mWindowManager.removeViewImmediate(mLens);
            } catch (Throwable t) {
                Log.w(TAG, "remove lens", t);
            }
        }
        mLens = null;
        mLensSurface = null;
        mLensParams = null;
        mWindowManager = null;
        mWindowContext = null;
    }

    // ---------------------------------------------------------------- mirror

    private void createMirror() {
        mDefaultDisplay.getRealSize(mDisplaySize);
        mLastRotation = mDefaultDisplay.getRotation();
        int w = mDisplaySize.x;
        int h = mDisplaySize.y;

        mImageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        mImageReader.setOnImageAvailableListener(this::onImageAvailable, mHandler);

        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(
                "tb520fu-magnifier-mirror", w, h, getDensity())
                .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
                .setDisplayIdToMirror(Display.DEFAULT_DISPLAY)
                .setSurface(mImageReader.getSurface())
                .build();
        mVirtualDisplay = mDisplayManager.createVirtualDisplay(config);
        Log.i(TAG, "mirror " + w + "x" + h + " -> " + mVirtualDisplay);
    }

    private int getDensity() {
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        return metrics.densityDpi > 0 ? metrics.densityDpi : DisplayMetrics.DENSITY_DEVICE_STABLE;
    }

    private void releaseMirror() {
        if (mImageReader != null) {
            mImageReader.setOnImageAvailableListener(null, null);
            mImageReader.close();
            mImageReader = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mBand != null) {
            mBand.recycle();
            mBand = null;
            mBandWidth = 0;
        }
    }

    private void syncRotation() {
        if (!mActive) return;
        if (mDefaultDisplay.getRotation() == mLastRotation) return;
        Log.i(TAG, "rotation changed, re-mirroring");
        mHandler.removeCallbacks(mHide);
        hideLens();
        releaseMirror();
        Safe.postDelayed(mHandler, "magnifier re-mirror", () -> {
            if (!mActive) return;
            mRenderedX = Integer.MIN_VALUE;
            mRenderedY = Integer.MIN_VALUE;
            createMirror();
        }, ROTATION_DELAY_MS);
    }

    private void onImageAvailable(ImageReader reader) {
        if (reader != mImageReader) return;
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            if (!mActive || !mNeedFreshFrame) return;
            if (image.getWidth() == 0 || image.getHeight() == 0) return;

            int width = image.getWidth();
            int height = image.getHeight();
            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) return;
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            if (pixelStride != 4) {
                Log.w(TAG, "unexpected pixelStride " + pixelStride);
                return;
            }
            int bandWidth = rowStride / pixelStride;
            if (mBand == null || mBandWidth != bandWidth
                    || mBand.getHeight() != SOURCE_SIZE) {
                if (mBand != null) mBand.recycle();
                mBand = Bitmap.createBitmap(bandWidth, SOURCE_SIZE, Bitmap.Config.ARGB_8888);
                mBandWidth = bandWidth;
            }

            // Clamp the source window so the lens never shows empty edges; the
            // pen is then off-centre close to a border, which is preferable to
            // black bars.
            int x0 = Math.max(0, Math.min(width - SOURCE_SIZE, mCursorX - SOURCE_HALF));
            int y0 = Math.max(0, Math.min(height - SOURCE_SIZE, mCursorY - SOURCE_HALF));
            int offset = y0 * rowStride;
            int length = SOURCE_SIZE * rowStride;
            if (offset + length > buffer.capacity()) return;
            buffer.position(offset);
            buffer.limit(offset + length);
            mBand.copyPixelsFromBuffer(buffer);

            if (mCursorX == mRenderedX && mCursorY == mRenderedY) {
                // Nothing moved since the frame was consumed.
                return;
            }
            mRenderedX = mCursorX;
            mRenderedY = mCursorY;
            mNeedFreshFrame = false;
            showLens(x0, y0);
        } catch (Throwable t) {
            Log.e(TAG, "onImageAvailable", t);
        } finally {
            if (image != null) {
                try {
                    image.close();
                } catch (Throwable ignored) { }
            }
        }
    }

    private void showLens(int srcX, int srcY) {
        if (mLens == null || mBand == null) return;
        mLens.setFrame(mBand, srcX, srcY);
        if (!mShown) {
            mShown = true;
            mLens.setVisibility(View.VISIBLE);
        }
    }

    private void hideLens() {
        if (mLens == null) return;
        mNeedFreshFrame = false;
        if (!mShown) return;
        mShown = false;
        mLens.setVisibility(View.INVISIBLE);
    }

    // ----------------------------------------------------------------- input

    private void registerInput() {
        InputManager im = mContext.getSystemService(InputManager.class);
        mMonitor = im.monitorGestureInput("tb520fu-magnifier", Display.DEFAULT_DISPLAY);
        mReceiver = new InputReceiver(mMonitor);
        Log.i(TAG, "monitoring stylus input for the lens");
    }

    private void unregisterInput() {
        if (mReceiver != null) {
            mReceiver.dispose();
            mReceiver = null;
        }
        if (mMonitor != null) {
            mMonitor.dispose();
            mMonitor = null;
        }
    }

    private final class InputReceiver extends InputEventReceiver {
        InputReceiver(InputMonitor monitor) {
            super(monitor.getInputChannel(), mHandler.getLooper());
        }

        @Override
        public void onInputEvent(InputEvent event) {
            try {
                if (event instanceof MotionEvent) {
                    MotionEvent motion = (MotionEvent) event;
                    if ((motion.getSource() & InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS
                            && motion.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                        onStylus(motion);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "onInputEvent", t);
            } finally {
                finishInputEvent(event, false);
            }
        }
    }

    private void onStylus(MotionEvent ev) {
        if (!mActive) return;
        int action = ev.getActionMasked();
        mCursorX = Math.round(ev.getRawX());
        mCursorY = Math.round(ev.getRawY());

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mHandler.removeCallbacks(mHide);
                mNeedFreshFrame = true;
                mRenderedX = Integer.MIN_VALUE;
                mRenderedY = Integer.MIN_VALUE;
                moveLens();
                break;
            case MotionEvent.ACTION_MOVE:
                mNeedFreshFrame = true;
                moveLens();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mHandler.removeCallbacks(mHide);
                mHandler.postDelayed(mHide, HIDE_DELAY_MS);
                break;
            default:
                break;
        }
    }

    /**
     * Moves the window to be centred on the pen. {@code -LENS_HALF} matches the
     * stock offset: the lens centre is the pen tip.
     */
    private void moveLens() {
        if (mLens == null || mLensParams == null) return;
        int x = mCursorX - LENS_HALF;
        int y = mCursorY - LENS_HALF;
        mLensParams.x = x;
        mLensParams.y = y;
        if (mLensSurface == null || !mLensSurface.isValid()) return;
        try {
            mTransaction.setPosition(mLensSurface, x, y).apply();
        } catch (Throwable t) {
            Log.e(TAG, "setPosition", t);
        }
    }

    // ------------------------------------------------------------------ view

    /**
     * The lens itself: a circular window that draws a zoomed crop of the
     * mirrored frame with a stock-like two-tone border.
     */
    private static final class MagnifierView extends View {
        private static final Paint FRAME_PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);
        private static final int SHADOW_WIDTH = 3;
        private static final int BORDER_WIDTH = 5;
        private static final int INNER_WIDTH = 2;

        private final Rect mSrc = new Rect();
        private final Rect mDst = new Rect();
        private final Path mClip = new Path();
        private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap mBitmap;
        private int mSrcX;
        private int mSrcY;

        MagnifierView(Context context) {
            super(context);
        }

        void setFrame(Bitmap band, int srcX, int srcY) {
            mBitmap = band;
            mSrcX = srcX;
            mSrcY = srcY;
            postInvalidateOnAnimation();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(LENS_SIZE, LENS_SIZE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float cx = w / 2f;
            float cy = h / 2f;
            float outer = Math.min(w, h) / 2f - SHADOW_WIDTH;
            float radius = outer - BORDER_WIDTH / 2f;

            Bitmap bitmap = mBitmap;
            if (bitmap != null && !bitmap.isRecycled()) {
                mClip.reset();
                mClip.addCircle(cx, cy, radius - BORDER_WIDTH / 2f, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(mClip);
                mSrc.set(mSrcX, 0, mSrcX + SOURCE_SIZE, SOURCE_SIZE);
                mDst.set(0, 0, w, h);
                canvas.drawBitmap(bitmap, mSrc, mDst, FRAME_PAINT);
                canvas.restore();
            }

            Paint paint = mBorderPaint;
            paint.setStyle(Paint.Style.STROKE);
            // Subtle drop shadow ring (stock drew a shadow; this is a cheap
            // approximation that needs no software layer).
            paint.setColor(0x40000000);
            paint.setStrokeWidth(SHADOW_WIDTH);
            canvas.drawCircle(cx, cy, outer, paint);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(BORDER_WIDTH);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setColor(0xFF888888);
            paint.setStrokeWidth(INNER_WIDTH);
            canvas.drawCircle(cx, cy, radius - BORDER_WIDTH / 2f - INNER_WIDTH / 2f, paint);
        }
    }
}
