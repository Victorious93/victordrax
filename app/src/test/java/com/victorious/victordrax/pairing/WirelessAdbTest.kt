package com.victorious.victordrax.pairing

import com.victorious.victordrax.pairing.WirelessAdb.Check
import com.victorious.victordrax.pairing.WirelessAdb.HostPort
import org.junit.Assert.*
import org.junit.Test

class WirelessAdbTest {
    @Test fun parsesIpv4HostPort() {
        assertEquals(HostPort("192.168.1.20", 37123), WirelessAdb.parseHostPort(" 192.168.1.20:37123 "))
        assertEquals("192.168.1.20:5555", WirelessAdb.parseHostPort("192.168.1.20:5555").toString())
    }

    @Test fun parsesHostnameAndIpv6() {
        assertEquals(HostPort("pixel.lan", 40001), WirelessAdb.parseHostPort("Pixel.lan:40001"))
        val v6 = WirelessAdb.parseHostPort("[fe80::1]:41000")
        assertEquals(HostPort("fe80::1", 41000), v6)
        assertEquals("[fe80::1]:41000", v6.toString())
    }

    @Test fun rejectsBadHostPort() {
        listOf("", "192.168.1.20", "192.168.1.20:", ":5555", "192.168.1.20:0", "192.168.1.20:65536",
            "192.168.1.256:5555", "1.2.3:5555", "host:12a", "a b:5555", "fe80::1:5555", "[fe80::1]5555",
            "host;rm:5555", "\$(id):5555", "host:123456").forEach {
            assertNull("should reject '$it'", WirelessAdb.parseHostPort(it))
        }
    }

    @Test fun pairingCodeFormat() {
        assertTrue(WirelessAdb.isValidPairingCode("123456"))
        assertEquals("123456", WirelessAdb.normalizeCode("123 456"))
        assertEquals("123456", WirelessAdb.normalizeCode("123-456"))
        listOf("", "12345", "1234567", "12345a", "abcdef", "12 34 5").forEach { assertFalse(it, WirelessAdb.isValidPairingCode(it)) }
    }

    @Test fun validateForm() {
        assertEquals(Check.Ok, WirelessAdb.validate("10.0.0.5:37000", "654321", "10.0.0.5:41000"))
        assertEquals("pair", (WirelessAdb.validate("10.0.0.5", "654321", "10.0.0.5:41000") as Check.Error).field)
        assertEquals("code", (WirelessAdb.validate("10.0.0.5:37000", "6543", "10.0.0.5:41000") as Check.Error).field)
        assertEquals("connect", (WirelessAdb.validate("10.0.0.5:37000", "654321", "x") as Check.Error).field)
        // Android 11+: pairing port and connect port are different.
        assertEquals("connect", (WirelessAdb.validate("10.0.0.5:37000", "654321", "10.0.0.5:37000") as Check.Error).field)
    }

    @Test fun sequenceIsQuotedAndOrdered() {
        val seq = WirelessAdb.sequence(HostPort("10.0.0.5", 37000), "654 321", HostPort("10.0.0.5", 41000))
        assertTrue(seq.startsWith("'adb' 'pair' '10.0.0.5:37000' '654321' && 'adb' 'connect' '10.0.0.5:41000' && 'adb' '-s' '10.0.0.5:41000' 'shell' "))
        assertTrue(seq.contains("libshizuku.so"))
        assertFalse(WirelessAdb.reconnectSequence(HostPort("10.0.0.5", 41000)).contains("pair"))
    }
}
