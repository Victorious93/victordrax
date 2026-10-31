package com.victorious.andrax.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.victorious.andrax.data.Root
import com.victorious.andrax.data.Settings
import com.victorious.andrax.ui.GradientTitle
import com.victorious.andrax.ui.LinkButton
import com.victorious.andrax.ui.Section
import kotlinx.coroutines.launch

@Composable
fun DesktopScreen(settings: Settings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val ready = settings.envInstalled && Root.suBinaryPresent()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GradientTitle("Desktop (VNC)")
        Section("Server") {
            Text("Runs `vncserver ${settings.vncDisplay}` inside the chroot, bound to localhost (port ${Vnc.port(settings.vncDisplay)}).")
            if (!ready) Text("Requires root and an installed environment.", color = MaterialTheme.colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = ready && !busy, onClick = { busy = true; scope.launch {
                    val r = Vnc.start(settings.installPath, settings.vncDisplay); log = "start → exit ${r.code}\n${r.out}"; busy = false } }) { Text("Start") }
                OutlinedButton(enabled = ready && !busy, onClick = { busy = true; scope.launch {
                    val r = Vnc.stop(settings.installPath, settings.vncDisplay); log = "stop → exit ${r.code}\n${r.out}"; busy = false } }) { Text("Stop") }
            }
        }
        Section("Viewer") {
            Button({ if (!Vnc.openViewer(ctx, settings.vncDisplay)) log = "No app handles vnc:// links. Install a VNC viewer first." }) { Text("Open VNC viewer") }
            Text("Uses any installed viewer that supports vnc:// URIs. No embedded viewer yet.", style = MaterialTheme.typography.bodySmall)
            LinkButton("Find a VNC viewer (F-Droid)", "https://f-droid.org/en/packages/com.gaurav.avnc/")
        }
        if (log.isNotBlank()) Section("Output") { Text(log, style = MaterialTheme.typography.bodySmall) }
    }
}
