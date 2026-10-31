package com.victorious.victordrax.tools.spec

import com.victorious.victordrax.tools.spec.FieldType.*
import com.victorious.victordrax.tools.spec.ValueKind.*

/**
 * Specs for the full GUI form tools of the Kali Top 10. Each is a standard wrapper: it only
 * assembles a command line from a form and hands it to the user's own terminal (VictorSuite).
 * Target-bearing tools carry an [FieldSpec.isTarget] field that [CommandBuilder] scope-checks.
 */
object ToolSpecs {

    private val nmap = ToolSpec(
        id = "nmap", name = "Nmap", program = listOf("nmap"), parseOutput = true,
        fields = listOf(
            FieldSpec("scan", "Scan type", DROPDOWN, flag = null, default = "-sV", options = listOf(
                Option("-sV", "Service/version (-sV)"), Option("-sS", "SYN stealth (-sS)"),
                Option("-sT", "TCP connect (-sT)"), Option("-sU", "UDP (-sU)"), Option("-sn", "Ping sweep (-sn)"),
            )),
            FieldSpec("timing", "Timing", DROPDOWN, flag = null, default = "-T4", options =
                (0..5).map { Option("-T$it", "Timing -T$it") }),
            FieldSpec("ports", "Ports", TEXT, flag = "-p", kind = PORTS, placeholder = "1-1000 or 22,80,443"),
            FieldSpec("os", "OS detection (-O)", TOGGLE, flag = "-O"),
            FieldSpec("scripts", "Default scripts (-sC)", TOGGLE, flag = "-sC"),
            FieldSpec("skipPing", "Skip host discovery (-Pn)", TOGGLE, flag = "-Pn"),
            FieldSpec("target", "Target", TEXT, flag = null, required = true, isTarget = true, kind = HOST,
                placeholder = "in-scope host or IP"),
        ),
        presets = listOf(
            Preset("Quick service scan", mapOf("scan" to "-sV", "ports" to "1-1000", "timing" to "-T4")),
            Preset("Scripts + no ping", mapOf("scan" to "-sV", "scripts" to "true", "skipPing" to "true")),
        ),
    )

    private val sqlmap = ToolSpec(
        id = "sqlmap", name = "sqlmap", program = listOf("sqlmap"),
        fields = listOf(
            FieldSpec("url", "Target URL", TEXT, flag = "-u", required = true, isTarget = true, kind = URL,
                placeholder = "https://host/page?id=1"),
            FieldSpec("data", "POST data", TEXT, flag = "--data", kind = PARAM, placeholder = "id=1&name=x"),
            FieldSpec("level", "Level", DROPDOWN, flag = "--level", default = "1", options = (1..5).map { Option("$it") }),
            FieldSpec("risk", "Risk", DROPDOWN, flag = "--risk", default = "1", options = (1..3).map { Option("$it") }),
            FieldSpec("technique", "Techniques", TEXT, flag = "--technique", kind = GENERIC, placeholder = "BEUSTQ"),
            FieldSpec("dbms", "Force DBMS", TEXT, flag = "--dbms", kind = GENERIC, placeholder = "mysql"),
            FieldSpec("threads", "Threads", NUMBER, flag = "--threads", kind = INT),
            FieldSpec("dbs", "Enumerate databases (--dbs)", TOGGLE, flag = "--dbs"),
            FieldSpec("batch", "Non-interactive (--batch)", TOGGLE, flag = "--batch", default = "true"),
        ),
        presets = listOf(
            Preset("Detect only (batch)", mapOf("batch" to "true", "level" to "1", "risk" to "1")),
            Preset("Enumerate databases", mapOf("batch" to "true", "dbs" to "true", "level" to "2")),
        ),
    )

    private val hydra = ToolSpec(
        id = "hydra", name = "THC Hydra", program = listOf("hydra"),
        fields = listOf(
            FieldSpec("user", "Username", TEXT, flag = "-l", kind = USERNAME),
            FieldSpec("userlist", "Username list file", TEXT, flag = "-L", kind = PATH),
            FieldSpec("pass", "Single password", TEXT, flag = "-p", kind = PARAM),
            FieldSpec("passlist", "Password list file", TEXT, flag = "-P", kind = PATH),
            FieldSpec("port", "Port", NUMBER, flag = "-s", kind = INT),
            FieldSpec("threads", "Tasks", NUMBER, flag = "-t", kind = INT),
            FieldSpec("target", "Target", TEXT, flag = null, required = true, isTarget = true, kind = HOST),
            FieldSpec("service", "Service/module", DROPDOWN, flag = null, required = true, default = "ssh", options = listOf(
                Option("ssh"), Option("ftp"), Option("http-get"), Option("https-get"),
                Option("smb"), Option("rdp"), Option("mysql"), Option("postgres"), Option("vnc"),
            )),
        ),
        presets = listOf(
            Preset("SSH wordlist", mapOf("service" to "ssh", "threads" to "4")),
        ),
    )

    // Local file auditing: no network target, so no scope target (operates on a hash file on disk).
    private val john = ToolSpec(
        id = "john", name = "John the Ripper", program = listOf("john"),
        fields = listOf(
            FieldSpec("wordlist", "Wordlist", TEXT, flag = "--wordlist", joinWithEquals = true, kind = PATH),
            FieldSpec("format", "Hash format", TEXT, flag = "--format", joinWithEquals = true, kind = GENERIC,
                placeholder = "raw-md5"),
            FieldSpec("rules", "Mangling rules (--rules)", TOGGLE, flag = "--rules"),
            FieldSpec("incremental", "Incremental (--incremental)", TOGGLE, flag = "--incremental"),
            FieldSpec("show", "Show cracked (--show)", TOGGLE, flag = "--show"),
            FieldSpec("hashFile", "Hash file", TEXT, flag = null, required = true, kind = PATH,
                placeholder = "/root/hashes.txt"),
        ),
        presets = listOf(
            Preset("Wordlist + rules", mapOf("rules" to "true")),
            Preset("Show cracked", mapOf("show" to "true")),
        ),
    )

    // Wireless auditing on a local capture file: no IP target.
    private val aircrack = ToolSpec(
        id = "aircrack", name = "Aircrack-ng", program = listOf("aircrack-ng"),
        fields = listOf(
            FieldSpec("wordlist", "Wordlist", TEXT, flag = "-w", kind = PATH, placeholder = "/root/wordlist.txt"),
            FieldSpec("bssid", "BSSID", TEXT, flag = "-b", kind = GENERIC, placeholder = "AA:BB:CC:DD:EE:FF"),
            FieldSpec("essid", "ESSID", TEXT, flag = "-e", kind = GENERIC),
            FieldSpec("capFile", "Capture file (.cap/.pcap)", TEXT, flag = null, required = true, kind = PATH,
                placeholder = "/root/capture-01.cap"),
        ),
        presets = listOf(Preset("WPA dictionary crack", mapOf())),
    )

    private val zaproxy = ToolSpec(
        id = "zaproxy", name = "OWASP ZAP", program = listOf("zap.sh", "-cmd"),
        fields = listOf(
            FieldSpec("quickurl", "Target URL", TEXT, flag = "-quickurl", required = true, isTarget = true, kind = URL,
                placeholder = "https://host"),
            FieldSpec("quickout", "Report output file", TEXT, flag = "-quickout", kind = PATH,
                placeholder = "/root/zap-report.html"),
            FieldSpec("quickprogress", "Show progress (-quickprogress)", TOGGLE, flag = "-quickprogress", default = "true"),
        ),
        presets = listOf(
            Preset("Baseline scan + HTML report", mapOf("quickprogress" to "true", "quickout" to "/root/zap-report.html")),
        ),
    )

    val all: List<ToolSpec> = listOf(nmap, sqlmap, hydra, john, aircrack, zaproxy)
    fun byId(id: String): ToolSpec? = all.firstOrNull { it.id == id }
}
