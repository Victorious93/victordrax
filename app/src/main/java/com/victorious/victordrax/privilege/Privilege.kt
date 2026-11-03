package com.victorious.victordrax.privilege

import android.content.Context
import com.victorious.victordrax.data.Root
import com.victorious.victordrax.data.ShellResult
import com.victorious.victordrax.shizuku.ShizukuGate
import com.victorious.victordrax.shizuku.ShizukuShell
import com.victorious.victordrax.shizuku.ShizukuStatus

/**
 * App-wide privilege / capability model.
 *
 * Preference order for [PrivilegeMode.AUTO]: root (su) → Shizuku (if granted) → none.
 * Chroot / mount / VNC still require uid 0 (classic su, or Shizuku via Sui).
 */
object Privilege {
    data class Snapshot(
        val mode: PrivilegeMode,
        val backend: PrivilegeBackend,
        val suPresent: Boolean,
        val shizuku: ShizukuStatus,
        val shizukuUid: Int,
        val canChroot: Boolean,
    )

    fun snapshot(ctx: Context, mode: PrivilegeMode): Snapshot {
        ShizukuGate.ensureHooked()
        val su = Root.suBinaryPresent()
        val st = ShizukuGate.status(ctx)
        val granted = st == ShizukuStatus.GRANTED
        val backend = PrivilegeResolver.resolve(mode, su, granted)
        val uid = if (granted) ShizukuGate.serverUid() else -1
        return Snapshot(
            mode = mode,
            backend = backend,
            suPresent = su,
            shizuku = st,
            shizukuUid = uid,
            canChroot = PrivilegeResolver.canChroot(backend, uid.takeIf { it >= 0 }),
        )
    }

    /** Elevated shell for in-app work (installer, VNC, diagnostics). */
    suspend fun exec(mode: PrivilegeMode, command: String): ShellResult {
        val su = Root.suBinaryPresent()
        val granted = ShizukuGate.isGranted()
        return when (PrivilegeResolver.resolve(mode, su, granted)) {
            PrivilegeBackend.ROOT -> Root.exec(command)
            PrivilegeBackend.SHIZUKU -> ShizukuShell.exec(command)
            PrivilegeBackend.NONE -> ShellResult(
                -1,
                when {
                    mode == PrivilegeMode.NONE -> "Privilege mode is Unprivileged"
                    mode == PrivilegeMode.ROOT -> "Root (su) not found"
                    mode == PrivilegeMode.SHIZUKU -> "Shizuku not granted or not running"
                    else -> "No elevated backend available (need su or Shizuku)"
                },
            )
        }
    }

    /** True when the current mode can perform uid-0 chroot operations. */
    fun canChroot(ctx: Context, mode: PrivilegeMode): Boolean = snapshot(ctx, mode).canChroot

    /** Soft "has any elevated capability" used by dashboard / tool forms. */
    fun hasElevated(ctx: Context, mode: PrivilegeMode): Boolean {
        val s = snapshot(ctx, mode)
        return s.backend == PrivilegeBackend.ROOT || s.backend == PrivilegeBackend.SHIZUKU
    }
}
