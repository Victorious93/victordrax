package com.victorious.victordrax.report

import kotlin.math.min

/** Severity bands shared by findings (manual or CVSS-derived). */
enum class Severity(val label: String, val min: Double) {
    CRITICAL("Critical", 9.0), HIGH("High", 7.0), MEDIUM("Medium", 4.0), LOW("Low", 0.1), INFO("Info", 0.0);
    companion object {
        fun fromScore(s: Double): Severity = when {
            s >= 9.0 -> CRITICAL; s >= 7.0 -> HIGH; s >= 4.0 -> MEDIUM; s >= 0.1 -> LOW; else -> INFO
        }
        fun fromLabel(l: String): Severity = entries.firstOrNull { it.label.equals(l, true) || it.name.equals(l, true) } ?: INFO
    }
}

/**
 * CVSS v3.1 Base score calculator. Implements the specification arithmetic
 * (https://www.first.org/cvss/v3.1/specification-document, section 7.1). Pure; unit tested.
 */
object Cvss {
    private val AV = mapOf("N" to 0.85, "A" to 0.62, "L" to 0.55, "P" to 0.2)
    private val AC = mapOf("L" to 0.77, "H" to 0.44)
    private val PR_U = mapOf("N" to 0.85, "L" to 0.62, "H" to 0.27)
    private val PR_C = mapOf("N" to 0.85, "L" to 0.68, "H" to 0.5)
    private val UI = mapOf("N" to 0.85, "R" to 0.62)
    private val CIA = mapOf("H" to 0.56, "L" to 0.22, "N" to 0.0)

    data class Metrics(val av: String, val ac: String, val pr: String, val ui: String, val scope: String, val c: String, val i: String, val a: String)

    fun parseVector(vector: String): Metrics? {
        val parts = vector.trim().removePrefix("CVSS:3.1/").removePrefix("CVSS:3.0/").split('/')
            .mapNotNull { val kv = it.split(':'); if (kv.size == 2) kv[0] to kv[1] else null }.toMap()
        fun g(k: String, allowed: Set<String>) = parts[k]?.takeIf { it in allowed }
        val m = Metrics(
            g("AV", setOf("N", "A", "L", "P")) ?: return null,
            g("AC", setOf("L", "H")) ?: return null,
            g("PR", setOf("N", "L", "H")) ?: return null,
            g("UI", setOf("N", "R")) ?: return null,
            g("S", setOf("U", "C")) ?: return null,
            g("C", setOf("H", "L", "N")) ?: return null,
            g("I", setOf("H", "L", "N")) ?: return null,
            g("A", setOf("H", "L", "N")) ?: return null,
        )
        return m
    }

    /** Round up to one decimal place, per the CVSS 3.1 Appendix A roundup. */
    fun roundUp(x: Double): Double {
        val i = Math.round(x * 100000.0)
        return if (i % 10000 == 0L) i / 100000.0 else (Math.floorDiv(i, 10000L) + 1) / 10.0
    }

    fun baseScore(m: Metrics): Double {
        val changed = m.scope == "C"
        val iss = 1 - (1 - CIA.getValue(m.c)) * (1 - CIA.getValue(m.i)) * (1 - CIA.getValue(m.a))
        val impact = if (changed) 7.52 * (iss - 0.029) - 3.25 * Math.pow(iss - 0.02, 15.0)
        else 6.42 * iss
        val pr = (if (changed) PR_C else PR_U).getValue(m.pr)
        val exploit = 8.22 * AV.getValue(m.av) * AC.getValue(m.ac) * pr * UI.getValue(m.ui)
        if (impact <= 0) return 0.0
        val raw = if (changed) min(1.08 * (impact + exploit), 10.0) else min(impact + exploit, 10.0)
        return roundUp(raw)
    }

    fun baseScore(vector: String): Double? = parseVector(vector)?.let { baseScore(it) }
    fun severityOf(vector: String): Severity? = baseScore(vector)?.let { Severity.fromScore(it) }
}
