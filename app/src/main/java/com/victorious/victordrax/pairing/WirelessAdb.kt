package com.victorious.victordrax.pairing

import com.victorious.victordrax.terminal.VictorSuiteLauncher

/**
 * Pure parsing / validation / command building for the Android 11+ Wireless debugging flow
 * (pair with a 6-digit code on one port, then connect on a separate port) used to start
 * Shizuku on the user's OWN device without a PC. No Android deps, so it is JVM unit tested.
 *
 * The pairing code is single-use and is never persisted; only the host is remembered.
 */
object WirelessAdb {
    data class HostPort(val host: String, val port: Int) {
        /** adb-style address; IPv6 hosts are bracketed. */
        override fun toString(): String = if (':' in host) "[$host]:$port" else "$host:$port"
    }

    private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")
    private val HOSTNAME = Regex("^(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$")
    private val IPV6 = Regex("^[0-9A-Fa-f:]{2,39}$")
    private val CODE = Regex("^\\d{6}$")

    fun isValidHost(h: String): Boolean = when {
        h.isEmpty() -> false
        IPV4.matches(h) -> true
        ':' in h -> IPV6.matches(h) && h.count { it == ':' } >= 2
        h.all { it.isDigit() || it == '.' } -> false // malformed dotted number
        else -> HOSTNAME.matches(h)
    }

    fun isValidPort(p: Int): Boolean = p in 1..65535

    /** Parse "host:port" or "[ipv6]:port" as shown on the Wireless debugging screen. Null if invalid. */
    fun parseHostPort(raw: String): HostPort? {
        val s = raw.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        val host: String
        val portStr: String
        if (s.startsWith("[")) {
            val end = s.indexOf("]:")
            if (end < 0) return null
            host = s.substring(1, end); portStr = s.substring(end + 2)
        } else {
            if (s.count { it == ':' } != 1) return null
            host = s.substringBefore(':'); portStr = s.substringAfter(':')
        }
        if (portStr.isEmpty() || portStr.length > 5 || !portStr.all(Char::isDigit)) return null
        val port = portStr.toInt()
        if (!isValidPort(port) || !isValidHost(host)) return null
        return HostPort(host.lowercase(), port)
    }

    /** Normalise a typed pairing code (spaces/dashes allowed while typing) to 6 digits, or null. */
    fun normalizeCode(raw: String): String? {
        val c = raw.filterNot { it == ' ' || it == '-' }
        return if (CODE.matches(c)) c else null
    }

    fun isValidPairingCode(raw: String): Boolean = normalizeCode(raw) != null

    sealed interface Check {
        data object Ok : Check
        data class Error(val field: String, val message: String) : Check
    }

    /** Validate the whole form. Pairing and connect ports differ on Android 11+. */
    fun validate(pairAddr: String, code: String, connectAddr: String): Check {
        val p = parseHostPort(pairAddr) ?: return Check.Error("pair", "Pairing address must be host:port, e.g. 192.168.1.20:37123")
        if (!isValidPairingCode(code)) return Check.Error("code", "Pairing code is the 6-digit code shown by \"Pair device with pairing code\"")
        val c = parseHostPort(connectAddr) ?: return Check.Error("connect", "Connect address must be host:port from the Wireless debugging screen")
        if (p == c) return Check.Error("connect", "Connect port differs from the pairing port — use the IP address & Port shown on the main Wireless debugging screen")
        return Check.Ok
    }

    /** Shizuku's ADB start: run the bundled libshizuku.so starter of the installed Shizuku app. */
    const val SHIZUKU_START_REMOTE =
        "p=\$(pm path moe.shizuku.privileged.api | head -n1 | cut -d: -f2); " +
            "s=\$(ls \"\${p%/*}\"/lib/*/libshizuku.so 2>/dev/null | head -n1); " +
            "if [ -n \"\$s\" ]; then exec \"\$s\"; else echo 'Shizuku starter not found - open the Shizuku app'; exit 1; fi"

    fun pairArgv(pair: HostPort, code: String): List<String> =
        listOf("adb", "pair", pair.toString(), requireNotNull(normalizeCode(code)) { "invalid code" })

    fun connectArgv(connect: HostPort): List<String> = listOf("adb", "connect", connect.toString())

    fun shizukuStartArgv(connect: HostPort): List<String> =
        listOf("adb", "-s", connect.toString(), "shell", SHIZUKU_START_REMOTE)

    /**
     * One shell line: pair → connect → start Shizuku, each token single-quote escaped.
     * Run inside VictorSuite (needs `pkg install android-tools`).
     */
    fun sequence(pair: HostPort, code: String, connect: HostPort): String = listOf(
        VictorSuiteLauncher.shJoin(pairArgv(pair, code)),
        VictorSuiteLauncher.shJoin(connectArgv(connect)),
        VictorSuiteLauncher.shJoin(shizukuStartArgv(connect)),
    ).joinToString(" && ")

    /** Connect + start only (device already paired earlier). */
    fun reconnectSequence(connect: HostPort): String = listOf(
        VictorSuiteLauncher.shJoin(connectArgv(connect)),
        VictorSuiteLauncher.shJoin(shizukuStartArgv(connect)),
    ).joinToString(" && ")
}
