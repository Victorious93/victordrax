package com.victorious.victordrax.capture

import com.victorious.victordrax.terminal.VictorSuiteLauncher
import org.junit.Assert.*
import org.junit.Test

class RunCaptureTest {
    @Test fun parsesBodyAndExitCode() {
        val raw = "line one\nline two\n${RunCapture.EXIT_MARKER} 0\n"
        val p = RunCapture.parse(raw)
        assertEquals("line one\nline two", p.output)
        assertEquals(0, p.exitCode)
        assertTrue(p.finished)
    }

    @Test fun parsesNonZeroExit() {
        val p = RunCapture.parse("boom\n${RunCapture.EXIT_MARKER} 2")
        assertEquals("boom", p.output)
        assertEquals(2, p.exitCode)
        assertTrue(p.finished)
    }

    @Test fun runningLogHasNoSentinel() {
        val p = RunCapture.parse("partial output so far\n")
        assertEquals("partial output so far", p.output)
        assertNull(p.exitCode)
        assertFalse(p.finished)
    }

    @Test fun emptyLog() {
        val p = RunCapture.parse("")
        assertEquals("", p.output); assertNull(p.exitCode); assertFalse(p.finished)
    }

    @Test fun nonNumericExitIsNull() {
        val p = RunCapture.parse("x\n${RunCapture.EXIT_MARKER} oops")
        assertTrue(p.finished); assertNull(p.exitCode); assertEquals("x", p.output)
    }

    @Test fun fileNameStableForRun() {
        assertEquals("run-42.log", RunCapture.fileName(42))
    }

    @Test fun fileForJoinsPath() {
        assertEquals("/sdcard/VictorDrax/runs/run-7.log", RunOutputStore.fileFor("/sdcard/VictorDrax/runs/", 7))
        assertEquals("/sdcard/VictorDrax/runs/run-7.log", RunOutputStore.fileFor("/sdcard/VictorDrax/runs", 7))
    }

    @Test fun captureWrapTeesAndAppendsSentinel() {
        val w = VictorSuiteLauncher.captureWrap("nmap -sV 10.0.0.5", "/sdcard/VictorDrax/runs/run-1.log")
        assertTrue(w.contains("| tee '/sdcard/VictorDrax/runs/run-1.log'"))
        assertTrue(w.contains("2>&1"))
        assertTrue(w.contains(RunCapture.EXIT_MARKER))
        assertTrue(w.contains("\${PIPESTATUS[0]}"))
    }

    @Test fun launcherScriptWrapsWhenCaptureSet() {
        val r = com.victorious.victordrax.terminal.TerminalRequest(
            command = "whoami", title = "t", chroot = false, chrootPath = "/data/local/andrax",
            captureFile = "/sdcard/VictorDrax/runs/run-5.log")
        val s = VictorSuiteLauncher.script(r)
        assertTrue(s.contains("tee '/sdcard/VictorDrax/runs/run-5.log'"))
        assertTrue(s.contains(RunCapture.EXIT_MARKER))
        assertTrue(s.endsWith("exec bash -l"))
    }

    @Test fun launcherRejectsUnsafeCapturePath() {
        val r = com.victorious.victordrax.terminal.TerminalRequest(
            command = "whoami", title = "t", chroot = false, chrootPath = "/data/local/andrax",
            captureFile = "/sdcard/../etc/passwd")
        assertThrows(IllegalArgumentException::class.java) { VictorSuiteLauncher.script(r) }
    }

    @Test fun roundTripParsesWhatWrapWouldProduce() {
        // Simulate tee output: body then the sentinel line the shell appends.
        val body = "host up\nport 22 open"
        val raw = body + "\n" + RunCapture.EXIT_MARKER + " 0"
        val p = RunCapture.parse(raw)
        assertEquals(body, p.output); assertEquals(0, p.exitCode)
    }
}
