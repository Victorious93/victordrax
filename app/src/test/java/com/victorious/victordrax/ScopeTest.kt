package com.victorious.victordrax

import com.victorious.victordrax.data.Scope
import org.junit.Assert.*
import org.junit.Test

class ScopeTest {
    private val scope = listOf("10.0.0.0/24", "example.com", "*.lab.test", "192.168.1.5")

    @Test fun hostExtraction() {
        assertEquals("example.com", Scope.hostOf("https://example.com:8443/a?b=1"))
        assertEquals("10.0.0.7", Scope.hostOf("10.0.0.7:22"))
        assertEquals("host.lab.test", Scope.hostOf("HOST.lab.test"))
    }
    @Test fun inScopeCases() {
        assertTrue(Scope.inScope("10.0.0.200", scope))
        assertTrue(Scope.inScope("https://example.com/login", scope))
        assertTrue(Scope.inScope("api.example.com", scope))
        assertTrue(Scope.inScope("a.lab.test", scope))
        assertTrue(Scope.inScope("192.168.1.5", scope))
    }
    @Test fun outOfScopeRefused() {
        assertFalse(Scope.inScope("10.0.1.1", scope))
        assertFalse(Scope.inScope("evil-example.com", scope))
        assertFalse(Scope.inScope("example.com.evil.net", scope))
        assertFalse(Scope.inScope("lab.test.evil.net", scope))
        assertFalse(Scope.inScope("192.168.1.6", scope))
        assertFalse(Scope.inScope("10.0.0.5", emptyList()))
    }

    // --- batch-2 scope validation API ---
    @Test fun syntax() {
        assertTrue(Scope.isIpv4("10.0.0.1")); assertFalse(Scope.isIpv4("10.0.0.256")); assertFalse(Scope.isIpv4("01.2.3.4"))
        assertTrue(Scope.isCidr("10.0.0.0/24")); assertFalse(Scope.isCidr("10.0.0.0/33"))
        assertTrue(Scope.isHostname("app.example.com")); assertFalse(Scope.isHostname("bad_host!"))
        assertFalse(Scope.isValidTarget("10.0.0.1; rm -rf /"))
    }
    @Test fun inScope() {
        val s = listOf("10.0.0.0/24", "192.168.1.10", "example.com")
        assertTrue(Scope.targetInScope("10.0.0.77", s))
        assertFalse(Scope.targetInScope("10.0.1.1", s))
        assertTrue(Scope.targetInScope("192.168.1.10", s)); assertFalse(Scope.targetInScope("192.168.1.11", s))
        assertTrue(Scope.targetInScope("WWW.Example.com", s)); assertFalse(Scope.targetInScope("example.com.evil.net", s))
        assertFalse(Scope.targetInScope("notexample.com", s))
        assertTrue(Scope.targetInScope("10.0.0.128/25", s)); assertFalse(Scope.targetInScope("10.0.0.0/16", s))
        assertFalse(Scope.targetInScope("10.0.0.1", emptyList()))
    }
}
