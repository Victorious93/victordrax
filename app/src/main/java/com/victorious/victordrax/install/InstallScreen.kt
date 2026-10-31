package com.victorious.victordrax.install

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.victorious.victordrax.data.*
import com.victorious.victordrax.ui.*
import kotlinx.coroutines.launch

@Composable
fun InstallScreen(settings: Settings, repo: SettingsRepo) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember(settings.rootfsUrl) { mutableStateOf(settings.rootfsUrl) }
    var sha by remember(settings.rootfsSha256) { mutableStateOf(settings.rootfsSha256) }
    var confirmUninstall by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val infos by WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkLiveData(InstallWorker.UNIQUE).observeAsState(emptyList())
    val info = infos.lastOrNull()
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val urlOk = Validation.isHttpsUrl(url) && Validation.tarFlagsFor(url) != null
    val shaOk = Checksum.parseExpected(sha) != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("Install environment")
        AuthorizedBanner()
        Section("Requirements") {
            Text("• Rooted device (su) — the environment runs as a chroot\n• arm64-v8a recommended\n• Several GB free storage\n• The rootfs archive URL and SHA-256 from the official upstream project")
        }
        Section("Official upstream sources") {
            Text("Copy the rootfs link and its SHA-256 from the official release page only. No mirror is built in.")
            LinkButton("SNAKE Security on GitHub", "https://github.com/snakesec")
            LinkButton("afos-ng (Apache-2.0)", "https://github.com/snakesec/afos-ng")
            LinkButton("andrax-base-files (no license; link only)", "https://github.com/snakesec/andrax-base-files")
        }
        Section("Rootfs source") {
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Rootfs URL (https, .tar/.tar.gz/.tar.xz)") },
                isError = url.isNotBlank() && !urlOk, singleLine = true)
            OutlinedTextField(sha, { sha = it }, Modifier.fillMaxWidth(), label = { Text("Expected SHA-256") },
                isError = sha.isNotBlank() && !shaOk, singleLine = true)
            Text("Install path: ${settings.installPath} (change in Settings)", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = urlOk && shaOk && info?.state?.isFinished != false, onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    scope.launch { repo.setRootfs(url, sha); InstallWorker.enqueue(ctx) }
                }) { Text(if (InstallWorker.partFile(ctx).exists()) "Resume install" else "Download & install") }
                if (info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED)
                    OutlinedButton({ InstallWorker.cancel(ctx) }) { Text("Pause / cancel") }
            }
        }
        if (info != null) Section("Progress") {
            val stage = info.progress.getString(InstallWorker.KEY_STAGE) ?: info.state.name.lowercase()
            val pct = info.progress.getInt(InstallWorker.KEY_PCT, -1)
            Text("State: ${info.state} · $stage")
            if (info.state == WorkInfo.State.RUNNING) {
                if (pct >= 0) LinearProgressIndicator(progress = { pct / 100f }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            info.outputData.getString(InstallWorker.KEY_ERR)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (info.state == WorkInfo.State.SUCCEEDED) Text("Installed ✔", color = MaterialTheme.colorScheme.primary)
        }
        Section("Installed environment") {
            Text(if (settings.envInstalled) "Installed at ${settings.installPath}" else "Not installed")
            OutlinedButton({ confirmUninstall = true }, enabled = settings.envInstalled || InstallWorker.partFile(ctx).exists()) {
                Text("Uninstall…", color = MaterialTheme.colorScheme.error) }
            status?.let { Text(it) }
        }
    }
    if (confirmUninstall) AlertDialog(onDismissRequest = { confirmUninstall = false },
        title = { Text("Uninstall ANDRAX-NG?") },
        text = { Text("This unmounts and permanently deletes ${settings.installPath} (as root) and any partial download.") },
        confirmButton = { TextButton({
            confirmUninstall = false
            scope.launch {
                InstallWorker.cancel(ctx); InstallWorker.partFile(ctx).delete()
                val err = if (settings.envInstalled) Uninstaller.uninstall(settings.installPath) else null
                if (err == null) repo.setInstalled(false)
                status = err ?: "Uninstalled."
            }
        }) { Text("Uninstall", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmUninstall = false }) { Text("Cancel") } })
}
