package com.victorious.victordrax.findings

import com.victorious.victordrax.db.Finding
import com.victorious.victordrax.report.Severity

/** Lifecycle of a finding. Stored by [name] in `findings.status`. */
enum class FindingStatus(val label: String) {
    OPEN("Open"), CONFIRMED("Confirmed"), FIXED("Fixed"), WONT_FIX("Won't fix");
    companion object {
        fun of(s: String): FindingStatus =
            entries.firstOrNull { it.name.equals(s, true) || it.label.equals(s, true) } ?: OPEN
    }
}

val Finding.sev: Severity get() = Severity.fromLabel(severity)
val Finding.state: FindingStatus get() = FindingStatus.of(status)
val Finding.lastChanged: Long get() = if (updatedAt > 0) updatedAt else createdAt

enum class FindingSort(val label: String) {
    SEVERITY("Severity"), NEWEST("Newest"), OLDEST("Oldest"), STATUS("Status"), TITLE("Title")
}

/** Filter + sort spec for the findings list. Empty sets mean "any". Pure; unit tested. */
data class FindingQuery(
    val severities: Set<Severity> = emptySet(),
    val statuses: Set<FindingStatus> = emptySet(),
    val tag: String? = null,
    val text: String = "",
    val sort: FindingSort = FindingSort.SEVERITY,
)

object FindingLogic {
    /** Severity order Critical→Info, then newest first; stable for equal keys. */
    val bySeverity: Comparator<Finding> =
        compareBy<Finding> { it.sev.ordinal }.thenByDescending { it.lastChanged }.thenBy { it.id }

    fun comparator(sort: FindingSort): Comparator<Finding> = when (sort) {
        FindingSort.SEVERITY -> bySeverity
        FindingSort.NEWEST -> compareByDescending<Finding> { it.createdAt }.thenBy { it.id }
        FindingSort.OLDEST -> compareBy<Finding> { it.createdAt }.thenBy { it.id }
        FindingSort.STATUS -> compareBy<Finding> { it.state.ordinal }.then(bySeverity)
        FindingSort.TITLE -> compareBy<Finding, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id }
    }

    fun matches(f: Finding, q: FindingQuery): Boolean =
        (q.severities.isEmpty() || f.sev in q.severities) &&
            (q.statuses.isEmpty() || f.state in q.statuses) &&
            (q.tag.isNullOrBlank() || f.tagList().any { it.equals(q.tag.trim(), true) }) &&
            (q.text.isBlank() || listOf(f.title, f.description, f.target, f.evidence, f.tags).any { it.contains(q.text.trim(), true) })

    fun apply(list: List<Finding>, q: FindingQuery): List<Finding> = list.filter { matches(it, q) }.sortedWith(comparator(q.sort))

    /** All distinct tags across findings, case-insensitively de-duplicated, sorted. */
    fun allTags(list: List<Finding>): List<String> =
        list.flatMap { it.tagList() }.distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** Normalise free-form tag input ("web, Auth ,,web") → "web,Auth". */
    fun normalizeTags(raw: String): String =
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(",")

    /** Group by severity Critical→Info, omitting empty bands. */
    fun groupBySeverity(list: List<Finding>): Map<Severity, List<Finding>> =
        Severity.entries.associateWith { s -> list.filter { it.sev == s }.sortedWith(bySeverity) }.filterValues { it.isNotEmpty() }
}
