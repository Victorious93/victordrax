package com.victorious.victordrax.afos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.terminal.InstallVictorSuiteDialog
import com.victorious.victordrax.terminal.VictorSuiteLauncher
import com.victorious.victordrax.terminal.rememberTerminalLaunchState
import com.victorious.victordrax.terminal.requestFor
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.LinkButton
import com.victorious.victordrax.ui.Section

@Composable
fun AfosScreen(settings: Settings) {
    val ctx = LocalContext.current
    val repo = remember { runCatching { Afos.parseRepository(ctx.assets.open("afos_packages.yaml").bufferedReader().readText()) }.getOrDefault(emptyList()) }
    AfosBrowser(repo, settings) { cmd, title -> VictorSuiteLauncher.launch(ctx, requestFor(ctx, settings, cmd, title)) }
}

@Composable
fun AfosBrowser(repo: List<AfosPackage>, settings: Settings, run: (String, String) -> com.victorious.victordrax.terminal.LaunchResult) {
    var installedText by remember { mutableStateOf("") }
    val installed = remember(installedText) { Afos.parseInstalled(installedText) }
    val pkgs = remember(repo, installed) { Afos.merge(repo, installed) }
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var installedOnly by remember { mutableStateOf(false) }
    val cats = remember(repo) { Afos.categoriesOf(repo) }
    val shown = remember(pkgs, query, cat, installedOnly) { Afos.filter(pkgs, query, cat, installedOnly) }
    val state = rememberTerminalLaunchState()
    var showPaste by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(12.dp)); GradientTitle("Packages (afos-ng)")
            Text("${repo.size} packages in the bundled afos-ng catalog. Commands run inside the ANDRAX-NG chroot via VictorSuite.",
                style = MaterialTheme.typography.bodySmall)
            if (!settings.envInstalled) Text("⚠ Environment not installed: afos commands need the ANDRAX-NG chroot (root).", color = MaterialTheme.colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ state.handle(run(Afos.checkUpdatesCommand(), "afos update check")) }) { Text("Check updates") }
                OutlinedButton({ state.handle(run(Afos.updateAllCommand(), "afos update all")) }) { Text("Update all") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ state.handle(run(Afos.listInstalledCommand(), "afos list")) }) { Text("List installed") }
                TextButton({ showPaste = !showPaste }) { Text(if (showPaste) "Hide" else "Paste afos -l output") }
            }
            if (showPaste) OutlinedTextField(installedText, { installedText = it }, Modifier.fillMaxWidth(), minLines = 3,
                label = { Text("Paste output of `afos -l` to mark installed (${installed.size})") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search packages") })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(installedOnly, { installedOnly = !installedOnly }, { Text("Installed") })
                FilterChip(cat == null, { cat = null }, { Text("All") })
                cats.forEach { c -> FilterChip(cat == c, { cat = if (cat == c) null else c }, { Text(c) }) }
            }
            Text("${shown.size} shown", style = MaterialTheme.typography.labelSmall)
        }
        items(shown, key = { it.name }) { p ->
            ListItem(headlineContent = { Text(p.name + if (p.version.isNotBlank()) "  ${p.version}" else "") },
                supportingContent = { Text(p.description.take(140), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    TextButton({ state.handle(run(if (p.installed) Afos.updateCommand(p.name) else Afos.installCommand(p.name), "afos ${p.name}")) }) {
                        Text(if (p.installed) "Update" else "Install") }
                })
        }
        item {
            Section("Source") {
                Text("Catalog: afos-ng repository/afos.yaml (SNAKE Security, Apache License 2.0).", style = MaterialTheme.typography.bodySmall)
                LinkButton("github.com/snakesec/afos-ng", "https://github.com/snakesec/afos-ng")
            }
        }
    }
    InstallVictorSuiteDialog(state)
}
