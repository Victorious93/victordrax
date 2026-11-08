package com.victorious.victordrax.network

import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.workflow.RunGuard
import org.junit.Assert.*
import org.junit.Test

class NetworkDashboardTest {
    private fun fx(n: String) = javaClass.classLoader!!.getResource("fixtures/$n")!!.readText()

    private fun eng(scope: String, authorized: Boolean = true) = Engagement(
        id = 1, name = "Lab", client = "Self", scope = scope,
        authorizedBy = if (authorized) "Don" else "",
        authorizedDate = if (authorized) 1L else 0L,
        authDocUri = if (authorized) "content://auth/1" else "",
    )

    @Test fun parsesHostsWithOsAndDown() {
        val hosts = NmapXmlHosts.parse(fx("nmap-os.xml"))
        assertEquals(3, hosts.size)
        val db = hosts.first { it.address == "10.0.0.5" }
        assertEquals("up", db.status)
        assertEquals("db.lab.local", db.hostname)
        assertEquals("Linux 5.15 - 6.1", db.osGuess)
        assertEquals(96, db.osAccuracy)
        assertEquals(listOf(22, 3306, 443), db.ports.map { it.port })
        assertEquals(2, db.openPorts.size)
        assertEquals("OpenSSH 8.9p1", db.openPorts.first { it.port == 22 }.versionText)
        val down = hosts.first { it.address == "10.0.0.6" }
        assertEquals("down", down.status)
        assertTrue(down.ports.isEmpty())
        val gw = hosts.first { it.address == "10.0.0.1" }
        assertEquals("domain", gw.ports.single().service)
    }

    @Test fun reusesExistingNmapFixture() {
        val hosts = NmapXmlHosts.parse(fx("nmap.xml"))
        assertEquals(3, hosts.size) // .2 up, .3 down, .1 up
        assertEquals(2, hosts.count { it.status == "up" })
        val web = hosts.first { it.address == "192.168.56.2" }
        assertEquals("lab-web.local", web.hostname)
        assertEquals(2, web.openPorts.size) // 22, 80 (443 filtered)
    }

    @Test fun rejectsXxe() {
        val evil = """<?xml version="1.0"?><!DOCTYPE nmaprun [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <nmaprun><host><status state="up"/><address addr="10.0.0.1" addrtype="ipv4"/>
            <ports><port protocol="tcp" portid="1"><state state="open"/><service name="&x;"/></port></ports>
            </host></nmaprun>"""
        val hosts = runCatching { NmapXmlHosts.parse(evil) }.getOrDefault(emptyList())
        hosts.forEach { h -> h.ports.forEach { assertFalse(it.service.contains("root:")) } }
    }

    @Test fun filterByPortServiceState() {
        val hosts = NmapXmlHosts.parse(fx("nmap-os.xml"))
        assertEquals(1, HostLogic.apply(hosts, HostFilter(port = 3306)).size)
        assertEquals(1, HostLogic.apply(hosts, HostFilter(service = "ssh")).size)
        assertEquals(2, HostLogic.apply(hosts, HostFilter(state = "up")).size)
        assertEquals(1, HostLogic.apply(hosts, HostFilter(state = "down")).size)
        assertEquals(2, HostLogic.apply(hosts, HostFilter(state = "open")).size)
        assertEquals(1, HostLogic.apply(hosts, HostFilter(text = "Linux")).size)
    }

    @Test fun draftFindingFromHostAndPort() {
        val h = NmapXmlHosts.parse(fx("nmap-os.xml")).first { it.address == "10.0.0.5" }
        val hostFinding = HostLogic.draftFinding(7, h)
        assertEquals(7, hostFinding.engagementId)
        assertTrue(hostFinding.title.contains("10.0.0.5"))
        assertTrue(hostFinding.tags.contains("nmap"))
        assertEquals("10.0.0.5", hostFinding.target)
        assertTrue(hostFinding.evidence.contains("Linux 5.15"))
        val portFinding = HostLogic.draftFinding(7, h, h.openPorts.first { it.port == 3306 })
        assertTrue(portFinding.title.contains("3306"))
        assertTrue(portFinding.tags.contains("mysql"))
        assertTrue(portFinding.evidence.contains("mysql"))
    }

    @Test fun snapshotRoundTrip() {
        val hosts = NmapXmlHosts.parse(fx("nmap-os.xml"))
        val snap = DiscoverySnapshot(1, "10.0.0.0/30", 42, "nmap -sV", hosts)
        assertEquals(snap, HostLogic.fromJson(HostLogic.toJson(snap)))
    }

    @Test fun guardRequiresAuthAndScope() {
        assertTrue(NetworkCommands.guard(null, "10.0.0.5") is RunGuard.Blocked)
        assertTrue(NetworkCommands.guard(eng("10.0.0.0/24", authorized = false), "10.0.0.5") is RunGuard.Blocked)
        assertTrue(NetworkCommands.guard(eng("10.0.0.0/24"), "1.2.3.4") is RunGuard.Blocked)
        assertEquals(RunGuard.Ok, NetworkCommands.guard(eng("10.0.0.0/24"), "10.0.0.5"))
        assertEquals(RunGuard.Ok, NetworkCommands.guard(eng("lab.local\n10.0.0.0/24"), "db.lab.local"))
    }

    @Test fun argvIncludesXmlOutput() {
        val argv = NetworkCommands.argv("10.0.0.5", NetworkCommands.Profile.OS)
        assertEquals("nmap", argv.first())
        assertTrue(argv.contains("-oX"))
        assertTrue(argv.contains("-O"))
        assertEquals("10.0.0.5", argv.last())
        assertTrue(NetworkCommands.preview("10.0.0.5").contains("-oX"))
    }
}
