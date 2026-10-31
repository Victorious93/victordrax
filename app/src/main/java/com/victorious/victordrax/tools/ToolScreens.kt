package com.victorious.victordrax.tools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.data.SettingsRepo
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.terminal.*
import com.victorious.victordrax.tools.spec.ToolSpecs
import com.victorious.victordrax.ui.*
import kotlinx.coroutines.launch

@Composable
fun rememberCatalog(): Catalog? {
    val ctx = LocalContext.current
    return remember { runCatching { ToolCatalog.parse(ctx.assets.open("tools.json").bufferedReader().readText()) }.getOrNull() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolLibraryScreen(settings: Settings, repo: SettingsRepo, open: (String) -> Unit) {
    val catalog = rememberCatalog() ?: run { Text("Tool catalog failed to load."); return }
    var q by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val list = remember(q, cat, favOnly, settings.favorites) { ToolCatalog.filter(catalog.tools, q, cat, settings.favorites, favOnly) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp)); GradientTitle("Tool library")
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search tools") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(favOnly, { favOnly = !favOnly }, { Text("★ Favorites") })
            FilterChip(cat == null, { cat = null }, { Text("All") })
            catalog.categories.forEach { c -> FilterChip(cat == c.id, { cat = if (cat == c.id) null else c.id }, { Text(c.name) }) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { t ->
                val fav = t.id in settings.favorites
                ListItem(
                    headlineContent = { Text(t.name) },
                    supportingContent = { Text(t.description) },
                    overlineContent = { Text(catalog.categories.first { it.id == t.category }.name) },
                    trailingContent = { IconButton({ scope.launch { repo.toggleFavorite(t.id) } }) {
                        Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, "Favorite", tint = MaterialTheme.colorScheme.secondary) } },
                    modifier = Modifier.animateItemPlacement().fillMaxWidth(),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
                TextButton({ open(t.id) }, Modifier.offset(y = (-8).dp)) { Text("Docs & launch →") }
            }
            if (list.isEmpty()) item { Text("No tools match.", Modifier.padding(16.dp)) }
        }
    }
}

@Composable
fun ToolDetailScreen(id: String, settings: Settings, repo: SettingsRepo, openScreen: (String) -> Unit = {}, onLaunched: () -> Unit = {}) {
    val ctx = LocalContext.current
    val tool = rememberCatalog()?.tools?.find { it.id == id } ?: run { Text("Unknown tool"); return }
    val scope = rememberCoroutineScope()
    var warn by remember { mutableStateOf<String?>(null) }
    val term = rememberTerminalLaunchState()
    fun launch() { term.handle(VictorSuiteLauncher.launch(ctx, requestFor(ctx, settings, tool.command, tool.name))) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle(tool.name)
        AuthorizedBanner()
        val dedicated: Pair<String, String>? = when {
            tool.id == "metasploit" -> "metasploit" to "Open console & resource builder"
            ToolSpecs.byId(tool.id) != null -> "toolform/${tool.id}" to "Open ${tool.name} form"
            tool.launchOnly -> "launch/${tool.id}" to "Open launcher (desktop)"
            else -> null
        }
        dedicated?.let { (route, label) ->
            Button({ openScreen(route) }, Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text(label)
            }
        }
        Section("Description") { Text(tool.description) }
        Section("Usage") { Text(tool.usage, fontFamily = FontFamily.Monospace) }
        Section("Upstream") { LinkButton(tool.upstream, tool.upstream) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                scope.launch {
                    val e = settings.activeEngagementId.takeIf { it > 0 }?.let { AppDb.get(ctx).engagements().get(it) }
                    warn = when {
                        e == null -> "No active engagement is selected."
                        !e.isAuthorized -> "Active engagement \"${e.name}\" has no complete authorization record (who, date, document)."
                        else -> null
                    }
                    if (warn == null) launch()
                }
            }) { Icon(Icons.Default.Terminal, null); Spacer(Modifier.width(6.dp)); Text("Open in VictorSuite") }
            OutlinedButton({ scope.launch { repo.toggleFavorite(tool.id) } }) {
                Text(if (tool.id in settings.favorites) "★ Unfavorite" else "☆ Favorite") }
        }
        Text("Runs: ${tool.command}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        term.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
    }
    InstallVictorSuiteDialog(term)
    warn?.let { msg ->
        AlertDialog(onDismissRequest = { warn = null },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("No authorization on record") },
            text = { Text("$msg\n\nOnly continue if you have written permission to test the target.") },
            confirmButton = { TextButton({ warn = null; launch() }) { Text("I am authorized – open") } },
            dismissButton = { TextButton({ warn = null }) { Text("Cancel") } })
    }
}
