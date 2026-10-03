/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.hardware.input.InputSettings;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.Locale;

/**
 * Lenovo keyboard special keys, shortcuts and settings (stock
 * KeyboardZuiKeyInputPolicy / KeyboardShortcutController). The keylayouts map
 * the ZUI-only keys to AOSP keycodes: SCREEN_LOCK -> LOCK, FULL_SCREEN ->
 * FULLSCREEN etc. are left to the framework, the rest arrive here:
 *   TP_MUTE -> F24 (toggle the keyboard touchpad), APP1/APP2 -> MACRO_1/2
 *   (user selectable action), FN_LOCK -> MACRO_3, backlight keys -> keyboard
 *   HAL levels 0..2 (3 = automatic).
 * The user settings are the stock Settings.System keys, written by the
 * physical keyboard page of TB520FUParts (KeyboardFragment).
 */
final class KeyboardController {
    private static final String TAG = "TB520FUKeyboard";

    // Stock Settings.System keys (ZuiSettings physical keyboard page)
    static final String SETTING_AUTO_BACKLIGHT = "keyboard_auto_backlight";
    static final String SETTING_TAP_WAKE = "keyboard_touch_bright_screen";
    static final String SETTING_SINGLE_FINGER_TAP = "physical_keyboard_single_finger_touch";
    static final String SETTING_APP1 = "keyboard_shortcut_value_app1";
    static final String SETTING_APP2 = "keyboard_shortcut_value_app2";

    static final String COMBO_WIN_D = "keyboard_combo_win_d";
    static final String COMBO_WIN_N = "keyboard_combo_win_n";
    static final String COMBO_WIN_L = "keyboard_combo_win_l";
    static final String COMBO_WIN_W = "keyboard_combo_win_w";
    static final String COMBO_ALT_TAB = "keyboard_combo_alt_tab";
    static final String COMBO_WIN_S = "keyboard_combo_win_s";
    static final String COMBO_WIN_A = "keyboard_combo_win_a";
    static final String COMBO_WIN_E = "keyboard_combo_win_e";
    static final String COMBO_WIN_I = "keyboard_combo_win_i";
    static final String COMBO_WIN_M = "keyboard_combo_win_m";
    static final String COMBO_WIN_BACK = "keyboard_combo_win_back";
    static final String COMBO_WIN_NUMBER = "keyboard_combo_win_number";
    static final String COMBO_LR_ARROW = "keyboard_combo_lr_arrow";
    static final String COMBO_UD_ARROW = "keyboard_combo_ud_arrow";
    static final String COMBO_ALT_SHIFT = "keyboard_combo_alt_shift";
    static final String COMBO_CTRL_SHIFT = "keyboard_combo_ctrl_shift";
    static final String COMBO_CTRL_SPACE = "keyboard_combo_ctrl_space";
    static final String COMBO_CTRL_3 = "keyboard_combo_ctrl_3";

    // Internal state (Settings.Global), not shown in the settings
    static final String SETTING_TOUCHPAD = "tb520fu_touchpad_enabled";
    static final String SETTING_BACKLIGHT = "tb520fu_kb_backlight";
    static final String SETTING_FN_LOCK = "tb520fu_kb_fn_lock";

    static final String DEFAULT_APP1 = "screenshot";
    static final String DEFAULT_APP2 = "notifications";
    static final String ACTION_LAUNCH = "launch:";
    static final String ACTION_URL = "url:";

    private static final int KB_FN_LOCK_LED = 1;
    private static final int KB_BACKLIGHT = 4;
    private static final int KB_TAP_WAKE = 6;
    private static final int MAX_BACKLIGHT = 2;
    private static final int AUTO_BACKLIGHT = 3;
    private static final long CTRL_HOLD_MS = 3000;

    private final Context mContext;
    private final Handler mHandler;
    /** Key codes whose down event was consumed, so the up is consumed too. */
    private final ArraySet<Integer> mSwallowed = new ArraySet<>();
    private final Runnable mCtrlHeld = Safe.run("ctrl held", this::showShortcutHelper);
    private boolean mCtrlPending;

    KeyboardController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    void start() {
        ContentResolver cr = mContext.getContentResolver();
        ContentObserver observer = Safe.observer(mHandler, "keyboard settings", uri -> apply());
        cr.registerContentObserver(Settings.Global.getUriFor(SETTING_TOUCHPAD), false, observer);
        for (String key : new String[] {SETTING_TAP_WAKE, SETTING_SINGLE_FINGER_TAP,
                SETTING_AUTO_BACKLIGHT, Settings.System.SCREEN_BRIGHTNESS_MODE}) {
            cr.registerContentObserver(Settings.System.getUriFor(key), false, observer,
                    UserHandle.USER_ALL);
        }
        InputManager im = mContext.getSystemService(InputManager.class);
        im.registerInputDeviceListener(new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                Safe.run("keyboard added", () -> onDeviceAdded(deviceId)).run();
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {}

            @Override
            public void onInputDeviceChanged(int deviceId) {}
        }, mHandler);
        apply();
    }

    private void onDeviceAdded(int id) {
        InputDevice dev = mContext.getSystemService(InputManager.class).getInputDevice(id);
        if (dev == null || dev.getVendorId() != 0x17ef) return;
        Log.d(TAG, "Lenovo input device added: " + dev.getName());
        apply();
        LenovoHal.setKeyboardStatus(KB_BACKLIGHT, backlightLevel(), -1);
        LenovoHal.setKeyboardStatus(KB_FN_LOCK_LED, getGlobal(SETTING_FN_LOCK, 0), -1);
    }

    private void apply() {
        boolean touchpad = getGlobal(SETTING_TOUCHPAD, 1) != 0;
        for (InputDevice dev : touchpads()) {
            if (dev.isEnabled() != touchpad) {
                if (touchpad) dev.enable(); else dev.disable();
            }
        }
        // Stock ZUI sets tap-to-click from its own key; mirror it to the AOSP one.
        boolean tap = getSystem(SETTING_SINGLE_FINGER_TAP, 1) != 0;
        if (InputSettings.useTouchpadTapToClick(mContext) != tap) {
            InputSettings.setTouchpadTapToClick(mContext, tap);
        }
        boolean tapWake = getSystem(SETTING_TAP_WAKE, 1) != 0;
        if (LenovoHal.available(LenovoHal.KEYBOARD)) {
            LenovoHal.setKeyboardStatus(KB_TAP_WAKE, tapWake ? 1 : 0, tapWake ? 5 : 0);
            // Automatic backlight needs automatic screen brightness (stock updateAutoBackLightSwitch)
            if (getGlobal(SETTING_BACKLIGHT, 1) == AUTO_BACKLIGHT && !autoBacklightAllowed()) {
                putGlobal(SETTING_BACKLIGHT, 1);
                LenovoHal.setKeyboardStatus(KB_BACKLIGHT, 1, -1);
            }
        }
    }

    private boolean autoBacklightAllowed() {
        return getSystem(SETTING_AUTO_BACKLIGHT, 0) != 0
                && getSystem(Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                        == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
    }

    private int backlightLevel() {
        int level = getGlobal(SETTING_BACKLIGHT, 1);
        return level == AUTO_BACKLIGHT && !autoBacklightAllowed() ? 1 : level;
    }

    private java.util.List<InputDevice> touchpads() {
        java.util.List<InputDevice> out = new java.util.ArrayList<>();
        InputManager im = mContext.getSystemService(InputManager.class);
        for (int id : im.getInputDeviceIds()) {
            InputDevice dev = im.getInputDevice(id);
            if (dev != null && dev.getName().toLowerCase(Locale.ROOT).contains("touchpad")
                    && (dev.getSources() & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
                out.add(dev);
            }
        }
        return out;
    }

    /** Called from interceptKeyBeforeQueueing; must never throw. */
    boolean handle(KeyEvent event) {
        try {
            return handleKey(event) || handleShortcut(event);
        } catch (RuntimeException e) {
            Log.e(TAG, "handle " + event, e);
            return false;
        }
    }

    private boolean handleKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean relevant = code == KeyEvent.KEYCODE_F24 || code == KeyEvent.KEYCODE_MACRO_1
                || code == KeyEvent.KEYCODE_MACRO_2 || code == KeyEvent.KEYCODE_MACRO_3
                || code == KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE
                || code == KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_UP
                || code == KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_DOWN;
        if (!relevant || !PenKeys.isLenovoDevice(event)) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return true;
        Safe.post(mHandler, "keyboard key", () -> onKey(code));
        return true;
    }

    private static boolean isPhysicalKeyboard(KeyEvent event) {
        InputDevice dev = event.getDevice();
        return dev != null && !dev.isVirtual() && dev.isExternal()
                && dev.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
    }

    /**
     * Stock "Shortcuts setting": every Lenovo shortcut has a switch. A switched
     * off shortcut is swallowed; a switched on one runs the stock action where
     * Android has none and otherwise goes on to the framework (KeyGestureController).
     */
    private boolean handleShortcut(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (!down) {
            if (KeyEvent.isModifierKey(code)) cancelCtrlHold();
            return mSwallowed.remove(code);
        }
        if (!isPhysicalKeyboard(event)) return false;

        boolean ctrlOnly = (code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT)
                && !event.isShiftPressed() && !event.isAltPressed() && !event.isMetaPressed();
        if (ctrlOnly) {
            if (event.getRepeatCount() == 0) {
                cancelCtrlHold();
                mCtrlPending = true;
                mHandler.postDelayed(mCtrlHeld, CTRL_HOLD_MS);
            }
            return false;
        }
        cancelCtrlHold();

        String combo = comboFor(event);
        if (combo == null) return false;
        if (event.getRepeatCount() > 0) return mSwallowed.contains(code);
        if (getSystem(combo, 1) == 0) {
            Log.d(TAG, "shortcut off: " + combo);
            mSwallowed.add(code);
            return true;
        }
        Runnable action = actionFor(combo);
        if (action == null) return false;
        mSwallowed.add(code);
        Safe.post(mHandler, "shortcut " + combo, action);
        return true;
    }

    private static String comboFor(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.isMetaPressed()) {
            switch (code) {
                case KeyEvent.KEYCODE_D: return COMBO_WIN_D;
                case KeyEvent.KEYCODE_N: return COMBO_WIN_N;
                case KeyEvent.KEYCODE_L: return COMBO_WIN_L;
                case KeyEvent.KEYCODE_W: return COMBO_WIN_W;
                case KeyEvent.KEYCODE_S: return COMBO_WIN_S;
                case KeyEvent.KEYCODE_A: return COMBO_WIN_A;
                case KeyEvent.KEYCODE_E: return COMBO_WIN_E;
                case KeyEvent.KEYCODE_I: return COMBO_WIN_I;
                case KeyEvent.KEYCODE_M: return COMBO_WIN_M;
                case KeyEvent.KEYCODE_ESCAPE:
                case KeyEvent.KEYCODE_DEL: return COMBO_WIN_BACK;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT: return COMBO_LR_ARROW;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN: return COMBO_UD_ARROW;
                default:
                    if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9) {
                        return COMBO_WIN_NUMBER;
                    }
                    return null;
            }
        }
        if (event.isAltPressed()) {
            if (code == KeyEvent.KEYCODE_TAB) return COMBO_ALT_TAB;
            if (code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                return COMBO_ALT_SHIFT;
            }
        }
        if (event.isCtrlPressed()) {
            if (code == KeyEvent.KEYCODE_SPACE) return COMBO_CTRL_SPACE;
            if (code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                return COMBO_CTRL_SHIFT;
            }
        }
        return null;
    }

    /** The stock action for shortcuts Android does not handle itself, or null. */
    private Runnable actionFor(String combo) {
        switch (combo) {
            case COMBO_WIN_D: return () -> injectKey(KeyEvent.KEYCODE_HOME);
            case COMBO_WIN_N: return () -> mContext.getSystemService(StatusBarManager.class)
                    .expandNotificationsPanel();
            case COMBO_WIN_S: return () -> injectKey(KeyEvent.KEYCODE_SEARCH);
            case COMBO_WIN_E: return () -> startActivity(Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES));
            case COMBO_WIN_I: return () -> startActivity(new Intent(Settings.ACTION_SETTINGS));
            default: return null;
        }
    }

    private void cancelCtrlHold() {
        if (mCtrlPending) {
            mCtrlPending = false;
            mHandler.removeCallbacks(mCtrlHeld);
        }
    }

    /** Stock: hold Ctrl for 3 seconds to see the keyboard shortcuts. */
    private void showShortcutHelper() {
        mCtrlPending = false;
        if (getSystem(COMBO_CTRL_3, 1) == 0) return;
        Intent intent = new Intent(Intent.ACTION_SHOW_KEYBOARD_SHORTCUTS)
                .setPackage("com.android.systemui");
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    private void onKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_F24: {
                boolean on = getGlobal(SETTING_TOUCHPAD, 1) == 0;
                putGlobal(SETTING_TOUCHPAD, on ? 1 : 0); // observer applies it
                toast(on ? "터치패드 켜짐" : "터치패드 꺼짐", on ? "Touchpad on" : "Touchpad off");
                break;
            }
            case KeyEvent.KEYCODE_MACRO_1:
                runAction(getSystemString(SETTING_APP1), DEFAULT_APP1);
                break;
            case KeyEvent.KEYCODE_MACRO_2:
                runAction(getSystemString(SETTING_APP2), DEFAULT_APP2);
                break;
            case KeyEvent.KEYCODE_MACRO_3: {
                int fn = getGlobal(SETTING_FN_LOCK, 0) == 0 ? 1 : 0;
                putGlobal(SETTING_FN_LOCK, fn);
                LenovoHal.setKeyboardStatus(KB_FN_LOCK_LED, fn, -1);
                toast(fn == 1 ? "Fn 잠금 켜짐" : "Fn 잠금 꺼짐", fn == 1 ? "Fn lock on" : "Fn lock off");
                break;
            }
            case KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE:
            case KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_UP:
            case KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_DOWN: {
                // Stock getNextBackLightLevel: off -> low -> high (-> auto) -> off
                int max = autoBacklightAllowed() ? AUTO_BACKLIGHT : MAX_BACKLIGHT;
                int level = Math.min(backlightLevel(), max);
                if (code == KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE) {
                    level = level >= max ? 0 : level + 1;
                } else if (code == KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_UP) {
                    level = Math.min(max, level + 1);
                } else {
                    level = Math.max(0, level - 1);
                }
                putGlobal(SETTING_BACKLIGHT, level);
                LenovoHal.setKeyboardStatus(KB_BACKLIGHT, level, -1);
                break;
            }
        }
    }

    /** Actions selectable for the APP1 / APP2 keys in TB520FUParts. */
    private void runAction(String action, String def) {
        if (TextUtils.isEmpty(action)) action = def;
        Log.d(TAG, "app key action " + action);
        if (action.startsWith(ACTION_LAUNCH)) {
            Intent launch = mContext.getPackageManager().getLaunchIntentForPackage(
                    action.substring(ACTION_LAUNCH.length()));
            if (launch != null) startActivity(launch);
            return;
        }
        if (action.startsWith(ACTION_URL)) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(action.substring(ACTION_URL.length()))));
            return;
        }
        StatusBarManager sbm = mContext.getSystemService(StatusBarManager.class);
        switch (action) {
            case "screenshot":
                injectKey(KeyEvent.KEYCODE_SYSRQ);
                break;
            case "notifications":
                sbm.expandNotificationsPanel();
                break;
            case "quick_settings":
                sbm.expandSettingsPanel();
                break;
            case "recents":
                injectKey(KeyEvent.KEYCODE_RECENT_APPS);
                break;
            case "assistant":
                injectKey(KeyEvent.KEYCODE_ASSIST);
                break;
            case "search":
                injectKey(KeyEvent.KEYCODE_SEARCH);
                break;
            case "settings":
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                break;
            default:
                break;
        }
    }

    private void startActivity(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivityAsUser(intent, UserHandle.CURRENT);
        } catch (RuntimeException e) {
            Log.w(TAG, "start " + intent, e);
        }
    }

    private void injectKey(int code) {
        InputManager im = mContext.getSystemService(InputManager.class);
        long now = SystemClock.uptimeMillis();
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent ev = new KeyEvent(now, now, action, code, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
            im.injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    }

    private void toast(String ko, String en) {
        boolean korean = "ko".equals(mContext.getResources().getConfiguration()
                .getLocales().get(0).getLanguage());
        Toast.makeText(mContext, korean ? ko : en, Toast.LENGTH_SHORT).show();
    }

    private int getSystem(String key, int def) {
        return Settings.System.getIntForUser(mContext.getContentResolver(), key, def,
                UserHandle.USER_CURRENT);
    }

    private String getSystemString(String key) {
        return Settings.System.getStringForUser(mContext.getContentResolver(), key,
                UserHandle.USER_CURRENT);
    }

    private int getGlobal(String key, int def) {
        return Settings.Global.getInt(mContext.getContentResolver(), key, def);
    }

    private void putGlobal(String key, int value) {
        Settings.Global.putInt(mContext.getContentResolver(), key, value);
    }
}
