package com.victorious.andrax

import com.victorious.andrax.data.Validation
import com.victorious.andrax.terminal.TerminalRequest
import com.victorious.andrax.terminal.VictorSuiteLauncher
import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun paths() {
        assertTrue(Validation.isSafePath("/data/local/andrax"))
        assertFalse(Validation.isSafePath("/data/local/x; rm -rf /"))
        assertFalse(Validation.isSafePath("/data/../system"))
        assertFalse(Validation.isSafePath("relative/path"))
        assertFalse(Validation.isSafePath("/data/\$(id)"))
    }
    @Test fun urls() {
        assertTrue(Validation.isHttpsUrl("https://example.org/rootfs.tar.xz"))
        assertFalse(Validation.isHttpsUrl("http://example.org/rootfs.tar.xz"))
        assertFalse(Validation.isHttpsUrl("file:///sdcard/x.tar"))
    }
    @Test fun tarFlags() {
        assertEquals("-xJpf", Validation.tarFlagsFor("https://h/a.tar.xz?dl=1"))
        assertEquals("-xzpf", Validation.tarFlagsFor("a.TGZ"))
        assertEquals("-xpf", Validation.tarFlagsFor("a.tar"))
        assertNull(Validation.tarFlagsFor("a.zip"))
    }
    @Test fun vncDisplay() { assertTrue(Validation.isVncDisplay(":1")); assertFalse(Validation.isVncDisplay("1;id")) }

    @Test fun victorSuiteScriptLocal() =
        assertEquals("nmap --help; exec bash -l", VictorSuiteLauncher.script(TerminalRequest("nmap --help", "t", false, "/data/local/andrax")))

    @Test fun victorSuiteScriptChroot() {
        val s = VictorSuiteLauncher.script(TerminalRequest("nmap --help", "t", true, "/data/local/andrax"))
        assertTrue(s.startsWith("su -c '"))
        assertTrue(s.contains("chroot /data/local/andrax"))
        assertTrue(s.endsWith("/bin/bash -lc \"nmap --help; exec /bin/bash -l\"'"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun victorSuiteRejectsInjection() { VictorSuiteLauncher.script(TerminalRequest("nmap'; reboot", "t", true, "/data/local/andrax")) }

    @Test fun victorSuiteIntentExtras() {
        // Intent is an Android class; only check constants here (intent built in instrumented use).
        assertEquals("com.termux", VictorSuiteLauncher.PACKAGE)
        assertEquals("com.termux.RUN_COMMAND", VictorSuiteLauncher.ACTION_RUN_COMMAND)
    }
}
