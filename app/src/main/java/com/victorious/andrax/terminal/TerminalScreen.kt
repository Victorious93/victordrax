package com.victorious.andrax.terminal

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.andrax.data.Root
import com.victorious.andrax.data.Settings
import com.victorious.andrax.ui.GradientTitle
import com.victorious.andrax.ui.LinkButton
import com.victorious.andrax.ui.Section

/** Holds the state for a launch: result message and the "install VictorSuite" prompt. */
class TerminalLaunchState {
    var message by mutableStateOf<String?>(null)
    var showInstall by mutableStateOf(false)
    fun handle(r: LaunchResult) {
        when (r) {
            LaunchResult.Sent -> message = "Opened in VictorSuite."
            LaunchResult.NotInstalled -> showInstall = true
            is LaunchResult.FellBack -> message = "${r.reason}\nVictorSuite opened; the command is on your clipboard — paste it into a session."
            is LaunchResult.Rejected -> message = "Not launched: ${r.reason}"
        }
    }
}

@Composable fun rememberTerminalLaunchState() = remember { TerminalLaunchState() }

fun requestFor(settings: Settings, command: String?, title: String) =
    TerminalRequest(command, title, chroot = settings.envInstalled && Root.suBinaryPresent(), chrootPath = settings.installPath)

@Composable
fun InstallVictorSuiteDialog(state: TerminalLaunchState) {
    if (!state.showInstall) return
    AlertDialog(onDismissRequest = { state.showInstall = false },
        icon = { Icon(Icons.Default.Terminal, null) },
        title = { Text("VictorSuite is not installed") },
        text = { Column { Text("Victorious ANDRAX uses VictorSuite as its terminal. Install it, then try again.")
            LinkButton("Get VictorSuite", VictorSuiteLauncher.RELEASES_URL) } },
        confirmButton = { TextButton({ state.showInstall = false }) { Text("OK") } })
}

@Composable
fun TerminalScreen(settings: Settings) {
    val ctx = LocalContext.current
    val state = rememberTerminalLaunchState()
    var available by remember { mutableStateOf(VictorSuiteLauncher.isAvailable(ctx)) }
    var granted by remember { mutableStateOf(VictorSuiteLauncher.hasPermission(ctx)) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val req = requestFor(settings, null, "ANDRAX-NG")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("Terminal")
        Section("VictorSuite") {
            Text(if (available) "Installed (${VictorSuiteLauncher.PACKAGE})" else "Not installed")
            Text("RUN_COMMAND permission: " + if (granted) "granted" else "not granted")
            Text("Session mode: " + if (req.chroot) "ANDRAX-NG chroot via su at ${req.chrootPath}" else "VictorSuite shell (environment not installed / no root)")
            Spacer(Modifier.height(8.dp))
            Button({ available = VictorSuiteLauncher.isAvailable(ctx); state.handle(VictorSuiteLauncher.launch(ctx, req)) }) {
                Icon(Icons.Default.Terminal, null); Spacer(Modifier.width(6.dp)); Text("Open terminal") }
            if (available && !granted) OutlinedButton({ perm.launch(VictorSuiteLauncher.PERMISSION_RUN_COMMAND) }) { Text("Grant RUN_COMMAND permission") }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp)) }
        }
        Section("One-time setup in VictorSuite") {
            Text("To let this app open sessions directly, add this line to ~/.termux/termux.properties in VictorSuite and restart it:")
            Text("allow-external-apps = true", fontFamily = FontFamily.Monospace)
            Text("Without it, the app opens VictorSuite and copies the command to your clipboard instead.", style = MaterialTheme.typography.bodySmall)
        }
        Section("Command that will run") { Text(VictorSuiteLauncher.script(req), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        LinkButton("VictorSuite on GitHub", "https://github.com/Victorious93/VictorSuite")
    }
    InstallVictorSuiteDialog(state)
}
