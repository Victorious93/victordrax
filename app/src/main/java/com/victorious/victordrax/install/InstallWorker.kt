package com.victorious.victordrax.install

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.victorious.victordrax.R
import com.victorious.victordrax.data.Checksum
import com.victorious.victordrax.data.Root
import com.victorious.victordrax.data.SettingsRepo
import com.victorious.victordrax.data.Validation
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the user-configured rootfs (resumable via HTTP Range), verifies SHA-256,
 * then extracts it with su into the install path.
 */
class InstallWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val UNIQUE = "rootfs-install"
        const val CHANNEL = "installer"
        const val NOTIF_ID = 42
        const val KEY_STAGE = "stage"; const val KEY_PCT = "pct"; const val KEY_ERR = "error"

        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<InstallWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.KEEP, req)
        }
        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE)
        fun partFile(ctx: Context) = File(ctx.filesDir, "rootfs.download.part")
    }

    private val repo = SettingsRepo(ctx)

    private fun fail(msg: String) = Result.failure(workDataOf(KEY_ERR to msg))

    override suspend fun doWork(): Result {
        val s = repo.current()
        if (!Validation.isHttpsUrl(s.rootfsUrl)) return fail("Rootfs URL must be an https:// link to the official upstream release.")
        val expected = Checksum.parseExpected(s.rootfsSha256) ?: return fail("A valid SHA-256 (64 hex chars) is required before installing.")
        val flags = Validation.tarFlagsFor(s.rootfsUrl) ?: return fail("Unsupported archive type. Use .tar, .tar.gz or .tar.xz.")
        if (!Validation.isSafePath(s.installPath)) return fail("Install path contains unsupported characters.")
        if (!Root.suBinaryPresent()) return fail("Root (su) not found. The chroot needs a rooted device.")

        setForeground(foreground("Downloading rootfs…", 0))
        val part = partFile(applicationContext)
        try { download(s.rootfsUrl, part) } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else fail("Download failed: ${e.message}. Partial file kept; retry resumes.")
        }

        report("verify", 100); setForeground(foreground("Verifying SHA-256…", 100))
        val actual = Checksum.sha256(part)
        if (!Checksum.matches(actual, expected)) {
            part.delete()
            return fail("Checksum mismatch.\nexpected $expected\nactual   $actual\nThe file was deleted.")
        }

        report("extract", 100); setForeground(foreground("Extracting with root…", 100))
        val p = s.installPath
        val src = part.absolutePath
        if (!Validation.isSafePath(src)) return fail("Unexpected download path.")
        val r = Root.exec("mkdir -p $p && tar $flags $src -C $p && touch $p/.victorious_installed")
        if (r.code != 0) return fail("Extraction failed (exit ${r.code}): ${r.out.takeLast(400)}")
        part.delete()
        repo.setInstalled(true)
        report("done", 100)
        return Result.success()
    }

    private suspend fun download(url: String, dest: File) {
        var have = if (dest.exists()) dest.length() else 0L
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 30_000
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        val code = c.responseCode
        if (code == 416) { c.disconnect(); return } // already complete
        if (code !in listOf(200, 206)) throw IllegalStateException("HTTP $code")
        if (code == 200) have = 0 // server ignored Range; restart
        val total = c.getHeaderField("Content-Length")?.toLongOrNull()?.let { it + have } ?: -1
        c.inputStream.use { inp ->
            FileOutputStream(dest, code == 206).use { out ->
                val buf = ByteArray(128 * 1024); var done = have; var lastPct = -1
                while (true) {
                    if (isStopped) throw InterruptedException("cancelled")
                    val n = inp.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                    if (pct != lastPct) { lastPct = pct; report("download", pct); setForeground(foreground("Downloading rootfs…", pct)) }
                }
            }
        }
    }

    private suspend fun report(stage: String, pct: Int) = setProgress(workDataOf(KEY_STAGE to stage, KEY_PCT to pct))

    private fun foreground(text: String, pct: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Installer", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle("ANDRAX-NG environment")
            .setContentText(text)
            .setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, pct.coerceAtLeast(0), pct < 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, n)
    }
}

object Uninstaller {
    suspend fun uninstall(path: String): String? {
        if (!Validation.isSafePath(path) || path == "/" || path.count { it == '/' } < 2) return "Refusing to delete unsafe path: $path"
        val r = Root.exec("for m in dev/pts dev proc sys; do umount $path/\$m 2>/dev/null; done; " +
            "if mount | grep -q \" $path/\"; then echo 'still mounted'; exit 3; fi; rm -rf $path")
        return if (r.code == 0) null else "Uninstall failed (exit ${r.code}): ${r.out.takeLast(300)}"
    }
}
