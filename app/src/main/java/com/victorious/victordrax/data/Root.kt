package com.victorious.victordrax.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ShellResult(val code: Int, val out: String)

/** Minimal su wrapper. Every command string must be built only from validated values. */
object Root {
    private val SU_PATHS = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/debug_ramdisk/su")

    fun suBinaryPresent(): Boolean = SU_PATHS.any { File(it).exists() } || File("/data/adb/magisk").exists()

    suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            ShellResult(p.waitFor(), out)
        }.getOrElse { ShellResult(-1, it.message ?: "su unavailable") }
    }

    /** Commands that mount the pseudo filesystems and enter the chroot at [root]. */
    fun chrootPrefix(root: String): String {
        require(Validation.isSafePath(root))
        return "mount -t proc proc $root/proc 2>/dev/null; " +
            "mount -o bind /dev $root/dev 2>/dev/null; " +
            "mount -t devpts devpts $root/dev/pts 2>/dev/null; " +
            "mount -t sysfs sysfs $root/sys 2>/dev/null; " +
            "chroot $root /usr/bin/env -i HOME=/root TERM=xterm-256color " +
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    }
}
