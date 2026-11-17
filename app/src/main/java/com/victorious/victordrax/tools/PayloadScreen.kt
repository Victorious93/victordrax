package com.victorious.victordrax.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.capture.RunOutputStore
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.db.AppDb
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.findings.FindingsRepo
import com.victorious.victordrax.terminal.*
import com.victorious.victordrax.tools.spec.PayloadBuilder
import com.victorious.victordrax.ui.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(value.ifBlank { "(none)" }, {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt.ifBlank { "(none)" }) }, onClick = { onPick(opt); open = false })
            }
        }
    }
}

@Composable
fun PayloadBuilderScreen(settings: Settings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val engagement by produceState<Engagement?>(initialValue = null, settings.activeEngagementId) {
        value = settings.activeEngagementId.takeIf { it > 0 }?.let { AppDb.get(ctx).engagements().get(it) }
    }
    var payload by remember { mutableStateOf(PayloadBuilder.PAYLOADS.first()) }
    var lhost by remember { mutableStateOf("") }
    var lport by remember { mutableStateOf("4444") }
    var format by remember { mutableStateOf("raw") }
    var encoder by remember { mutableStateOf("") }
    var iterations by remember { mutableStateOf("") }
    var outDir by remember { mutableStateOf(PayloadBuilder.DEFAULT_OUT_DIR) }
    var fileName by remember { mutableStateOf("payload.bin") }
    val term = rememberTerminalLaunchState()
    var error by remember { mutableStateOf<String?>(null) }

    val opts = PayloadBuilder.Options(payload, lhost, lport, format, encoder, iterations, outDir, fileName)
    val built = PayloadBuilder.buildPayload(opts)
    val handler = PayloadBuilder.buildHandler(opts)

    fun gateFail(): String? = when {
        engagement == null -> "Select an active engagement first (Engage tab)."
        engagement?.isAuthorized != true -> "The active engagement has no authorization on record (who, date, document)."
        else -> null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("Payload & listener builder")
        AuthorizedBanner()
        val (eText, eColor) = when {
            engagement == null -> "No active engagement selected." to MaterialTheme.colorScheme.error
            engagement?.isAuthorized != true -> "Active engagement \"${engagement?.name}\" has no authorization on record." to MaterialTheme.colorScheme.secondary
            else -> "Active engagement: ${engagement?.name}" to MaterialTheme.colorScheme.primary
        }
        Text(eText, color = eColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        Section("Scope & use") {
            Text("Generates a payload with msfvenom and a matching Metasploit handler, then hands both to " +
                "VictorSuite for your own authorized test. It does not deploy, spread or persist anything — that " +
                "is the operator's responsibility, on systems they own or have written permission to test.",
                style = MaterialTheme.typography.bodySmall)
        }
        Section("msfvenom documentation") { LinkButton("docs.metasploit.com/msfvenom", "https://docs.metasploit.com/docs/using-metasploit/basics/how-to-use-msfvenom.html") }

        Section("Payload") {
            Dropdown("Payload type", payload, PayloadBuilder.PAYLOADS) { payload = it }
            OutlinedTextField(payload, { payload = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Payload (editable)") })
            if (PayloadBuilder.needsLhost(payload))
                OutlinedTextField(lhost, { lhost = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("LHOST * (your listener IP/host)") })
            else Text("Bind payload — no LHOST needed.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(lport, { lport = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("LPORT *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Dropdown("Output format", format, PayloadBuilder.FORMATS) { format = it }
            Dropdown("Encoder (optional, bad-char avoidance)", encoder, PayloadBuilder.ENCODERS) { encoder = it }
            if (encoder.isNotBlank()) OutlinedTextField(iterations, { iterations = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Encoder iterations (0-50)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(outDir, { outDir = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Output folder (in VictorSuite)") })
            OutlinedTextField(fileName, { fileName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Output filename") })
        }

        Section("Payload command preview") {
            when (built) {
                is PayloadBuilder.Result.Ok -> Text(built.preview, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                is PayloadBuilder.Result.Err -> Text(built.message, color = MaterialTheme.colorScheme.error)
            }
        }
        Button(onClick = {
            error = gateFail()
            if (error == null) when (built) {
                is PayloadBuilder.Result.Err -> error = built.message
                is PayloadBuilder.Result.Ok -> {
                    val e = engagement!!
                    val capture = RunOutputStore.fileFor(settings.runOutputDir, System.currentTimeMillis())
                    val res = VictorSuiteLauncher.launch(ctx, requestFor(ctx, settings, null, "msfvenom payload", built.argv, captureFile = capture))
                    term.handle(res)
                    if (res is LaunchResult.Sent || res is LaunchResult.FellBack)
                        scope.launch { FindingsRepo(AppDb.get(ctx)).recordRun(e.id, "msfvenom payload", built.preview, "tool", outputPath = capture) }
                }
            }
        }, enabled = built is PayloadBuilder.Result.Ok) {
            Icon(Icons.Default.Terminal, null); Spacer(Modifier.width(6.dp)); Text("Build payload in VictorSuite")
        }

        Section("Listener (exploit/multi/handler) preview") {
            when (handler) {
                is PayloadBuilder.Result.Ok -> Text(handler.preview, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                is PayloadBuilder.Result.Err -> Text(handler.message, color = MaterialTheme.colorScheme.error)
            }
        }
        Button(onClick = {
            error = gateFail()
            if (error == null) when (handler) {
                is PayloadBuilder.Result.Err -> error = handler.message
                is PayloadBuilder.Result.Ok -> {
                    val e = engagement!!
                    val res = VictorSuiteLauncher.launch(ctx, requestFor(ctx, settings, null, "Metasploit handler", handler.argv))
                    term.handle(res)
                    if (res is LaunchResult.Sent || res is LaunchResult.FellBack)
                        scope.launch { FindingsRepo(AppDb.get(ctx)).recordRun(e.id, "Metasploit handler", handler.preview, "tool") }
                }
            }
        }, enabled = handler is PayloadBuilder.Result.Ok) {
            Icon(Icons.Default.Hearing, null); Spacer(Modifier.width(6.dp)); Text("Start listener in VictorSuite")
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
        term.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp)) }
        Text("The handler session stays interactive. The generated payload is written inside VictorSuite at the " +
            "output path above; move or share it from there.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
    InstallVictorSuiteDialog(term)
}
