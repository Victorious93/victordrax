package com.victorious.victordrax.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.victorious.victordrax.MainActivity
import com.victorious.victordrax.dash.StatsReader

private val Cyan = ColorProvider(Color(0xFF00F0FF))
private val Magenta = ColorProvider(Color(0xFFFF00E5))
private val Fg = ColorProvider(Color(0xFFE6E6F0))
private val Bg = ColorProvider(Color(0xFF0A0A12))

fun launchIntent(ctx: Context, route: String) =
    Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

class StatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = StatsReader.read(context)
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(16.dp).padding(12.dp)
                .clickable(actionStartActivity(launchIntent(context, "dashboard")))) {
                Text("VICTORDRAX", style = TextStyle(color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                Text("RAM ${StatsReader.gb(s.ramUsed)} / ${StatsReader.gb(s.ramTotal)}", style = TextStyle(color = Fg, fontSize = 12.sp))
                Text("Free ${StatsReader.gb(s.storageFree)}", style = TextStyle(color = Fg, fontSize = 12.sp))
                Text("Battery ${s.batteryPct}% · ${s.batteryTempC}°C", style = TextStyle(color = Fg, fontSize = 12.sp))
                Text(if (s.root) "root: available" else "root: not detected", style = TextStyle(color = Magenta, fontSize = 12.sp))
            }
        }
    }
}

class QuickLaunchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Row(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(16.dp).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                listOf("Terminal" to "terminal", "Tools" to "tools", "Desktop" to "desktop").forEachIndexed { i, (label, route) ->
                    Text(label, style = TextStyle(color = if (i % 2 == 0) Cyan else Magenta, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.padding(8.dp).clickable(actionStartActivity(launchIntent(context, route))))
                }
            }
        }
    }
}

class StatsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StatsWidget() }
class QuickLaunchWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickLaunchWidget() }
