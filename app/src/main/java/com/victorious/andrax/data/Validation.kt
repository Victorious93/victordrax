package com.victorious.andrax.data

import java.net.URI

/** Input validation shared by installer, terminal and VNC code (values end up in su commands). */
object Validation {
    private val SAFE_PATH = Regex("^/[A-Za-z0-9._/-]{1,200}$")
    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")
    private val DISPLAY = Regex("^:[0-9]{1,2}$")

    fun isSafePath(p: String) = SAFE_PATH.matches(p) && ".." !in p.split('/')
    fun isSha256(s: String) = HEX64.matches(s.trim())
    fun isVncDisplay(s: String) = DISPLAY.matches(s)

    /** Only https URLs; no hardcoded mirror allow-list, the user supplies the official upstream URL. */
    fun isHttpsUrl(u: String): Boolean = runCatching {
        val uri = URI(u.trim()); uri.scheme == "https" && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    /** tar flags for a supported rootfs archive name, or null if unsupported. */
    fun tarFlagsFor(name: String): String? {
        val n = name.lowercase().substringBefore('?')
        return when {
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> "-xJpf"
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> "-xzpf"
            n.endsWith(".tar") -> "-xpf"
            else -> null
        }
    }
}
