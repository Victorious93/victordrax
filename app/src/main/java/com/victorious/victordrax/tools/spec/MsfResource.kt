package com.victorious.victordrax.tools.spec

import com.victorious.victordrax.data.Scope

/**
 * Metasploit Framework helper: builds a resource script (ordered msfconsole commands) and the
 * msfconsole argv that runs it. Pure and unit tested. The RHOSTS value is scope-checked against
 * the active engagement, exactly like the other tools.
 */
object MsfResource {
    private val MODULE = Regex("^[a-z0-9]+(/[a-z0-9_]+)+$")
    private val OPTKEY = Regex("^[A-Za-z0-9_]{1,40}$")
    private val INT = Regex("^[0-9]{1,9}$")
    private val VALUE = Regex("^[^;'\"`\\\\\\s\\u0000-\\u001f]{1,200}$")

    data class Options(
        val module: String,
        val rhosts: String,
        val rport: String = "",
        val payload: String = "",
        val lhost: String = "",
        val lport: String = "",
        /** Extra "KEY VALUE" set lines, KEY -> VALUE. */
        val extra: Map<String, String> = emptyMap(),
        val action: String = "run", // run | exploit | check
    )

    sealed interface Result {
        data class Ok(val lines: List<String>, val argv: List<String>, val preview: String) : Result
        data class Err(val message: String) : Result
    }

    /** Build the ordered resource-script lines for [o], enforcing [scope] on RHOSTS. */
    fun build(o: Options, scope: List<String>): Result {
        if (!MODULE.matches(o.module)) return Result.Err("Module path looks invalid (e.g. auxiliary/scanner/ssh/ssh_login).")
        if (o.rhosts.isBlank()) return Result.Err("RHOSTS is required.")
        if (!VALUE.matches(o.rhosts)) return Result.Err("RHOSTS has unsupported characters.")
        if (scope.isEmpty()) return Result.Err("The active engagement has no scope defined.")
        if (!Scope.inScope(o.rhosts, scope))
            return Result.Err("RHOSTS \"${Scope.hostOf(o.rhosts)}\" is outside the active engagement scope.")
        if (o.action !in setOf("run", "exploit", "check")) return Result.Err("Unknown action.")

        val lines = ArrayList<String>()
        lines += "use ${o.module}"
        lines += "set RHOSTS ${o.rhosts}"
        fun setIf(key: String, raw: String, numeric: Boolean = false): String? {
            val v = raw.trim(); if (v.isEmpty()) return null
            if (numeric && !INT.matches(v)) return "$key must be a number."
            if (!numeric && !VALUE.matches(v)) return "$key has unsupported characters."
            lines += "set $key $v"; return null
        }
        setIf("RPORT", o.rport, numeric = true)?.let { return Result.Err(it) }
        setIf("PAYLOAD", o.payload)?.let { return Result.Err(it) }
        setIf("LHOST", o.lhost)?.let { return Result.Err(it) }
        setIf("LPORT", o.lport, numeric = true)?.let { return Result.Err(it) }
        for ((k, v) in o.extra) {
            if (!OPTKEY.matches(k)) return Result.Err("Option name \"$k\" is invalid.")
            setIf(k, v)?.let { return Result.Err(it) }
        }
        lines += o.action  // no "exit": the msfconsole session stays open and interactive

        val script = lines.joinToString("; ")
        val argv = listOf("msfconsole", "-q", "-x", script)
        return Result.Ok(lines, argv, "msfconsole -q -x \"$script\"")
    }
}
