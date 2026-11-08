package com.victorious.victordrax.network

import com.victorious.victordrax.data.Scope
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.workflow.RunGuard

/**
 * Pure builders + authorization guard for the network discovery dashboard.
 * Scans only run against in-scope authorized targets; the command always writes -oX so the
 * operator can paste/import the XML back into the dashboard.
 */
object NetworkCommands {

    /** Safe output path inside VictorSuite home (quoted by TerminalLauncher argv escaping). */
    const val DEFAULT_XML_PATH = "/data/data/com.termux/files/home/victordrax-discovery.xml"

    enum class Profile(val label: String, val extra: List<String>) {
        QUICK("Quick top-ports", listOf("--top-ports", "100", "-sV", "-Pn", "-T4")),
        SERVICE("Service/version", listOf("-sV", "-Pn", "-T3")),
        OS("Service + OS guess", listOf("-sV", "-O", "-Pn", "-T3")),
        FULL("Top 1000 + scripts", listOf("-sV", "-sC", "--top-ports", "1000", "-Pn", "-T3")),
    }

    /**
     * Build argv for `nmap … -oX <path> <target>`. Returns null argv when inputs are invalid —
     * callers should surface [guard] first.
     */
    fun argv(target: String, profile: Profile = Profile.SERVICE, xmlPath: String = DEFAULT_XML_PATH): List<String> =
        listOf("nmap") + profile.extra + listOf("-oX", xmlPath, target.trim())

    fun preview(target: String, profile: Profile = Profile.SERVICE, xmlPath: String = DEFAULT_XML_PATH): String =
        argv(target, profile, xmlPath).joinToString(" ")

    /** Same engagement + scope rules as Playbooks.guard for a single {target}. */
    fun guard(engagement: Engagement?, target: String): RunGuard {
        if (engagement == null) return RunGuard.Blocked("Select an engagement first.")
        if (!engagement.isAuthorized) return RunGuard.Blocked("The engagement has no authorization record. Record written authorization before scanning.")
        val t = target.trim()
        if (t.isBlank()) return RunGuard.Blocked("Provide a target.")
        if (!Scope.isValidTarget(t) && !Scope.isCidr(t) && Scope.hostOf(t).isBlank()) {
            return RunGuard.Blocked("'$t' is not a valid IP, CIDR or hostname.")
        }
        // Accept host, CIDR or host extracted from host:port / URL.
        val check = when {
            Scope.isValidTarget(t) || Scope.isCidr(t) -> t
            else -> Scope.hostOf(t)
        }
        if (!Scope.targetInScope(check, engagement.scopeList()) && !Scope.inScope(t, engagement.scopeList())) {
            return RunGuard.Blocked("Target '$t' is outside the engagement scope.")
        }
        return RunGuard.Ok
    }
}
