package com.victorious.victordrax.tools

import com.victorious.victordrax.tools.spec.*
import org.junit.Assert.*
import org.junit.Test

class CommandBuilderTest {
    private val scope = listOf("10.0.0.0/24", "example.com")
    private fun ok(id: String, v: Map<String, String>): List<String> {
        val spec = ToolSpecs.byId(id)!!
        val r = CommandBuilder.build(spec, spec.defaults() + v, scope)
        assertTrue("expected Ok for $id but got $r", r is BuildResult.Ok)
        return (r as BuildResult.Ok).argv
    }
    private fun err(id: String, v: Map<String, String>) {
        val spec = ToolSpecs.byId(id)!!
        assertTrue(CommandBuilder.build(spec, spec.defaults() + v, scope) is BuildResult.Err)
    }

    @Test fun allSixFormsRegistered() {
        listOf("nmap", "sqlmap", "hydra", "john", "aircrack", "zaproxy").forEach { assertNotNull(ToolSpecs.byId(it)) }
    }

    @Test fun nmapFlags() {
        val a = ok("nmap", mapOf("ports" to "22,80", "os" to "true", "target" to "10.0.0.5"))
        assertEquals("nmap", a.first()); assertEquals("10.0.0.5", a.last())
        assertTrue(a.containsAll(listOf("-sV", "-T4", "-O")))
        assertEquals("22,80", a[a.indexOf("-p") + 1])
        assertFalse(a.contains("-sC"))
    }
    @Test fun nmapScopeRefused() { err("nmap", mapOf("target" to "8.8.8.8")) }
    @Test fun nmapRequiresTarget() { err("nmap", mapOf("target" to "")) }
    @Test fun nmapInjectionRejected() {
        err("nmap", mapOf("target" to "10.0.0.5;rm -rf /"))
        err("nmap", mapOf("target" to "10.0.0.5", "ports" to "22`id`"))
        err("nmap", mapOf("target" to "\$(id)"))
    }

    @Test fun sqlmapFlags() {
        val a = ok("sqlmap", mapOf("url" to "https://example.com/item?id=1&x=2", "data" to "id=1&n=x", "dbs" to "true"))
        assertEquals("https://example.com/item?id=1&x=2", a[a.indexOf("-u") + 1])
        assertEquals("id=1&n=x", a[a.indexOf("--data") + 1])
        assertTrue(a.contains("--dbs")); assertTrue(a.contains("--batch"))
    }
    @Test fun sqlmapScope() { err("sqlmap", mapOf("url" to "https://evil.net/?id=1")) }
    @Test fun sqlmapQuotesRejected() { err("sqlmap", mapOf("url" to "https://example.com/", "data" to "id=1' OR '1")) }

    @Test fun hydraFlags() {
        val a = ok("hydra", mapOf("user" to "admin", "passlist" to "/root/pw.txt", "target" to "10.0.0.9", "service" to "ssh", "threads" to "4"))
        assertEquals(listOf("10.0.0.9", "ssh"), a.takeLast(2))
        assertEquals("admin", a[a.indexOf("-l") + 1]); assertEquals("4", a[a.indexOf("-t") + 1])
    }
    @Test fun hydraScopeAndInt() {
        err("hydra", mapOf("target" to "172.16.0.1", "service" to "ssh"))
        err("hydra", mapOf("target" to "10.0.0.9", "service" to "ssh", "threads" to "4; id"))
    }

    @Test fun johnEquals() {
        val a = ok("john", mapOf("wordlist" to "/root/rockyou.txt", "format" to "raw-md5", "hashFile" to "/root/h.txt"))
        assertTrue(a.contains("--wordlist=/root/rockyou.txt")); assertTrue(a.contains("--format=raw-md5"))
        assertEquals("/root/h.txt", a.last())
        err("john", mapOf("hashFile" to "/root/../etc/shadow;id"))
    }
    @Test fun aircrack() {
        val a = ok("aircrack", mapOf("wordlist" to "/root/w.txt", "bssid" to "AA:BB:CC:DD:EE:FF", "capFile" to "/root/c.cap"))
        assertEquals("aircrack-ng", a.first()); assertEquals("/root/c.cap", a.last())
        err("aircrack", mapOf("capFile" to ""))
    }
    @Test fun zap() {
        val a = ok("zaproxy", mapOf("quickurl" to "https://example.com", "quickout" to "/root/zap.html"))
        assertEquals(listOf("zap.sh", "-cmd"), a.take(2))
        assertEquals("https://example.com", a[a.indexOf("-quickurl") + 1])
        err("zaproxy", mapOf("quickurl" to "https://not-in-scope.org"))
        err("zaproxy", mapOf("quickurl" to "ftp://example.com"))
    }
    @Test fun presetsBuild() {
        ToolSpecs.all.forEach { s -> s.presets.forEach { p ->
            val r = CommandBuilder.build(s, s.defaults() + p.values + mapOf(
                "target" to "10.0.0.1", "url" to "https://example.com/?id=1", "quickurl" to "https://example.com",
                "hashFile" to "/root/h", "capFile" to "/root/c.cap"), scope)
            assertTrue("${s.id}/${p.name}: $r", r is BuildResult.Ok)
        } }
    }
}
