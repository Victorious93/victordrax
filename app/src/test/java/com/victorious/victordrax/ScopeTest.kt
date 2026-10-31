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
}
