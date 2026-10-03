/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.input;

import android.content.Context;
import android.os.Handler;
import android.view.KeyEvent;

/**
 * Extension point for the optional customizations repository
 * (vendor/lenovo/TB520FU-custom). Its tb520fu-input-custom.jar is loaded into
 * system_server at runtime and cannot compile against this jar, which is why
 * InputCore loads it through a PathClassLoader and drives it through this
 * interface. Without the jar no implementation exists and nothing is loaded.
 */
public interface InputExtension {

    /** Called once when the extension is loaded, on the worker thread's handler. */
    void init(Context context, Handler handler);

    /** Called once the system has finished booting. */
    void start();

    /** Called on the input dispatcher's policy thread; must stay cheap. */
    boolean handleKey(KeyEvent event);
}
