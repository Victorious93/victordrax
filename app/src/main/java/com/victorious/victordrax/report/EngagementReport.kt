package com.victorious.victordrax.report

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfDocument
import com.victorious.victordrax.LEGAL_NOTICE
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.db.Finding
import com.victorious.victordrax.db.Note
import com.victorious.victordrax.db.ToolRun
import com.victorious.victordrax.findings.FindingLogic
import com.victorious.victordrax.findings.state
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Everything an engagement report needs. Pure data so the Markdown builder is JVM-testable. */
data class EngagementReportData(
    val engagement: Engagement,
    val findings: List<Finding>,
    val runs: List<ToolRun>,
    val notes: List<Note> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis(),
)

/**
 * Full engagement report: legal notice + scope at the top of every report, authorization,
 * tools/workflows run with timestamps, findings grouped by severity, notes.
 */
object EngagementReport {
    private const val CYAN = 0xFF00F0FF.toInt()
    private const val MAGENTA = 0xFFFF00E5.toInt()

    fun fmt(ms: Long, tz: TimeZone): String =
        if (ms <= 0) "not set" else SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).apply { timeZone = tz }.format(Date(ms))

    /** Escape pipes/newlines for Markdown table cells. */
    fun cell(s: String): String = s.replace("|", "\\|").replace("\r", "").replace("\n", " ").ifBlank { "—" }

    fun fileName(e: Engagement, ext: String): String =
        "VictorDrax-" + e.name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "engagement" } + "-report.$ext"

    fun markdown(d: EngagementReportData, tz: TimeZone = TimeZone.getDefault()): String = buildString {
        val e = d.engagement
        appendLine("# Engagement report: ${e.name}")
        appendLine()
        appendLine("> **Legal / authorization notice.** ${LEGAL_NOTICE.replace("\n", " ").trim()}")
        appendLine()
        appendLine("## Scope")
        val scope = e.scopeList()
        if (scope.isEmpty()) appendLine("- (no scope recorded)") else scope.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("- **Client:** ${e.client.ifBlank { "—" }}")
        appendLine("- **Generated:** ${fmt(d.generatedAt, tz)}")
        appendLine()
        appendLine("## Authorization")
        if (e.isAuthorized) {
            appendLine("- **Authorized by:** ${e.authorizedBy}")
            appendLine("- **Date:** ${fmt(e.authorizedDate, tz)}")
            appendLine("- **Document:** ${e.authDocUri}")
        } else {
            appendLine("> ⚠ NO AUTHORIZATION RECORDED. Testing must not proceed without written permission.")
        }
        appendLine()
        appendLine("## Summary")
        val groups = FindingLogic.groupBySeverity(d.findings)
        appendLine("| Severity | Count |")
        appendLine("|---|---|")
        Severity.entries.forEach { s -> appendLine("| ${s.label} | ${groups[s]?.size ?: 0} |") }
        appendLine()
        appendLine("Tool/workflow runs: ${d.runs.size}")
        appendLine()
        appendLine("## Tools & workflows run")
        if (d.runs.isEmpty()) appendLine("(none recorded)") else {
            appendLine("| Time | Source | Label | Target | Command |")
            appendLine("|---|---|---|---|---|")
            d.runs.sortedBy { it.startedAt }.forEach { r ->
                appendLine("| ${fmt(r.startedAt, tz)} | ${cell(r.source)} | ${cell(r.label)} | ${cell(r.target)} | `${cell(r.command).replace("`", "'")}` |")
            }
        }
        appendLine()
        appendLine("## Findings")
        if (groups.isEmpty()) appendLine("(no findings)")
        var n = 0
        groups.forEach { (sev, list) ->
            appendLine()
            appendLine("### ${sev.label} (${list.size})")
            list.forEach { f ->
                n++
                appendLine()
                appendLine("#### $n. ${f.title}")
                appendLine("- **Status:** ${f.state.label}")
                if (f.target.isNotBlank()) appendLine("- **Target:** `${f.target}`")
                if (f.tagList().isNotEmpty()) appendLine("- **Tags:** ${f.tagList().joinToString(", ")}")
                if (f.cvssVector.isNotBlank()) appendLine("- **CVSS 3.1:** `${f.cvssVector}`" + (Cvss.baseScore(f.cvssVector)?.let { " (base $it)" } ?: ""))
                appendLine("- **Created:** ${fmt(f.createdAt, tz)}" + if (f.updatedAt > 0) " · **Updated:** ${fmt(f.updatedAt, tz)}" else "")
                if (f.description.isNotBlank()) { appendLine(); appendLine(f.description.trim()) }
                if (f.evidence.isNotBlank()) { appendLine(); appendLine("**Evidence**"); appendLine("```"); appendLine(f.evidence.trimEnd()); appendLine("```") }
                if (f.evidenceList().isNotEmpty()) { appendLine(); appendLine("**Attachments**"); f.evidenceList().forEach { appendLine("- $it") } }
                if (f.remediation.isNotBlank()) { appendLine(); appendLine("**Remediation:** ${f.remediation.trim()}") }
            }
        }
        if (d.notes.isNotEmpty()) {
            appendLine()
            appendLine("## Notes")
            d.notes.forEach { appendLine("- ${fmt(it.createdAt, tz)}: ${it.text.replace("\n", " ")}") }
        }
        appendLine()
        appendLine("_Generated by VictorDrax. Authorized testing only._")
    }

    /** Branded PDF (Android PdfDocument; no third-party libs). Verified on device only. */
    fun writePdf(d: EngagementReportData, out: OutputStream, tz: TimeZone = TimeZone.getDefault(), logo: Drawable? = null) {
        val doc = PdfDocument()
        val w = 595; val h = 842; val margin = 40f; val lineH = 14f; val top = 96f
        val text = Paint().apply { textSize = 10f; isAntiAlias = true; color = Color.BLACK }
        val bold = Paint(text).apply { isFakeBoldText = true }
        val h1 = Paint(text).apply { textSize = 14f; isFakeBoldText = true }
        val mono = Paint(text).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = 9f }
        val head = Paint().apply { textSize = 20f; isAntiAlias = true; color = Color.WHITE; isFakeBoldText = true }
        val small = Paint().apply { textSize = 8f; isAntiAlias = true; color = Color.WHITE }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        fun header() {
            val c = page.canvas
            c.drawRect(0f, 0f, w.toFloat(), 70f, Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), 0f, CYAN, MAGENTA, Shader.TileMode.CLAMP) })
            val ring = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.WHITE; isAntiAlias = true }
            if (logo != null) { logo.setBounds(16, 11, 64, 59); logo.draw(c) }
            c.drawCircle(40f, 35f, 26f, ring) // logo ring
            c.drawText("VictorDrax", 76f, 40f, head)
            c.drawText("Authorized testing only · ${d.engagement.name} · page $pageNo", 76f, 58f, small)
        }
        header()
        var y = top
        fun line(s: String, p: Paint = text, wrap: Int = 95) {
            for (chunk in if (s.isEmpty()) listOf("") else s.chunked(wrap)) {
                if (y > h - margin) {
                    doc.finishPage(page); pageNo++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create()); header(); y = top
                }
                page.canvas.drawText(chunk, margin, y, p); y += lineH
            }
        }
        // Render from the Markdown so PDF and .md stay identical in content.
        var inCode = false
        markdown(d, tz).lines().forEach { raw ->
            when {
                raw.startsWith("```") -> inCode = !inCode
                inCode -> line("  $raw", mono, 100)
                raw.startsWith("# ") -> line(raw.removePrefix("# "), h1)
                raw.startsWith("#### ") -> line(raw.removePrefix("#### "), bold)
                raw.startsWith("### ") -> line(raw.removePrefix("### "), bold)
                raw.startsWith("## ") -> { line(""); line(raw.removePrefix("## ").uppercase(), bold) }
                raw.startsWith("|---") -> Unit
                else -> line(raw.replace("**", "").replace("`", "").replace("\\|", "¦").trim('|').replace(" | ", "  ·  ").removePrefix("> "))
            }
        }
        doc.finishPage(page)
        doc.writeTo(out); doc.close()
    }
}
