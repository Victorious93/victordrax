package com.victorious.andrax

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Exported entry point so VictorSuite's drawer can list and open ANDRAX as an apk plugin.
 * It only forwards to MainActivity (the legal gate still applies); it never runs commands.
 * Optional extra `action_id`: one of dashboard, tools, terminal, engagements, install, desktop.
 */
class PluginEntryActivity : Activity() {
    private val allowed = setOf("dashboard", "tools", "terminal", "engagements", "install", "desktop")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = intent?.getStringExtra("action_id")?.takeIf { it in allowed } ?: "dashboard"
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
