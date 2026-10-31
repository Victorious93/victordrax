package com.victorious.victordrax.tools

import com.victorious.victordrax.tools.parse.NmapParser
import org.junit.Assert.*
import org.junit.Test

class NmapParserTest {
    @Test fun parses() {
        val out = """
            Starting Nmap 7.94
            Nmap scan report for web.example.com (10.0.0.5)
            Host is up (0.01s latency).
            PORT     STATE  SERVICE VERSION
            22/tcp   open   ssh     OpenSSH 8.9p1
            80/tcp   open   http    nginx 1.24
            443/tcp  closed https
            Nmap scan report for 10.0.0.6
            53/udp   open   domain
        """.trimIndent()
        val h = NmapParser.parse(out)
        assertEquals(2, h.size)
        assertEquals("10.0.0.5", h[0].address); assertEquals("web.example.com", h[0].hostname)
        assertEquals(listOf(22, 80), h[0].openPorts.map { it.port })
        assertEquals("OpenSSH 8.9p1", h[0].ports[0].version)
        assertEquals("udp", h[1].ports[0].proto)
    }
    @Test fun garbage() { assertTrue(NmapParser.parse("hello\nworld").isEmpty()) }
}
