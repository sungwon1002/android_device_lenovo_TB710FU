/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.startup;

/** Compile-time stand-in; the real class comes from the updater dex. */
public final class StartupException extends RuntimeException {
    public StartupException(String message) {
        super(message);
    }
}
