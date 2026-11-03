package com.victorious.victordrax.desktop

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.victorious.victordrax.data.Root
import com.victorious.victordrax.data.ShellResult
import com.victorious.victordrax.data.Validation
import com.victorious.victordrax.privilege.Privilege
import com.victorious.victordrax.privilege.PrivilegeMode

/** Starts/stops a VNC server inside the chroot (expects TigerVNC/TightVNC's `vncserver` in the rootfs). */
object Vnc {
    fun port(display: String) = 5900 + display.removePrefix(":").toInt()

    fun startCommand(root: String, display: String, geometry: String = "1280x720"): String {
        require(Validation.isVncDisplay(display) && Regex("^[0-9]{3,4}x[0-9]{3,4}$").matches(geometry))
        return "${Root.chrootPrefix(root)} USER=root /bin/sh -c 'vncserver $display -geometry $geometry -localhost yes'"
    }

    fun stopCommand(root: String, display: String): String {
        require(Validation.isVncDisplay(display))
        return "${Root.chrootPrefix(root)} USER=root /bin/sh -c 'vncserver -kill $display'"
    }

    suspend fun start(root: String, display: String, mode: PrivilegeMode = PrivilegeMode.AUTO): ShellResult =
        Privilege.exec(mode, startCommand(root, display))

    suspend fun stop(root: String, display: String, mode: PrivilegeMode = PrivilegeMode.AUTO): ShellResult =
        Privilege.exec(mode, stopCommand(root, display))

    /** Opens any installed VNC viewer that handles vnc:// URIs. Returns false if none is installed. */
    fun openViewer(ctx: Context, display: String): Boolean = try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnc://127.0.0.1:${port(display)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false }
}
