package com.victorious.victordrax.tools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.data.SettingsRepo
import com.victorious.victordrax.terminal.*
import com.victorious.victordrax.tools.parse.NmapParser
import com.victorious.victordrax.tools.spec.*
import com.victorious.victordrax.ui.*

/** Loads the active engagement for scope enforcement and launch gating. */
@Composable
private fun activeEngagement(settings: Settings): State<Engagement?> {
    val ctx = LocalContext.current
    return produceState<Engagement?>(initialValue = null, settings.activeEngagementId) {
        value = settings.activeEngagementId.takeIf { it > 0 }?.let { AppDb.get(ctx).engagements().get(it) }
    }
}

@Composable
private fun EngagementBar(e: Engagement?) {
    val (text, color) = when {
        e == null -> "No active engagement selected — pick one under Engage before running tools." to MaterialTheme.colorScheme.error
        !e.isAuthorized -> "Active engagement \"${e.name}\" has no complete authorization on record." to MaterialTheme.colorScheme.secondary
        else -> "Active engagement: ${e.name} · ${e.scopeList().size} in-scope target(s)" to MaterialTheme.colorScheme.primary
    }
    Text(text, color = color, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
}

private fun requestArgv(ctx: android.content.Context, settings: Settings, argv: List<String>, title: String) =
    com.victorious.victordrax.terminal.requestFor(ctx, settings, null, title, argv)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolFormScreen(id: String, settings: Settings, repo: SettingsRepo) {
    val spec = ToolSpecs.byId(id) ?: run { Text("No form for \"$id\"."); return }
    val ctx = LocalContext.current
    val tool = rememberCatalog()?.tools?.find { it.id == id }
    val engagement by activeEngagement(settings)
    val values = remember(spec.id) { mutableStateMapOf<String, String>().apply { putAll(spec.defaults()) } }
    val term = rememberTerminalLaunchState()
    var error by remember { mutableStateOf<String?>(null) }

    val scopeList = engagement?.scopeList() ?: emptyList()
    val result = CommandBuilder.build(spec, values, scopeList)
    val preview = (result as? BuildResult.Ok)?.preview

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle(spec.name)
        AuthorizedBanner()
        EngagementBar(engagement)
        tool?.let { Section("Documentation") { LinkButton(it.docsUrl, it.docsUrl) } }

        if (spec.presets.isNotEmpty()) Section("Presets") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                spec.presets.forEach { p ->
                    AssistChip(onClick = { values.putAll(spec.defaults()); values.putAll(p.values) }, label = { Text(p.name) })
                }
            }
        }

        Section("Options") {
            spec.fields.forEach { f -> FieldEditor(f, values) }
        }

        Section("Command preview") {
            Text(preview ?: (result as BuildResult.Err).message, fontFamily = FontFamily.Monospace,
                color = if (preview != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        }

        Button(onClick = {
            error = null
            val e = engagement
            when {
                e == null -> error = "Select an active engagement first."
                !e.isAuthorized -> error = "The active engagement has no authorization on record."
                result is BuildResult.Err -> error = result.message
                result is BuildResult.Ok -> term.handle(VictorSuiteLauncher.launch(ctx, requestArgv(ctx, settings, result.argv, spec.name)))
            }
        }, enabled = preview != null) {
            Icon(Icons.Default.Terminal, null); Spacer(Modifier.width(6.dp)); Text("Run in VictorSuite")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
        term.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp)) }

        if (spec.parseOutput) NmapResults()
    }
    InstallVictorSuiteDialog(term)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditor(f: FieldSpec, values: MutableMap<String, String>) {
    val v = values[f.key] ?: f.default
    when (f.type) {
        FieldType.TOGGLE -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = v.equals("true", true), onCheckedChange = { values[f.key] = it.toString() })
            Spacer(Modifier.width(8.dp)); Text(f.label)
        }
        FieldType.DROPDOWN -> {
            var open by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(f.options.firstOrNull { it.value == v }?.label ?: v, {}, readOnly = true,
                    label = { Text(f.label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                    modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(open, { open = false }) {
                    f.options.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt.label) }, onClick = { values[f.key] = opt.value; open = false })
                    }
                }
            }
        }
        else -> OutlinedTextField(
            value = v, onValueChange = { values[f.key] = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(f.label + if (f.required) " *" else "") },
            placeholder = { if (f.placeholder.isNotEmpty()) Text(f.placeholder) },
            singleLine = true,
            keyboardOptions = if (f.type == FieldType.NUMBER) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        )
    }
}

@Composable
private fun NmapResults() {
    var raw by remember { mutableStateOf("") }
    val hosts = remember(raw) { runCatching { NmapParser.parse(raw) }.getOrDefault(emptyList()) }
    Section("Parse results") {
        Text("Paste nmap output (or the contents of an -oN file) to see parsed hosts and open ports.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(raw, { raw = it }, Modifier.fillMaxWidth().height(140.dp),
            label = { Text("nmap output") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        hosts.forEach { h ->
            Spacer(Modifier.height(6.dp))
            Text("${h.address}${h.hostname?.let { " ($it)" } ?: ""} — ${h.openPorts.size} open",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            h.openPorts.forEach { p ->
                Text("  ${p.port}/${p.proto}  ${p.service} ${p.version}".trimEnd(),
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (raw.isNotBlank() && hosts.isEmpty()) Text("No hosts parsed.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MetasploitScreen(settings: Settings, repo: SettingsRepo) {
    val ctx = LocalContext.current
    val engagement by activeEngagement(settings)
    var module by remember { mutableStateOf("auxiliary/scanner/ssh/ssh_version") }
    var rhosts by remember { mutableStateOf("") }
    var rport by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var lhost by remember { mutableStateOf("") }
    var lport by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("run") }
    val term = rememberTerminalLaunchState()
    var error by remember { mutableStateOf<String?>(null) }

    val scopeList = engagement?.scopeList() ?: emptyList()
    val opts = MsfResource.Options(module.trim(), rhosts.trim(), rport.trim(), payload.trim(), lhost.trim(), lport.trim(), action = action)
    val built = MsfResource.build(opts, scopeList)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("Metasploit Framework")
        AuthorizedBanner()
        EngagementBar(engagement)
        Section("Documentation") { LinkButton("docs.metasploit.com", "https://docs.metasploit.com/") }
        Section("Resource script builder") {
            OutlinedTextField(module, { module = it }, Modifier.fillMaxWidth(), label = { Text("Module *") }, singleLine = true)
            OutlinedTextField(rhosts, { rhosts = it }, Modifier.fillMaxWidth(), label = { Text("RHOSTS * (in-scope)") }, singleLine = true)
            OutlinedTextField(rport, { rport = it }, Modifier.fillMaxWidth(), label = { Text("RPORT") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(payload, { payload = it }, Modifier.fillMaxWidth(), label = { Text("PAYLOAD") }, singleLine = true)
            OutlinedTextField(lhost, { lhost = it }, Modifier.fillMaxWidth(), label = { Text("LHOST") }, singleLine = true)
            OutlinedTextField(lport, { lport = it }, Modifier.fillMaxWidth(), label = { Text("LPORT") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("run", "exploit", "check").forEach { a ->
                    FilterChip(action == a, { action = a }, { Text(a) })
                }
            }
        }
        Section("Resource script preview") {
            when (built) {
                is MsfResource.Result.Ok -> built.lines.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                is MsfResource.Result.Err -> Text(built.message, color = MaterialTheme.colorScheme.error)
            }
        }
        Button(onClick = {
            error = null
            val e = engagement
            when {
                e == null -> error = "Select an active engagement first."
                !e.isAuthorized -> error = "The active engagement has no authorization on record."
                built is MsfResource.Result.Err -> error = built.message
                built is MsfResource.Result.Ok -> term.handle(VictorSuiteLauncher.launch(ctx, requestArgv(ctx, settings, built.argv, "Metasploit")))
            }
        }, enabled = built is MsfResource.Result.Ok) {
            Icon(Icons.Default.Terminal, null); Spacer(Modifier.width(6.dp)); Text("Launch msfconsole")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
        term.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp)) }
        Text("Opens an msfconsole session with the script above. The console stays interactive after it runs.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
    InstallVictorSuiteDialog(term)
}

@Composable
fun LaunchOnlyScreen(id: String, settings: Settings) {
    val ctx = LocalContext.current
    val spec = LaunchSpecs.byId(id) ?: run { Text("Unknown tool \"$id\"."); return }
    val tool = rememberCatalog()?.tools?.find { it.id == id }
    val engagement by activeEngagement(settings)
    val term = rememberTerminalLaunchState()
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle(spec.name)
        AuthorizedBanner()
        EngagementBar(engagement)
        tool?.let { Section("Documentation") { LinkButton(it.docsUrl, it.docsUrl) } }
        Section("Heavy desktop tool") {
            Text("${spec.name} is a desktop application. It opens inside the ANDRAX-NG VNC desktop session rather than a form. " +
                "Stay within your authorized engagement scope.", style = MaterialTheme.typography.bodySmall)
        }
        spec.options.forEach { opt ->
            Section(opt.label) {
                Text(opt.note, style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    error = null
                    val e = engagement
                    when {
                        e == null -> error = "Select an active engagement first."
                        !e.isAuthorized -> error = "The active engagement has no authorization on record."
                        opt.command == null -> term.handle(VictorSuiteLauncher.launch(ctx,
                            requestArgv(ctx, settings, listOf("dumpcap", "-D"), spec.name + " (list interfaces)")))
                        else -> term.handle(VictorSuiteLauncher.launch(ctx,
                            requestArgv(ctx, settings, listOf(opt.command), spec.name)))
                    }
                }) { Icon(Icons.Default.DesktopWindows, null); Spacer(Modifier.width(6.dp)); Text(if (opt.desktop) "Open in desktop" else "Start capture helper") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
        term.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp)) }
        Text("Tip: start the VNC desktop from More → Desktop (VNC) before opening a GUI tool.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
    InstallVictorSuiteDialog(term)
}
