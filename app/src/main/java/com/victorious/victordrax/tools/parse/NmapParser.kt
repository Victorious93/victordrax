package com.victorious.victordrax.tools.parse

/**
 * Minimal parser for `nmap` normal output. The RUN_COMMAND contract cannot capture a session's
 * stdout, so the results viewer lets the user paste output (or import an -oN file); this turns it
 * into structured host/port rows. Pure and unit tested.
 */
object NmapParser {
    data class Port(val port: Int, val proto: String, val state: String, val service: String, val version: String)
    data class Host(val address: String, val hostname: String?, val ports: List<Port>) {
        val openPorts: List<Port> get() = ports.filter { it.state == "open" }
    }

    private val HOST_LINE = Regex("""Nmap scan report for (?:([^ ]+) \(([0-9.]+)\)|([0-9A-Fa-f.:]+))""")
    private val PORT_LINE = Regex("""^(\d+)/(tcp|udp)\s+(\S+)\s+(\S+)(?:\s+(.*))?$""")

    fun parse(text: String): List<Host> {
        val hosts = ArrayList<Host>()
        var curAddr: String? = null
        var curName: String? = null
        var ports = ArrayList<Port>()
        fun flush() { curAddr?.let { hosts += Host(it, curName, ports) }; ports = ArrayList(); curName = null }
        for (raw in text.lines()) {
            val line = raw.trim()
            val hm = HOST_LINE.find(line)
            if (hm != null) {
                flush()
                if (hm.groupValues[1].isNotEmpty()) { curName = hm.groupValues[1]; curAddr = hm.groupValues[2] }
                else { curAddr = hm.groupValues[3]; curName = null }
                continue
            }
            val pm = PORT_LINE.find(line) ?: continue
            ports += Port(
                port = pm.groupValues[1].toInt(),
                proto = pm.groupValues[2],
                state = pm.groupValues[3],
                service = pm.groupValues[4],
                version = pm.groupValues[5].trim(),
            )
        }
        flush()
        return hosts
    }
}
