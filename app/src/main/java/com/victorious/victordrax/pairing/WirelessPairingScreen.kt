package com.victorious.victordrax.pairing

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.shizuku.ShizukuGate
import com.victorious.victordrax.shizuku.ShizukuStatus
import com.victorious.victordrax.terminal.InstallVictorSuiteDialog
import com.victorious.victordrax.terminal.TerminalRequest
import com.victorious.victordrax.terminal.VictorSuiteLauncher
import com.victorious.victordrax.terminal.rememberTerminalLaunchState
import com.victorious.victordrax.ui.AppLogo
import com.victorious.victordrax.ui.AuthorizedBanner
import com.victorious.victordrax.ui.GradientTitle
import com.victorious.victordrax.ui.Section
import kotlinx.coroutines.launch

/** Best-effort deep links; OEM Settings differ, so each falls back to the next. */
object DevSettingsLinks {
    const val ACTION_WIRELESS_DEBUGGING = "android.settings.WIRELESS_DEBUGGING_SETTINGS"

    fun openDeveloperOptions(ctx: Context): Boolean =
        start(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) ||
            start(ctx, Intent(AndroidSettings.ACTION_DEVICE_INFO_SETTINGS)) // tap Build number 7x

    fun openWirelessDebugging(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT >= 30 && start(ctx, Intent(ACTION_WIRELESS_DEBUGGING))) ||
            // AOSP Settings fragment highlight extra; ignored where unsupported.
            start(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")) ||
            openDeveloperOptions(ctx)

    private fun start(ctx: Context, i: Intent): Boolean = runCatching {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}

@Composable
fun WirelessPairingScreen() {
    val ctx = LocalContext.current
    val prefs = remember { PairingPrefs(ctx) }
    val scope = rememberCoroutineScope()
    val savedHost by prefs.host.collectAsState(initial = "")
    var pairAddr by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connectAddr by remember { mutableStateOf("") }
    LaunchedEffect(savedHost) {
        if (savedHost.isNotBlank()) {
            if (pairAddr.isBlank()) pairAddr = "$savedHost:"
            if (connectAddr.isBlank()) connectAddr = "$savedHost:"
        }
    }
    var shizuku by remember { mutableStateOf(ShizukuGate.status(ctx)) }
    DisposableEffect(Unit) {
        ShizukuGate.ensureHooked()
        val l: (ShizukuStatus) -> Unit = { shizuku = it }
        ShizukuGate.addStatusListener(l)
        onDispose { ShizukuGate.removeStatusListener(l) }
    }
    val launch = rememberTerminalLaunchState()
    val check = WirelessAdb.validate(pairAddr, code, connectAddr)
    val err = check as? WirelessAdb.Check.Error

    fun run(seq: String, host: String) {
        scope.launch { prefs.saveHost(host) }
        launch.handle(VictorSuiteLauncher.launch(ctx,
            TerminalRequest(command = null, title = "Wireless debugging → Shizuku", chroot = false, chrootPath = "",
                argv = listOf("bash", "-c", seq))))
        code = "" // single-use; never kept
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row { AppLogo(40.dp); Spacer(Modifier.width(10.dp)); GradientTitle("Wireless debugging → Shizuku") }
        Spacer(Modifier.height(8.dp))
        AuthorizedBanner()
        Text("Use this only on YOUR OWN device, for authorized testing. It enables ADB on this phone so " +
            "Shizuku can run. Turn Wireless debugging off when you are done. VictorDrax stores no secrets: " +
            "the pairing code is used once and never saved (only the last host IP is remembered).",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))

        Section("Shizuku status") {
            val up = shizuku == ShizukuStatus.GRANTED
            Text((if (up) "✔ " else "• ") + ShizukuGate.label(shizuku),
                color = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            when (shizuku) {
                ShizukuStatus.NOT_INSTALLED -> Button({ ShizukuGate.openDownload(ctx) }) { Text("Get Shizuku") }
                ShizukuStatus.DENIED -> Button({ ShizukuGate.requestPermission() }) { Text("Grant VictorDrax access") }
                ShizukuStatus.GRANTED -> Text("Shizuku is up. Privilege mode Auto/Shizuku will now use it.", style = MaterialTheme.typography.bodySmall)
                else -> TextButton({ shizuku = ShizukuGate.status(ctx) }) { Text("Refresh") }
            }
        }

        Section("1 · Enable Developer options & Wireless debugging") {
            Text("Android 11+ required. Settings → About phone → tap Build number 7 times. Then Developer options → " +
                "Wireless debugging → On (stay on Wi-Fi).", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ DevSettingsLinks.openDeveloperOptions(ctx) }) { Text("Developer options") }
                Button({ DevSettingsLinks.openWirelessDebugging(ctx) }) { Text("Wireless debugging") }
            }
            if (Build.VERSION.SDK_INT < 30) Text("This device is below Android 11: use USB ADB from a PC or root instead.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Section("2 · Easiest: Shizuku's built-in pairing") {
            Text("Shizuku → \"Start via Wireless debugging\" → Pairing, then enter the code from its notification " +
                "(use split-screen). No terminal needed. Come back here; status updates automatically.",
                style = MaterialTheme.typography.bodySmall)
            Button({ ShizukuGate.openManager(ctx) }) { Text("Open Shizuku") }
        }

        Section("3 · Or pair from VictorSuite (adb)") {
            Text("Needs VictorSuite with android-tools (pkg install android-tools). Tap \"Pair device with pairing code\" " +
                "and copy the IP address & port + 6-digit code. The connect port is the different one on the main " +
                "Wireless debugging screen.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(pairAddr, { pairAddr = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Pairing host:port") }, isError = err?.field == "pair" && pairAddr.isNotBlank(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Pairing code (6 digits, not saved)") }, visualTransformation = PasswordVisualTransformation(),
                isError = err?.field == "code" && code.isNotBlank(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            OutlinedTextField(connectAddr, { connectAddr = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Connect host:port") }, isError = err?.field == "connect" && connectAddr.isNotBlank(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            err?.let { Text("⚠ ${it.message}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    val p = WirelessAdb.parseHostPort(pairAddr)!!; val c = WirelessAdb.parseHostPort(connectAddr)!!
                    run(WirelessAdb.sequence(p, code, c), c.host)
                }, enabled = check == WirelessAdb.Check.Ok) { Text("Pair, connect & start") }
                val c = WirelessAdb.parseHostPort(connectAddr)
                OutlinedButton({ run(WirelessAdb.reconnectSequence(c!!), c.host) }, enabled = c != null) { Text("Reconnect only") }
            }
            Text("If VictorSuite refuses RUN_COMMAND the command (including the one-time code) is copied to the clipboard; " +
                "clear it after pasting.", style = MaterialTheme.typography.bodySmall)
            launch.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        }
    }
    InstallVictorSuiteDialog(launch)
}
