package com.victorious.victordrax

import com.victorious.victordrax.report.Cvss
import com.victorious.victordrax.report.Severity
import org.junit.Assert.*
import org.junit.Test

/** Expected scores from the FIRST CVSS v3.1 calculator / NVD. */
class CvssTest {
    private fun s(v: String) = Cvss.baseScore(v)
    @Test fun knownVectors() {
        assertEquals(9.8, s("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H")!!, 0.0)
        assertEquals(10.0, s("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H")!!, 0.0)
        assertEquals(6.1, s("CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N")!!, 0.0) // reflected XSS
        assertEquals(5.3, s("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N")!!, 0.0)
        assertEquals(7.8, s("CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:H")!!, 0.0)
        assertEquals(8.8, s("CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:H")!!, 0.0)
        assertEquals(0.0, s("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N")!!, 0.0)
        assertEquals(4.6, s("CVSS:3.1/AV:P/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N")!!, 0.0)
    }
    @Test fun roundUpSpec() {
        assertEquals(4.0, Cvss.roundUp(4.0), 0.0); assertEquals(4.1, Cvss.roundUp(4.02), 0.0)
        assertEquals(4.0, Cvss.roundUp(4.000001), 0.0) // spec: float noise below 1e-5 is ignored
    }
    @Test fun invalidAndSeverity() {
        assertNull(s("CVSS:3.1/AV:X/AC:L")); assertNull(s(""))
        assertEquals(Severity.CRITICAL, Cvss.severityOf("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"))
        assertEquals(Severity.MEDIUM, Severity.fromScore(6.1)); assertEquals(Severity.INFO, Severity.fromScore(0.0))
        assertEquals(Severity.HIGH, Severity.fromLabel("high"))
    }
}
