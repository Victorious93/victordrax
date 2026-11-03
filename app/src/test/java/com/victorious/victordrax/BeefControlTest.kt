package com.victorious.victordrax

import com.victorious.victordrax.beef.BeefClient
import com.victorious.victordrax.beef.BeefCommands
import com.victorious.victordrax.beef.BeefConfig
import com.victorious.victordrax.beef.BeefGuard
import com.victorious.victordrax.beef.HookedBrowser
import com.victorious.victordrax.beef.HttpEngine
import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.workflow.RunGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BeefControlTest {
    private fun eng(auth: Boolean, scope: String) = Engagement(
        id = 1, name = "E", client = "C", scope = scope,
        authorizedBy = if (auth) "CISO" else "", authorizedDate = if (auth) 1L else 0L,
        authDocUri = if (auth) "content://doc" else "",
    )
    private fun hook(domain: String) = HookedBrowser("s", true, "", "FF", "1", "Linux", "", domain, "https://$domain/")

    @Test fun serverGuardRequiresAuthorizedEngagement() {
        assertTrue(BeefGuard.guardServer(null) is RunGuard.Blocked)
        assertTrue(BeefGuard.guardServer(eng(false, "example.com")) is RunGuard.Blocked)
        assertEquals(RunGuard.Ok, BeefGuard.guardServer(eng(true, "example.com")))
    }

    @Test fun moduleGuardChecksScope() {
        val e = eng(true, "example.com\n10.0.0.0/24")
        assertEquals(RunGuard.Ok, BeefGuard.guardModule(e, hook("app.example.com")))
        assertTrue(BeefGuard.guardModule(e, hook("evil.com")) is RunGuard.Blocked)
        assertTrue(BeefGuard.guardModule(e, null) is RunGuard.Blocked)
        assertTrue(BeefGuard.guardModule(eng(false, "example.com"), hook("app.example.com")) is RunGuard.Blocked)
    }

    @Test fun moduleGuardIpScope() {
        val e = eng(true, "10.0.0.0/24")
        val h = HookedBrowser("s", true, "10.0.0.9", "FF", "1", "Linux", "", "", "")
        assertEquals(RunGuard.Ok, BeefGuard.guardModule(e, h))
        val out = HookedBrowser("s", true, "10.0.1.9", "FF", "1", "Linux", "", "", "")
        assertTrue(BeefGuard.guardModule(e, out) is RunGuard.Blocked)
    }

    @Test fun commandBuilders() {
        assertEquals("cd /data/local/andrax/beef && ./beef", BeefCommands.start("/data/local/andrax/beef"))
        assertTrue(BeefCommands.stop().contains("pkill"))
        assertEquals("cat /data/local/andrax/beef/config.yaml", BeefCommands.showConfig("/data/local/andrax/beef"))
        assertThrows(IllegalArgumentException::class.java) { BeefCommands.start("/evil; rm -rf /") }
        val steps = BeefCommands.installSteps("/data/local/andrax/beef")
        assertTrue(steps.any { it.command.contains("git clone https://github.com/beefproject/beef") })
        assertTrue(steps.any { it.command.contains("bundle install") })
    }

    @Test fun configValidation() {
        assertTrue(BeefConfig.isValidHost("127.0.0.1"))
        assertTrue(BeefConfig.isValidHost("localhost"))
        assertTrue(BeefConfig.isValidHost("beef.lab.test"))
        assertFalse(BeefConfig.isValidHost(""))
        assertFalse(BeefConfig.isValidHost("bad host!"))
        assertTrue(BeefConfig.isValidPort(3000))
        assertFalse(BeefConfig.isValidPort(0))
        assertFalse(BeefConfig.isValidPort(70000))
        assertEquals("http://127.0.0.1:3000", BeefConfig().baseUrl)
    }

    // Fake transport to exercise the client end-to-end without a network.
    private class FakeEngine(val handler: (String, String, String?) -> HttpEngine.Resp) : HttpEngine {
        val calls = mutableListOf<Triple<String, String, String?>>()
        override fun request(method: String, url: String, body: String?): HttpEngine.Resp {
            calls += Triple(method, url, body); return handler(method, url, body)
        }
    }

    @Test fun clientLoginThenHooks() = runBlocking {
        val engine = FakeEngine { _, url, _ ->
            when {
                url.endsWith("/api/admin/login") -> HttpEngine.Resp(200, """{"success":true,"token":"T0K"}""")
                url.contains("/api/hooks") -> HttpEngine.Resp(200, """{"hooked-browsers":{"online":{"0":{"session":"s1","ip":"127.0.0.1","name":"FF"}}}}""")
                else -> HttpEngine.Resp(404, "{}")
            }
        }
        val c = BeefClient("http://127.0.0.1:3000", engine)
        assertFalse(c.isAuthenticated)
        assertEquals("T0K", c.login("beef", "pw"))
        assertTrue(c.isAuthenticated)
        val hooks = c.hooks()
        assertEquals("s1", hooks.single().session)
        // token carried on the hooks URL
        assertTrue(engine.calls.last().second.contains("token=T0K"))
    }

    @Test fun clientRejects401() = runBlocking {
        val engine = FakeEngine { _, _, _ -> HttpEngine.Resp(401, "unauthorized") }
        val c = BeefClient("http://127.0.0.1:3000", engine)
        try { c.login("beef", "bad"); fail("expected failure") } catch (e: Exception) { /* ok */ }
        assertFalse(c.isAuthenticated)
    }

    @Test fun hooksBeforeLoginThrows() = runBlocking {
        val c = BeefClient("http://127.0.0.1:3000", FakeEngine { _, _, _ -> HttpEngine.Resp(200, "{}") })
        try { c.hooks(); fail("expected") } catch (e: Exception) { /* requires token */ }
    }

    @Test fun launchModuleFlow() = runBlocking {
        val engine = FakeEngine { method, url, _ ->
            when {
                url.endsWith("/api/admin/login") -> HttpEngine.Resp(200, """{"success":true,"token":"T"}""")
                method == "POST" && url.contains("/api/modules/") -> HttpEngine.Resp(200, """{"success":true,"command_id":5}""")
                method == "GET" && url.contains("/api/modules/") -> HttpEngine.Resp(200, """{"success":true,"data":"done"}""")
                else -> HttpEngine.Resp(404, "{}")
            }
        }
        val c = BeefClient("http://127.0.0.1:3000", engine)
        c.login("beef", "pw")
        assertEquals("5", c.launchModule("s1", "42", mapOf("k" to "v")))
        assertEquals("done", c.moduleResult("s1", "42", "5"))
    }
}
