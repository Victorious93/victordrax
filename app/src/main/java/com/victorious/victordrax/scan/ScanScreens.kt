package com.victorious.victordrax.scan

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.ScanImport
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

@Composable
fun ScanListScreen(engagementId: Long, open: (Long) -> Unit) {
    val ctx = LocalContext.current
    val dao = AppDb.get(ctx).scans()
    val list by dao.forEngagement(engagementId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            msg = runCatching {
                val (name, text) = withContext(Dispatchers.IO) {
                    val n = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null } ?: "scan"
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    require(bytes.size <= MAX_IMPORT_BYTES) { "file too large (max 8 MB)" }
                    n to String(bytes)
                }
                val table = ScanParser.parse(name, text)
                dao.insert(ScanImport(engagementId = engagementId, name = name, format = table.format, rowsJson = ScanTable.toJson(table)))
                "Imported ${table.rows.size} rows from $name (${table.format})"
            }.getOrElse { "Import failed: ${it.message}" }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(12.dp)); GradientTitle("Scan results")
            Section("Import") {
                Text("nmap XML (-oX), nmap grepable (-oG), JSON or CSV. Results are stored with this engagement.")
                Button({ pick.launch(arrayOf("text/xml", "application/xml", "application/json", "text/csv", "text/plain", "*/*")) }) {
                    Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("Import file") }
                msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            if (list.isEmpty()) Text("No imports yet.")
        }
        items(list, key = { it.id }) { s ->
            ListItem(headlineContent = { Text(s.name) }, supportingContent = { Text(s.format) },
                modifier = Modifier.clickable { open(s.id) },
                trailingContent = { IconButton({ scope.launch { dao.delete(s) } }) { Icon(Icons.Default.Delete, "Delete import") } })
        }
    }
}

private val COLS = listOf("host" to 130, "port" to 64, "state" to 72, "service" to 96, "version" to 180)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanTableView(table: ScanTable, title: String) {
    var query by remember { mutableStateOf("") }
    var host by remember { mutableStateOf<String?>(null) }
    var openOnly by remember { mutableStateOf(false) }
    var sortCol by remember { mutableStateOf("host") }
    var desc by remember { mutableStateOf(false) }
    val filtered = remember(table, query, host, openOnly, sortCol, desc) {
        val keep = table.filter(query, host, openOnly).toSet()
        table.sortedBy(sortCol, desc).filter { it in keep }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp)); GradientTitle(title, 22)
        Text("${table.hosts().size} hosts · ${table.rows.size} rows · showing ${filtered.size}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Filter (host, port, service, version)") })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(openOnly, { openOnly = !openOnly }, { Text("Open only") })
            FilterChip(host == null, { host = null }, { Text("All hosts") })
            table.hosts().take(12).forEach { h -> FilterChip(host == h, { host = if (host == h) null else h }, { Text(h) }) }
        }
        val hs = rememberScrollState()
        Column(Modifier.horizontalScroll(hs)) {
            Row(Modifier.padding(vertical = 6.dp)) {
                COLS.forEach { (c, w) ->
                    Text(c.uppercase() + if (sortCol == c) (if (desc) " ▼" else " ▲") else "",
                        Modifier.width(w.dp).clickable { if (sortCol == c) desc = !desc else { sortCol = c; desc = false } },
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(Modifier.width(COLS.sumOf { it.second }.dp))
            LazyColumn {
                items(filtered) { r ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        val cells = listOf(r.host + if (r.hostname.isNotBlank()) "\n${r.hostname}" else "", if (r.port > 0) "${r.port}/${r.proto}" else "", r.state, r.service, r.versionText)
                        cells.zip(COLS).forEach { (v, c) -> Text(v, Modifier.width(c.second.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanDetailScreen(importId: Long) {
    val dao = AppDb.get(LocalContext.current).scans()
    var imp by remember { mutableStateOf<ScanImport?>(null) }
    LaunchedEffect(importId) { imp = dao.get(importId) }
    val s = imp ?: return
    val table = remember(s.id) { runCatching { ScanTable.fromJson(s.rowsJson) }.getOrElse { ScanTable(s.format, emptyList()) } }
    ScanTableView(table, s.name)
}
