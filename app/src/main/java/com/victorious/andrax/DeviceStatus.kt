package com.victorious.andrax

import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

object DeviceStatus {
    /** Passive check: looks for an `su` binary on common paths. Does not request root. */
    fun rootAvailable(): Boolean = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/data/adb/magisk", "/system/app/Superuser.apk",
    ).any { File(it).exists() }

    fun arch(): String = Build.SUPPORTED_ABIS.joinToString()

    fun freeStorageBytes(): Long = StatFs(Environment.getDataDirectory().path).availableBytes

    fun human(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024 * 1024)
        return String.format("%.2f GB", gb)
    }
}
