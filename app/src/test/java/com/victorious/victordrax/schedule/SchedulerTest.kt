package com.victorious.victordrax.schedule

import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.workflow.RunGuard
import org.junit.Assert.*
import org.junit.Test

class SchedulerTest {
    private val now = 1_800_000_000_000L
    private val authorized = Engagement(id = 7, name = "Lab", client = "Me", scope = "10.0.0.0/24\nlab.example.com",
        authorizedBy = "Don", authorizedDate = 1L, authDocUri = "content://doc")
    private fun s(
        target: String = "10.0.0.5", commands: List<String> = listOf("nmap -sV -Pn {target}"),
        first: Long = now + 3_600_000L, repeat: Repeat = Repeat.ONCE, engId: Long = 7,
    ) = ScheduledScan("id", ScheduleKind.WORKFLOW, "Nmap", "1", commands, engId, target, first, repeat)

    private fun blocked(g: RunGuard) = g is RunGuard.Blocked

    @Test fun acceptsAuthorizedInScope() {
        assertEquals(RunGuard.Ok, Scheduler.validate(s(), authorized, now))
        assertEquals(RunGuard.Ok, Scheduler.validate(s(target = "api.lab.example.com"), authorized, now))
    }

    @Test fun refusesMissingOrUnauthorizedEngagement() {
        assertTrue(blocked(Scheduler.validate(s(), null, now)))
        assertTrue(blocked(Scheduler.validate(s(), authorized.copy(authDocUri = ""), now)))
        assertTrue(blocked(Scheduler.validate(s(engId = 8), authorized, now)))
    }

    @Test fun refusesOutOfScopeOrInvalidTarget() {
        assertTrue(blocked(Scheduler.validate(s(target = "10.0.1.5"), authorized, now)))
        assertTrue(blocked(Scheduler.validate(s(target = "evil.com"), authorized, now)))
        assertTrue(blocked(Scheduler.validate(s(target = ""), authorized, now)))
        assertTrue(blocked(Scheduler.validate(s(target = "10.0.0.5;id"), authorized, now)))
    }

    @Test fun refusesCommandsWithoutTargetOrUnsafe() {
        assertTrue(blocked(Scheduler.validate(s(commands = listOf("nmap -sV 8.8.8.8")), authorized, now)))
        assertTrue(blocked(Scheduler.validate(s(commands = emptyList()), authorized, now)))
        assertTrue(blocked(Scheduler.validate(s(commands = listOf("nmap {target} | sh")), authorized, now)))
    }

    @Test fun refusesBadTimes() {
        assertTrue(blocked(Scheduler.validate(s(first = now - 1), authorized, now)))
        assertTrue(blocked(Scheduler.validate(s(first = now + 400L * 86_400_000L), authorized, now)))
        // Fire-time re-check ignores the lead-time rule.
        assertEquals(RunGuard.Ok, Scheduler.validate(s(first = now - 1), authorized, now, forCreate = false))
    }

    @Test fun nextRunOnce() {
        assertEquals(now + 3_600_000L, Scheduler.nextRunAt(s(), now))
        assertEquals(now, Scheduler.nextRunAt(s(first = now - 5), now)) // overdue → run now
        assertNull(Scheduler.nextRunAt(s().copy(lastRunAt = now), now))
        assertNull(Scheduler.nextRunAt(s().copy(active = false), now))
    }

    @Test fun nextRunRecurring() {
        val h = 3_600_000L
        val daily = s(first = now, repeat = Repeat.DAILY)
        assertEquals(now + 24 * h, Scheduler.nextRunAt(daily, now))
        assertEquals(now + 24 * h, Scheduler.nextRunAt(daily, now + 5 * h))
        assertEquals(now + 48 * h, Scheduler.nextRunAt(daily, now + 24 * h))
        assertEquals(now + 10 * h, Scheduler.nextRunAt(s(first = now + 10 * h, repeat = Repeat.HOURLY), now))
        assertEquals(now + 3 * h, Scheduler.nextRunAt(s(first = now, repeat = Repeat.HOURLY), now + 2 * h + 1))
        assertEquals(0L, Scheduler.delayMs(s(first = now - 1), now))
        assertEquals(7 * 24 * h, Repeat.WEEKLY.intervalMs)
    }
}
