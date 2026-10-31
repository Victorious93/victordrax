package com.victorious.victordrax

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.victorious.victordrax.data.Settings
import com.victorious.victordrax.data.SettingsRepo
import com.victorious.victordrax.db.*
import com.victorious.victordrax.scan.ScanParser
import com.victorious.victordrax.scan.ScanTable
import com.victorious.victordrax.ui.FleetTheme
import com.victorious.victordrax.workflow.seedBuiltIns
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import java.io.File

/**
 * Renders every main screen headlessly (Robolectric native graphics) and writes PNGs.
 * Doubles as a smoke test: any crash on first composition fails the build.
 * Output: -Dvictordrax.shots (defaults to docs/screenshots via build.gradle.kts).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun settle() {
        repeat(12) {
            Thread.sleep(60)
            shadowOf(Looper.getMainLooper()).idle()
            rule.mainClock.advanceTimeBy(250)
        }
    }

    private fun shot(dir: File, name: String) {
        settle()
        // Software-draw the window (captureToImage's redraw wait never fires under Robolectric).
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun allScreens() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(System.getProperty("victordrax.shots") ?: "build/screenshots").apply { mkdirs() }
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        val db = AppDb.get(ctx)
        val engId = runBlocking {
            seedBuiltIns(db)
            val id = db.engagements().insert(Engagement(name = "ACME internal pentest", client = "ACME Corp",
                scope = "10.0.0.0/24\nlab.acme.test", authorizedBy = "Jane Doe (CISO)", authorizedDate = 1_761_800_000_000, authDocUri = "content://demo/sow.pdf"))
            db.engagements().addNote(Note(engagementId = id, text = "Kick-off call done; testing window 09:00-17:00."))
            db.findings().insert(Finding(engagementId = id, title = "Outdated OpenSSH on 10.0.0.5", severity = "HIGH",
                cvssVector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:L/A:N", evidence = "Banner shows OpenSSH 7.2.", remediation = "Upgrade OpenSSH."))
            db.findings().insert(Finding(engagementId = id, title = "Directory listing enabled", severity = "LOW", remediation = "Disable autoindex."))
            val xml = javaClass.getResource("/fixtures/nmap.xml")!!.readText()
            val t = ScanParser.parse("nmap.xml", xml)
            db.scans().insert(ScanImport(engagementId = id, name = "nmap.xml", format = t.format, rowsJson = ScanTable.toJson(t)))
            id
        }
        val scanId = runBlocking { db.scans().forEngagementOnce(engId).first().id }
        val pbId = runBlocking { db.playbooks().allOnce().first().id }
        val settings = Settings(activeEngagementId = engId)
        val repo = SettingsRepo(ctx); val prefs = Prefs(ctx)
        val route = mutableStateOf<String?>(null)
        val firstRun = mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FleetTheme {
                Surface {
                    if (firstRun.value) FirstRunScreen {} else AppShell(settings, repo, prefs, route)
                }
            }
        }
        shot(dir, "00_first_run")
        firstRun.value = false
        val routes = listOf(
            "01_dashboard" to "dashboard", "02_tools" to "tools", "03_tool_detail" to "tool/nmap",
            "04_terminal" to "terminal", "05_engagements" to "engagements", "06_engagement_detail" to "engagement/$engId",
            "07_report_builder" to "engagement/$engId/report", "08_scans" to "engagement/$engId/scans",
            "09_scan_table" to "scan/$scanId", "10_workflows" to "workflows", "11_playbook" to "playbook/$pbId",
            "12_packages_afos" to "packages", "13_backup" to "backup", "14_more" to "more", "15_install" to "install",
            "16_desktop" to "desktop", "17_settings" to "settings", "18_about" to "about",
        )
        for ((name, r) in routes) { route.value = r; shot(dir, name) }
    }
}
