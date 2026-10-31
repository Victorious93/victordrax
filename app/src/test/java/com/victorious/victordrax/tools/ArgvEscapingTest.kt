package com.victorious.victordrax.tools

import com.victorious.victordrax.terminal.TerminalRequest
import com.victorious.victordrax.terminal.VictorSuiteLauncher
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ArgvEscapingTest {
    private val nasty = listOf("printf", "%s\\n", "a b", "it's", "\$(id)", "`id`", "x;y&z|w", "\"q\"", "*", "\\back")

    private fun bash(script: String): String {
        val p = ProcessBuilder("/bin/bash", "-c", script).redirectErrorStream(true).start()
        return p.inputStream.bufferedReader().readText().also { p.waitFor() }
    }

    @Test fun singleLevelRoundTrip() {
        assumeTrue(File("/bin/bash").exists())
        assertEquals(nasty.drop(2), bash(VictorSuiteLauncher.shJoin(nasty)).lines().dropLast(1))
    }
    @Test fun nestedRoundTrip() {
        assumeTrue(File("/bin/bash").exists())
        val inner = VictorSuiteLauncher.shJoin(nasty)
        val outer = "/bin/bash -c " + VictorSuiteLauncher.shQuote("/bin/bash -lc " + VictorSuiteLauncher.shQuote(inner))
        assertEquals(nasty.drop(2), bash(outer).lines().dropLast(1))
    }
    @Test fun chrootScriptShape() {
        val s = VictorSuiteLauncher.script(TerminalRequest(null, "t", true, "/data/local/andrax", listOf("nmap", "x';id;'")))
        assertTrue(s.startsWith("su -c '")); assertTrue(s.endsWith("'"))
        assertFalse(s.contains("x';id;'"))  // the raw quote must be escaped
    }
    @Test(expected = IllegalArgumentException::class) fun newlineRejected() {
        VictorSuiteLauncher.script(TerminalRequest(null, "t", false, "/x", listOf("nmap", "a\nid")))
    }
}
