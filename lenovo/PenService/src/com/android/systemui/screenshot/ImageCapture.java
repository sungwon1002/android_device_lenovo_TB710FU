/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screenshot;

import android.graphics.Bitmap;
import android.graphics.Rect;

import kotlin.coroutines.Continuation;

/** Same shape as the interface in PenService (Kotlin suspend fun captureTask). */
public interface ImageCapture {
    Bitmap captureDisplay(int displayId, Rect crop);

    Object captureTask(int taskId, Continuation continuation);
}
