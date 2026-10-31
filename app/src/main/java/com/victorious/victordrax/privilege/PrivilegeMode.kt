package com.victorious.victordrax.privilege

/** How the app should obtain elevated privileges for in-app shell work. */
enum class PrivilegeMode(val label: String, val description: String) {
    AUTO(
        "Auto (prefer root, then Shizuku)",
        "Use su when present; otherwise Shizuku if granted; otherwise unprivileged.",
    ),
    ROOT(
        "Root (su) only",
        "Only use the classic su binary. Fail if root is unavailable.",
    ),
    SHIZUKU(
        "Shizuku only",
        "Only use Shizuku (ADB shell or Sui). Fail if Shizuku is not granted.",
    ),
    NONE(
        "Unprivileged",
        "Never elevate. In-app privileged commands will fail.",
    ),
}

/** Backend chosen after resolving [PrivilegeMode] against live capability. */
enum class PrivilegeBackend { ROOT, SHIZUKU, NONE }

object PrivilegeResolver {
    /**
     * Pure decision: which backend to use for a privileged [exec].
     * [shizukuGranted] means Shizuku binder is alive AND permission is granted.
     */
    fun resolve(mode: PrivilegeMode, suPresent: Boolean, shizukuGranted: Boolean): PrivilegeBackend =
        when (mode) {
            PrivilegeMode.NONE -> PrivilegeBackend.NONE
            PrivilegeMode.ROOT -> if (suPresent) PrivilegeBackend.ROOT else PrivilegeBackend.NONE
            PrivilegeMode.SHIZUKU -> if (shizukuGranted) PrivilegeBackend.SHIZUKU else PrivilegeBackend.NONE
            PrivilegeMode.AUTO -> when {
                suPresent -> PrivilegeBackend.ROOT
                shizukuGranted -> PrivilegeBackend.SHIZUKU
                else -> PrivilegeBackend.NONE
            }
        }

    /**
     * Chroot / mount / VNC inside ANDRAX-NG need uid 0.
     * Shizuku ADB mode is uid 2000 (shell); only Sui (or root-started Shizuku) is uid 0.
     */
    fun canChroot(backend: PrivilegeBackend, shizukuUid: Int?): Boolean = when (backend) {
        PrivilegeBackend.ROOT -> true
        PrivilegeBackend.SHIZUKU -> shizukuUid != null && shizukuUid == 0
        PrivilegeBackend.NONE -> false
    }
}
