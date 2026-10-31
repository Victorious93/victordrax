package com.victorious.victordrax.terminal

import com.victorious.victordrax.privilege.Privilege
import com.victorious.victordrax.privilege.PrivilegeBackend
import com.victorious.victordrax.privilege.PrivilegeMode
import com.victorious.victordrax.privilege.PrivilegeResolver
import com.victorious.victordrax.shizuku.ShizukuStatus
import org.junit.Assert.*
import org.junit.Test

class TerminalPrivilegeTest {
    private val path = "/data/local/andrax"
    private fun snap(su: Boolean, shizuku: Boolean, uid: Int, mode: PrivilegeMode = PrivilegeMode.AUTO): Privilege.Snapshot {
        val b = PrivilegeResolver.resolve(mode, su, shizuku)
        return Privilege.Snapshot(mode, b, su, if (shizuku) ShizukuStatus.GRANTED else ShizukuStatus.NOT_RUNNING,
            if (shizuku) uid else -1, PrivilegeResolver.canChroot(b, uid.takeIf { shizuku }))
    }

    @Test fun rootKeepsSu() {
        val s = VictorSuiteLauncher.script(requestFor(snap(true, true, 2000), true, path, null, "t", listOf("nmap", "-sV")))
        assertTrue(s.startsWith("su -c '")); assertFalse(s.contains("rish"))
    }

    @Test fun suiUsesRishAndChroots() {
        val r = requestFor(snap(false, true, 0), true, path, "nmap --help", "t")
        assertEquals(PrivilegeBackend.SHIZUKU, r.backend); assertTrue(r.chroot)
        val s = VictorSuiteLauncher.script(r)
        assertFalse(s.contains("su -c")); assertTrue(s.contains("rish -c '")); assertTrue(s.contains("chroot $path"))
        assertTrue(s.startsWith("command -v rish"))
    }

    @Test fun shizukuModeOverridesSu() {
        val r = requestFor(snap(true, true, 0, PrivilegeMode.SHIZUKU), true, path, null, "t")
        assertTrue(VictorSuiteLauncher.script(r).contains("rish -c"))
    }

    @Test fun adbShizukuChrootBlocked() {
        val r = requestFor(snap(false, true, 2000), true, path, null, "t", listOf("nmap"))
        val e = runCatching { VictorSuiteLauncher.script(r) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
        assertEquals(VictorSuiteLauncher.ADB_SHIZUKU_NO_CHROOT, e!!.message)
    }

    @Test fun adbShizukuHostSessionStillWorks() {
        val r = requestFor(snap(false, true, 2000), false, path, null, "t", listOf("nmap"))
        assertEquals("'nmap'; exec bash -l", VictorSuiteLauncher.script(r))
    }

    @Test fun unprivilegedNoWrapper() {
        val r = requestFor(snap(true, true, 0, PrivilegeMode.NONE), true, path, "nmap --help", "t")
        assertFalse(r.chroot); assertEquals("nmap --help; exec bash -l", VictorSuiteLauncher.script(r))
    }

    @Test fun rishWrapperIsQuoteSafe() {
        val s = VictorSuiteLauncher.elevate(PrivilegeBackend.SHIZUKU, "echo 'x'")
        assertTrue(s.endsWith("rish -c 'echo '\\''x'\\'''"))
    }
}
