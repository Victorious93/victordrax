package com.victorious.victordrax.beef

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal HTTP transport abstraction so [BeefClient] can be unit-tested with a fake engine
 * (no network / no device). The production implementation is [OkHttpEngine].
 */
interface HttpEngine {
    data class Resp(val code: Int, val body: String)
    /** Perform a request. [body] non-null implies POST with application/json. */
    fun request(method: String, url: String, body: String?): Resp
}

class OkHttpEngine(connectTimeoutSec: Long = 10, readTimeoutSec: Long = 15) : HttpEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override fun request(method: String, url: String, body: String?): HttpEngine.Resp {
        val builder = Request.Builder().url(url)
        if (method.equals("POST", true)) builder.post((body ?: "{}").toRequestBody(jsonMedia)) else builder.get()
        client.newCall(builder.build()).execute().use { resp ->
            return HttpEngine.Resp(resp.code, resp.body?.string().orEmpty())
        }
    }
}

/**
 * High-level BeEF REST client. Holds the REST token IN MEMORY ONLY (never persisted).
 * All blocking I/O is dispatched to [Dispatchers.IO]. Pure request/response handling lives
 * in [BeefApi], which is unit-tested directly.
 */
class BeefClient(
    private val base: String,
    private val engine: HttpEngine = OkHttpEngine(),
) {
    @Volatile var token: String? = null
        private set

    val isAuthenticated: Boolean get() = !token.isNullOrBlank()

    private fun requireToken(): String = token ?: throw BeefApi.BeefException("Not connected to BeEF — log in first.")

    suspend fun login(username: String, password: String): String = withContext(Dispatchers.IO) {
        val resp = engine.request("POST", BeefApi.loginUrl(base), BeefApi.loginBody(username, password))
        if (resp.code == 401 || resp.code == 403) throw BeefApi.BeefException("BeEF rejected the credentials (HTTP ${resp.code}).")
        val t = BeefApi.parseLoginToken(resp.body)
        token = t
        t
    }

    fun logout() { token = null }

    suspend fun hooks(): List<HookedBrowser> = withContext(Dispatchers.IO) {
        val resp = engine.request("GET", BeefApi.hooksUrl(base, requireToken()), null)
        BeefApi.parseHooks(resp.body)
    }

    suspend fun modules(): List<BeefModule> = withContext(Dispatchers.IO) {
        val resp = engine.request("GET", BeefApi.modulesUrl(base, requireToken()), null)
        BeefApi.parseModules(resp.body)
    }

    /** Launch [moduleId] against [session] with [options]; returns the command id. */
    suspend fun launchModule(session: String, moduleId: String, options: Map<String, String>): String = withContext(Dispatchers.IO) {
        val resp = engine.request("POST", BeefApi.launchUrl(base, session, moduleId, requireToken()), BeefApi.launchBody(options))
        BeefApi.parseLaunch(resp.body)
    }

    suspend fun moduleResult(session: String, moduleId: String, commandId: String): String = withContext(Dispatchers.IO) {
        val resp = engine.request("GET", BeefApi.resultUrl(base, session, moduleId, commandId, requireToken()), null)
        BeefApi.parseModuleResult(resp.body)
    }
}
