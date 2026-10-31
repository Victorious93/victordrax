package com.victorious.victordrax.data

/**
 * Target / scope validation. Used to make sure a workflow or scan only ever runs against a target
 * that is inside an authorized engagement's scope. Pure (no Android deps) so it is unit tested.
 */
object Scope {
    private val HOSTNAME = Regex("^(?=.{1,253}$)([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$")

    fun isIpv4(s: String): Boolean {
        val p = s.split('.')
        if (p.size != 4) return false
        return p.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) && it.toInt() in 0..255 && (it == "0" || !it.startsWith("0")) }
    }

    /** Accepts "a.b.c.d/n" with 0<=n<=32. */
    fun isCidr(s: String): Boolean {
        val i = s.indexOf('/'); if (i < 0) return false
        val ip = s.substring(0, i); val bits = s.substring(i + 1)
        return isIpv4(ip) && bits.isNotEmpty() && bits.all(Char::isDigit) && bits.toInt() in 0..32
    }

    fun isHostname(s: String): Boolean = !isIpv4(s) && HOSTNAME.matches(s) && s.any { it == '.' || it.isLetter() }

    /** A scope entry is valid if it is an IPv4, a CIDR block or a hostname. */
    fun isValidScopeEntry(s: String): Boolean = s.isNotBlank() && (isIpv4(s) || isCidr(s) || isHostname(s))

    fun isValidTarget(s: String): Boolean = isValidScopeEntry(s)

    private fun ipToLong(ip: String): Long =
        ip.split('.').fold(0L) { acc, o -> (acc shl 8) or o.toLong() }

    private fun inCidr(ip: String, cidr: String): Boolean {
        if (!isIpv4(ip)) return false
        val slash = cidr.indexOf('/')
        val base = cidr.substring(0, slash); val bits = cidr.substring(slash + 1).toInt()
        val mask = if (bits == 0) 0L else (-1L shl (32 - bits)) and 0xFFFFFFFFL
        return (ipToLong(ip) and mask) == (ipToLong(base) and mask)
    }

    /**
     * True if [target] is covered by at least one entry in [scope].
     * IPv4 targets match an identical IP or any containing CIDR. Hostnames match
     * case-insensitively (exact, or a subdomain of a scope hostname).
     */
    fun targetInScope(target: String, scope: List<String>): Boolean {
        val t = target.trim()
        if (!isValidTarget(t)) return false
        for (raw in scope) {
            val e = raw.trim()
            when {
                isCidr(e) && isIpv4(t) -> if (inCidr(t, e)) return true
                isCidr(e) && isCidr(t) -> {
                    // a target range is in scope only if it is entirely inside the scope range
                    val tb = t.substringAfter('/').toInt(); val eb = e.substringAfter('/').toInt()
                    if (tb >= eb && inCidr(t.substringBefore('/'), e)) return true
                }
                isIpv4(e) && isIpv4(t) -> if (e == t) return true
                isHostname(e) && isHostname(t) -> {
                    val le = e.lowercase(); val lt = t.lowercase()
                    if (lt == le || lt.endsWith(".$le")) return true
                }
            }
        }
        return false
    }
}
