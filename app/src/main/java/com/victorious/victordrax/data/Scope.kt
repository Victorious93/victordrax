package com.victorious.victordrax.data

import java.net.URI

/**
 * Engagement scope enforcement. Shared by every tool-form command builder so a tool can
 * only ever be pointed at a target that falls inside the active engagement's authorized
 * scope. Pure (no Android deps) so it is fully unit-tested.
 *
 * A scope line can be:
 *  - an exact host or IPv4 address        (example.com, 10.0.0.5)
 *  - a domain (matches the host and any sub-domain)  (example.com matches api.example.com)
 *  - a wildcard domain                    (*.example.com)
 *  - an IPv4 CIDR range                   (10.0.0.0/24)
 *  - a URL (only its host is considered)  (https://example.com/app)
 */
object Scope {
    /** Extract the bare host from a target that may be a URL, host:port, or plain host/IP. */
    fun hostOf(target: String): String {
        var t = target.trim()
        if (t.isEmpty()) return ""
        if ("://" in t) {
            runCatching { URI(t).host }.getOrNull()?.let { if (it.isNotBlank()) return it.lowercase() }
            t = t.substringAfter("://")
        }
        // strip any path / query / fragment
        t = t.substringBefore('/').substringBefore('?').substringBefore('#')
        // strip userinfo
        t = t.substringAfterLast('@')
        // strip :port (but keep bare IPv6 out of scope — unsupported here)
        if (t.count { it == ':' } == 1) t = t.substringBefore(':')
        return t.lowercase()
    }

    private fun isIpv4(s: String): Boolean {
        val p = s.split('.')
        return p.size == 4 && p.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
    }

    private fun ipv4ToLong(s: String): Long =
        s.split('.').fold(0L) { acc, o -> (acc shl 8) or o.toLong() }

    private fun cidrContains(cidr: String, ip: String): Boolean {
        val parts = cidr.split('/')
        if (parts.size != 2) return false
        val base = parts[0]; val bits = parts[1].toIntOrNull() ?: return false
        if (!isIpv4(base) || !isIpv4(ip) || bits !in 0..32) return false
        if (bits == 0) return true
        val mask = (-1L shl (32 - bits)) and 0xFFFFFFFFL
        return (ipv4ToLong(base) and mask) == (ipv4ToLong(ip) and mask)
    }

    /** Does [entry] (a single scope line) authorize [host]? */
    private fun entryMatches(entry: String, host: String): Boolean {
        val e = entry.trim().lowercase()
        if (e.isEmpty()) return false
        return when {
            '/' in e -> cidrContains(e, host)                          // CIDR
            e.startsWith("*.") -> {                                    // wildcard sub-domains only
                val suffix = e.substring(1)                            // ".example.com"
                host.endsWith(suffix) && host != suffix.removePrefix(".")
            }
            "://" in e || e.contains('/') -> hostOf(e).let { it.isNotEmpty() && matchDomain(it, host) }
            else -> matchDomain(e, host)
        }
    }

    /** exact host, or host is a sub-domain of entry; never partial-label matches. */
    private fun matchDomain(entry: String, host: String): Boolean =
        host == entry || host.endsWith(".$entry")

    /** True when [target] resolves to a host authorized by one of the [scope] lines. */
    fun inScope(target: String, scope: List<String>): Boolean {
        val host = hostOf(target)
        if (host.isBlank() || scope.isEmpty()) return false
        return scope.any { entryMatches(it, host) }
    }
}
