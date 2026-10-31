package com.victorious.victordrax.tools.spec

import com.victorious.victordrax.data.Scope

/** Result of turning a filled-in [ToolSpec] form into a command. */
sealed interface BuildResult {
    data class Ok(val argv: List<String>, val preview: String) : BuildResult
    data class Err(val message: String) : BuildResult
}

/**
 * Pure command-line builder. Given a [ToolSpec], the current field [values] and the active
 * engagement [scope], it validates every field, enforces engagement scope on the target field,
 * and returns an argv list ready for [com.victorious.victordrax.terminal.VictorSuiteLauncher].
 *
 * It never performs shell escaping itself (the launcher does, once) but it does reject any value
 * containing shell metacharacters or control characters, as a defence-in-depth second layer.
 */
object CommandBuilder {

    private val CONTROL = Regex("[\\u0000-\\u001f\\u007f]")
    private val HOST = Regex("^[A-Za-z0-9](?:[A-Za-z0-9.\\-]{0,253}[A-Za-z0-9])?$")
    private val PORTS = Regex("^[UTS]?:?[0-9][0-9,\\-]*$")
    private val INT = Regex("^[0-9]{1,9}$")
    private val PATH = Regex("^[A-Za-z0-9 ._/\\-]{1,255}$")
    private val USERNAME = Regex("^[A-Za-z0-9._@\\-]{1,64}$")
    // Generic free-text: printable, but no characters that could change shell word-splitting intent.
    private val SHELL_META = Regex("[\\s'\";`$<>|&(){}\\[\\]!*\\\\]")

    private fun validate(f: FieldSpec, raw: String): String? {
        val v = raw.trim()
        if (CONTROL.containsMatchIn(v)) return "${f.label}: control characters are not allowed"
        return when (f.kind) {
            ValueKind.HOST -> if (HOST.matches(v)) null else "${f.label}: not a valid host or IP"
            ValueKind.URL -> when {
                !(v.startsWith("http://") || v.startsWith("https://")) -> "${f.label}: must be an http(s) URL"
                !HOST.matches(Scope.hostOf(v)) -> "${f.label}: URL host is not valid"
                v.any { it == '\'' || it == '"' || it == '`' || it == '\\' || it == ' ' } -> "${f.label}: URL has unsupported characters"
                else -> null
            }
            ValueKind.PARAM -> if (v.any { it == '\'' || it == '"' || it == '`' || it == '\\' || it == ' ' }) "${f.label}: unsupported characters" else null
            ValueKind.PORTS -> if (PORTS.matches(v)) null else "${f.label}: port list must look like 22,80,443 or 1-1024"
            ValueKind.INT -> if (INT.matches(v)) null else "${f.label}: must be a whole number"
            ValueKind.PATH -> if (PATH.matches(v)) null else "${f.label}: unsupported characters in path"
            ValueKind.USERNAME -> if (USERNAME.matches(v)) null else "${f.label}: unsupported characters in username"
            ValueKind.GENERIC -> if (SHELL_META.containsMatchIn(v)) "${f.label}: unsupported characters" else null
        }
    }

    fun build(spec: ToolSpec, values: Map<String, String>, scope: List<String>): BuildResult {
        val argv = ArrayList(spec.program)
        var sawTarget = false
        for (f in spec.fields) {
            val raw = (values[f.key] ?: f.default)
            if (f.type == FieldType.TOGGLE) {
                if (raw.trim().equals("true", ignoreCase = true)) f.flag?.let { argv += it }
                continue
            }
            val v = raw.trim()
            if (v.isEmpty()) {
                if (f.required) return BuildResult.Err("${f.label} is required")
                continue
            }
            validate(f, v)?.let { return BuildResult.Err(it) }

            if (f.isTarget) {
                sawTarget = true
                if (scope.isEmpty()) return BuildResult.Err("The active engagement has no scope defined. Add in-scope targets first.")
                if (!Scope.inScope(v, scope))
                    return BuildResult.Err("Target \"${Scope.hostOf(v)}\" is outside the active engagement scope. Refusing to build the command.")
            }

            when {
                f.flag == null -> argv += v
                f.joinWithEquals -> argv += "${f.flag}=$v"
                else -> { argv += f.flag; argv += v }
            }
        }
        if (spec.fields.any { it.isTarget } && !sawTarget)
            return BuildResult.Err("A target is required and must be inside the engagement scope.")
        return BuildResult.Ok(argv, preview(argv))
    }

    /** Human-readable preview: quote only tokens that contain spaces or shell-ish characters. */
    fun preview(argv: List<String>): String = argv.joinToString(" ") { t ->
        if (t.isEmpty() || t.any { it == ' ' || it == '\'' || it == '"' }) "'" + t.replace("'", "'\\''") + "'" else t
    }
}
