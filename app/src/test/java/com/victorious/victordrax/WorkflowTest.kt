package com.victorious.victordrax

import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.workflow.Playbooks
import com.victorious.victordrax.workflow.RunGuard
import org.junit.Assert.*
import org.junit.Test

class WorkflowTest {
    private val authed = Engagement(id = 1, name = "Lab", client = "Me", scope = "10.0.0.0/24\nexample.com",
        authorizedBy = "Jane", authorizedDate = 1, authDocUri = "content://x")
    private val cmds = listOf("nmap -sV -Pn {target}", "whois {target}")

    @Test fun variablesAndSubstitution() {
        assertEquals(listOf("port", "target"), Playbooks.variablesIn(listOf("nmap -p {port} {target}", "x {target}")))
        assertEquals("nmap -sV -Pn 10.0.0.5", Playbooks.substitute(cmds[0], mapOf("target" to "10.0.0.5")))
        assertThrows(IllegalArgumentException::class.java) { Playbooks.substitute(cmds[0], emptyMap()) }
    }
    @Test fun guard() {
        assertTrue(Playbooks.guard(null, cmds, mapOf("target" to "10.0.0.5")) is RunGuard.Blocked)
        assertTrue(Playbooks.guard(authed.copy(authDocUri = ""), cmds, mapOf("target" to "10.0.0.5")) is RunGuard.Blocked)
        assertTrue(Playbooks.guard(authed, cmds, emptyMap()) is RunGuard.Blocked)
        assertTrue(Playbooks.guard(authed, cmds, mapOf("target" to "8.8.8.8")) is RunGuard.Blocked)
        assertTrue(Playbooks.guard(authed, cmds, mapOf("target" to "10.0.0.5;id")) is RunGuard.Blocked)
        assertEquals(RunGuard.Ok, Playbooks.guard(authed, cmds, mapOf("target" to "10.0.0.5")))
        assertEquals(RunGuard.Ok, Playbooks.guard(authed, cmds, mapOf("target" to "www.example.com")))
    }
    @Test fun builtInsAreSafeTemplates() {
        Playbooks.builtIns.forEach { b ->
            assertEquals(RunGuard.Ok, Playbooks.guard(authed, b.steps.map { it.second }, mapOf("target" to "10.0.0.9")))
        }
    }
}
