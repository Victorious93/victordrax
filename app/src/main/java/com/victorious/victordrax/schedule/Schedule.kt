package com.victorious.victordrax.schedule

import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.workflow.Playbooks
import com.victorious.victordrax.workflow.RunGuard
import kotlinx.serialization.Serializable

enum class ScheduleKind { TOOL, WORKFLOW }

/** How often a schedule repeats. WorkManager cannot fire more often than every 15 minutes. */
enum class Repeat(val label: String, val intervalMs: Long) {
    ONCE("Once", 0L),
    HOURLY("Every hour", 3_600_000L),
    EVERY_6H("Every 6 hours", 6 * 3_600_000L),
    DAILY("Daily", 24 * 3_600_000L),
    WEEKLY("Weekly", 7 * 24 * 3_600_000L),
}

/**
 * A scheduled scan. Stored as JSON in its own DataStore (not the Room DB / findings tables).
 * [commands] are templates (e.g. `nmap -sV {target}`); they are re-validated against the
 * engagement's authorization and scope every time the schedule fires.
 */
@Serializable
data class ScheduledScan(
    val id: String,
    val kind: ScheduleKind,
    val label: String,
    /** Playbook id for WORKFLOW, tool id for TOOL. */
    val refId: String,
    val commands: List<String>,
    val engagementId: Long,
    val target: String,
    val firstRunAt: Long,
    val repeat: Repeat = Repeat.ONCE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long = 0L,
    val lastResult: String = "",
    val active: Boolean = true,
)

/** Pure validation + next-run maths. Unit tested. */
object Scheduler {
    const val MIN_LEAD_MS = 60_000L
    const val MAX_AHEAD_MS = 366L * 24 * 3_600_000L

    /**
     * Refuses to schedule unless: engagement exists and is authorized, the commands use {target},
     * the target is a valid in-scope host, and every resolved command is launch-safe
     * (delegates to [Playbooks.guard], the same guard used for manual runs).
     */
    fun validate(s: ScheduledScan, engagement: Engagement?, now: Long, forCreate: Boolean = true): RunGuard {
        if (engagement == null || engagement.id != s.engagementId) return RunGuard.Blocked("Pick an authorized engagement.")
        if (s.commands.isEmpty()) return RunGuard.Blocked("Nothing to run: the tool/workflow has no commands.")
        if ("target" !in Playbooks.variablesIn(s.commands))
            return RunGuard.Blocked("Scheduled commands must use {target} so the scope can be enforced.")
        if (s.target.isBlank()) return RunGuard.Blocked("Enter an in-scope target.")
        val g = Playbooks.guard(engagement, s.commands, mapOf("target" to s.target.trim()))
        if (g != RunGuard.Ok) return g
        if (forCreate) {
            if (s.repeat == Repeat.ONCE && s.firstRunAt < now + MIN_LEAD_MS) return RunGuard.Blocked("Pick a time at least 1 minute from now.")
            if (s.firstRunAt > now + MAX_AHEAD_MS) return RunGuard.Blocked("Pick a time within the next year.")
        }
        return RunGuard.Ok
    }

    /** Next fire time strictly after [now], or null when a one-off has already fired / is inactive. */
    fun nextRunAt(s: ScheduledScan, now: Long): Long? {
        if (!s.active) return null
        if (s.repeat == Repeat.ONCE) return if (s.lastRunAt == 0L) maxOf(s.firstRunAt, now) else null
        if (now < s.firstRunAt) return s.firstRunAt
        val i = s.repeat.intervalMs
        val n = (now - s.firstRunAt) / i + 1
        return s.firstRunAt + n * i
    }

    /** Delay for WorkManager's initial delay (never negative). */
    fun delayMs(s: ScheduledScan, now: Long): Long? = nextRunAt(s, now)?.let { maxOf(0L, it - now) }
}
