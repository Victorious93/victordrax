package com.victorious.andrax

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.victorious.andrax.dash.DashboardScreen
import com.victorious.andrax.data.AppTheme
import com.victorious.andrax.data.Settings
import com.victorious.andrax.data.SettingsRepo
import com.victorious.andrax.data.Validation
import com.victorious.andrax.desktop.DesktopScreen
import com.victorious.andrax.engage.EngagementDetailScreen
import com.victorious.andrax.engage.EngagementListScreen
import com.victorious.andrax.install.InstallScreen
import com.victorious.andrax.terminal.TerminalScreen
import com.victorious.andrax.tools.ToolDetailScreen
import com.victorious.andrax.tools.ToolLibraryScreen
import com.victorious.andrax.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_ROUTE = "route" }
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val repo = SettingsRepo(applicationContext)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle(initialValue = Settings())
            FleetTheme(settings.theme) {
                var accepted by remember { mutableStateOf(prefs.legalAccepted) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AnimatedContent(accepted, label = "gate", transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) }) { ok ->
                        if (!ok) FirstRunScreen { prefs.legalAccepted = true; accepted = true }
                        else AppShell(settings, repo, prefs, pendingRoute)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }
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
            Text("I will only test systems I own or have written permission to test.", Modifier.padding(top = 12.dp))
        }
        Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) { Text("Accept and continue") }
    }
}

private data class Dest(val route: String, val label: String, val icon: ImageVector)
private val primaryDests = listOf(
    Dest("dashboard", "Home", Icons.Default.Dashboard), Dest("tools", "Tools", Icons.Default.Build),
    Dest("terminal", "Terminal", Icons.Default.Terminal), Dest("engagements", "Engage", Icons.Default.Assignment),
    Dest("more", "More", Icons.Default.Menu),
)

@Composable
fun AppShell(settings: Settings, repo: SettingsRepo, prefs: Prefs, pendingRoute: MutableState<String?>) {
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    fun go(route: String) = nav.navigate(route) { launchSingleTop = true; popUpTo("dashboard") { saveState = true }; restoreState = true }
    LaunchedEffect(pendingRoute.value) { pendingRoute.value?.let { go(it); pendingRoute.value = null } }
    val selected = primaryDests.firstOrNull { current?.startsWith(it.route) == true }?.route
        ?: if (current in listOf("install", "desktop", "settings", "about")) "more" else if (current?.startsWith("tool/") == true) "tools"
        else if (current?.startsWith("engagement/") == true) "engagements" else null

    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        if (wide) Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(12.dp))
                primaryDests.forEach { d -> NavigationRailItem(selected == d.route, { go(d.route) }, { Icon(d.icon, null) }, label = { Text(d.label) }) }
            }
            Box(Modifier.weight(1f)) { Host(nav, settings, repo, prefs, ::go) }
        } else Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                primaryDests.forEach { d -> NavigationBarItem(selected == d.route, { go(d.route) }, { Icon(d.icon, null) }, label = { Text(d.label) }) }
            }
        }) { pad -> Box(Modifier.padding(pad)) { Host(nav, settings, repo, prefs, ::go) } }
    }
}

@Composable
private fun Host(nav: NavHostController, settings: Settings, repo: SettingsRepo, prefs: Prefs, go: (String) -> Unit) {
    NavHost(nav, "dashboard",
        enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(300)) { it / 8 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(300)) { -it / 8 } },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 8 } }) {
        composable("dashboard") { DashboardScreen(settings, go) }
        composable("tools") { ToolLibraryScreen(settings, repo) { nav.navigate("tool/$it") } }
        composable("tool/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
            ToolDetailScreen(it.arguments?.getString("id") ?: "", settings, repo) {}
        }
        composable("terminal") { TerminalScreen(settings) }
        composable("engagements") { EngagementListScreen(settings) { id -> nav.navigate("engagement/$id") } }
        composable("engagement/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
            EngagementDetailScreen(it.arguments?.getLong("id") ?: 0, settings, repo) { nav.popBackStack() }
        }
        composable("more") { MoreScreen(go) }
        composable("install") { InstallScreen(settings, repo) }
        composable("desktop") { DesktopScreen(settings) }
        composable("settings") { SettingsScreen(settings, repo, prefs) }
        composable("about") { AboutScreen() }
    }
}

@Composable
fun MoreScreen(go: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientTitle("More")
        listOf(Triple("Install environment", Icons.Default.Download, "install"), Triple("Desktop (VNC)", Icons.Default.DesktopWindows, "desktop"),
            Triple("Settings", Icons.Default.Settings, "settings"), Triple("About & legal", Icons.Default.Info, "about"))
            .forEach { (l, i, r) -> QuickTile(l, i, Modifier.fillMaxWidth()) { go(r) } }
    }
}

@Composable
fun SettingsScreen(settings: Settings, repo: SettingsRepo, prefs: Prefs) {
    val scope = rememberCoroutineScope()
    var path by remember(settings.installPath) { mutableStateOf(settings.installPath) }
    var display by remember(settings.vncDisplay) { mutableStateOf(settings.vncDisplay) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("Settings")
        Section("Theme") {
            AppTheme.entries.forEach { t ->
                Row { RadioButton(settings.theme == t, { scope.launch { repo.setTheme(t) } }); Text(t.label, Modifier.padding(top = 12.dp)) }
            }
        }
        Section("Install path") {
            OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), singleLine = true, isError = !Validation.isSafePath(path))
            Button({ scope.launch { repo.setInstallPath(path) } }, enabled = Validation.isSafePath(path) && !settings.envInstalled) { Text("Save path") }
            if (settings.envInstalled) Text("Uninstall before changing the path.", style = MaterialTheme.typography.bodySmall)
        }
        Section("VNC display") {
            OutlinedTextField(display, { display = it }, Modifier.fillMaxWidth(), singleLine = true, isError = !Validation.isVncDisplay(display))
            Button({ scope.launch { repo.setVncDisplay(display) } }, enabled = Validation.isVncDisplay(display)) { Text("Save display") }
        }
        Section("Legal") {
            Text("Re-show the legal notice on next launch.")
            TextButton({ prefs.legalAccepted = false }) { Text("Reset acceptance") }
        }
    }
}

@Composable
fun AboutScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle("About")
        Section("Legal notice") { Text(LEGAL_NOTICE) }
        Section("Credits") {
            Text("• ANDRAX — original by Weidsom Nascimento / The Cracker Technology\n" +
                "• ANDRAX-NG — SNAKE Security\n" +
                "• afos-ng — SNAKE Security, Apache License 2.0\n" +
                "• Terminal: VictorSuite (separate app, launched via intents)")
            LinkButton("github.com/snakesec", "https://github.com/snakesec")
            LinkButton("VictorSuite", "https://github.com/Victorious93/VictorSuite")
        }
        Section("License") {
            Text("Victorious ANDRAX is licensed under the Apache License 2.0. " +
                "It is an independent launcher and is not affiliated with or endorsed by SNAKE Security.")
        }
        Section("Version") { Text(BuildConfig.VERSION_NAME) }
    }
}
