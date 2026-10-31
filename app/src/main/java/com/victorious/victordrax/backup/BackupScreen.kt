package com.victorious.victordrax.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MIN_PASSPHRASE = 10

@Composable
fun BackupScreen() {
    val ctx = LocalContext.current
    val store = remember { BackupStore(AppDb.get(ctx)) }
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var rpass by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val p = pass.toCharArray()
        scope.launch {
            busy = true
            msg = runCatching {
                val b = store.collect()
                withContext(Dispatchers.Default) { ctx.contentResolver.openOutputStream(uri)!!.use { Backup.writeEncrypted(b, p, it) } }
                "Backup written: ${b.engagements.size} engagements, ${b.findings.size} findings, ${b.playbooks.size} playbooks"
            }.getOrElse { "Backup failed: ${it.message}" }
            p.fill('\u0000'); pass = ""; pass2 = ""; busy = false
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val p = rpass.toCharArray()
        scope.launch {
            busy = true
            msg = runCatching {
                val b = withContext(Dispatchers.Default) { ctx.contentResolver.openInputStream(uri)!!.use { Backup.readEncrypted(p, it) } }
                val r = store.restore(b, mode)
                "Restored (${mode.name.lowercase()}): ${r.engagements} engagements, ${r.findings} findings, ${r.notes} notes, ${r.playbooks} playbooks"
            }.getOrElse { if (it is Backup.WrongPasswordException) "Wrong passphrase (or the file is damaged). Nothing was changed." else "Restore failed: ${it.message}" }
            p.fill('\u0000'); rpass = ""; busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("Backup & restore")
        Text("Engagements (with notes and authorization records), findings and your playbooks are encrypted with AES-256-GCM. " +
            "The key is derived from your passphrase with PBKDF2-HMAC-SHA256 (210,000 iterations). The passphrase is never stored; if you lose it the backup cannot be recovered.",
            style = MaterialTheme.typography.bodySmall)
        Section("Export") {
            OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Passphrase (min $MIN_PASSPHRASE chars)") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(pass2, { pass2 = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Repeat passphrase") }, visualTransformation = PasswordVisualTransformation(),
                isError = pass2.isNotEmpty() && pass2 != pass)
            Button({ export.launch("victordrax-backup.vdbak") }, enabled = !busy && pass.length >= MIN_PASSPHRASE && pass == pass2) { Text("Export encrypted backup") }
        }
        Section("Restore") {
            OutlinedTextField(rpass, { rpass = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation())
            Row { RadioButton(mode == RestoreMode.MERGE, { mode = RestoreMode.MERGE }); Text("Merge — add as new items, keep existing", Modifier.padding(top = 12.dp)) }
            Row { RadioButton(mode == RestoreMode.REPLACE, { mode = RestoreMode.REPLACE }); Text("Replace — delete current data first", Modifier.padding(top = 12.dp)) }
            Button({ restore.launch(arrayOf("*/*")) }, enabled = !busy && rpass.isNotEmpty()) { Text("Choose backup file") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
