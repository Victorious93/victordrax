package com.victorious.victordrax.findings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Finding
import com.victorious.victordrax.report.FindingEditor
import com.victorious.victordrax.report.FindingReport
import com.victorious.victordrax.report.Severity
import com.victorious.victordrax.report.severityColor
import com.victorious.victordrax.ui.AuthorizedBanner
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.Section
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Neon severity pill: tinted fill + border in the severity colour (cyan→magenta scale). */
@Composable
fun SeverityPill(s: Severity, modifier: Modifier = Modifier) {
    val c = severityColor(s)
    val shape = RoundedCornerShape(50)
    Text(s.label.uppercase(), modifier.clip(shape).background(c.copy(alpha = .16f)).border(1.dp, c, shape).padding(horizontal = 10.dp, vertical = 3.dp),
        color = c, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FindingsScreen(engagementId: Long, openReport: () -> Unit = {}) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val repo = remember(db) { FindingsRepo(db) }
    val e by db.engagements().observe(engagementId).collectAsState(initial = null)
    val all by repo.observe(engagementId).collectAsState(initial = emptyList())
    val runs by repo.runs(engagementId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(FindingQuery()) }
    var editing by remember { mutableStateOf<Finding?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var sortOpen by remember { mutableStateOf(false) }
    var showRuns by remember { mutableStateOf(false) }
    val cur = e ?: return
    val shown = remember(all, query) { FindingLogic.apply(all, query) }
    val tags = remember(all) { FindingLogic.allTags(all) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, floatingActionButton = {
        if (cur.isAuthorized) ExtendedFloatingActionButton(onClick = { editing = Finding(engagementId = engagementId, title = "", severity = Severity.MEDIUM.label) },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Finding") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Spacer(Modifier.height(12.dp)); GradientTitle("Findings: ${cur.name}")
                AuthorizedBanner()
                if (!cur.isAuthorized) Text("⚠ This engagement has no complete authorization record (who, date, document). Record it before adding findings.",
                    color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 6.dp))
                Section("Overview") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FindingReport.severityCounts(all).forEach { (s, n) ->
                            FilterChip(s in query.severities, { query = query.copy(severities = query.severities.toggle(s)) },
                                { Text("${s.label} $n", color = severityColor(s)) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FindingStatus.entries.forEach { st ->
                            FilterChip(st in query.statuses, { query = query.copy(statuses = query.statuses.toggle(st)) },
                                { Text("${st.label} ${all.count { it.state == st }}") })
                        }
                    }
                    if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { t -> FilterChip(query.tag.equals(t, true), { query = query.copy(tag = if (query.tag.equals(t, true)) null else t) }, { Text("#$t") }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query.text, { query = query.copy(text = it) }, Modifier.weight(1f), singleLine = true, label = { Text("Search") })
                        Box {
                            TextButton({ sortOpen = true }) { Icon(Icons.Default.Sort, null); Text(query.sort.label) }
                            DropdownMenu(sortOpen, { sortOpen = false }) {
                                FindingSort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { query = query.copy(sort = s); sortOpen = false }) }
                            }
                        }
                    }
                    if (query != FindingQuery(sort = query.sort)) TextButton({ query = FindingQuery(sort = query.sort) }) { Text("Clear filters") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ showRuns = !showRuns }) { Text(if (showRuns) "Hide tool runs" else "From tool run (${runs.size})") }
                    OutlinedButton(openReport) { Text("Report / CVSS") }
                }
                if (showRuns) Section("Tool & workflow runs") {
                    if (runs.isEmpty()) Text("No runs recorded yet. Launching a tool or playbook step with this engagement active records it here.", style = MaterialTheme.typography.bodySmall)
                    runs.take(30).forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.label, fontWeight = FontWeight.SemiBold)
                                Text(r.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                Text("${r.source} · ${DateFormat.getDateTimeInstance().format(Date(r.startedAt))}", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton({ editing = FindingsRepo.draftFromRun(r) }, enabled = cur.isAuthorized) { Text("→ Finding") }
                        }
                    }
                }
                Text("${shown.size} of ${all.size} findings", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
            items(shown, key = { it.id }) { f ->
                ListItem(
                    headlineContent = { Text(f.title.ifBlank { "(untitled)" }, fontWeight = FontWeight.SemiBold) },
                    overlineContent = { Text(f.state.label + (f.target.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")) },
                    supportingContent = {
                        Column {
                            if (f.description.isNotBlank()) Text(f.description, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                            val extras = listOfNotNull(f.tagList().takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
                                f.evidenceList().size.takeIf { it > 0 }?.let { "📎 $it" })
                            if (extras.isNotEmpty()) Text(extras.joinToString("  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    leadingContent = { SeverityPill(f.sev) },
                    trailingContent = { if (f.evidenceList().isNotEmpty()) Icon(Icons.Default.AttachFile, "Has attachments", tint = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.clickable { editing = f },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
            if (all.isNotEmpty() && shown.isEmpty()) item { Text("No findings match the filters.", Modifier.padding(16.dp)) }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
    editing?.let { f ->
        FindingEditor(f, onDismiss = { editing = null; saveError = null },
            onDelete = if (f.id != 0L) ({ scope.launch { repo.delete(f); editing = null } }) else null, error = saveError) { saved ->
            scope.launch {
                when (val r = repo.save(saved)) {
                    is SaveResult.Saved -> { editing = null; saveError = null }
                    is SaveResult.Rejected -> saveError = r.reason
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v
