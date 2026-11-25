package com.victorious.victordrax.capture

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads the per-run capture logs that VictorSuite writes. The capture directory is shared so both
 * the app and VictorSuite can reach it (default /sdcard/VictorDrax/runs, configurable in Settings).
 *
 * Reading the shared directory directly needs storage access on a real device (root / All-files
 * access, or the file lives on primary shared storage the app can read). When the app cannot read it
 * directly, the operator can import the log via the Storage Access Framework ([readUri]); either way
 * the bytes are parsed by [RunCapture].
 */
object RunOutputStore {
    /** Absolute path of a run's capture log inside [dir]. */
    fun fileFor(dir: String, runId: Long): String = dir.trimEnd('/') + "/" + RunCapture.fileName(runId)

    /** Best-effort direct read of a capture file (works with the right storage access). */
    suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        runCatching { File(path).takeIf { it.canRead() }?.readText() }.getOrNull()
    }

    /** Read a user-picked capture log via SAF. Guaranteed-available import path on a real device. */
    suspend fun readUri(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
    }
}
