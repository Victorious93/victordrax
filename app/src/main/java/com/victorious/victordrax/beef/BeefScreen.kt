package com.victorious.victordrax.beef

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.LEGAL_NOTICE
import com.victorious.victordrax.data.Root
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.terminal.InstallVictorSuiteDialog
import com.victorious.victordrax.terminal.TerminalRequest
import com.victorious.victordrax.terminal.VictorSuiteLauncher
import com.victorious.victordrax.terminal.rememberTerminalLaunchState
import com.victorious.victordrax.ui.AuthorizedBanner
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.NeonCard
import com.victorious.victordrax.ui.Section
import com.victorious.victordrax.workflow.RunGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Build an argv-based VictorSuite request so shell operators (&&, ||) are carried safely. */
private fun beefRequest(settings: Settings, command: String, title: String) = TerminalRequest(
    command = null,
    title = title,
    chroot = settings.envInstalled && Root.suBinaryPresent(),
    chrootPath = settings.installPath,
    argv = listOf("bash", "-lc", command),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeefScreen(settings: Settings) {
    val ctx = LocalContext.current
    val db = AppDb.get(ctx)
    val scope = rememberCoroutineScope()
    val repo = remember { BeefConfigRepo(ctx) }
    val cfg by repo.config.collectAsState(initial = BeefConfig())
    val engagements by db.engagements().all().collectAsState(initial = emptyList())
    val launch = rememberTerminalLaunchState()

    var engId by remember { mutableStateOf(settings.activeEngagementId) }
    val eng: Engagement? = engagements.firstOrNull { it.id == engId }
    val serverGuard = BeefGuard.guardServer(eng)

    // Connection state (token held in memory on the client, never persisted).
    var client by remember { mutableStateOf<BeefClient?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var hooks by remember { mutableStateOf<List<HookedBrowser>>(emptyList()) }
    var modules by remember { mutableStateOf<List<BeefModule>>(emptyList()) }
    var selectedHook by remember { mutableStateOf<HookedBrowser?>(null) }
    var autoRefresh by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("BeEF-XSS Control Panel")
        Spacer(Modifier.height(8.dp))
        AuthorizedBanner()
        Spacer(Modifier.height(4.dp))
        Text(LEGAL_NOTICE, style = MaterialTheme.typography.bodySmall)

        // ---- Engagement selection + authorization gate -------------------------------------
        var pickerOpen by remember { mutableStateOf(false) }
        Section("Engagement") {
            ExposedDropdownMenuBox(pickerOpen, { pickerOpen = it }) {
                OutlinedTextField(eng?.name ?: "Select engagement", {}, readOnly = true, label = { Text("Authorized engagement") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pickerOpen) })
                ExposedDropdownMenu(pickerOpen, { pickerOpen = false }) {
                    engagements.forEach { e ->
                        DropdownMenuItem(text = { Text(e.name + if (e.isAuthorized) "" else " (no authorization)") },
                            onClick = { engId = e.id; pickerOpen = false })
                    }
                }
            }
            eng?.let { Text("Scope: " + it.scopeList().joinToString(", ").ifBlank { "(empty)" }, style = MaterialTheme.typography.bodySmall) }
            GuardLine(serverGuard)
        }

        // ---- Connection config --------------------------------------------------------------
        var host by remember(cfg.host) { mutableStateOf(cfg.host) }
        var port by remember(cfg.port) { mutableStateOf(cfg.port.toString()) }
        var user by remember(cfg.username) { mutableStateOf(cfg.username) }
        var pass by remember(cfg.password) { mutableStateOf(cfg.password) }
        var installDir by remember(cfg.installDir) { mutableStateOf(cfg.installDir) }
        var showPass by remember { mutableStateOf(false) }
        Section("BeEF connection") {
            OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Host") },
                isError = !BeefConfig.isValidHost(host))
            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = port.toIntOrNull()?.let { !BeefConfig.isValidPort(it) } ?: true)
            OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Username") })
            OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Password") },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ showPass = !showPass }) { Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle password") }
                })
            Text("Default: http://127.0.0.1:3000 — credentials are in BeEF's config.yaml.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    val p = port.toIntOrNull() ?: BeefApi.DEFAULT_PORT
                    scope.launch { repo.save(BeefConfig(host.trim(), p, user.trim(), pass, installDir.trim())); status = "Saved." }
                }, enabled = BeefConfig.isValidHost(host) && (port.toIntOrNull()?.let { BeefConfig.isValidPort(it) } == true)) { Text("Save") }

                val canConnect = serverGuard == RunGuard.Ok && BeefConfig.isValidHost(host) &&
                    (port.toIntOrNull()?.let { BeefConfig.isValidPort(it) } == true) && !connecting
                Button(onClick = {
                    val p = port.toIntOrNull() ?: BeefApi.DEFAULT_PORT
                    val c = BeefClient(BeefApi.baseUrl(host.trim(), p))
                    connecting = true; status = "Connecting…"
                    scope.launch {
                        runCatching { c.login(user.trim(), pass) }
                            .onSuccess { client = c; status = "Connected to BeEF REST API."; hooks = runCatching { c.hooks() }.getOrDefault(emptyList()); modules = runCatching { c.modules() }.getOrDefault(emptyList()) }
                            .onFailure { status = "Connect failed: ${it.message}" }
                        connecting = false
                    }
                }, enabled = canConnect) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(6.dp)); Text("Connect") }

                if (client?.isAuthenticated == true) OutlinedButton({ client?.logout(); client = null; hooks = emptyList(); modules = emptyList(); status = "Disconnected." }) { Text("Disconnect") }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp)) }
        }

        // ---- Start / stop / install ---------------------------------------------------------
        Section("Run BeEF in VictorSuite") {
            OutlinedTextField(installDir, { installDir = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("BeEF install dir") })
            Text("Starts/stops BeEF in your VictorSuite (Termux/ANDRAX-NG) environment.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ launch.handle(VictorSuiteLauncher.launch(ctx, beefRequest(settings, BeefCommands.start(installDir.trim()), "Start BeEF"))) },
                    enabled = serverGuard == RunGuard.Ok) { Icon(Icons.Default.PlayArrow, null); Text("Start") }
                OutlinedButton({ launch.handle(VictorSuiteLauncher.launch(ctx, beefRequest(settings, BeefCommands.stop(), "Stop BeEF"))) }) { Icon(Icons.Default.Stop, null); Text("Stop") }
                OutlinedButton({ launch.handle(VictorSuiteLauncher.launch(ctx, beefRequest(settings, BeefCommands.showConfig(installDir.trim()), "BeEF config"))) }) { Text("Show config") }
            }
            if (serverGuard != RunGuard.Ok) Text("Authorize an engagement to start BeEF.", style = MaterialTheme.typography.bodySmall)
            launch.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        }

        var showInstall by remember { mutableStateOf(false) }
        Section("Install / setup helper") {
            Text("BeEF is not trivial to install in Termux (Ruby gems with native extensions). " +
                "Run these steps in VictorSuite in order, then confirm BeEF is up before connecting.",
                style = MaterialTheme.typography.bodySmall)
            TextButton({ showInstall = !showInstall }) { Text(if (showInstall) "Hide steps" else "Show setup steps") }
            if (showInstall) {
                val clip = ctx.getSystemService(ClipboardManager::class.java)
                BeefCommands.installSteps(installDir.trim()).forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${s.label}", style = MaterialTheme.typography.labelLarge)
                            Text(s.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton({ clip.setPrimaryClip(ClipData.newPlainText("beef-step", s.command)) }) { Icon(Icons.Default.ContentCopy, "Copy") }
                    }
                }
            }
        }

        // ---- Hooked browsers dashboard ------------------------------------------------------
        if (client?.isAuthenticated == true) {
            // Auto-refresh poll loop.
            LaunchedEffect(client, autoRefresh) {
                while (autoRefresh && client?.isAuthenticated == true) {
                    hooks = runCatching { client!!.hooks() }.getOrDefault(hooks)
                    delay(5000)
                }
            }
            Section("Hooked browsers (${hooks.count { it.online }} online / ${hooks.size} total)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(autoRefresh, { autoRefresh = it }); Text("Auto-refresh (5s)", Modifier.padding(start = 4.dp))
                    Spacer(Modifier.weight(1f))
                    IconButton({ scope.launch { hooks = runCatching { client!!.hooks() }.getOrDefault(hooks) } }) { Icon(Icons.Default.Refresh, "Refresh") }
                }
                if (hooks.isEmpty()) Text("No hooked browsers yet.", style = MaterialTheme.typography.bodySmall)
                hooks.forEach { h ->
                    Spacer(Modifier.height(6.dp))
                    NeonCard(onClick = { selectedHook = h }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (h.online) Icons.Default.Circle else Icons.Default.RadioButtonUnchecked,
                                null, tint = if (h.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(h.browserLabel, style = MaterialTheme.typography.titleSmall)
                            if (selectedHook?.session == h.session) { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.CheckCircle, "selected", tint = MaterialTheme.colorScheme.primary) }
                        }
                        Text(h.osLabel, style = MaterialTheme.typography.bodySmall)
                        Text("IP: ${h.ip.ifBlank { "?" }}   Host: ${h.scopeHost.ifBlank { "?" }}", style = MaterialTheme.typography.bodySmall)
                        Text(h.pageUri.ifBlank { "(no page)" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ---- Module picker + launch -----------------------------------------------------
            ModuleLauncher(client!!, eng, selectedHook, modules, scope)
        }
    }
    InstallVictorSuiteDialog(launch)
}

@Composable
private fun GuardLine(g: RunGuard) = when (g) {
    is RunGuard.Blocked -> Text("⚠ ${g.reason}", color = MaterialTheme.colorScheme.secondary)
    RunGuard.Ok -> Text("✔ Authorized", color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleLauncher(
    client: BeefClient,
    eng: Engagement?,
    hook: HookedBrowser?,
    modules: List<BeefModule>,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var selectedModule by remember { mutableStateOf<BeefModule?>(null) }
    var moduleMenu by remember { mutableStateOf(false) }
    var optionsText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val guard = BeefGuard.guardModule(eng, hook)

    Section("Run a command module") {
        GuardLine(guard)
        Text("Target: " + (hook?.let { "${it.browserLabel} @ ${it.scopeHost}" } ?: "select a hooked browser"),
            style = MaterialTheme.typography.bodySmall)
        ExposedDropdownMenuBox(moduleMenu, { moduleMenu = it }) {
            OutlinedTextField(selectedModule?.name ?: "Select module (${modules.size})", {}, readOnly = true,
                label = { Text("Command module") }, modifier = Modifier.menuAnchor().fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(moduleMenu) })
            ExposedDropdownMenu(moduleMenu, { moduleMenu = false }) {
                modules.forEach { m ->
                    DropdownMenuItem(text = { Text(m.name + (if (m.category.isNotBlank()) " · ${m.category}" else "")) },
                        onClick = { selectedModule = m; moduleMenu = false })
                }
            }
        }
        OutlinedTextField(optionsText, { optionsText = it }, Modifier.fillMaxWidth(),
            label = { Text("Options (key=value per line, optional)") },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
        Button(onClick = {
            val m = selectedModule ?: return@Button
            val h = hook ?: return@Button
            val opts = optionsText.lines().mapNotNull { l ->
                val i = l.indexOf('='); if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 1).trim()
            }.toMap()
            busy = true; result = "Launching…"
            scope.launch {
                runCatching {
                    val cmdId = client.launchModule(h.session, m.id, opts)
                    "Launched (command_id=$cmdId).\n" + runCatching { client.moduleResult(h.session, m.id, cmdId) }.getOrDefault("")
                }.onSuccess { result = it }.onFailure { result = "Failed: ${it.message}" }
                busy = false
            }
        }, enabled = guard == RunGuard.Ok && selectedModule != null && !busy) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Launch module")
        }
        result?.let { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    }
}
