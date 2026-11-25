package com.victorious.victordrax.mitm

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MitmScreen(settings: Settings) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val scope = rememberCoroutineScope()
    val cfgRepo = remember { MitmConfigRepo(ctx) }
    val flowStore = remember { MitmStore(ctx) }
    val findings = remember { FindingsRepo(db) }
    val cfg by cfgRepo.config.collectAsState(initial = MitmConfig())
    val storedFlows by flowStore.flows.collectAsState(initial = emptyList())
    val engagements by db.engagements().all().collectAsState(initial = emptyList())
    val term = rememberTerminalLaunchState()

    var engId by remember { mutableStateOf(settings.activeEngagementId) }
    val eng: Engagement? = engagements.firstOrNull { it.id == engId }
    val guard = MitmCommands.guard(eng)

    var host by remember(cfg.listenHost) { mutableStateOf(cfg.listenHost) }
    var port by remember(cfg.listenPort) { mutableStateOf(cfg.listenPort.toString()) }
    var paste by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FlowFilter()) }
    var status by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<MitmFlow?>(null) }
    var findingDraft by remember { mutableStateOf<Finding?>(null) }

    val flows = remember(storedFlows, filter) { MitmFlowLogic.apply(storedFlows, filter) }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
            ingest(text, flowStore) { status = it }
        }
    }

    fun launchCmd(cmd: String, title: String) {
        when (val g = MitmCommands.guard(eng)) {
            is RunGuard.Blocked -> status = g.reason
            RunGuard.Ok -> term.handle(
                VictorSuiteLauncher.launch(
                    ctx,
                    requestFor(ctx, settings, null, title, listOf("bash", "-lc", cmd)),
                ),
            )
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("MITM proxy console")
        Spacer(Modifier.height(8.dp))
        AuthorizedBanner()
        Spacer(Modifier.height(4.dp))
        Text(LEGAL_NOTICE, style = MaterialTheme.typography.bodySmall)
        Text(
            "This console drives mitmdump against YOUR OWN authorized test traffic only. " +
                "Do not intercept third parties without consent. No credential-harvesting automation is included.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )

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
                Text("Scope: " + it.scopeList().joinToString(", ").ifBlank { "(empty)" }, style = MaterialTheme.typography.bodySmall)
            }
            when (guard) {
                RunGuard.Ok -> Text("Ready — engagement authorized.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                is RunGuard.Blocked -> Text(guard.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Section("Proxy") {
            OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Listen / advertise host") })
            OutlinedTextField(port, { port = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Listen port") })
            val portInt = port.toIntOrNull() ?: -1
            val draft = cfg.copy(listenHost = host.trim(), listenPort = portInt)
            Text("Proxy URL: ${if (draft.isValid) draft.proxyUrl else "(invalid)"}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text("Flow file: ${cfg.flowPath}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (!draft.isValid) { status = "Invalid host/port."; return@OutlinedButton }
                    scope.launch { cfgRepo.save(draft); status = "Proxy settings saved." }
                }) { Text("Save") }
                Button(
                    onClick = {
                        if (!draft.isValid) { status = "Invalid host/port."; return@Button }
                        scope.launch { cfgRepo.save(draft) }
                        launchCmd(MitmCommands.start(draft), "mitmdump")
                        status = "mitmdump start requested. Point authorized clients at ${draft.proxyUrl}."
                    },
                    enabled = guard is RunGuard.Ok && draft.isValid,
                ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Start") }
                OutlinedButton(
                    onClick = { launchCmd(MitmCommands.stop(), "stop mitmdump"); status = "Stop requested." },
                    enabled = guard is RunGuard.Ok,
                ) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("Stop") }
            }
            term.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
        }

        Section("CA certificate (required for HTTPS)") {
            Text(
                "Clients must trust the mitmproxy CA to decrypt HTTPS. Install it only on devices/browsers you own or are authorized to test.",
                style = MaterialTheme.typography.bodySmall,
            )
            MitmCommands.caInstallSteps.forEach { (title, body) ->
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
            }
            TextButton(onClick = { launchCmd(MitmCommands.showCaPath(), "show mitm CA") }) {
                Text("Show CA path in VictorSuite")
            }
        }

        Section("Import captured flows") {
            Text(
                "VictorSuite sessions are interactive. After capturing, export a readable log and paste/import it here. Formats: JSONL, JSON array, or mitmdump --flow-detail text.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { launchCmd(MitmCommands.exportReadable(cfg), "export mitm flows") },
                    enabled = guard is RunGuard.Ok,
                ) { Text("Export readable log") }
            }
            OutlinedTextField(
                paste, { paste = it }, Modifier.fillMaxWidth().height(120.dp),
                label = { Text("Flow dump (JSONL / JSON / text)") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { ingest(paste, flowStore) { status = it } } }) { Text("Parse paste") }
                OutlinedButton(onClick = { importFile.launch("*/*") }) { Text("Import file") }
                if (storedFlows.isNotEmpty()) TextButton(onClick = {
                    scope.launch { flowStore.clear(); status = "Flows cleared." }
                }) { Text("Clear") }
            }
        }

        status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

        Section("Flows · ${flows.size}" + if (storedFlows.size != flows.size) " (of ${storedFlows.size})" else "") {
            OutlinedTextField(filter.text, { filter = filter.copy(text = it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Filter text") })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(filter.host, { filter = filter.copy(host = it) }, Modifier.weight(1f), singleLine = true, label = { Text("Host") })
                OutlinedTextField(filter.method, { filter = filter.copy(method = it) }, Modifier.weight(1f), singleLine = true, label = { Text("Method") })
                OutlinedTextField(
                    filter.status?.toString() ?: "",
                    { filter = filter.copy(status = it.trim().toIntOrNull()) },
                    Modifier.weight(1f), singleLine = true, label = { Text("Status") },
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("", "GET", "POST", "PUT", "DELETE").forEach { m ->
                    FilterChip(
                        selected = filter.method.equals(m, true) || (m.isEmpty() && filter.method.isEmpty()),
                        onClick = { filter = filter.copy(method = m) },
                        label = { Text(m.ifEmpty { "Any" }) },
                    )
                }
            }
            if (flows.isEmpty()) Text("No flows yet — start mitmdump, capture authorized traffic, then import.", style = MaterialTheme.typography.bodySmall)
            flows.forEach { f ->
                val inScope = MitmCommands.flowInScope(eng, f)
                NeonCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).animateContentSize(), onClick = { detail = f }) {
                    Text(f.summary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text(
                        listOfNotNull(
                            f.contentType.takeIf { it.isNotBlank() },
                            if (inScope) "in scope" else "outside scope",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (inScope) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
    InstallVictorSuiteDialog(term)

    detail?.let { f ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("${f.method} ${f.statusLabel}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(f.url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    if (f.requestHeaders.isNotBlank()) {
                        Text("Request headers", fontWeight = FontWeight.SemiBold)
                        Text(f.requestHeaders, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (f.requestBodyPreview.isNotBlank()) {
                        Text("Request body", fontWeight = FontWeight.SemiBold)
                        Text(f.requestBodyPreview, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (f.responseHeaders.isNotBlank()) {
                        Text("Response headers", fontWeight = FontWeight.SemiBold)
                        Text(f.responseHeaders, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (f.responseBodyPreview.isNotBlank()) {
                        Text("Response body", fontWeight = FontWeight.SemiBold)
                        Text(f.responseBodyPreview, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (listOf(f.requestHeaders, f.requestBodyPreview, f.responseHeaders, f.responseBodyPreview).all { it.isBlank() }) {
                        Text("No header/body detail in this dump. Re-export with more detail or use JSONL.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val e = eng
                    if (e == null || !e.isAuthorized) status = "Select an authorized engagement to create a finding."
                    else { findingDraft = MitmFlowLogic.draftFinding(e.id, f); detail = null }
                }) { Text("Create finding") }
            },
            dismissButton = { TextButton({ detail = null }) { Text("Close") } },
        )
    }

    findingDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { findingDraft = null },
            title = { Text("Create finding") },
            text = {
                Column {
                    Text(draft.title, fontWeight = FontWeight.SemiBold)
                    Text(draft.evidence.take(600), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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

private suspend fun ingest(text: String, store: MitmStore, setStatus: (String) -> Unit) {
    if (text.isBlank()) { setStatus("Paste or import a flow dump first."); return }
    val parsed = runCatching { MitmFlowParser.parse(text) }.getOrElse {
        setStatus("Parse failed: ${it.message}"); return
    }
    if (parsed.isEmpty()) { setStatus("No flows recognized in that dump."); return }
    store.save(parsed)
    setStatus("Loaded ${parsed.size} flow(s).")
}
