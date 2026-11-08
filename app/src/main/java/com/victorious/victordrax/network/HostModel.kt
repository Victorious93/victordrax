package com.victorious.victordrax.network

import com.victorious.victordrax.db.Finding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One open/closed/filtered port on a discovered host. */
@Serializable
data class HostPort(
    val port: Int,
    val proto: String = "tcp",
    val state: String = "open",
    val service: String = "",
    val product: String = "",
    val version: String = "",
) {
    val versionText: String get() = listOf(product, version).filter { it.isNotBlank() }.joinToString(" ")
    val label: String get() = "$port/$proto $state ${service.ifBlank { "" }} ${versionText}".trim().replace(Regex("""\s+"""), " ")
}

/** A host from an nmap -oX run, with status, ports and optional OS guess. */
@Serializable
data class DiscoveredHost(
    val address: String,
    val hostname: String = "",
    /** "up" or "down". */
    val status: String = "up",
    val ports: List<HostPort> = emptyList(),
    /** Best OS match name from nmap <osmatch>, empty if unknown. */
    val osGuess: String = "",
    val osAccuracy: Int = 0,
) {
    val openPorts: List<HostPort> get() = ports.filter { it.state.equals("open", true) }
    val displayName: String get() =
        if (hostname.isNotBlank()) "$hostname ($address)" else address
}

/** A full discovery snapshot stored in NetworkStore (NOT tool_runs / Room). */
@Serializable
data class DiscoverySnapshot(
    val engagementId: Long = 0,
    val target: String = "",
    val scannedAt: Long = 0,
    val command: String = "",
    val hosts: List<DiscoveredHost> = emptyList(),
) {
    val upHosts: List<DiscoveredHost> get() = hosts.filter { it.status.equals("up", true) }
    val downHosts: List<DiscoveredHost> get() = hosts.filter { !it.status.equals("up", true) }
}

/** Filter/sort for the host dashboard. Pure; unit tested. */
data class HostFilter(
    val text: String = "",
    val port: Int? = null,
    val service: String = "",
    /** null = any; "up"/"down" = status; "open" = has at least one open port. */
    val state: String? = null,
)

object HostLogic {
    fun matches(h: DiscoveredHost, f: HostFilter): Boolean {
        val q = f.text.trim().lowercase()
        if (q.isNotEmpty()) {
            val hay = buildString {
                append(h.address); append(' '); append(h.hostname); append(' '); append(h.osGuess)
                h.ports.forEach { append(' '); append(it.label) }
            }.lowercase()
            if (!hay.contains(q)) return false
        }
        if (f.port != null && h.ports.none { it.port == f.port }) return false
        if (f.service.isNotBlank() && h.ports.none { it.service.contains(f.service.trim(), true) }) return false
        when (f.state?.lowercase()) {
            null, "", "any" -> {}
            "up" -> if (!h.status.equals("up", true)) return false
            "down" -> if (h.status.equals("up", true)) return false
            "open" -> if (h.openPorts.isEmpty()) return false
            else -> if (h.ports.none { it.state.equals(f.state, true) }) return false
        }
        return true
    }

    fun apply(hosts: List<DiscoveredHost>, f: HostFilter): List<DiscoveredHost> =
        hosts.filter { matches(it, f) }
            .sortedWith(compareByDescending<DiscoveredHost> { it.status.equals("up", true) }
                .thenByDescending { it.openPorts.size }
                .thenBy { it.address })

    /**
     * Pre-fill a Finding from a host (and optional port) for the findings manager.
     * Does not write to the DB — caller passes the draft to FindingsRepo.save.
     */
    fun draftFinding(engagementId: Long, host: DiscoveredHost, port: HostPort? = null): Finding {
        val title = if (port != null) {
            "Open ${port.port}/${port.proto} (${port.service.ifBlank { "unknown" }}) on ${host.address}"
        } else {
            "Host ${host.displayName} — ${host.openPorts.size} open port(s)"
        }
        val evidence = buildString {
            append("Host: ").append(host.displayName).append('\n')
            append("Status: ").append(host.status).append('\n')
            if (host.osGuess.isNotBlank()) append("OS guess: ").append(host.osGuess)
                .append(if (host.osAccuracy > 0) " (${host.osAccuracy}%)" else "").append('\n')
            if (port != null) {
                append("Port: ").append(port.label).append('\n')
            } else {
                append("Open ports:\n")
                host.openPorts.forEach { append("  - ").append(it.label).append('\n') }
            }
        }
        return Finding(
            engagementId = engagementId,
            title = title,
            severity = "Info",
            description = "Discovered during network scan (authorized engagement).",
            target = host.address,
            tags = "network,nmap" + if (port != null && port.service.isNotBlank()) ",${port.service}" else "",
            evidence = evidence.trimEnd(),
            status = "OPEN",
        )
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun toJson(s: DiscoverySnapshot): String = json.encodeToString(DiscoverySnapshot.serializer(), s)
    fun fromJson(s: String): DiscoverySnapshot = json.decodeFromString(DiscoverySnapshot.serializer(), s)
}
