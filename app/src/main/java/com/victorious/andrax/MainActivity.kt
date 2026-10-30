package com.victorious.andrax

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.victorious.andrax.ui.FleetTheme
import com.victorious.andrax.ui.NeonGradient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            FleetTheme {
                var accepted by remember { mutableStateOf(prefs.legalAccepted) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!accepted) FirstRunScreen { prefs.legalAccepted = true; accepted = true }
                    else AppShell(prefs)
                }
            }
        }
    }
}

@Composable
fun GradientTitle(text: String) {
    Text(text, style = TextStyle(brush = NeonGradient, fontSize = 26.sp, fontWeight = FontWeight.Bold))
}

@Composable
fun Section(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            body()
        }
    }
}

@Composable
fun LinkButton(label: String, url: String) {
    val ctx = LocalContext.current
    TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(label) }
}

@Composable
fun FirstRunScreen(onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(NeonGradient))
        Spacer(Modifier.height(16.dp))
        GradientTitle("Victorious ANDRAX")
        Spacer(Modifier.height(16.dp))
        Section("Legal notice") { Text(LEGAL_NOTICE) }
        Row(Modifier.padding(vertical = 8.dp)) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Text("I will only test systems I own or have written permission to test.",
                Modifier.padding(top = 12.dp))
        }
        Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) {
            Text("Accept and continue")
        }
    }
}

private data class Dest(val route: String, val label: String)
private val dests = listOf(
    Dest("status", "Status"), Dest("install", "Install"),
    Dest("settings", "Settings"), Dest("about", "About"),
)

@Composable
fun AppShell(prefs: Prefs) {
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(bottomBar = {
        NavigationBar {
            dests.forEach { d ->
                NavigationBarItem(
                    selected = current == d.route,
                    onClick = { nav.navigate(d.route) { launchSingleTop = true; popUpTo("status") } },
                    icon = { Text(d.label.take(1)) },
                    label = { Text(d.label) },
                )
            }
        }
    }) { pad ->
        NavHost(nav, "status", Modifier.padding(pad)) {
            composable("status") { Screen("Status") { StatusContent() } }
            composable("install") { Screen("Install environment") { InstallContent() } }
            composable("settings") { Screen("Settings") { SettingsContent(prefs) } }
            composable("about") { Screen("About") { AboutContent() } }
        }
    }
}

@Composable
fun Screen(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle(title)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun StatusContent() {
    var refresh by remember { mutableIntStateOf(0) }
    val root = remember(refresh) { DeviceStatus.rootAvailable() }
    val arch = remember { DeviceStatus.arch() }
    val free = remember(refresh) { DeviceStatus.human(DeviceStatus.freeStorageBytes()) }
    Section("Root") { Text(if (root) "su binary found (root likely available)" else "No su binary found (root not detected)") }
    Section("Device architecture") { Text(arch) }
    Section("Free storage (/data)") { Text(free) }
    Button(onClick = { refresh++ }) { Text("Refresh") }
}

@Composable
fun InstallContent() {
    Section("Requirements") {
        Text("• Rooted device (ANDRAX-NG runs as a chroot environment)\n" +
            "• arm64-v8a recommended\n" +
            "• Several GB of free storage\n" +
            "• Only for authorized testing")
    }
    Section("Official upstream sources") {
        Text("Get ANDRAX-NG only from its official project:")
        LinkButton("SNAKE Security on GitHub", "https://github.com/snakesec")
        LinkButton("afos-ng (Apache-2.0)", "https://github.com/snakesec/afos-ng")
        LinkButton("andrax-base-files (no license; link only)", "https://github.com/snakesec/andrax-base-files")
    }
    Section("Installer") {
        // TODO: implement verified download + chroot setup from official upstream releases.
        Text("TODO: automated download and setup is not implemented yet.")
        Button(onClick = {}, enabled = false) { Text("Install (coming soon)") }
    }
}

@Composable
fun SettingsContent(prefs: Prefs) {
    var path by remember { mutableStateOf(prefs.installPath) }
    Section("Install path") {
        OutlinedTextField(value = path, onValueChange = { path = it; prefs.installPath = it },
            singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    Section("Legal") {
        Text("Re-show the legal notice on next launch.")
        TextButton(onClick = { prefs.legalAccepted = false }) { Text("Reset acceptance") }
    }
}

@Composable
fun AboutContent() {
    Section("Legal notice") { Text(LEGAL_NOTICE) }
    Section("Credits") {
        Text("• ANDRAX — original by Weidsom Nascimento / The Cracker Technology\n" +
            "• ANDRAX-NG — SNAKE Security\n" +
            "• afos-ng — SNAKE Security, Apache License 2.0")
        LinkButton("github.com/snakesec", "https://github.com/snakesec")
    }
    Section("License") {
        Text("Victorious ANDRAX is licensed under the Apache License 2.0. " +
            "It is an independent launcher and is not affiliated with or endorsed by SNAKE Security.")
    }
    Section("Version") { Text(BuildConfigInfo.VERSION) }
}

object BuildConfigInfo { const val VERSION = "0.1.0" }
