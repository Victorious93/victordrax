package com.victorious.andrax.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.victorious.andrax.data.Root
import com.victorious.andrax.data.Validation

/** Something that can open a terminal session running a command. */
interface TerminalLauncher {
    fun isAvailable(ctx: Context): Boolean
    fun launch(ctx: Context, request: TerminalRequest): LaunchResult
}

data class TerminalRequest(
    /** Command to run inside the session (null = interactive shell). */
    val command: String?,
    val title: String,
    /** Enter the ANDRAX-NG chroot via su (when installed). */
    val chroot: Boolean,
    val chrootPath: String,
)

sealed interface LaunchResult {
    data object Sent : LaunchResult
    data object NotInstalled : LaunchResult
    /** RUN_COMMAND not allowed; VictorSuite was opened and the command copied to the clipboard. */
    data class FellBack(val reason: String, val copied: String) : LaunchResult
    data class Rejected(val reason: String) : LaunchResult
}

/**
 * VictorSuite (Victorious93/VictorSuite) is a Termux-based suite with applicationId `com.termux`.
 * It keeps the upstream Termux RUN_COMMAND contract (service com.termux.app.RunCommandService).
 * See docs/victorsuite-integration.md.
 */
object VictorSuiteLauncher : TerminalLauncher {
    const val PACKAGE = "com.termux"
    const val SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    const val EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    const val HOME = "/data/data/com.termux/files/home"
    const val RELEASES_URL = "https://github.com/Victorious93/VictorSuite/releases"

    /** Tool commands are restricted to a conservative charset because they are nested inside su/chroot quoting. */
    private val SAFE_CMD = Regex("^[A-Za-z0-9 ._/:=,+@%-]{1,200}$")
    fun isSafeCommand(c: String) = SAFE_CMD.matches(c)

    /** The bash -c script that VictorSuite runs. Pure, so it is unit tested. */
    fun script(r: TerminalRequest): String {
        r.command?.let { require(isSafeCommand(it)) { "unsupported characters in command" } }
        val inner = r.command?.let { "$it; exec /bin/bash -l" } ?: "exec /bin/bash -l"
        return if (r.chroot) {
            require(Validation.isSafePath(r.chrootPath))
            "su -c '${Root.chrootPrefix(r.chrootPath)} /bin/bash -lc \"$inner\"'"
        } else r.command?.let { "$it; exec bash -l" } ?: "exec bash -l"
    }

    fun intent(r: TerminalRequest): Intent = Intent(ACTION_RUN_COMMAND).setClassName(PACKAGE, SERVICE)
        .putExtra(EXTRA_PATH, BASH)
        .putExtra(EXTRA_ARGUMENTS, arrayOf("-c", script(r)))
        .putExtra(EXTRA_WORKDIR, HOME)
        .putExtra(EXTRA_BACKGROUND, false)
        .putExtra(EXTRA_SESSION_ACTION, "0") // new session, switch to it, open the activity
        .putExtra(EXTRA_LABEL, r.title)

    override fun isAvailable(ctx: Context) = runCatching { ctx.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    fun hasPermission(ctx: Context) = ContextCompat.checkSelfPermission(ctx, PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED

    override fun launch(ctx: Context, request: TerminalRequest): LaunchResult {
        if (!isAvailable(ctx)) return LaunchResult.NotInstalled
        val script = try { script(request) } catch (e: IllegalArgumentException) { return LaunchResult.Rejected(e.message ?: "invalid") }
        if (hasPermission(ctx)) {
            try {
                val i = intent(request)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                return LaunchResult.Sent
            } catch (e: Exception) { return fallback(ctx, script, "VictorSuite refused RUN_COMMAND (${e.javaClass.simpleName}). Enable allow-external-apps.") }
        }
        return fallback(ctx, script, "RUN_COMMAND permission not granted.")
    }

    private fun fallback(ctx: Context, script: String, reason: String): LaunchResult {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("command", script))
        ctx.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return LaunchResult.FellBack(reason, script)
    }
}
