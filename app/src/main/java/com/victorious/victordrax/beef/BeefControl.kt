package com.victorious.victordrax.beef

import com.victorious.victordrax.data.Scope
import com.victorious.victordrax.data.Validation
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.workflow.RunGuard

/**
 * Authorization / scope gating for BeEF operations, built on the same engagement model and
 * scope rules as [com.victorious.victordrax.workflow.Playbooks.guard].
 *
 * BeEF operations must be tied to an authorized engagement:
 *  - starting/stopping BeEF and connecting to the API require an authorized engagement,
 *  - launching a module against a hooked browser additionally requires that browser's host
 *    (its domain, or its IP) to be inside the engagement scope.
 *
 * This is an operator control panel for the user's OWN authorized engagements; it deliberately
 * has no auto-exploit, mass-hook or stealth behaviour.
 */
object BeefGuard {
    /** Gate for connecting to / controlling the operator's BeEF server. */
    fun guardServer(engagement: Engagement?): RunGuard = when {
        engagement == null -> RunGuard.Blocked("Select an engagement first.")
        !engagement.isAuthorized -> RunGuard.Blocked("The engagement has no authorization record. Record written authorization before using BeEF.")
        else -> RunGuard.Ok
    }

    /** Gate for launching a module against a specific hooked browser. */
    fun guardModule(engagement: Engagement?, hook: HookedBrowser?): RunGuard {
        when (val base = guardServer(engagement)) { is RunGuard.Blocked -> return base; RunGuard.Ok -> {} }
        if (hook == null) return RunGuard.Blocked("Select a hooked browser first.")
        val host = hook.scopeHost
        if (host.isBlank()) return RunGuard.Blocked("The hooked browser has no resolvable host to scope-check.")
        if (!Scope.inScope(host, engagement!!.scopeList())) {
            return RunGuard.Blocked("Hooked browser host '$host' is outside the engagement scope.")
        }
        return RunGuard.Ok
    }
}

/**
 * Pure builders for the shell commands VictorDrax sends to VictorSuite (Termux/ANDRAX-NG) to
 * install, start and stop BeEF. Kept pure so they are unit-tested. The commands are plain BeEF
 * project commands run inside the operator's own environment — nothing destructive or hidden.
 */
object BeefCommands {
    /** `cd <dir> && ./beef` — start BeEF from its install dir. */
    fun start(installDir: String): String {
        require(Validation.isSafePath(installDir)) { "unsafe install dir" }
        return "cd ${installDir} && ./beef"
    }

    /** Stop any running beef process (ruby/beef). */
    fun stop(): String = "pkill -f beef || echo 'no beef process found'"

    /** Show the generated config (creds/port) so the operator can copy them into the app. */
    fun showConfig(installDir: String): String {
        require(Validation.isSafePath(installDir)) { "unsafe install dir" }
        return "cat ${installDir}/config.yaml"
    }

    /**
     * Guided install steps for BeEF inside the Termux/ANDRAX environment. A full automatic
     * install is NOT reliable in Termux (ruby gems with native extensions), so these are shown
     * as explicit, copyable steps for the operator to run and verify.
     */
    data class Step(val label: String, val command: String)

    fun installSteps(installDir: String): List<Step> {
        val dir = if (Validation.isSafePath(installDir)) installDir else BeefConfig.DEFAULT_INSTALL_DIR
        return listOf(
            Step("Install prerequisites", "pkg install -y git ruby ruby-dev make clang libffi openssl sqlite"),
            Step("Clone BeEF", "git clone https://github.com/beefproject/beef ${dir}"),
            Step("Enter the BeEF directory", "cd ${dir}"),
            Step("Install Ruby bundler", "gem install bundler"),
            Step("Install BeEF gems", "bundle install"),
            Step("First run (generates config.yaml)", "cd ${dir} && ./beef"),
        )
    }
}
