package com.victorious.victordrax.schedule

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.tools.ToolCatalog
import com.victorious.victordrax.ui.AppLogo
import com.victorious.victordrax.ui.AuthorizedBanner
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.NeonCard
import com.victorious.victordrax.ui.Section
import com.victorious.victordrax.workflow.RunGuard
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(settings: Settings) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val store = remember { ScheduleStore(ctx) }
    val scope = rememberCoroutineScope()
    val schedules by store.all.collectAsState(initial = emptyList())
    val engagements by db.engagements().all().collectAsState(initial = emptyList())
    val playbooks by db.playbooks().all().collectAsState(initial = emptyList())
    val tools = remember { runCatching { ToolCatalog.parse(ctx.assets.open("tools.json").bufferedReader().readText()).tools }.getOrDefault(emptyList()) }

    var notifGranted by remember { mutableStateOf(ScheduleNotifier.canPost(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    var kind by remember { mutableStateOf(ScheduleKind.WORKFLOW) }
    var engId by remember { mutableStateOf(settings.activeEngagementId) }
    var refId by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var toolCmd by remember { mutableStateOf("") }
    var wfCmds by remember { mutableStateOf<List<String>>(emptyList()) }
    var target by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("60") }
    var repeat by remember { mutableStateOf(Repeat.ONCE) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, refId) {
        if (kind == ScheduleKind.WORKFLOW) wfCmds = refId.toLongOrNull()?.let { db.playbooks().stepsOnce(it).map { s -> s.command } } ?: emptyList()
    }

    val eng = engagements.firstOrNull { it.id == engId }
    val now = System.currentTimeMillis()
    val draft = ScheduledScan(
        id = "draft", kind = kind, label = label.ifBlank { "Scheduled scan" }, refId = refId,
        commands = if (kind == ScheduleKind.TOOL) listOf(toolCmd.trim()).filter { it.isNotEmpty() } else wfCmds,
        engagementId = engId, target = target.trim(),
        firstRunAt = now + (minutes.toLongOrNull() ?: -1L) * 60_000L, repeat = repeat,
    )
    val guard = if (refId.isBlank()) RunGuard.Blocked("Pick a ${if (kind == ScheduleKind.TOOL) "tool" else "workflow"}.")
        else Scheduler.validate(draft, eng, now)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row { AppLogo(40.dp); Spacer(Modifier.width(10.dp)); GradientTitle("Scan scheduler") }
        Spacer(Modifier.height(8.dp))
        AuthorizedBanner()
        if (!notifGranted && Build.VERSION.SDK_INT >= 33) Section("Notifications") {
            Text("Allow notifications to be told when a scheduled run starts and finishes.", style = MaterialTheme.typography.bodySmall)
            Button({ permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
        }

        Section("New schedule") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(kind == ScheduleKind.WORKFLOW, { kind = ScheduleKind.WORKFLOW; refId = "" }, { Text("Saved workflow") })
                FilterChip(kind == ScheduleKind.TOOL, { kind = ScheduleKind.TOOL; refId = "" }, { Text("Tool") })
            }
            if (kind == ScheduleKind.WORKFLOW) {
                Dropdown("Workflow", playbooks.firstOrNull { it.id.toString() == refId }?.name ?: "Select workflow",
                    playbooks.map { it.id.toString() to it.name }) { id, n -> refId = id; label = n }
                wfCmds.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            } else {
                Dropdown("Tool", tools.firstOrNull { it.id == refId }?.name ?: "Select tool", tools.map { it.id to it.name }) { id, n ->
                    refId = id; label = n; toolCmd = "${tools.first { it.id == id }.command.substringBefore(' ')} {target}"
                }
                OutlinedTextField(toolCmd, { toolCmd = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Command (must use {target})") })
            }
            Dropdown("Engagement", eng?.name ?: "Select engagement",
                engagements.map { it.id.toString() to (it.name + if (it.isAuthorized) "" else " (no authorization)") }) { id, _ -> engId = id.toLong() }
            eng?.let { Text("Scope: " + it.scopeList().joinToString(", ").ifBlank { "(empty)" }, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(target, { target = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Target (in scope)") })
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("First run in (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Repeat.entries.forEach { r -> FilterChip(repeat == r, { repeat = r }, { Text(r.label, style = MaterialTheme.typography.labelSmall) }) }
            }
            when (guard) {
                is RunGuard.Blocked -> Text("⚠ ${guard.reason}", color = MaterialTheme.colorScheme.secondary)
                RunGuard.Ok -> Text("✔ Authorized and in scope · first run ${fmt(draft.firstRunAt)}", color = MaterialTheme.colorScheme.primary)
            }
            Button({
                scope.launch {
                    val s = draft.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())
                    // Re-validate with a fresh clock right before persisting.
                    val g = Scheduler.validate(s, db.engagements().get(s.engagementId), System.currentTimeMillis())
                    if (g is RunGuard.Blocked) { message = g.reason; return@launch }
                    store.upsert(s); ScheduleWork.enqueue(ctx, s); ScheduleNotifier.ensureChannel(ctx)
                    message = "Scheduled ${s.label} for ${fmt(s.firstRunAt)}"
                }
            }, enabled = guard == RunGuard.Ok) { Text("Schedule") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Text("Runs open in VictorSuite (RUN_COMMAND). Authorization and scope are checked again when each run fires.",
                style = MaterialTheme.typography.bodySmall)
        }

        Section("Schedules") {
            if (schedules.isEmpty()) Text("No schedules yet.")
            schedules.forEach { s ->
                NeonCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${s.label} → ${s.target}")
                    Text("${s.kind.name.lowercase()} · ${s.repeat.label} · " +
                        (Scheduler.nextRunAt(s, System.currentTimeMillis())?.let { "next ${fmt(it)}" } ?: "inactive"),
                        style = MaterialTheme.typography.bodySmall)
                    if (s.lastRunAt > 0) Text("Last: ${fmt(s.lastRunAt)} — ${s.lastResult}", style = MaterialTheme.typography.bodySmall)
                    Row {
                        if (s.active) TextButton({ scope.launch { ScheduleWork.cancel(ctx, s.id); store.upsert(s.copy(active = false, lastResult = "Cancelled")) } }) { Text("Cancel") }
                        TextButton({ scope.launch { ScheduleWork.cancel(ctx, s.id); store.remove(s.id) } }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

private fun fmt(t: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(t))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, shown: String, items: List<Pair<String, String>>, onPick: (String, String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(shown, {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.menuAnchor().fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) })
        ExposedDropdownMenu(open, { open = false }) {
            items.forEach { (id, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { onPick(id, n); open = false }) }
        }
    }
}
