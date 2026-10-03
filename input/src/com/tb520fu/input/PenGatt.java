/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;

/**
 * One GATT client per connected Lenovo pen. Android allows a single
 * outstanding GATT operation per client, so reads/writes go through a queue;
 * writes that carry a key (haptic state) replace an older queued write with
 * the same key instead of piling up.
 */
final class PenGatt extends BluetoothGattCallback {
    private static final String TAG = "TB520FUPenGatt";

    static final UUID CLIENT_CONFIG = uuid16(0x2902);

    // Standard services
    static final UUID BATTERY_SERVICE = uuid16(0x180F);
    static final UUID BATTERY_LEVEL = uuid16(0x2A19);
    static final UUID BATTERY_STATE = uuid16(0x2A1A);
    static final UUID DEVICE_INFO_SERVICE = uuid16(0x180A);
    static final UUID SERIAL_NUMBER = uuid16(0x2A25);
    static final UUID FIRMWARE_VERSION = uuid16(0x2A26);
    static final UUID HARDWARE_VERSION = uuid16(0x2A27);

    // Lenovo common service (also Parker charge / 6DOF switch)
    static final UUID COMMON_SERVICE = UUID.fromString("0000fe40-cc7a-482a-984a-7f2ed5b3e512");
    static final UUID COMMON_CHAR = UUID.fromString("0000fe41-cc7a-482a-984a-7f2ed5b3e512");

    // Lenovo haptic service (Tab Pen Pro)
    static final UUID HAPTIC_SERVICE = UUID.fromString("00000000-000f-11e1-9ab4-0002a5d5c51b");
    static final UUID HAPTIC_REQ_INFO = UUID.fromString("00000002-000f-11e1-9ab4-0002a5d5c51b");
    static final UUID HAPTIC_CONTINUOUS = UUID.fromString("00000006-000f-11e1-9ab4-0002a5d5c51b");
    static final UUID HAPTIC_IMPACT = UUID.fromString("00000008-000f-11e1-9ab4-0002a5d5c51b");
    static final UUID HAPTIC_INFO_NOTIFY = UUID.fromString("0000000a-000f-11e1-9ab4-0002a5d5c51b");
    static final UUID HAPTIC_SWITCH = UUID.fromString("0000000e-000f-11e1-9ab4-0002a5d5c51b");

    private static final long OP_TIMEOUT_MS = 1500;
    private static final int MAX_CONNECT_RETRIES = 3;

    interface Listener {
        void onGattReady(PenGatt gatt);

        void onGattLost(PenGatt gatt);

        void onRead(PenGatt gatt, UUID characteristic, byte[] value);

        void onChanged(PenGatt gatt, UUID characteristic, byte[] value);
    }

    private abstract static class Op {
        final String key;

        Op(String key) {
            this.key = key;
        }

        /** Starts the operation; false when it could not be issued. */
        abstract boolean start(BluetoothGatt gatt);
    }

    private final Context mContext;
    private final Handler mHandler;
    private final Listener mListener;
    private final BluetoothDevice mDevice;
    private final ArrayDeque<Op> mQueue = new ArrayDeque<>();
    private BluetoothGatt mGatt;
    private Op mCurrent;
    private boolean mReady;
    private boolean mClosed;
    private int mRetries;

    private final Runnable mOpTimeout = Safe.run("gatt timeout", () -> {
        Log.w(TAG, "operation timed out: " + (mCurrent != null ? mCurrent.key : null));
        next();
    });

    PenGatt(Context context, Handler handler, BluetoothDevice device, Listener listener) {
        mContext = context;
        mHandler = handler;
        mDevice = device;
        mListener = listener;
    }

    String address() {
        return mDevice.getAddress();
    }

    boolean isReady() {
        return mReady && !mClosed;
    }

    void connect() {
        if (mClosed) return;
        Log.d(TAG, "connect " + mDevice.getAddress());
        mGatt = mDevice.connectGatt(mContext, false, this, BluetoothDevice.TRANSPORT_LE,
                BluetoothDevice.PHY_LE_1M_MASK, mHandler);
    }

    void close() {
        mClosed = true;
        mReady = false;
        mQueue.clear();
        mCurrent = null;
        mHandler.removeCallbacks(mOpTimeout);
        if (mGatt != null) {
            try {
                mGatt.disconnect();
                mGatt.close();
            } catch (Exception e) {
                Log.w(TAG, "close", e);
            }
            mGatt = null;
        }
    }

    boolean has(UUID service, UUID characteristic) {
        return find(service, characteristic) != null;
    }

    private BluetoothGattCharacteristic find(UUID service, UUID characteristic) {
        if (mGatt == null) return null;
        BluetoothGattService s = mGatt.getService(service);
        return s != null ? s.getCharacteristic(characteristic) : null;
    }

    // ---- public operations (called on mHandler) ----

    void read(UUID service, UUID characteristic) {
        enqueue(new Op("read " + characteristic) {
            @Override
            boolean start(BluetoothGatt gatt) {
                BluetoothGattCharacteristic c = find(service, characteristic);
                return c != null && gatt.readCharacteristic(c);
            }
        }, false);
    }

    void write(UUID service, UUID characteristic, byte[] value) {
        write(service, characteristic, value, null);
    }

    /** With a non-null replaceKey an older queued write with the same key is dropped. */
    void write(UUID service, UUID characteristic, byte[] value, String replaceKey) {
        final byte[] v = value.clone();
        enqueue(new Op(replaceKey != null ? replaceKey : "write " + characteristic) {
            @Override
            boolean start(BluetoothGatt gatt) {
                BluetoothGattCharacteristic c = find(service, characteristic);
                if (c == null) return false;
                Log.d(TAG, "write " + characteristic + " " + Arrays.toString(v));
                int r = gatt.writeCharacteristic(c, v, c.getWriteType());
                return r == BluetoothStatusCodes.SUCCESS;
            }
        }, replaceKey != null);
    }

    void setNotify(UUID service, UUID characteristic, boolean enable) {
        enqueue(new Op("notify " + characteristic) {
            @Override
            boolean start(BluetoothGatt gatt) {
                BluetoothGattCharacteristic c = find(service, characteristic);
                if (c == null) return false;
                gatt.setCharacteristicNotification(c, enable);
                BluetoothGattDescriptor d = c.getDescriptor(CLIENT_CONFIG);
                if (d == null) return false;
                byte[] v = enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                return gatt.writeDescriptor(d, v) == BluetoothStatusCodes.SUCCESS;
            }
        }, false);
    }

    // ---- queue ----

    private void enqueue(Op op, boolean replace) {
        if (mClosed || !mReady) {
            Log.d(TAG, "drop " + op.key + " (not ready)");
            return;
        }
        if (replace) {
            for (Iterator<Op> it = mQueue.iterator(); it.hasNext(); ) {
                if (op.key.equals(it.next().key)) it.remove();
            }
        }
        mQueue.add(op);
        if (mCurrent == null) next();
    }

    private void next() {
        mHandler.removeCallbacks(mOpTimeout);
        mCurrent = null;
        while (!mQueue.isEmpty() && mGatt != null && !mClosed) {
            Op op = mQueue.poll();
            boolean started;
            try {
                started = op.start(mGatt);
            } catch (Exception e) {
                Log.w(TAG, op.key, e);
                started = false;
            }
            if (started) {
                mCurrent = op;
                mHandler.postDelayed(mOpTimeout, OP_TIMEOUT_MS);
                return;
            }
            Log.d(TAG, "skipped " + op.key);
        }
    }

    // ---- BluetoothGattCallback (runs on mHandler) ----

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Safe.run("onConnectionStateChange", () -> {
            if (gatt != mGatt || mClosed) return;
            Log.d(TAG, "state " + newState + " status " + status);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mRetries = 0;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                boolean wasReady = mReady;
                mReady = false;
                mQueue.clear();
                mCurrent = null;
                mHandler.removeCallbacks(mOpTimeout);
                gatt.close();
                mGatt = null;
                if (wasReady) mListener.onGattLost(this);
                if (mRetries++ < MAX_CONNECT_RETRIES) {
                    Safe.postDelayed(mHandler, "gatt reconnect", this::connect, 2000L * mRetries);
                }
            }
        }).run();
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Safe.run("onServicesDiscovered", () -> {
            if (gatt != mGatt || mClosed) return;
            Log.d(TAG, "services discovered, status " + status);
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            mReady = true;
            mListener.onGattReady(this);
        }).run();
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic c,
            byte[] value, int status) {
        Safe.run("onCharacteristicRead", () -> {
            if (gatt != mGatt) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mListener.onRead(this, c.getUuid(), value);
            }
            next();
        }).run();
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
        Safe.run("onCharacteristicWrite", () -> {
            if (gatt != mGatt) return;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "write " + c.getUuid() + " status " + status);
            }
            next();
        }).run();
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor d, int status) {
        Safe.run("onDescriptorWrite", () -> {
            if (gatt != mGatt) return;
            next();
        }).run();
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic c,
            byte[] value) {
        Safe.run("onCharacteristicChanged", () -> {
            if (gatt != mGatt) return;
            mListener.onChanged(this, c.getUuid(), value);
        }).run();
    }

    // ---- helpers ----

    static UUID uuid16(int id) {
        return UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", id));
    }

    /** uint16 little endian if two bytes are present, else uint8, else -1. */
    static int intValue(byte[] v) {
        if (v == null || v.length == 0) return -1;
        if (v.length >= 2) return (v[0] & 0xff) | ((v[1] & 0xff) << 8);
        return v[0] & 0xff;
    }
}
