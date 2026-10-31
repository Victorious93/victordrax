package com.victorious.victordrax.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.victorious.victordrax.data.Root
import com.victorious.victordrax.data.Validation
import com.victorious.victordrax.privilege.PrivilegeBackend

/** Something that can open a terminal session running a command. */
interface TerminalLauncher {
    fun isAvailable(ctx: Context): Boolean
    fun launch(ctx: Context, request: TerminalRequest): LaunchResult
}

data class TerminalRequest(
    /** Simple command string (null = interactive shell). Restricted to [VictorSuiteLauncher.isSafeCommand]. */
    val command: String?,
    val title: String,
    /** Enter the ANDRAX-NG chroot via su (when installed). */
    val chroot: Boolean,
    val chrootPath: String,
    /**
     * Pre-parsed argv built by a tool-form command builder. When present it takes precedence
     * over [command] and each element is POSIX single-quote escaped, so user-supplied values
     * (URLs with ?/&, wordlist paths, etc.) are handled safely without a character allow-list.
     */
    val argv: List<String>? = null,
    /**
     * Privilege backend resolved from the app's PrivilegeMode (same resolver as in-app tool runs).
     * Only used to wrap chroot sessions: ROOT -> `su -c`, SHIZUKU -> `rish -c`, NONE -> no wrapper.
     */
    val backend: PrivilegeBackend = PrivilegeBackend.ROOT,
    /** From PrivilegeResolver.canChroot: false for ADB Shizuku (uid 2000) or unprivileged. */
    val canChroot: Boolean = true,
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
    const val EXTRA_SHELL_NAME = "com.termux.RUN_COMMAND_SHELL_NAME"
    const val SHELL_NAME = "VictorDrax"
    const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    const val HOME = "/data/data/com.termux/files/home"
    const val RELEASES_URL = "https://github.com/Victorious93/VictorSuite/releases"

    /** Tool commands (string path) are restricted to a conservative charset because they are nested inside su/chroot quoting. */
    private val SAFE_CMD = Regex("^[A-Za-z0-9 ._/:=,+@%-]{1,200}$")
    fun isSafeCommand(c: String) = SAFE_CMD.matches(c)

    /**
     * POSIX single-quote escaping: the returned token, placed verbatim in an unquoted bash
     * context, expands to exactly [s]. Composable, so it can be nested to any depth.
     */
    fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Join an argv into one safely-quoted shell command line. */
    fun shJoin(argv: List<String>): String = argv.joinToString(" ") { shQuote(it) }

    /** Reject values that cannot be carried even when quoted (NUL / newline would split or truncate). */
    fun argvIsSafe(argv: List<String>): Boolean =
        argv.isNotEmpty() && argv.all { it.isNotEmpty() && it.none { c -> c == '\u0000' || c == '\n' || c == '\r' } }

    /** Shizuku's terminal bridge. Must be exported into VictorSuite's PATH from the Shizuku app. */
    const val RISH = "rish"
    const val RISH_MISSING = "rish (Shizuku shell) not found in VictorSuite PATH. In Shizuku: Use Shizuku in terminal apps -> Export files, then copy rish + rish_shizuku.dex into \$PREFIX/bin."
    const val ADB_SHIZUKU_NO_CHROOT = "Shizuku is running in ADB mode (uid 2000), which cannot chroot into ANDRAX-NG. Use root (su) or Sui, or switch Privilege mode."

    /** The bash -c script that VictorSuite runs. Pure, so it is unit tested. */
    fun script(r: TerminalRequest): String {
        if (r.argv != null) return scriptFromArgv(r)
        r.command?.let { require(isSafeCommand(it)) { "unsupported characters in command" } }
        val inner = r.command?.let { "$it; exec /bin/bash -l" } ?: "exec /bin/bash -l"
        return if (r.chroot) {
            requireChroot(r)
            elevate(r.backend, "${Root.chrootPrefix(r.chrootPath)} /bin/bash -lc \"$inner\"")
        } else r.command?.let { "$it; exec bash -l" } ?: "exec bash -l"
    }

    /** argv path: every token is single-quote escaped; nesting is built inside-out so it is quote-safe. */
    private fun scriptFromArgv(r: TerminalRequest): String {
        val argv = r.argv!!
        require(argvIsSafe(argv)) { "unsupported characters in argument" }
        val cmd = shJoin(argv)
        return if (r.chroot) {
            requireChroot(r)
            val inner = "$cmd; exec /bin/bash -l"
            elevate(r.backend, Root.chrootPrefix(r.chrootPath) + " /bin/bash -lc " + shQuote(inner))
        } else {
            "$cmd; exec bash -l"
        }
    }

    private fun requireChroot(r: TerminalRequest) {
        require(Validation.isSafePath(r.chrootPath))
        require(r.backend != PrivilegeBackend.NONE) { "Chroot session needs root or Shizuku (Sui); privilege is unavailable." }
        require(r.canChroot) { ADB_SHIZUKU_NO_CHROOT }
    }

    /**
     * Wrap a uid-0 command for the resolved backend. VictorSuite runs in its own process, so it
     * cannot reach VictorDrax's in-process ShizukuShell binder; Shizuku's supported terminal
     * bridge is `rish`, which execs through the same Shizuku server (Sui = uid 0).
     */
    fun elevate(backend: PrivilegeBackend, full: String): String = when (backend) {
        PrivilegeBackend.ROOT -> "su -c " + shQuote(full)
        PrivilegeBackend.SHIZUKU ->
            "command -v $RISH >/dev/null 2>&1 || { echo ${shQuote(RISH_MISSING)}; exec bash -l; }; $RISH -c " + shQuote(full)
        PrivilegeBackend.NONE -> full
    }

    fun intent(r: TerminalRequest): Intent = Intent(ACTION_RUN_COMMAND).setClassName(PACKAGE, SERVICE)
        .putExtra(EXTRA_PATH, BASH)
        .putExtra(EXTRA_ARGUMENTS, arrayOf("-c", script(r)))
        .putExtra(EXTRA_WORKDIR, HOME)
        .putExtra(EXTRA_BACKGROUND, false)
        .putExtra(EXTRA_SESSION_ACTION, "0") // new session, switch to it, open the activity
        .putExtra(EXTRA_SHELL_NAME, SHELL_NAME)
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
