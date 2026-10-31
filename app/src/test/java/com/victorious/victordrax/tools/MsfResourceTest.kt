package com.victorious.victordrax.tools

import com.victorious.victordrax.tools.spec.MsfResource
import com.victorious.victordrax.tools.spec.MsfResource.Options
import org.junit.Assert.*
import org.junit.Test

class MsfResourceTest {
    private val scope = listOf("10.0.0.0/24")
    @Test fun buildsScript() {
        val r = MsfResource.build(Options("auxiliary/scanner/ssh/ssh_version", "10.0.0.4", rport = "22"), scope)
        r as MsfResource.Result.Ok
        assertEquals(listOf("use auxiliary/scanner/ssh/ssh_version", "set RHOSTS 10.0.0.4", "set RPORT 22"), r.lines.take(3))
        assertEquals(listOf("msfconsole", "-q", "-x"), r.argv.take(3))
    }
    @Test fun outOfScope() { assertTrue(MsfResource.build(Options("exploit/x/y", "8.8.8.8"), scope) is MsfResource.Result.Err) }
    @Test fun injection() {
        assertTrue(MsfResource.build(Options("exploit/x; exit", "10.0.0.4"), scope) is MsfResource.Result.Err)
        assertTrue(MsfResource.build(Options("exploit/x/y", "10.0.0.4", rport = "22; run"), scope) is MsfResource.Result.Err)
        assertTrue(MsfResource.build(Options("exploit/x/y", "10.0.0.4", payload = "a;b"), scope) is MsfResource.Result.Err)
    }
}
