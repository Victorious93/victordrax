package com.victorious.victordrax

import com.victorious.victordrax.data.Scope
import org.junit.Assert.*
import org.junit.Test

class ScopeTest {
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
