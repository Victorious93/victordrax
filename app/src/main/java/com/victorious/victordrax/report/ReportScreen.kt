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
import com.victorious.victordrax.findings.FindingLogic
import com.victorious.victordrax.findings.FindingStatus
import com.victorious.victordrax.findings.FindingsRepo
import com.victorious.victordrax.findings.SaveResult
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
    var saveError by remember { mutableStateOf<String?>(null) }
    val repo = remember(db) { FindingsRepo(db) }
    val cur = e ?: return
    var summary by remember(cur.id) { mutableStateOf(cur.execSummary) }

    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val t = FindingReport.markdown(cur, findings); withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(t.toByteArray()) } }; msg = "Markdown report exported" }
    }
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { FindingReport.writePdf(cur, findings, it, logo = androidx.core.content.ContextCompat.getDrawable(ctx, com.victorious.victordrax.R.drawable.victordrax_logo)) } }; msg = "PDF report exported" }
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
        FindingEditor(f, onDismiss = { editing = null; saveError = null }, onDelete = if (f.id != 0L) ({ scope.launch { repo.delete(f); editing = null } }) else null, error = saveError) { saved ->
            scope.launch {
                when (val r = repo.save(saved)) { is SaveResult.Saved -> { editing = null; saveError = null }; is SaveResult.Rejected -> saveError = r.reason }
            }
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
fun FindingEditor(initial: Finding, onDismiss: () -> Unit, onDelete: (() -> Unit)?, error: String? = null, onSave: (Finding) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var sev by remember { mutableStateOf(Severity.fromLabel(initial.severity)) }
    var status by remember { mutableStateOf(FindingStatus.of(initial.status)) }
    var description by remember { mutableStateOf(initial.description) }
    var target by remember { mutableStateOf(initial.target) }
    var tags by remember { mutableStateOf(initial.tags) }
    var vector by remember { mutableStateOf(initial.cvssVector) }
    var evidence by remember { mutableStateOf(initial.evidence) }
    val files = remember { mutableStateListOf<String>().apply { addAll(initial.evidenceList()) } }
    var remediation by remember { mutableStateOf(initial.remediation) }
    var showCalc by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // SAF picker: any file or screenshot. Persist read access so the URI survives reboots.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (uri.toString() !in files) files.add(uri.toString())
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.id == 0L) "New finding" else "Edit finding") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") })
                Text("Severity", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Severity.entries.forEach { s -> FilterChip(sev == s, { sev = s }, { Text(s.label, color = severityColor(s)) }) }
                }
                Text("Status", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FindingStatus.entries.forEach { st -> FilterChip(status == st, { status = st }, { Text(st.label) }) }
                }
                OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Target / asset (in scope)") })
                OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Tags (comma-separated)") })
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2)
                OutlinedTextField(vector, { vector = it }, Modifier.fillMaxWidth(), label = { Text("CVSS 3.1 vector (optional)") },
                    isError = vector.isNotBlank() && Cvss.parseVector(vector) == null, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                TextButton({ showCalc = !showCalc }) { Text(if (showCalc) "Hide calculator" else "CVSS calculator") }
                if (showCalc) CvssCalculator(vector) { v -> vector = v; Cvss.severityOf(v)?.let { sev = it } }
                OutlinedTextField(evidence, { evidence = it }, Modifier.fillMaxWidth(), label = { Text("Evidence (text / tool output)") }, minLines = 3,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
                Text("Evidence files (${files.size})", style = MaterialTheme.typography.labelMedium)
                files.toList().forEach { u ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(evidenceName(u), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        IconButton({ files.remove(u) }) { Icon(Icons.Default.Delete, "Remove attachment") }
                    }
                }
                OutlinedButton({ pick.launch(arrayOf("image/*", "text/*", "application/pdf", "*/*")) }) { Text("Attach file / screenshot") }
                OutlinedTextField(remediation, { remediation = it }, Modifier.fillMaxWidth(), label = { Text("Remediation") }, minLines = 2)
                error?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.secondary) }
                onDelete?.let { TextButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, null); Text("Delete", color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = { TextButton({ onSave(initial.copy(title = title.trim(), severity = sev.label, status = status.name, description = description,
                target = target.trim(), tags = FindingLogic.normalizeTags(tags), cvssVector = vector.trim(), evidence = evidence,
                evidencePaths = files.joinToString("\n"), remediation = remediation)) },
            enabled = title.isNotBlank() && (vector.isBlank() || Cvss.parseVector(vector) != null)) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete finding?") },
        text = { Text("\"${initial.title}\" will be removed. Attached files are not touched.") },
        confirmButton = { TextButton({ confirmDelete = false; onDelete?.invoke() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

/** Readable tail of a content:// URI for display. */
fun evidenceName(uri: String): String = android.net.Uri.decode(uri).substringAfterLast('/').substringAfterLast(':').ifBlank { uri }
