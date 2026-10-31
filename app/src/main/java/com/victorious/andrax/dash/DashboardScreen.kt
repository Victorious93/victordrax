package com.victorious.andrax.dash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.victorious.andrax.data.Settings
import com.victorious.andrax.ui.GradientTitle
import com.victorious.andrax.ui.NeonCard
import com.victorious.andrax.ui.QuickTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun StatCard(title: String, value: String, fraction: Float?, sub: String = "") {
    val anim by animateFloatAsState(fraction ?: 0f, tween(600), label = "bar")
    NeonCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (fraction != null) LinearProgressIndicator(progress = { anim }, Modifier.fillMaxWidth().padding(top = 6.dp),
            color = MaterialTheme.colorScheme.secondary, strokeCap = StrokeCap.Round)
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DashboardScreen(settings: Settings, navigate: (String) -> Unit) {
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf<Stats?>(null) }
    LaunchedEffect(Unit) { while (true) { stats = withContext(Dispatchers.IO) { StatsReader.read(ctx) }; delay(2000) } }
    val s = stats
    LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.padding(top = 12.dp)) {
            GradientTitle("Dashboard"); Text("Live · refreshes every 2 s", style = MaterialTheme.typography.bodySmall) } }
        if (s == null) { item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }; return@LazyVerticalGrid }
        item { StatCard("CPU", s.cpuLoadPct?.let { "$it% (est.)" } ?: "${s.cpuCores} cores", s.cpuLoadPct?.div(100f),
            "${s.cpuCores} cores" + (s.cpuMhz?.let { " · $it MHz avg" } ?: "")) }
        item { StatCard("RAM", "${StatsReader.gb(s.ramUsed)} / ${StatsReader.gb(s.ramTotal)}", s.ramUsed.toFloat() / s.ramTotal) }
        item { StatCard("Storage free", StatsReader.gb(s.storageFree), 1f - s.storageFree.toFloat() / s.storageTotal, "of ${StatsReader.gb(s.storageTotal)}") }
        item { StatCard("Battery", "${s.batteryPct}%", s.batteryPct / 100f, if (s.charging) "charging" else "on battery") }
        item { StatCard("Temperature", "${s.batteryTempC} °C", null, "battery sensor") }
        item { StatCard("Network", s.network, null) }
        item { StatCard("Root", if (s.root) "available" else "not detected", null, s.arch) }
        item { StatCard("ANDRAX-NG env", if (settings.envInstalled) "installed" else "not installed", null, settings.installPath) }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Quick actions", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
        item { QuickTile("Terminal", Icons.Default.Terminal) { navigate("terminal") } }
        item { QuickTile("Tool library", Icons.Default.Build) { navigate("tools") } }
        item { QuickTile("Engagements", Icons.Default.Assignment) { navigate("engagements") } }
        item { QuickTile("Install env", Icons.Default.Download) { navigate("install") } }
        item { QuickTile("Desktop (VNC)", Icons.Default.DesktopWindows) { navigate("desktop") } }
        item { QuickTile("Settings", Icons.Default.Settings) { navigate("settings") } }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }
    }
}
