package com.victorious.victordrax.report

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Finding
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun severityColor(s: Severity): Color = when (s) {
    Severity.CRITICAL -> Color(0xFFFF2BD6); Severity.HIGH -> Color(0xFFFF5370); Severity.MEDIUM -> Color(0xFFFFC857)
    Severity.LOW -> Color(0xFF00E5FF); Severity.INFO -> Color(0xFF8A93A6)
}

@Composable
fun SeverityChip(s: Severity) {
    AssistChip(onClick = {}, label = { Text(s.label) }, colors = AssistChipDefaults.assistChipColors(labelColor = severityColor(s)))
}

@Composable
fun ReportBuilderScreen(engagementId: Long) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val e by db.engagements().observe(engagementId).collectAsState(initial = null)
    val findings by db.findings().forEngagement(engagementId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Finding?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val cur = e ?: return
    var summary by remember(cur.id) { mutableStateOf(cur.execSummary) }

    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val t = FindingReport.markdown(cur, findings); withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(t.toByteArray()) } }; msg = "Markdown report exported" }
    }
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { FindingReport.writePdf(cur, findings, it) } }; msg = "PDF report exported" }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { editing = Finding(engagementId = engagementId, title = "", severity = Severity.MEDIUM.label) },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Finding") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 12.dp)) {
            item {
                Spacer(Modifier.height(12.dp)); GradientTitle("Report: ${cur.name}")
                Section("Severity overview") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FindingReport.severityCounts(findings).forEach { (s, n) -> Text("${s.label}: $n", color = severityColor(s), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Section("Executive summary") {
                    OutlinedTextField(summary, { summary = it }, Modifier.fillMaxWidth(), minLines = 3,
                        placeholder = { Text(FindingReport.executiveSummary(cur.copy(execSummary = ""), findings)) },
                        label = { Text("Leave blank to auto-generate") })
                    Button({ scope.launch { db.engagements().update(cur.copy(execSummary = summary)) } }) { Text("Save summary") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ exportMd.launch("${cur.name}-report.md") }) { Text("Export .md") }
                    Button({ exportPdf.launch("${cur.name}-report.pdf") }) { Text("Export branded PDF") }
                }
                msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (!cur.isAuthorized) Text("⚠ No authorization recorded; the report will flag this.", color = MaterialTheme.colorScheme.secondary)
            }
            items(findings.sortedBy { Severity.fromLabel(it.severity).ordinal }, key = { it.id }) { f ->
                val sev = Severity.fromLabel(f.severity)
                ListItem(headlineContent = { Text(f.title.ifBlank { "(untitled)" }) },
                    supportingContent = { Text(f.cvssVector.ifBlank { "No CVSS vector" } + (Cvss.baseScore(f.cvssVector)?.let { " · $it" } ?: "")) },
                    leadingContent = { SeverityChip(sev) },
                    trailingContent = { TextButton({ editing = f }) { Text("Edit") } })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    editing?.let { f ->
        FindingEditor(f, onDismiss = { editing = null }, onDelete = if (f.id != 0L) ({ scope.launch { db.findings().delete(f); editing = null } }) else null) { saved ->
            scope.launch { if (saved.id == 0L) db.findings().insert(saved) else db.findings().update(saved); editing = null }
        }
    }
}

private val METRICS = listOf(
    Triple("AV", "Attack vector", listOf("N" to "Network", "A" to "Adjacent", "L" to "Local", "P" to "Physical")),
    Triple("AC", "Attack complexity", listOf("L" to "Low", "H" to "High")),
    Triple("PR", "Privileges required", listOf("N" to "None", "L" to "Low", "H" to "High")),
    Triple("UI", "User interaction", listOf("N" to "None", "R" to "Required")),
    Triple("S", "Scope", listOf("U" to "Unchanged", "C" to "Changed")),
    Triple("C", "Confidentiality", listOf("H" to "High", "L" to "Low", "N" to "None")),
    Triple("I", "Integrity", listOf("H" to "High", "L" to "Low", "N" to "None")),
    Triple("A", "Availability", listOf("H" to "High", "L" to "Low", "N" to "None")),
)

fun vectorOf(sel: Map<String, String>): String? =
    if (METRICS.all { sel[it.first] != null }) "CVSS:3.1/" + METRICS.joinToString("/") { "${it.first}:${sel[it.first]}" } else null

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CvssCalculator(initial: String, onVector: (String) -> Unit) {
    val parsed = remember(initial) { Cvss.parseVector(initial) }
    val sel = remember(initial) {
        mutableStateMapOf<String, String>().apply {
            parsed?.let { m -> putAll(mapOf("AV" to m.av, "AC" to m.ac, "PR" to m.pr, "UI" to m.ui, "S" to m.scope, "C" to m.c, "I" to m.i, "A" to m.a)) }
        }
    }
    Column {
        METRICS.forEach { (key, label, opts) ->
            Text(label, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                opts.forEach { (v, l) -> FilterChip(sel[key] == v, { sel[key] = v; vectorOf(sel)?.let(onVector) }, { Text(l) }) }
            }
        }
        val v = vectorOf(sel)
        val score = v?.let { Cvss.baseScore(it) }
        Text(if (score != null) "Base score $score (${Severity.fromScore(score).label})" else "Pick all eight metrics",
            color = score?.let { severityColor(Severity.fromScore(it)) } ?: MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FindingEditor(initial: Finding, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Finding) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var sev by remember { mutableStateOf(Severity.fromLabel(initial.severity)) }
    var vector by remember { mutableStateOf(initial.cvssVector) }
    var evidence by remember { mutableStateOf(initial.evidence) }
    var remediation by remember { mutableStateOf(initial.remediation) }
    var showCalc by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.id == 0L) "New finding" else "Edit finding") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") })
                Text("Severity", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Severity.entries.forEach { s -> FilterChip(sev == s, { sev = s }, { Text(s.label, color = severityColor(s)) }) }
                }
                OutlinedTextField(vector, { vector = it }, Modifier.fillMaxWidth(), label = { Text("CVSS 3.1 vector (optional)") },
                    isError = vector.isNotBlank() && Cvss.parseVector(vector) == null, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                TextButton({ showCalc = !showCalc }) { Text(if (showCalc) "Hide calculator" else "CVSS calculator") }
                if (showCalc) CvssCalculator(vector) { v -> vector = v; Cvss.severityOf(v)?.let { sev = it } }
                OutlinedTextField(evidence, { evidence = it }, Modifier.fillMaxWidth(), label = { Text("Evidence") }, minLines = 3)
                OutlinedTextField(remediation, { remediation = it }, Modifier.fillMaxWidth(), label = { Text("Remediation") }, minLines = 2)
                onDelete?.let { TextButton(it) { Icon(Icons.Default.Delete, null); Text("Delete", color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = { TextButton({ onSave(initial.copy(title = title.trim(), severity = sev.label, cvssVector = vector.trim(), evidence = evidence, remediation = remediation)) },
            enabled = title.isNotBlank() && (vector.isBlank() || Cvss.parseVector(vector) != null)) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
