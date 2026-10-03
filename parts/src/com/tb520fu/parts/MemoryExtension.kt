/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.tb520fu.parts

import android.os.SystemProperties
import android.util.Log
import java.io.File
import kotlin.math.roundToInt

/**
 * "Memory extension": zram is always 50% of RAM, the extension is a zram
 * writeback file on /data. init.tb520fu.zram.rc picks
 * /vendor/etc/fstab.zram.<size> from [PROP] at boot, so changes need a restart.
 */
object MemoryExtension {

    private const val TAG = "TB710FUParts"
    private const val PROP = "persist.sys.tb520fu.vram_gb"

    /** Sizes that have a matching fstab.zram.<size> in the vendor image. */
    val SIZES_GB = intArrayOf(0, 2, 4, 6, 8, 12, 16)

    var selectedGb: Int
        get() = SystemProperties.getInt(PROP, 0).takeIf { it in SIZES_GB } ?: 0
        set(value) {
            require(value in SIZES_GB) { "Unsupported size $value" }
            SystemProperties.set(PROP, value.toString())
        }

    /** Writeback size in use since boot, or null if it can't be read. */
    val activeGb: Int?
        get() = try {
            val dev = File("/sys/block/zram0/backing_dev").readText().trim()
            if (dev.isEmpty() || dev == "none") {
                0
            } else {
                val sectors = File("/sys/block/${File(dev).name}/size").readText().trim().toLong()
                (sectors * 512 / GIB.toDouble()).roundToInt()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read the zram backing device", e)
            null
        }

    data class Status(val ramBytes: Long, val availableBytes: Long, val writebackBytes: Long)

    fun readStatus(): Status? = try {
        val info = File("/proc/meminfo").readLines().associate { line ->
            val (key, value) = line.split(":", limit = 2)
            key.trim() to value.trim().substringBefore(' ').toLong() * 1024
        }
        val total = info.getValue("MemTotal")
        Status(total, info["MemAvailable"] ?: total, readWritebackBytes())
    } catch (e: Exception) {
        Log.w(TAG, "Cannot read /proc/meminfo", e)
        null
    }

    /** Bytes written back to the zram backing device, or 0 if it can't be read. */
    private fun readWritebackBytes(): Long = try {
        // bd_stat: bd_count bd_reads bd_writes, all counted in pages on the backing device
        val pages = File("/sys/block/zram0/bd_stat").readText().trim().split(" ")[0].toLong()
        pages * PAGE_SIZE
    } catch (e: Exception) {
        Log.w(TAG, "Cannot read the zram backing device stats", e)
        0
    }

    private const val PAGE_SIZE = 4096L
    private const val GIB = 1024L * 1024 * 1024
}
