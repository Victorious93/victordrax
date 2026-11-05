package com.victorious.victordrax.workflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Playbook
import com.victorious.victordrax.db.PlaybookStep
import com.victorious.victordrax.findings.FindingsRepo
import com.victorious.victordrax.terminal.InstallVictorSuiteDialog
import com.victorious.victordrax.terminal.LaunchResult
import com.victorious.victordrax.terminal.VictorSuiteLauncher
import com.victorious.victordrax.terminal.rememberTerminalLaunchState
import com.victorious.victordrax.terminal.requestFor
import com.victorious.victordrax.ui.AuthorizedBanner
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.Section
import kotlinx.coroutines.launch

/** Seeds the built-in passive recon playbooks once. */
suspend fun seedBuiltIns(db: AppDb) {
    val dao = db.playbooks()
    if (dao.builtInCount() > 0) return
    Playbooks.builtIns.forEach { b ->
        val id = dao.insert(Playbook(name = b.name, description = b.description, builtIn = true))
        b.steps.forEachIndexed { i, (l, c) -> dao.insertStep(PlaybookStep(playbookId = id, ordinal = i, label = l, command = c)) }
    }
}

@Composable
fun PlaybookListScreen(open: (Long) -> Unit) {
    val db = AppDb.get(LocalContext.current)
    LaunchedEffect(Unit) { seedBuiltIns(db) }
    val list by db.playbooks().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { scope.launch { open(db.playbooks().insert(Playbook(name = "New playbook"))) } },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("New") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(12.dp)); GradientTitle("Workflows"); AuthorizedBanner()
                Text("Playbooks run only against a target inside an authorized engagement's scope.", style = MaterialTheme.typography.bodySmall) }
            items(list, key = { it.id }) { p ->
                ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text(p.description.ifBlank { "—" }) },
                    trailingContent = { if (p.builtIn) Text("built-in", style = MaterialTheme.typography.labelSmall) },
                    leadingContent = { Icon(Icons.Default.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { open(p.id) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybookScreen(id: Long, settings: Settings, back: () -> Unit) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val scope = rememberCoroutineScope()
    var pb by remember { mutableStateOf<Playbook?>(null) }
    val steps by db.playbooks().steps(id).collectAsState(initial = emptyList())
    val engagements by db.engagements().all().collectAsState(initial = emptyList())
    LaunchedEffect(id) { pb = db.playbooks().get(id) }
    val p = pb ?: return
    var name by remember(p.id) { mutableStateOf(p.name) }
    var desc by remember(p.id) { mutableStateOf(p.description) }
    var newLabel by remember { mutableStateOf("") }
    var newCmd by remember { mutableStateOf("") }
    var engId by remember { mutableStateOf(settings.activeEngagementId) }
    val values = remember { mutableStateMapOf<String, String>() }
    var pickerOpen by remember { mutableStateOf(false) }
    val launch = rememberTerminalLaunchState()
    val eng = engagements.firstOrNull { it.id == engId }
    val cmds = steps.map { it.command }
    val vars = Playbooks.variablesIn(cmds)
    val guard = Playbooks.guard(eng, cmds, values.toMap())
    val runs = remember(db) { FindingsRepo(db) }
    /** Launch one resolved step; when VictorSuite accepted it (or fell back to clipboard), log it as a tool run. */
    fun run(label: String, command: String) {
        val res = VictorSuiteLauncher.launch(ctx, requestFor(settings, command, label))
        launch.handle(res)
        if (res is LaunchResult.Sent || res is LaunchResult.FellBack) eng?.let { e ->
            scope.launch { runs.recordRun(e.id, label, command, "workflow:${p.name}", values["target"].orEmpty()) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle(p.name)
        if (!p.builtIn) Section("Playbook") {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") })
            OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), label = { Text("Description") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ scope.launch { db.playbooks().update(p.copy(name = name, description = desc)); pb = db.playbooks().get(id) } }) { Text("Save") }
                TextButton({ scope.launch { db.playbooks().delete(p); back() } }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        } else Text(p.description, style = MaterialTheme.typography.bodyMedium)
        Section("Steps (in order)") {
            if (steps.isEmpty()) Text("No steps yet.")
            steps.forEachIndexed { i, s ->
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${s.label}")
                        Text(s.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!p.builtIn) IconButton({ scope.launch {
                        val rest = steps.filter { it.id != s.id }
                        db.playbooks().deleteSteps(id)
                        rest.forEachIndexed { j, r -> db.playbooks().insertStep(r.copy(id = 0, ordinal = j)) }
                    } }) { Icon(Icons.Default.Delete, "Remove step") }
                }
            }
            if (!p.builtIn) {
                OutlinedTextField(newLabel, { newLabel = it }, Modifier.fillMaxWidth(), label = { Text("Step label") })
                OutlinedTextField(newCmd, { newCmd = it }, Modifier.fillMaxWidth(), label = { Text("Command, e.g. nmap -sV {target}") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    isError = newCmd.isNotBlank() && !VictorSuiteLauncher.isSafeCommand(newCmd.replace(Regex("[{}]"), "")))
                Button({ scope.launch {
                    db.playbooks().insertStep(PlaybookStep(playbookId = id, ordinal = steps.size, label = newLabel.ifBlank { "Step ${steps.size + 1}" }, command = newCmd.trim()))
                    newLabel = ""; newCmd = "" } }, enabled = newCmd.isNotBlank() && VictorSuiteLauncher.isSafeCommand(newCmd.replace(Regex("[{}]"), ""))) { Text("Add step") }
            }
        }
        Section("Run") {
            AuthorizedBanner()
            ExposedDropdownMenuBox(pickerOpen, { pickerOpen = it }) {
                OutlinedTextField(eng?.name ?: "Select engagement", {}, readOnly = true, label = { Text("Engagement") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pickerOpen) })
                ExposedDropdownMenu(pickerOpen, { pickerOpen = false }) {
                    engagements.forEach { e -> DropdownMenuItem(text = { Text(e.name + if (e.isAuthorized) "" else " (no authorization)") }, onClick = { engId = e.id; pickerOpen = false }) }
                }
            }
            eng?.let { Text("Scope: " + it.scopeList().joinToString(", ").ifBlank { "(empty)" }, style = MaterialTheme.typography.bodySmall) }
            vars.forEach { v -> OutlinedTextField(values[v] ?: "", { values[v] = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("{$v}") }) }
            when (guard) {
                is RunGuard.Blocked -> Text("⚠ ${guard.reason}", color = MaterialTheme.colorScheme.secondary)
                RunGuard.Ok -> Text("✔ Authorized and in scope", color = MaterialTheme.colorScheme.primary)
            }
            val resolved = if (guard == RunGuard.Ok) Playbooks.resolve(steps.map { it.label to it.command }, values.toMap()) else emptyList()
            resolved.forEach { r ->
                Row {
                    Text(r.command, Modifier.weight(1f).padding(top = 12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    TextButton({ run(r.label, r.command) }) { Text("Run") }
                }
            }
            Button({ resolved.forEach { r -> run(r.label, r.command) } },
                enabled = guard == RunGuard.Ok && resolved.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, null); Text("Run all in VictorSuite") }
            Text("Each step opens its own VictorSuite session.", style = MaterialTheme.typography.bodySmall)
            launch.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        }
    }
    InstallVictorSuiteDialog(launch)
}
