package com.victorious.victordrax.mitm

import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.workflow.RunGuard
import org.junit.Assert.*
import org.junit.Test

class MitmConsoleTest {
    private fun fx(n: String) = javaClass.classLoader!!.getResource("fixtures/$n")!!.readText()

    private fun eng(scope: String, authorized: Boolean = true) = Engagement(
        id = 1, name = "Lab", client = "Self", scope = scope,
        authorizedBy = if (authorized) "Don" else "",
        authorizedDate = if (authorized) 1L else 0L,
        authDocUri = if (authorized) "content://auth/1" else "",
    )

    @Test fun parseJsonl() {
        val flows = MitmFlowParser.parse(fx("mitm-flows.jsonl"))
        assertEquals(3, flows.size)
        assertEquals("GET", flows[0].method)
        assertEquals("api.lab.local", flows[0].host)
        assertEquals("/v1/health", flows[0].path)
        assertEquals(200, flows[0].status)
        assertEquals("application/json", flows[0].contentType)
        assertTrue(flows[0].responseBodyPreview.contains("ok"))
        assertEquals(401, flows[1].status)
        assertEquals("POST", flows[1].method)
    }

    @Test fun parseTextLog() {
        val flows = MitmFlowParser.parse(fx("mitm-flows.txt"))
        assertEquals(3, flows.size)
        assertEquals("api.lab.local", flows[0].host)
        assertEquals(200, flows[0].status)
        assertEquals("application/json", flows[0].contentType)
        assertEquals("POST", flows[1].method)
        assertEquals(401, flows[1].status)
        assertEquals("static.lab.local", flows[2].host)
    }

    @Test fun parseJsonArray() {
        val arr = """[{"method":"GET","host":"a.test","path":"/","status":204,"scheme":"https"}]"""
        val flows = MitmFlowParser.parse(arr)
        assertEquals(1, flows.size)
        assertEquals(204, flows[0].status)
        assertEquals("https://a.test/", flows[0].url)
    }

    @Test fun filterByHostMethodStatus() {
        val flows = MitmFlowParser.parse(fx("mitm-flows.jsonl"))
        assertEquals(2, MitmFlowLogic.apply(flows, FlowFilter(host = "api.lab")).size)
        assertEquals(1, MitmFlowLogic.apply(flows, FlowFilter(method = "POST")).size)
        assertEquals(1, MitmFlowLogic.apply(flows, FlowFilter(status = 401)).size)
        assertEquals(1, MitmFlowLogic.apply(flows, FlowFilter(text = "login")).size)
    }

    @Test fun draftFinding() {
        val f = MitmFlowParser.parse(fx("mitm-flows.jsonl"))[1]
        val finding = MitmFlowLogic.draftFinding(9, f)
        assertEquals(9, finding.engagementId)
        assertTrue(finding.title.contains("POST"))
        assertTrue(finding.title.contains("401"))
        assertEquals("api.lab.local", finding.target)
        assertTrue(finding.tags.contains("mitm"))
        assertTrue(finding.evidence.contains("/v1/login"))
    }

    @Test fun roundTrip() {
        val flows = MitmFlowParser.parse(fx("mitm-flows.jsonl"))
        assertEquals(flows, MitmFlowLogic.fromJson(MitmFlowLogic.toJson(flows)))
    }

    @Test fun configValidation() {
        assertTrue(MitmConfig().isValid)
        assertTrue(MitmConfig.isValidPort(8080))
        assertFalse(MitmConfig.isValidPort(0))
        assertFalse(MitmConfig.isValidPort(70000))
        assertTrue(MitmConfig.isValidHost("0.0.0.0"))
        assertTrue(MitmConfig.isValidHost("10.0.0.5"))
        assertFalse(MitmConfig.isValidHost(""))
        assertFalse(MitmConfig(listenPort = -1).isValid)
    }

    @Test fun guardAndScope() {
        assertTrue(MitmCommands.guard(null) is RunGuard.Blocked)
        assertTrue(MitmCommands.guard(eng("*.lab.local", authorized = false)) is RunGuard.Blocked)
        assertEquals(RunGuard.Ok, MitmCommands.guard(eng("*.lab.local")))
        val flow = MitmFlow(host = "api.lab.local", path = "/")
        assertTrue(MitmCommands.flowInScope(eng("*.lab.local\napi.lab.local"), flow))
        assertFalse(MitmCommands.flowInScope(eng("other.example"), flow))
    }

    @Test fun startStopCommands() {
        val cfg = MitmConfig(listenPort = 8888)
        val start = MitmCommands.start(cfg)
        assertTrue(start.contains("mitmdump"))
        assertTrue(start.contains("-p 8888"))
        assertTrue(start.contains("-w "))
        assertTrue(MitmCommands.stop().contains("pkill"))
        val exp = MitmCommands.exportReadable(cfg)
        assertTrue(exp.contains("--flow-detail"))
        assertTrue(exp.contains("tee"))
    }
}
