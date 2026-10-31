package com.victorious.victordrax

import com.victorious.victordrax.db.Finding
import com.victorious.victordrax.db.ToolRun
import com.victorious.victordrax.findings.*
import com.victorious.victordrax.report.Severity
import org.junit.Assert.*
import org.junit.Test

/** Pure JVM tests for severity/status parsing, filtering and sorting of findings. */
class FindingLogicTest {
    private fun f(id: Long, title: String, sev: String, status: String = "OPEN", tags: String = "", created: Long = id, updated: Long = 0) =
        Finding(id = id, engagementId = 1, title = title, severity = sev, status = status, tags = tags, createdAt = created, updatedAt = updated)

    private val list = listOf(
        f(1, "banner", "Info", tags = "recon"),
        f(2, "xss", "Medium", "CONFIRMED", "web, xss"),
        f(3, "rce", "CRITICAL", tags = "web"),
        f(4, "tls", "low", "FIXED", "crypto"),
        f(5, "sqli", "High", "WONT_FIX", "Web,db"),
        f(6, "idor", "High", created = 10),
    )

    @Test fun severityParsingIsLenient() {
        assertEquals(Severity.CRITICAL, f(0, "", "critical").sev)
        assertEquals(Severity.LOW, f(0, "", "LOW").sev)
        assertEquals(Severity.INFO, f(0, "", "bogus").sev)
    }

    @Test fun statusParsing() {
        assertEquals(FindingStatus.WONT_FIX, FindingStatus.of("Won't fix"))
        assertEquals(FindingStatus.CONFIRMED, FindingStatus.of("confirmed"))
        assertEquals(FindingStatus.OPEN, FindingStatus.of(""))
    }

    @Test fun severitySortCriticalFirstThenNewest() {
        val ids = FindingLogic.apply(list, FindingQuery()).map { it.id }
        assertEquals(listOf(3L, 6L, 5L, 2L, 4L, 1L), ids)
    }

    @Test fun updatedAtCountsAsNewerWithinBand() {
        val a = f(10, "a", "High", created = 1, updated = 100); val b = f(11, "b", "High", created = 50)
        assertEquals(listOf(10L, 11L), listOf(b, a).sortedWith(FindingLogic.bySeverity).map { it.id })
    }

    @Test fun otherSorts() {
        assertEquals(6L, FindingLogic.apply(list, FindingQuery(sort = FindingSort.NEWEST)).first().id)
        assertEquals(1L, FindingLogic.apply(list, FindingQuery(sort = FindingSort.OLDEST)).first().id)
        assertEquals(listOf("banner", "idor", "rce", "sqli", "tls", "xss"), FindingLogic.apply(list, FindingQuery(sort = FindingSort.TITLE)).map { it.title })
        val byStatus = FindingLogic.apply(list, FindingQuery(sort = FindingSort.STATUS)).map { it.state }
        assertEquals(byStatus.sortedBy { it.ordinal }, byStatus)
    }

    @Test fun filters() {
        assertEquals(setOf(5L, 6L), FindingLogic.apply(list, FindingQuery(severities = setOf(Severity.HIGH))).map { it.id }.toSet())
        assertEquals(listOf(4L), FindingLogic.apply(list, FindingQuery(statuses = setOf(FindingStatus.FIXED))).map { it.id })
        assertEquals(setOf(2L, 3L, 5L), FindingLogic.apply(list, FindingQuery(tag = "WEB")).map { it.id }.toSet())
        assertEquals(listOf(5L), FindingLogic.apply(list, FindingQuery(tag = "web", statuses = setOf(FindingStatus.WONT_FIX))).map { it.id })
        assertEquals(listOf(2L), FindingLogic.apply(list, FindingQuery(text = "XS")).map { it.id })
    }

    @Test fun tagsNormalizedAndCollected() {
        assertEquals("web,Auth", FindingLogic.normalizeTags(" web, Auth ,,WEB "))
        assertEquals(listOf("crypto", "db", "recon", "web", "xss"), FindingLogic.allTags(list))
    }

    @Test fun groupBySeverityOmitsEmptyBands() {
        val g = FindingLogic.groupBySeverity(list)
        assertEquals(listOf(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.INFO), g.keys.toList())
        assertEquals(2, g[Severity.HIGH]!!.size)
        assertTrue(FindingLogic.groupBySeverity(list.filter { it.sev == Severity.LOW }).keys == setOf(Severity.LOW))
    }

    @Test fun draftFromRunPrefillsEvidence() {
        val d = FindingsRepo.draftFromRun(ToolRun(id = 7, engagementId = 2, label = "nmap", command = "nmap -sV 10.0.0.5", source = "workflow:Recon", target = "10.0.0.5", output = "22/tcp open ssh\n"))
        assertEquals(7L, d.sourceRunId); assertEquals(2L, d.engagementId); assertEquals(0L, d.id)
        assertEquals("10.0.0.5", d.target); assertEquals("workflow", d.tags)
        assertEquals("$ nmap -sV 10.0.0.5\n22/tcp open ssh", d.evidence)
    }
}
