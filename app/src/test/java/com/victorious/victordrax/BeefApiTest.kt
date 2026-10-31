package com.victorious.victordrax

import com.victorious.victordrax.beef.BeefApi
import org.junit.Assert.*
import org.junit.Test

class BeefApiTest {
    @Test fun buildsUrls() {
        val base = BeefApi.baseUrl("127.0.0.1", 3000)
        assertEquals("http://127.0.0.1:3000", base)
        assertEquals("http://127.0.0.1:3000/api/admin/login", BeefApi.loginUrl(base))
        assertEquals("http://127.0.0.1:3000/api/hooks?token=abc", BeefApi.hooksUrl(base, "abc"))
        assertEquals("http://127.0.0.1:3000/api/modules?token=abc", BeefApi.modulesUrl(base, "abc"))
        assertEquals("http://127.0.0.1:3000/api/modules/S1/42?token=abc", BeefApi.launchUrl(base, "S1", "42", "abc"))
        assertEquals("http://127.0.0.1:3000/api/modules/S1/42/7?token=abc", BeefApi.resultUrl(base, "S1", "42", "7", "abc"))
    }

    @Test fun baseUrlTrimsTrailingSlash() {
        assertEquals("http://h:1/api/hooks?token=t", BeefApi.hooksUrl("http://h:1/", "t"))
    }

    @Test fun loginBodyRoundTrips() {
        val body = BeefApi.loginBody("beef", "s3cr3t")
        assertTrue(body.contains("\"username\":\"beef\""))
        assertTrue(body.contains("\"password\":\"s3cr3t\""))
    }

    @Test fun parsesLoginToken() {
        val token = BeefApi.parseLoginToken("""{"success":true,"token":"deadbeef00"}""")
        assertEquals("deadbeef00", token)
    }

    @Test fun loginFailureThrows() {
        assertThrows(BeefApi.BeefException::class.java) {
            BeefApi.parseLoginToken("""{"success":false}""")
        }
        assertThrows(BeefApi.BeefException::class.java) {
            BeefApi.parseLoginToken("""{"success":true,"token":""}""")
        }
    }

    @Test fun nonJsonThrows() {
        assertThrows(BeefApi.BeefException::class.java) { BeefApi.parseLoginToken("<html>nope</html>") }
    }

    @Test fun parsesHooksOnlineAndOffline() {
        val json = """
          {"hooked-browsers":{
            "online":{"0":{"session":"sess-a","ip":"10.0.0.5","name":"Firefox","version":"120","os":"Linux","domain":"app.example.com","page_uri":"https://app.example.com/login"}},
            "offline":{"1":{"session":"sess-b","ip":"10.0.0.6","name":"Chrome","os":"Windows","domain":"old.example.com"}}
          }}
        """.trimIndent()
        val hooks = BeefApi.parseHooks(json)
        assertEquals(2, hooks.size)
        val a = hooks.first { it.session == "sess-a" }
        assertTrue(a.online)
        assertEquals("Firefox 120", a.browserLabel)
        assertEquals("app.example.com", a.scopeHost)
        assertEquals("10.0.0.5", a.ip)
        val b = hooks.first { it.session == "sess-b" }
        assertFalse(b.online)
        assertEquals("old.example.com", b.scopeHost)
    }

    @Test fun scopeHostFallsBackToIp() {
        val hooks = BeefApi.parseHooks("""{"hooked-browsers":{"online":{"0":{"session":"s","ip":"192.168.1.9"}}}}""")
        assertEquals("192.168.1.9", hooks.single().scopeHost)
    }

    @Test fun emptyHooks() {
        assertTrue(BeefApi.parseHooks("""{"hooked-browsers":{"online":{},"offline":{}}}""").isEmpty())
        assertTrue(BeefApi.parseHooks("""{}""").isEmpty())
    }

    @Test fun parsesModules() {
        val json = """{"1":{"id":"1","name":"Alert Dialog","category":"Misc"},"2":{"id":"2","name":"Get Cookie","category":"Host"}}"""
        val mods = BeefApi.parseModules(json)
        assertEquals(2, mods.size)
        assertEquals("Alert Dialog", mods.first().name) // sorted by name
        assertEquals("Host", mods.first { it.id == "2" }.category)
    }

    @Test fun parsesLaunchCommandId() {
        assertEquals("7", BeefApi.parseLaunch("""{"success":true,"command_id":7}"""))
        assertEquals("9", BeefApi.parseLaunch("""{"command_id":"9"}"""))
    }

    @Test fun launchFailureThrows() {
        assertThrows(BeefApi.BeefException::class.java) { BeefApi.parseLaunch("""{"success":false}""") }
        assertThrows(BeefApi.BeefException::class.java) { BeefApi.parseLaunch("""{"success":true}""") }
    }

    @Test fun launchBodyEncodesOptions() {
        val body = BeefApi.launchBody(mapOf("target" to "https://x", "count" to "3"))
        assertTrue(body.contains("\"target\":\"https://x\""))
        assertTrue(body.contains("\"count\":\"3\""))
    }
}
