package com.victorious.victordrax.engage

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.data.SettingsRepo
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.db.Note
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun EngagementListScreen(settings: Settings, open: (Long) -> Unit) {
    val dao = AppDb.get(LocalContext.current).engagements()
    val list by dao.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { scope.launch { open(dao.insert(Engagement(name = "New engagement", client = "", scope = ""))) } },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("New") })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(12.dp)); GradientTitle("Engagements") }
            if (list.isEmpty()) item { Text("No engagements yet. Create one and record the written authorization before testing.") }
            items(list, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text(e.name + if (e.id == settings.activeEngagementId) "  · ACTIVE" else "") },
                    supportingContent = { Text("${e.client.ifBlank { "(no client)" }} · ${e.scopeList().size} targets") },
                    trailingContent = { if (e.isAuthorized) Icon(Icons.Default.VerifiedUser, "Authorized", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Default.Warning, "No authorization", tint = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.clickable { open(e.id) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngagementDetailScreen(id: Long, settings: Settings, repo: SettingsRepo, back: () -> Unit) {
    val ctx = LocalContext.current
    val dao = AppDb.get(ctx).engagements()
    val e by dao.observe(id).collectAsState(initial = null)
    val notes by dao.notes(id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val cur = e ?: return
    var name by remember(cur.id) { mutableStateOf(cur.name) }
    var client by remember(cur.id) { mutableStateOf(cur.client) }
    var scopeText by remember(cur.id) { mutableStateOf(cur.scope) }
    var who by remember(cur.id) { mutableStateOf(cur.authorizedBy) }
    var note by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun save(update: (Engagement) -> Engagement = { it }) = scope.launch {
        dao.update(update(cur.copy(name = name, client = client, scope = scopeText, authorizedBy = who)))
    }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            save { it.copy(authDocUri = uri.toString()) }
        }
    }
    suspend fun md() = Report.markdown(dao.get(id)!!, dao.notesOnce(id))
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val t = md(); withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(t.toByteArray()) } }; msg = "Markdown exported" }
    }
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { val t = md(); withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { Report.writePdf(t, it) } }; msg = "PDF exported" }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(12.dp)); GradientTitle(cur.name)
            Section("Details") {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Engagement name") })
                OutlinedTextField(client, { client = it }, Modifier.fillMaxWidth(), label = { Text("Client") })
                OutlinedTextField(scopeText, { scopeText = it }, Modifier.fillMaxWidth(), label = { Text("Scope: one target / IP range per line") }, minLines = 3)
                Button({ save() }, Modifier.padding(top = 8.dp)) { Text("Save") }
            }
            Section("Authorization (required)") {
                OutlinedTextField(who, { who = it }, Modifier.fillMaxWidth(), label = { Text("Authorized by (name, role)") })
                Text("Date: " + if (cur.authorizedDate > 0) DateFormat.getDateInstance().format(Date(cur.authorizedDate)) else "not set")
                Text("Document: " + cur.authDocUri.ifBlank { "none attached" }, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ showDate = true }) { Text("Set date") }
                    OutlinedButton({ pickDoc.launch(arrayOf("application/pdf", "image/*", "*/*")) }) { Text("Attach document") }
                }
                Button({ save() }) { Text("Save authorization") }
                Text(if (cur.isAuthorized) "✔ Authorization recorded" else "⚠ Incomplete — tools will warn before launching",
                    color = if (cur.isAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ scope.launch { repo.setActiveEngagement(if (settings.activeEngagementId == id) 0 else id) } }) {
                    Text(if (settings.activeEngagementId == id) "Deactivate" else "Set active") }
                OutlinedButton({ exportMd.launch("${cur.name}.md") }) { Text("Export .md") }
                OutlinedButton({ exportPdf.launch("${cur.name}.pdf") }) { Text("Export .pdf") }
            }
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Section("Notes") {
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Add note") })
                Button({ if (note.isNotBlank()) scope.launch { dao.addNote(Note(engagementId = id, text = note)); note = "" } }) { Text("Add") }
            }
        }
        items(notes, key = { it.id }) { n ->
            ListItem(headlineContent = { Text(n.text) },
                supportingContent = { Text(DateFormat.getDateTimeInstance().format(Date(n.createdAt))) },
                trailingContent = { IconButton({ scope.launch { dao.deleteNote(n) } }) { Icon(Icons.Default.Delete, "Delete note") } })
        }
        item {
            TextButton({ confirmDelete = true }, Modifier.padding(vertical = 16.dp)) { Text("Delete engagement", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = cur.authorizedDate.takeIf { it > 0 } ?: System.currentTimeMillis())
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton({ showDate = false; st.selectedDateMillis?.let { d -> save { it.copy(authorizedDate = d) } } }) { Text("OK") } }) {
            DatePicker(st)
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete engagement?") }, text = { Text("This removes the engagement and its notes.") },
        confirmButton = { TextButton({ scope.launch { if (settings.activeEngagementId == id) repo.setActiveEngagement(0); dao.delete(cur); back() } }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}
