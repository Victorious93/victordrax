package com.victorious.victordrax.network

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.LEGAL_NOTICE
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.db.Finding
import com.victorious.victordrax.findings.FindingsRepo
import com.victorious.victordrax.findings.SaveResult
import com.victorious.victordrax.terminal.InstallVictorSuiteDialog
import com.victorious.victordrax.terminal.VictorSuiteLauncher
import com.victorious.victordrax.terminal.rememberTerminalLaunchState
import com.victorious.victordrax.terminal.requestFor
import com.victorious.victordrax.ui.AuthorizedBanner
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.NeonCard
import com.victorious.victordrax.ui.Section
import com.victorious.victordrax.workflow.RunGuard
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NetworkScreen(settings: Settings) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val scope = rememberCoroutineScope()
    val store = remember { NetworkStore(ctx) }
    val findings = remember { FindingsRepo(db) }
    val engagements by db.engagements().all().collectAsState(initial = emptyList())
    val snap by store.snapshot.collectAsState(initial = null)
    val term = rememberTerminalLaunchState()

    var engId by remember { mutableStateOf(settings.activeEngagementId) }
    val eng: Engagement? = engagements.firstOrNull { it.id == engId }
    var target by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(NetworkCommands.Profile.SERVICE) }
    var paste by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HostFilter()) }
    var status by remember { mutableStateOf<String?>(null) }
    var findingDraft by remember { mutableStateOf<Finding?>(null) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    val guard = NetworkCommands.guard(eng, target)
    val hosts = remember(snap, filter) { HostLogic.apply(snap?.hosts ?: emptyList(), filter) }

    val importXml = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
            ingestXml(text, eng, target, profile, store) { status = it }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("Network discovery")
        Spacer(Modifier.height(8.dp))
        AuthorizedBanner()
        Spacer(Modifier.height(4.dp))
        Text(LEGAL_NOTICE, style = MaterialTheme.typography.bodySmall)

        var pickerOpen by remember { mutableStateOf(false) }
        Section("Engagement") {
            ExposedDropdownMenuBox(pickerOpen, { pickerOpen = it }) {
                OutlinedTextField(
                    eng?.name ?: "Select engagement", {}, readOnly = true,
                    label = { Text("Authorized engagement") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pickerOpen) },
                )
                ExposedDropdownMenu(pickerOpen, { pickerOpen = false }) {
                    engagements.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.name + if (e.isAuthorized) "" else " (no authorization)") },
                            onClick = { engId = e.id; pickerOpen = false },
                        )
                    }
                }
            }
            eng?.let {
                Text(
                    "Scope: " + it.scopeList().joinToString(", ").ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            GuardLine(guard)
        }

        Section("Scan") {
            OutlinedTextField(
                target, { target = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Target (in-scope host / CIDR)") },
                placeholder = { Text("10.0.0.0/24 or lab.example.com") },
            )
            Spacer(Modifier.height(6.dp))
            Text("Profile", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NetworkCommands.Profile.entries.forEach { p ->
                    FilterChip(selected = profile == p, onClick = { profile = p }, label = { Text(p.label) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Command: ${NetworkCommands.preview(target.ifBlank { "<target>" }, profile)}",
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    status = null
                    when (val g = NetworkCommands.guard(eng, target)) {
                        is RunGuard.Blocked -> status = g.reason
                        RunGuard.Ok -> {
                            val argv = NetworkCommands.argv(target, profile)
                            term.handle(
                                VictorSuiteLauncher.launch(
                                    ctx,
                                    requestFor(ctx, settings, null, "Nmap discovery", argv),
                                ),
                            )
                            status = "Scan launched in VictorSuite. When it finishes, paste or import the XML from ${NetworkCommands.DEFAULT_XML_PATH}."
                        }
                    }
                },
                enabled = guard is RunGuard.Ok,
            ) {
                Icon(Icons.Default.Radar, null); Spacer(Modifier.width(6.dp)); Text("Launch nmap (-oX)")
            }
            term.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
        }

        Section("Import results") {
            Text(
                "VictorSuite sessions are interactive — paste the nmap -oX XML here, or import the file. Results stay in this dashboard (not tool_runs).",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                paste, { paste = it }, Modifier.fillMaxWidth().height(120.dp),
                label = { Text("nmap -oX XML") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch { ingestXml(paste, eng, target, profile, store) { status = it } }
                }) { Text("Parse paste") }
                OutlinedButton(onClick = { importXml.launch("text/*") }) { Text("Import file") }
                if (snap != null) TextButton(onClick = {
                    scope.launch { store.clear(); status = "Dashboard cleared." }
                }) { Text("Clear") }
            }
        }

        status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

        snap?.let { s ->
            Section("Dashboard · ${s.upHosts.size} up / ${s.downHosts.size} down") {
                Text(
                    "Target ${s.target} · scanned ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.scannedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    filter.text, { filter = filter.copy(text = it) }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Filter text") },
                    placeholder = { Text("host, service, OS…") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        filter.port?.toString() ?: "",
                        { filter = filter.copy(port = it.trim().toIntOrNull()) },
                        Modifier.weight(1f), singleLine = true, label = { Text("Port") },
                    )
                    OutlinedTextField(
                        filter.service, { filter = filter.copy(service = it) },
                        Modifier.weight(1f), singleLine = true, label = { Text("Service") },
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "Any", "up" to "Up", "down" to "Down", "open" to "Has open").forEach { (v, label) ->
                        FilterChip(selected = filter.state == v, onClick = { filter = filter.copy(state = v) }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (hosts.isEmpty()) Text("No hosts match the filter.", style = MaterialTheme.typography.bodySmall)
                hosts.forEach { h ->
                    HostCard(
                        host = h,
                        expanded = h.address in expanded,
                        onToggle = {
                            expanded = if (h.address in expanded) expanded - h.address else expanded + h.address
                        },
                        onFinding = { port ->
                            val e = eng
                            if (e == null || !e.isAuthorized) status = "Select an authorized engagement to create a finding."
                            else findingDraft = HostLogic.draftFinding(e.id, h, port)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    InstallVictorSuiteDialog(term)

    findingDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { findingDraft = null },
            title = { Text("Create finding") },
            text = {
                Column {
                    Text(draft.title, fontWeight = FontWeight.SemiBold)
                    Text(draft.evidence, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton({
                    scope.launch {
                        when (val r = findings.save(draft)) {
                            is SaveResult.Saved -> { status = "Finding #${r.id} saved."; findingDraft = null }
                            is SaveResult.Rejected -> status = r.reason
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ findingDraft = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HostCard(
    host: DiscoveredHost,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFinding: (HostPort?) -> Unit,
) {
    NeonCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (host.status.equals("up", true)) Icons.Default.Dns else Icons.Default.CloudOff,
                null,
                tint = if (host.status.equals("up", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(host.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    "${host.status.uppercase()} · ${host.openPorts.size} open" +
                        if (host.osGuess.isNotBlank()) " · ${host.osGuess}" + if (host.osAccuracy > 0) " (${host.osAccuracy}%)" else "" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            TextButton({ onFinding(null) }) { Icon(Icons.Default.BugReport, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Create finding") }
            host.ports.sortedWith(compareByDescending<HostPort> { it.state.equals("open", true) }.thenBy { it.port }).forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    if (p.state.equals("open", true)) {
                        TextButton({ onFinding(p) }) { Text("Finding") }
                    }
                }
            }
            if (host.ports.isEmpty()) Text("No ports reported.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GuardLine(g: RunGuard) {
    when (g) {
        RunGuard.Ok -> Text("Ready — target is in scope and engagement is authorized.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        is RunGuard.Blocked -> Text(g.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

private suspend fun ingestXml(
    text: String,
    eng: Engagement?,
    target: String,
    profile: NetworkCommands.Profile,
    store: NetworkStore,
    setStatus: (String) -> Unit,
) {
    if (text.isBlank()) { setStatus("Paste or import nmap -oX XML first."); return }
    val hosts = runCatching { NmapXmlHosts.parse(text) }.getOrElse {
        setStatus("Could not parse XML: ${it.message}"); return
    }
    if (hosts.isEmpty()) { setStatus("XML parsed but no hosts found."); return }
    val snap = DiscoverySnapshot(
        engagementId = eng?.id ?: 0,
        target = target.ifBlank { "(imported)" },
        scannedAt = System.currentTimeMillis(),
        command = NetworkCommands.preview(target.ifBlank { "<imported>" }, profile),
        hosts = hosts,
    )
    store.save(snap)
    setStatus("Loaded ${hosts.size} host(s) · ${snap.upHosts.size} up.")
}
