package com.victorious.victordrax.schedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.victorious.victordrax.MainActivity
import com.victorious.victordrax.R
import com.victorious.victordrax.data.SettingsRepo
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.terminal.VictorSuiteLauncher
import com.victorious.victordrax.terminal.requestFor
import com.victorious.victordrax.workflow.Playbooks
import com.victorious.victordrax.workflow.RunGuard
import java.util.concurrent.TimeUnit

/** Notification channel + helpers for scheduled runs. */
object ScheduleNotifier {
    const val CHANNEL = "scheduler"
    const val ROUTE = "schedules"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Scheduled scans", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Start / finish of scheduled authorized scans" })
    }

    fun canPost(ctx: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Tapping opens the Schedules screen (MainActivity routes EXTRA_ROUTE). */
    fun contentIntent(ctx: Context, id: String): PendingIntent = PendingIntent.getActivity(ctx, id.hashCode(),
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, ROUTE)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun post(ctx: Context, id: String, title: String, text: String, ongoing: Boolean = false) {
        ensureChannel(ctx)
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(ctx, id)).setAutoCancel(!ongoing).setOngoing(ongoing)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id.hashCode(), n) }
    }
}

/** Enqueue / cancel helpers. Each schedule is one unique one-time work, re-enqueued after it fires. */
object ScheduleWork {
    const val KEY_ID = "schedule_id"
    fun uniqueName(id: String) = "scan-schedule-$id"

    fun enqueue(ctx: Context, s: ScheduledScan, now: Long = System.currentTimeMillis()): Boolean {
        val delay = Scheduler.delayMs(s, now) ?: return false
        val req = OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_ID to s.id))
            .addTag("scan-schedule")
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(uniqueName(s.id), ExistingWorkPolicy.REPLACE, req)
        return true
    }

    fun cancel(ctx: Context, id: String) { WorkManager.getInstance(ctx).cancelUniqueWork(uniqueName(id)) }
}

class ScheduleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val id = inputData.getString(ScheduleWork.KEY_ID) ?: return Result.failure()
        val store = ScheduleStore(ctx)
        val s = store.get(id) ?: return Result.success()
        if (!s.active) return Result.success()
        val now = System.currentTimeMillis()
        val eng = AppDb.get(ctx).engagements().get(s.engagementId)

        // Re-check authorization + scope at fire time: the engagement may have changed since scheduling.
        val guard = Scheduler.validate(s, eng, now, forCreate = false)
        if (guard is RunGuard.Blocked) {
            store.upsert(s.copy(active = false, lastRunAt = now, lastResult = "Blocked: ${guard.reason}"))
            ScheduleNotifier.post(ctx, id, "Scheduled scan blocked", "${s.label}: ${guard.reason}")
            return Result.success()
        }

        ScheduleNotifier.post(ctx, id, "Scheduled scan starting", "${s.label} → ${s.target} (${eng!!.name})", ongoing = true)
        val settings = SettingsRepo(ctx).current()
        val steps = Playbooks.resolve(s.commands.mapIndexed { i, c -> "${s.label} ${i + 1}" to c }, mapOf("target" to s.target.trim()))
        val result = dispatch(ctx, steps.map { requestFor(settings, it.command, it.label) })

        val updated = s.copy(lastRunAt = now, lastResult = result)
        val next = Scheduler.nextRunAt(updated, now + 1)
        store.upsert(if (next == null) updated.copy(active = false) else updated)
        if (next != null) ScheduleWork.enqueue(ctx, updated, now + 1)
        ScheduleNotifier.post(ctx, id, "Scheduled scan finished", "${s.label}: $result" +
            (next?.let { "\nNext run: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}" } ?: ""))
        return Result.success()
    }

    /** Sends each step to VictorSuite via RUN_COMMAND. No clipboard/activity fallback from the background. */
    private fun dispatch(ctx: Context, reqs: List<com.victorious.victordrax.terminal.TerminalRequest>): String {
        if (!VictorSuiteLauncher.isAvailable(ctx)) return "VictorSuite is not installed"
        if (!VictorSuiteLauncher.hasPermission(ctx)) return "RUN_COMMAND permission not granted — tap to open and run manually"
        var sent = 0
        for (r in reqs) {
            try {
                val i = VictorSuiteLauncher.intent(r)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                sent++
            } catch (e: Exception) {
                return "Dispatched $sent/${reqs.size} step(s); VictorSuite refused: ${e.javaClass.simpleName}"
            }
        }
        return "Dispatched $sent step(s) to VictorSuite"
    }
}
