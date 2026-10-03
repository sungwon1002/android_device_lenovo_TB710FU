/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lenovo pen connection, ported from the stock BaseBluetoothPolicy /
 * BluetoothPenConnectPolicy:
 *  - the charger driver reports the magnetically attached pen (MAC, level,
 *    charge state) with UEVENT_TO=PEN_FRAMEWORK uevents; attaching a pen
 *    connects it (bonded) or scans + bonds it without a pairing dialog,
 *  - HID connection changes, battery (BAS + pad Qi), SN and firmware version
 *    are broadcast with the lenovo.intent.action.PEN_* contract that
 *    PenService and ZuiUDevice (connect popup, battery overlay) listen to,
 *  - the touch panel is switched to pen mode while a pen is connected.
 */
final class PenController implements PenGatt.Listener {
    private static final String TAG = "TB520FUPen";

    static final int TYPE_NONE = 0;
    static final int TYPE_PICASSO = 1;
    static final int TYPE_PARKER = 2;
    static final int TYPE_SHEAFFER = 3;
    static final int TYPE_PARKER_2 = 4;

    static final String NAME_PARKER = "Lenovo Tab Pen Pro";
    static final String NAME_PARKER_2 = "Lenovo Tab Pen Pro 2";
    static final String NAME_PICASSO = "Lenovo Tab Pen Plus";
    static final String NAME_SHEAFFER = "Lenovo Precision Pen 3";

    static final int CHARGE_CHARGING = 1;
    static final int CHARGE_NOT_CHARGING = 2;
    static final int CHARGE_FULL = 3;
    static final int CHARGE_LOW_BATTERY = 6;

    private static final String ACTION_ATTACH_CHANGED = "lenovo.intent.action.PEN_ATTACH_CHANGED";
    private static final String ACTION_BATTERY_CHANGED = "lenovo.intent.action.PEN_BATTERY_CHANGED";
    private static final String ACTION_BT_CHANGED = "lenovo.intent.action.PEN_BT_CHANGED";
    private static final String ACTION_FIRST_PAIR = "lenovo.intent.action.PEN_FIRST_PAIR";
    private static final String ACTION_SN = "lenovo.intent.action.PEN_SN";
    private static final String ACTION_VERSION = "lenovo.intent.action.PEN_VERSION";
    private static final String ACTION_LOW_BATTERY = "lenovo.intent.action.PEN_LOW_BATTERY";
    private static final String ACTION_NEED_OPENBT = "lenovo.intent.action.PEN_NEED_OPENBT";
    private static final String ACTION_LINK_TOUCHED = "lenovo.intent.action.PEN_LINK_TOUCHED";
    private static final String ACTION_LINK_RECONNECT = "lenovo.intent.action.PEN_LINK_RECONNECT";
    private static final int BROADCAST_FLAGS =
            Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND;

    private static final String SETTING_PEN_STATUS = "lenovo_pen_status";
    private static final String SETTING_TAP_TWO = "pen_touch_film_tap_two";
    private static final String SETTING_COPY_PASTE = "pen_touch_film_copy_paste";
    private static final String SETTING_REMOTE_CONTROL = "pen_set_remote_control_on";
    private static final String SETTING_PENLINK_NOT_ALLOW = "penlink_not_allow";
    /** Pens that were connected before, to tell the first pairing apart. */
    private static final String SETTING_KNOWN_PENS = "tb520fu_known_pens";
    private static final String PROP_PEN_SN = "persist.sys.lenovo.pen.sn";

    private static final String WLS_TX = "/sys/class/power_supply/wls_tx/";

    // battery HAL setStylusQiCommand
    private static final int QI_DISCONNECT_PAD = 401;
    private static final int QI_CLOSE_RX = 402;
    private static final int QI_SWITCH_OPEN = 403;

    // common characteristic notifications
    private static final int COMMON_PEN_STILL = 10;
    private static final int COMMON_PEN_MOVE = 266;
    private static final int COMMON_QI_OPEN_REQUEST = 1029;

    private static final long SCAN_TIMEOUT_MS = 30000;

    static final class Pen {
        String mac = "";
        String name = "";
        int type;
        int level = -1;
        int charge;
        int attached;
        int connectState;
        String sn = "";

        void clear() {
            mac = "";
            name = "";
            type = TYPE_NONE;
            level = -1;
            charge = 0;
            sn = "";
        }
    }

    private final Context mContext;
    private final Handler mHandler;
    private final PenHaptics mHaptics;
    private final PowerManager mPowerManager;
    private final Pen mAttached = new Pen();
    private final Pen mConnected = new Pen();
    private final Map<String, String> mNames = new HashMap<>();

    private BluetoothAdapter mAdapter;
    private BluetoothHidHost mHidHost;
    private PenGatt mGatt;
    private String mPogoMac;
    private String mTpInfo;
    private boolean mFirstPair;
    private boolean mCloseRxSent;
    private boolean mOpenBtSent;
    private boolean mLowBatterySent;
    private boolean mWaitForBtOn;
    private boolean mScanning;
    private boolean mParkerSleeping;
    private boolean mScreenOn = true;
    private boolean mLost;
    private String mLastUevent;
    private long mLastConnectAttempt;
    private boolean mProxyRequested;

    private final UEventObserver mPenObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEvent event) {
            // Runs on the UEventObserver thread: copy what we need and hop over.
            final String type = event.get("TYPE");
            final String mac = event.get("MAC");
            final String attached = event.get("ATTACHED");
            final String level = event.get("LEVEL");
            final String charge = event.get("CHARGING_STATE");
            final String penType = event.get("PEN_TYPE");
            final String tpInfo = event.get("TOUCH_INFORMATION");
            Safe.post(mHandler, "pen uevent", () ->
                    onPenUevent(type, mac, attached, level, charge, penType, tpInfo));
        }
    };

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Safe.post(mHandler, "scan result", () -> onPenScanResult(result.getDevice()));
        }
    };

    private final Runnable mScanTimeout = Safe.run("scan timeout", () -> {
        Log.d(TAG, "scan timeout for " + mPogoMac);
        stopScan();
        if (isParker(mAttached.type)) LenovoHal.setStylusQiCommand(QI_DISCONNECT_PAD);
    });

    PenController(Context context, Handler handler, PenHaptics haptics) {
        mContext = context;
        mHandler = handler;
        mHaptics = haptics;
        mPowerManager = context.getSystemService(PowerManager.class);
    }

    void start() {
        BluetoothManager bm = mContext.getSystemService(BluetoothManager.class);
        mAdapter = bm != null ? bm.getAdapter() : null;

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiverAsUser(Safe.receiver("pen", (c, i) -> onBroadcast(i)),
                UserHandle.ALL, filter, null, mHandler, Context.RECEIVER_EXPORTED);

        // Confirm the pairing of the attached pen before Settings shows a dialog.
        IntentFilter pairing = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairing.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiverAsUser(new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice.class);
                    if (dev != null && dev.getAddress().equals(mPogoMac)) {
                        Log.d(TAG, "auto confirm pairing of attached pen " + mPogoMac);
                        abortBroadcast();
                        dev.setPairingConfirmation(true);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "pairing request", t);
                }
            }
        }, UserHandle.ALL, pairing, null, mHandler, Context.RECEIVER_EXPORTED);

        requestHidProxy();

        mPenObserver.startObserving("UEVENT_TO=PEN_FRAMEWORK");
        readAttachedFromSysfs();
    }

    /** The HID host proxy only connects while the profile is enabled; retried on BT on. */
    private void requestHidProxy() {
        if (mAdapter == null || mHidHost != null || mProxyRequested) return;
        mProxyRequested = mAdapter.getProfileProxy(mContext, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                Safe.post(mHandler, "hid proxy", () -> {
                    Log.d(TAG, "HID host proxy connected");
                    mHidHost = (BluetoothHidHost) proxy;
                    mProxyRequested = false;
                    syncConnectedPens();
                    if (mAttached.attached != 0) connectAttachedPen();
                });
            }

            @Override
            public void onServiceDisconnected(int profile) {
                Safe.post(mHandler, "hid proxy lost", () -> {
                    mHidHost = null;
                    mProxyRequested = false;
                });
            }
        }, BluetoothProfile.HID_HOST);
        if (!mProxyRequested) Log.w(TAG, "HID host profile not available");
    }

    // ---- attach (charger uevents) ----

    private void readAttachedFromSysfs() {
        String attached = readFile(WLS_TX + "attached");
        String mac = readFile(WLS_TX + "mac");
        if (attached == null || mac == null) return;
        Log.d(TAG, "initial attach state " + attached + " " + mac);
        onPenUevent("QI", mac, attached, readFile(WLS_TX + "level"),
                readFile(WLS_TX + "charge_state"), null, null);
    }

    private void onPenUevent(String type, String mac, String attachedStr, String level,
            String charge, String penType, String tpInfo) {
        String key = type + "/" + mac + "/" + attachedStr + "/" + level + "/" + charge + "/"
                + penType + "/" + tpInfo;
        if (key.equals(mLastUevent)) return;
        mLastUevent = key;
        Log.d(TAG, "uevent type=" + type + " mac=" + mac + " attached=" + attachedStr
                + " level=" + level + " charge=" + charge + " penType=" + penType);
        if ("TP".equals(type)) {
            if (tpInfo != null) {
                mTpInfo = tpInfo;
                sendTpInfo();
            }
            if (attachedStr == null && mac != null) onPenLink(mac.toUpperCase());
            return;
        }
        if (!"QI".equals(type) && !"NFC".equals(type)) return;
        if (attachedStr == null) return;

        int attached = "1".equals(attachedStr.trim()) ? 1 : 0;
        int chargeState = parseCharge(charge);
        String upper = mac != null ? mac.trim().toUpperCase() : "";
        checkChargeStateFull(chargeState, attached);
        initAttachedPen(upper, level, chargeState, attached, type, penType);
        if (!validMac(upper) && attached != 0) {
            checkPenLowBattery();
            return;
        }
        mPogoMac = upper;
        if (!checkPenLowBattery()) onAttachChanged(upper, attached);
    }

    private void initAttachedPen(String mac, String levelStr, int charge, int attached,
            String type, String penType) {
        mAttached.mac = mac;
        if (mAttached.attached != attached) {
            mAttached.attached = attached;
            sendAttachChanged();
        }
        boolean same = mac.equals(mConnected.mac);
        if (same) mConnected.attached = attached;
        if (attached == 0) {
            mConnected.attached = 0;
            mCloseRxSent = false;
            mOpenBtSent = false;
            mLowBatterySent = false;
        }
        if ("QI".equals(type)) {
            if ("2".equals(penType)) {
                mAttached.type = TYPE_PARKER_2;
                mAttached.name = NAME_PARKER_2;
            } else {
                mAttached.type = TYPE_PARKER;
                mAttached.name = NAME_PARKER;
            }
        } else if ("NFC".equals(type)) {
            mAttached.type = TYPE_SHEAFFER;
            mAttached.name = NAME_SHEAFFER;
        }
        if (same) {
            mConnected.type = mAttached.type;
            mConnected.name = mAttached.name;
        }
        if (attached == 0) {
            mAttached.charge = CHARGE_NOT_CHARGING;
            if (mConnected.type == TYPE_PARKER || mConnected.type == TYPE_SHEAFFER) {
                mConnected.charge = CHARGE_NOT_CHARGING;
                updateBatteryLevel(mConnected.mac, mConnected.level, 0);
            }
            return;
        }
        if (!TextUtils.isEmpty(levelStr)) {
            int level;
            try {
                level = Integer.parseInt(levelStr.trim());
            } catch (NumberFormatException e) {
                level = 0;
            }
            if (mAttached.level != level) {
                mAttached.level = level;
                if (same) mConnected.level = level;
                updateBatteryLevel(mac, level, 0);
            }
        }
        if (mAttached.charge != charge) {
            mAttached.charge = charge;
            if (same) mConnected.charge = charge;
            updateBatteryLevel(mac, mAttached.level, 0);
        }
    }

    private void onAttachChanged(String mac, int attached) {
        if (attached == 0) {
            mPogoMac = null;
            stopScan();
            return;
        }
        if (!mPowerManager.isInteractive()) {
            mPowerManager.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_UNKNOWN,
                    "PENATTACHED");
        }
        if (!validMac(mac) || mAdapter == null) return;
        if (!mAdapter.isEnabled()) {
            if (Settings.Global.getInt(mContext.getContentResolver(), "bluetooth_on", 1) == 0) {
                if (!mOpenBtSent) {
                    mOpenBtSent = true;
                    Intent i = new Intent(ACTION_NEED_OPENBT);
                    i.addFlags(BROADCAST_FLAGS);
                    i.putExtra("type", mAttached.type);
                    sendBroadcast(i);
                }
            } else {
                mWaitForBtOn = true;
            }
            return;
        }
        connectAttachedPen();
    }

    private void connectAttachedPen() {
        String mac = mPogoMac;
        if (!validMac(mac) || mAdapter == null) return;
        if (mac.equals(mConnected.mac) && mConnected.connectState == BluetoothProfile.STATE_CONNECTED) {
            tellParkerCloseRx();
            return;
        }
        BluetoothDevice dev = mAdapter.getRemoteDevice(mac);
        int bond = dev.getBondState();
        Log.d(TAG, "attached pen " + mac + " bond " + bond);
        if (bond == BluetoothDevice.BOND_BONDED) {
            if (mHidHost == null) {
                requestHidProxy();
                return;
            }
            int state = mHidHost.getConnectionState(dev);
            long now = SystemClock.elapsedRealtime();
            if (state == BluetoothProfile.STATE_DISCONNECTED && now - mLastConnectAttempt > 5000) {
                mLastConnectAttempt = now;
                Log.d(TAG, "reconnect HID " + mac);
                mHidHost.setConnectionPolicy(dev, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            }
        } else if (bond == BluetoothDevice.BOND_NONE) {
            startScan(mac);
            sendAttachChanged();
        }
    }

    // ---- pen link: a known pen touched the screen ----

    private void onPenLink(String mac) {
        if (!validMac(mac) || mAdapter == null || !mAdapter.isEnabled()) return;
        if (mac.equals(mConnected.mac)) return;
        String notAllowed = Settings.System.getStringForUser(mContext.getContentResolver(),
                SETTING_PENLINK_NOT_ALLOW, UserHandle.USER_CURRENT);
        BluetoothDevice dev = mAdapter.getRemoteDevice(mac);
        if (dev.getBondState() == BluetoothDevice.BOND_BONDED && mHidHost != null) {
            Intent i = new Intent(ACTION_LINK_RECONNECT);
            i.addFlags(BROADCAST_FLAGS);
            i.putExtra("address", mac);
            sendBroadcast(i);
            int state = mHidHost.getConnectionState(dev);
            LenovoHal.setPenBle(state == BluetoothProfile.STATE_DISCONNECTED);
            if (state == BluetoothProfile.STATE_DISCONNECTED) {
                mHidHost.setConnectionPolicy(dev, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
            }
            return;
        }
        if (!TextUtils.isEmpty(notAllowed) && notAllowed.contains(mac)) return;
        LenovoHal.setPenBle(true);
        Intent i = new Intent(ACTION_LINK_TOUCHED);
        i.addFlags(BROADCAST_FLAGS);
        i.putExtra("address", mac);
        sendBroadcast(i);
    }

    // ---- scan + bond an attached, unknown pen ----

    private void startScan(String mac) {
        if (mScanning || mAdapter == null) return;
        BluetoothLeScanner scanner = mAdapter.getBluetoothLeScanner();
        if (scanner == null) return;
        ScanFilter filter = new ScanFilter.Builder().setDeviceAddress(mac).build();
        ScanSettings settings = new ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setLegacy(false)
                .build();
        Log.d(TAG, "scan for " + mac);
        mScanning = true;
        scanner.startScan(Collections.singletonList(filter), settings, mScanCallback);
        mHandler.removeCallbacks(mScanTimeout);
        mHandler.postDelayed(mScanTimeout, SCAN_TIMEOUT_MS);
    }

    private void stopScan() {
        mHandler.removeCallbacks(mScanTimeout);
        if (!mScanning || mAdapter == null) return;
        mScanning = false;
        BluetoothLeScanner scanner = mAdapter.getBluetoothLeScanner();
        if (scanner != null) scanner.stopScan(mScanCallback);
    }

    private void onPenScanResult(BluetoothDevice dev) {
        if (!mScanning || dev == null || !dev.getAddress().equals(mPogoMac)) return;
        stopScan();
        Log.d(TAG, "found attached pen, bonding " + dev.getAddress());
        dev.createBond(BluetoothDevice.TRANSPORT_LE);
    }

    // ---- broadcasts from the system ----

    private void onBroadcast(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED: {
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice.class);
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED);
                if (dev != null) onHidStateChanged(dev, state);
                break;
            }
            case BluetoothAdapter.ACTION_STATE_CHANGED: {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                if (state == BluetoothAdapter.STATE_ON) requestHidProxy();
                if (state == BluetoothAdapter.STATE_ON && mWaitForBtOn) {
                    mWaitForBtOn = false;
                    connectAttachedPen();
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    onPenDisconnected(mConnected.mac);
                }
                break;
            }
            case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice.class);
                int bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (dev != null && dev.getAddress().equals(mPogoMac)
                        && bond == BluetoothDevice.BOND_BONDED) {
                    Safe.postDelayed(mHandler, "connect after bond", this::connectAttachedPen, 1000);
                }
                break;
            }
            case Intent.ACTION_SCREEN_ON:
            case Intent.ACTION_SCREEN_OFF:
                mScreenOn = Intent.ACTION_SCREEN_ON.equals(action);
                writePenGsensorFlag();
                break;
        }
    }

    private void syncConnectedPens() {
        if (mHidHost == null) return;
        List<BluetoothDevice> devices = mHidHost.getConnectedDevices();
        if (devices == null) return;
        for (BluetoothDevice dev : devices) {
            if (isLenovoPen(dev)) {
                onHidStateChanged(dev, BluetoothProfile.STATE_CONNECTED);
                break;
            }
        }
    }

    private boolean isLenovoPen(BluetoothDevice dev) {
        String mac = dev.getAddress();
        String name = mNames.get(mac);
        if (TextUtils.isEmpty(name)) name = dev.getName();
        if (name != null && (name.contains(NAME_PARKER) || name.contains(NAME_PICASSO)
                || name.contains(NAME_SHEAFFER))) {
            return true;
        }
        return mac.equals(mConnected.mac) || mac.equals(mAttached.mac);
    }

    static int typeByName(String name) {
        if (name == null) return TYPE_NONE;
        if (name.contains(NAME_SHEAFFER)) return TYPE_SHEAFFER;
        if (name.contains(NAME_PICASSO)) return TYPE_PICASSO;
        if (name.contains(NAME_PARKER_2)) return TYPE_PARKER_2;
        if (name.contains(NAME_PARKER)) return TYPE_PARKER;
        return TYPE_NONE;
    }

    static boolean isParker(int type) {
        return type == TYPE_PARKER || type == TYPE_PARKER_2;
    }

    // ---- HID connection ----

    private void onHidStateChanged(BluetoothDevice dev, int state) {
        if (!isLenovoPen(dev)) return;
        String mac = dev.getAddress();
        String name = mNames.get(mac);
        if (TextUtils.isEmpty(name)) name = dev.getName();
        Log.d(TAG, "pen HID " + mac + " (" + name + ") state " + state);
        if (state == BluetoothProfile.STATE_CONNECTED) {
            onPenConnected(dev, name);
        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
            onPenDisconnected(mac);
        }
    }

    private void onPenConnected(BluetoothDevice dev, String name) {
        String mac = dev.getAddress();
        if (mac.equals(mConnected.mac) && mConnected.connectState == BluetoothProfile.STATE_CONNECTED
                && mGatt != null) {
            return;
        }
        mLost = false;
        mConnected.mac = mac;
        mConnected.name = name != null ? name : "";
        mConnected.type = typeByName(name);
        mConnected.connectState = BluetoothProfile.STATE_CONNECTED;
        mConnected.sn = "";
        mConnected.level = mac.equals(mAttached.mac) ? mAttached.level : -1;
        mConnected.charge = mac.equals(mAttached.mac) ? mAttached.charge : 0;
        mConnected.attached = mac.equals(mAttached.mac) ? mAttached.attached : 0;
        if (!TextUtils.isEmpty(name)) mNames.put(mac, name);

        LenovoHal.setPenMode(true);
        LenovoHal.setPenBle(false);
        updatePenStatusSetting();

        mFirstPair = !isKnownPen(mac);
        if (mFirstPair) rememberPen(mac);
        sendBtChanged(1, mac, mFirstPair);
        disconnectOtherPens(mac);

        if (mGatt != null) mGatt.close();
        mGatt = new PenGatt(mContext, mHandler, dev, this);
        mGatt.connect();
    }

    private void onPenDisconnected(String mac) {
        if (TextUtils.isEmpty(mac) || !mac.equals(mConnected.mac)) return;
        mConnected.connectState = BluetoothProfile.STATE_DISCONNECTED;
        if (mGatt != null) {
            mGatt.close();
            mGatt = null;
        }
        mHaptics.onPenDisconnected();
        LenovoHal.setPenMode(false);
        sendBtChanged(0, mac, false);
        mConnected.mac = "";
        updatePenStatusSetting();
        mParkerSleeping = false;
    }

    private void disconnectOtherPens(String keep) {
        if (mHidHost == null) return;
        List<BluetoothDevice> devices = mHidHost.getConnectedDevices();
        if (devices == null) return;
        for (BluetoothDevice dev : devices) {
            if (!dev.getAddress().equals(keep) && typeByName(dev.getName()) != TYPE_NONE) {
                Log.d(TAG, "disconnect other pen " + dev.getAddress());
                dev.disconnect();
            }
        }
    }

    private void updatePenStatusSetting() {
        int status = TextUtils.isEmpty(mConnected.mac)
                || mConnected.connectState != BluetoothProfile.STATE_CONNECTED ? 0 : mConnected.type;
        Settings.Global.putInt(mContext.getContentResolver(), SETTING_PEN_STATUS, status);
    }

    private boolean isKnownPen(String mac) {
        String known = Settings.Global.getString(mContext.getContentResolver(), SETTING_KNOWN_PENS);
        return known != null && known.contains(mac);
    }

    private void rememberPen(String mac) {
        ContentResolver cr = mContext.getContentResolver();
        String known = Settings.Global.getString(cr, SETTING_KNOWN_PENS);
        Settings.Global.putString(cr, SETTING_KNOWN_PENS,
                TextUtils.isEmpty(known) ? mac : known + ";" + mac);
    }

    int connectedType() {
        return mConnected.connectState == BluetoothProfile.STATE_CONNECTED ? mConnected.type : 0;
    }

    void setLost(boolean lost) {
        mLost = lost;
    }

    // ---- GATT ----

    @Override
    public void onGattReady(PenGatt gatt) {
        if (gatt != mGatt) return;
        Log.d(TAG, "GATT ready for " + gatt.address());
        gatt.read(PenGatt.BATTERY_SERVICE, PenGatt.BATTERY_LEVEL);
        gatt.setNotify(PenGatt.BATTERY_SERVICE, PenGatt.BATTERY_LEVEL, true);
        gatt.setNotify(PenGatt.BATTERY_SERVICE, PenGatt.BATTERY_STATE, true);
        gatt.read(PenGatt.DEVICE_INFO_SERVICE, PenGatt.SERIAL_NUMBER);
        gatt.read(PenGatt.DEVICE_INFO_SERVICE, PenGatt.FIRMWARE_VERSION);
        gatt.read(PenGatt.DEVICE_INFO_SERVICE, PenGatt.HARDWARE_VERSION);
        if (gatt.has(PenGatt.COMMON_SERVICE, PenGatt.COMMON_CHAR)) {
            gatt.setNotify(PenGatt.COMMON_SERVICE, PenGatt.COMMON_CHAR, true);
            writeTouchfilmConfig();
            writePenGsensorFlag();
            sendTpInfo();
        }
        mHaptics.onGattReady(gatt, mConnected.type);
    }

    @Override
    public void onGattLost(PenGatt gatt) {
        if (gatt != mGatt) return;
        mHaptics.onPenDisconnected();
    }

    @Override
    public void onRead(PenGatt gatt, UUID characteristic, byte[] value) {
        String mac = gatt.address();
        if (PenGatt.BATTERY_LEVEL.equals(characteristic)) {
            updateBatteryLevel(mac, value.length > 0 ? value[0] & 0xff : -1, 1);
        } else if (PenGatt.SERIAL_NUMBER.equals(characteristic)) {
            String sn = new String(value, StandardCharsets.UTF_8).trim();
            if (sn.matches("[A-Za-z0-9]+")) {
                mConnected.sn = sn;
                SystemProperties.set(PROP_PEN_SN, sn);
                Intent i = new Intent(ACTION_SN);
                i.addFlags(BROADCAST_FLAGS);
                i.putExtra("sn", sn);
                i.putExtra("address", mac);
                sendBroadcast(i);
            }
        } else if (PenGatt.FIRMWARE_VERSION.equals(characteristic)) {
            Intent i = new Intent(ACTION_VERSION);
            i.addFlags(BROADCAST_FLAGS);
            i.putExtra("version", new String(value, StandardCharsets.UTF_8).trim());
            i.putExtra("address", mac);
            sendBroadcast(i);
        } else if (PenGatt.HARDWARE_VERSION.equals(characteristic)) {
            if (mFirstPair) {
                mFirstPair = false;
                Intent i = new Intent(ACTION_FIRST_PAIR);
                i.addFlags(BROADCAST_FLAGS);
                i.putExtra("address", mac);
                i.putExtra("type", "bluetooth");
                i.putExtra("subtype", new String(value, StandardCharsets.UTF_8).trim());
                sendStickyBroadcast(i);
            }
        }
    }

    @Override
    public void onChanged(PenGatt gatt, UUID characteristic, byte[] value) {
        String mac = gatt.address();
        if (PenGatt.BATTERY_LEVEL.equals(characteristic)) {
            updateBatteryLevel(mac, value.length > 0 ? value[0] & 0xff : -1, 1);
        } else if (PenGatt.BATTERY_STATE.equals(characteristic)) {
            int charge = value.length > 0 && (value[0] & 0xff) == 48
                    ? CHARGE_CHARGING : CHARGE_NOT_CHARGING;
            mConnected.charge = charge;
            updateBatteryLevel(mConnected.mac, mConnected.level, 1);
        } else if (PenGatt.COMMON_CHAR.equals(characteristic)) {
            onCommonValue(PenGatt.intValue(value));
        }
    }

    private void onCommonValue(int v) {
        Log.d(TAG, "common value " + v);
        if (v == COMMON_QI_OPEN_REQUEST) {
            LenovoHal.setStylusQiCommand(QI_SWITCH_OPEN);
        } else if (v == COMMON_PEN_MOVE || v == COMMON_PEN_STILL) {
            if (v == COMMON_PEN_STILL) LenovoHal.ioctl(LenovoHal.IOCTL_ERASER, false);
            writeCommon(new byte[] {10, (byte) (v == COMMON_PEN_MOVE ? 1 : 0)});
        }
    }

    private void writeCommon(byte[] cmd) {
        if (mGatt != null && mGatt.isReady()) {
            mGatt.write(PenGatt.COMMON_SERVICE, PenGatt.COMMON_CHAR, cmd);
        }
    }

    /** Double tap / slide gestures of the pen barrel, as configured in PenService. */
    void writeTouchfilmConfig() {
        ContentResolver cr = mContext.getContentResolver();
        boolean doubleTap = Settings.Global.getInt(cr, SETTING_TAP_TWO, 1) > 0;
        boolean slide = Settings.Global.getInt(cr, SETTING_COPY_PASTE, 0) == 1;
        boolean remote = "1".equals(Settings.Secure.getStringForUser(cr, SETTING_REMOTE_CONTROL,
                UserHandle.USER_CURRENT));
        int flags = (doubleTap ? 1 : 0) | (slide ? 12 : 0) | (remote ? 13 : 0);
        writeCommon(new byte[] {8, 6, (byte) flags});
    }

    private void writePenGsensorFlag() {
        if (mConnected.type == TYPE_PARKER && mParkerSleeping) return;
        writeCommon(new byte[] {5, (byte) (mScreenOn ? 2 : 1)});
    }

    private void sendTpInfo() {
        if (mTpInfo == null || mGatt == null || !mGatt.isReady()) return;
        mGatt.write(PenGatt.COMMON_SERVICE, PenGatt.COMMON_CHAR,
                ("0700" + mTpInfo).getBytes(StandardCharsets.US_ASCII), "tp info");
    }

    private void checkChargeStateFull(int charge, int attached) {
        if (!isParker(mConnected.type) || mConnected.connectState != BluetoothProfile.STATE_CONNECTED) {
            return;
        }
        if (!mAttached.mac.equals(mConnected.mac)) return;
        if (attached == 0) {
            writeCommon(new byte[] {5, 5});
            mParkerSleeping = false;
        } else if (charge == CHARGE_FULL) {
            writeCommon(new byte[] {5, 3});
            mParkerSleeping = true;
        }
    }

    private void tellParkerCloseRx() {
        if (mCloseRxSent) return;
        mCloseRxSent = true;
        Log.d(TAG, "close parker rx: " + LenovoHal.setStylusQiCommand(QI_CLOSE_RX));
    }

    // ---- battery / status broadcasts ----

    private Pen penFor(String mac) {
        if (mac != null && mac.equals(mConnected.mac)) return mConnected;
        if (mac != null && mac.equals(mAttached.mac)) return mAttached;
        return null;
    }

    /** Name and type of the pen last reported to PenService, shown by PenIsland. */
    static volatile String sLastPenName;
    static volatile int sLastPenType;

    private void updateBatteryLevel(String mac, int level, int source) {
        if (!validMac(mac)) return;
        Pen pen = penFor(mac);
        if (pen == null) return;
        pen.level = level;
        sLastPenName = pen.name;
        sLastPenType = pen.type;
        if (level < 0 || level > 100) return;
        int connected = -1;
        if (mHidHost != null && mAdapter != null) {
            connected = mHidHost.getConnectionState(mAdapter.getRemoteDevice(mac));
        }
        Intent i = new Intent(ACTION_BATTERY_CHANGED);
        i.addFlags(BROADCAST_FLAGS);
        i.putExtra("level", level);
        i.putExtra("charging_state", pen.charge);
        i.putExtra("attached", pen.attached);
        i.putExtra("attached_mac", mPogoMac);
        i.putExtra("address", mac);
        i.putExtra("sn", pen.sn);
        i.putExtra("type", pen.type);
        i.putExtra("name", pen.name);
        i.putExtra("source", source);
        i.putExtra("connected", connected);
        sendStickyBroadcast(i);
    }

    private void sendBtChanged(int status, String mac, boolean firstPair) {
        Pen pen = penFor(mac);
        if (pen == null) pen = mConnected;
        Intent i = new Intent(ACTION_BT_CHANGED);
        i.addFlags(BROADCAST_FLAGS);
        i.putExtra("status", status);
        i.putExtra("address", mac);
        i.putExtra("level", pen.level);
        i.putExtra("sn", pen.sn);
        i.putExtra("type", pen.type);
        i.putExtra("name", pen.name);
        i.putExtra("isfirstpair", firstPair);
        i.putExtra("attached", pen.attached);
        i.putExtra("attached_mac", mPogoMac);
        sendBroadcast(i);
    }

    private void sendAttachChanged() {
        Intent i = new Intent(ACTION_ATTACH_CHANGED);
        i.addFlags(BROADCAST_FLAGS);
        i.putExtra("status", mAttached.attached);
        i.putExtra("type", mAttached.type);
        sendStickyBroadcast(i);
    }

    private boolean checkPenLowBattery() {
        if (mLowBatterySent || mAttached.attached == 0) return false;
        if (Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) == 0) {
            return false;
        }
        boolean low = isParker(mAttached.type) && mAttached.charge == CHARGE_LOW_BATTERY;
        if (low) {
            mLowBatterySent = true;
            Intent i = new Intent(ACTION_LOW_BATTERY);
            i.addFlags(BROADCAST_FLAGS);
            sendBroadcast(i);
        }
        return low;
    }

    private void sendBroadcast(Intent i) {
        Log.d(TAG, "broadcast " + i + " " + i.getExtras());
        mContext.sendBroadcastAsUser(i, UserHandle.ALL);
    }

    @SuppressWarnings("deprecation")
    private void sendStickyBroadcast(Intent i) {
        Log.d(TAG, "sticky broadcast " + i + " " + i.getExtras());
        mContext.sendStickyBroadcastAsUser(i, UserHandle.ALL);
    }

    // ---- helpers ----

    static int parseCharge(String s) {
        if (s == null) return 0;
        s = s.trim();
        switch (s) {
            case "Charging": return CHARGE_CHARGING;
            case "Notcharging": return CHARGE_NOT_CHARGING;
            case "Full": return CHARGE_FULL;
            case "Hot_Notcharging": return 4;
            case "Cold_Notcharging": return 5;
            case "BatteryOverLow": return CHARGE_LOW_BATTERY;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean validMac(String mac) {
        return mac != null && !"00:00:00:00:00:00".equals(mac) && !"FF:FF:FF:FF:FF:FF".equals(mac)
                && BluetoothAdapter.checkBluetoothAddress(mac);
    }

    private static String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
