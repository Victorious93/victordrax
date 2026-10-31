package com.victorious.victordrax.workflow

import com.victorious.victordrax.data.Scope
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.terminal.VictorSuiteLauncher

/** A resolved, ready-to-run step (variables substituted). */
data class ResolvedStep(val label: String, val command: String)

sealed interface RunGuard {
    data object Ok : RunGuard
    data class Blocked(val reason: String) : RunGuard
}

/** Pure workflow logic: variable handling, substitution and the authorization/scope guard. Unit tested. */
object Playbooks {
    private val VAR = Regex("""\{([a-zA-Z_][a-zA-Z0-9_]*)\}""")

    /** Distinct variable names referenced by a set of command templates, in first-seen order. */
    fun variablesIn(commands: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        commands.forEach { c -> VAR.findAll(c).forEach { seen.add(it.groupValues[1]) } }
        return seen.toList()
    }

    /** Substitute {var} values. Throws if any variable is left unresolved. */
    fun substitute(template: String, values: Map<String, String>): String {
        val result = VAR.replace(template) { m ->
            values[m.groupValues[1]] ?: throw IllegalArgumentException("missing value for {${m.groupValues[1]}}")
        }
        return result
    }

    /**
     * Guard that must pass before any step runs:
     * - an engagement must be selected and fully authorized,
     * - every required variable must be supplied,
     * - a `target` value (if present) must be a valid target inside the engagement scope,
     * - every resolved command must be launch-safe.
     */
    fun guard(engagement: Engagement?, commands: List<String>, values: Map<String, String>): RunGuard {
        if (engagement == null) return RunGuard.Blocked("Select an engagement first.")
        if (!engagement.isAuthorized) return RunGuard.Blocked("The engagement has no authorization record. Record written authorization before running tools.")
        val needed = variablesIn(commands)
        needed.firstOrNull { values[it].isNullOrBlank() }?.let { return RunGuard.Blocked("Provide a value for {$it}.") }
        values["target"]?.let { t ->
            if (!Scope.isValidTarget(t)) return RunGuard.Blocked("'$t' is not a valid IP, CIDR or hostname.")
            if (!Scope.targetInScope(t, engagement.scopeList())) return RunGuard.Blocked("Target '$t' is outside the engagement scope.")
        }
        return try {
            commands.forEach { c -> require(VictorSuiteLauncher.isSafeCommand(substitute(c, values))) { "unsupported characters in: $c" } }
            RunGuard.Ok
        } catch (e: IllegalArgumentException) { RunGuard.Blocked(e.message ?: "invalid command") }
    }

    fun resolve(steps: List<Pair<String, String>>, values: Map<String, String>): List<ResolvedStep> =
        steps.map { (label, cmd) -> ResolvedStep(label, substitute(cmd, values)) }

    /** Built-in, passive/benign recon playbooks seeded on first run. name -> (description, steps[label,command]). */
    data class BuiltIn(val name: String, val description: String, val steps: List<Pair<String, String>>)

    val builtIns: List<BuiltIn> = listOf(
        BuiltIn("Nmap service scan", "Version detection on common ports (no host discovery).",
            listOf("Service/version scan" to "nmap -sV -Pn -T3 {target}")),
        BuiltIn("Passive host recon", "Benign WHOIS and DNS lookups for a target.",
            listOf("WHOIS" to "whois {target}", "DNS records" to "dig {target} ANY +noall +answer")),
        BuiltIn("Top-ports TCP sweep", "Quick scan of the top 100 TCP ports.",
            listOf("Top 100 ports" to "nmap --top-ports 100 -Pn {target}")),
    )
}
