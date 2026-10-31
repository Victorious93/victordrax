package com.victorious.victordrax

import com.victorious.victordrax.scan.ScanParser
import com.victorious.victordrax.scan.ScanTable
import org.junit.Assert.*
import org.junit.Test

class ScanParserTest {
    private fun fx(n: String) = javaClass.classLoader!!.getResource("fixtures/$n")!!.readText()

    @Test fun nmapXml() {
        val t = ScanParser.parse("scan.xml", fx("nmap.xml"))
        assertEquals("nmap-xml", t.format)
        assertEquals(4, t.rows.size) // 3 ports on .2, 1 on .1, down host has no ports
        val ssh = t.rows.first { it.port == 22 }
        assertEquals("192.168.56.2", ssh.host); assertEquals("lab-web.local", ssh.hostname)
        assertEquals("ssh", ssh.service); assertEquals("OpenSSH 9.2p1 Debian 2", ssh.versionText)
        assertEquals("udp", t.rows.first { it.port == 53 }.proto)
        assertEquals(listOf("192.168.56.1", "192.168.56.2"), t.hosts())
    }

    @Test fun nmapXmlDoesNotResolveExternalEntities() {
        val evil = """<?xml version="1.0"?><!DOCTYPE nmaprun [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <nmaprun><host><address addr="10.0.0.1" addrtype="ipv4"/><ports><port protocol="tcp" portid="1"><state state="open"/><service name="&x;"/></port></ports></host></nmaprun>"""
        val rows = runCatching { ScanParser.parseNmapXml(evil) }.getOrDefault(emptyList())
        rows.forEach { assertFalse(it.service.contains("root:")) }
    }

    @Test fun grepable() {
        val t = ScanParser.parse("scan.gnmap", fx("nmap.gnmap"))
        assertEquals("nmap-grep", t.format)
        assertEquals(listOf(22, 80, 443), t.rows.map { it.port })
        assertEquals("filtered", t.rows.last().state)
        assertEquals("nginx 1.22.1", t.rows[1].version)
    }

    @Test fun json() {
        val t = ScanParser.parse("scan.json", fx("scan.json"))
        assertEquals("json", t.format)
        assertEquals(2, t.rows.size)
        assertEquals(8080, t.rows[0].port); assertEquals("Squid 5.7", t.rows[0].versionText)
        assertEquals(3306, t.rows[1].port)
    }

    @Test fun csvQuoted() {
        val t = ScanParser.parse("scan.csv", fx("scan.csv"))
        assertEquals("csv", t.format)
        assertEquals(2, t.rows.size); assertEquals("vsftpd 3.0.5", t.rows[0].version)
    }

    @Test fun sortFilterAndRoundTrip() {
        val t = ScanParser.parse("scan.xml", fx("nmap.xml"))
        assertEquals(listOf(443, 80, 53, 22), t.sortedBy("port", true).map { it.port })
        assertEquals(3, t.filter("", null, openOnly = true).size)
        assertEquals(listOf(80), t.filter("nginx", null, false).map { it.port })
        assertEquals(1, t.filter("", "192.168.56.1", false).size)
        assertEquals(t, ScanTable.fromJson(ScanTable.toJson(t)))
    }
}
