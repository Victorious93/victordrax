package com.victorious.victordrax.beef

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for the BeEF-XSS (Browser Exploitation Framework) RESTful API.
 *
 * BeEF is Copyright (c) 2006-2024 Wade Alcorn and the BeEF project, and is distributed
 * under its own BSD-style licence (https://github.com/beefproject/beef). VictorDrax only
 * *talks to* a BeEF instance the operator runs themselves; it bundles none of BeEF's code.
 *
 * This control panel is for AUTHORIZED engagements only. Every destructive/interactive
 * action is gated by [BeefGuard] against the active engagement's authorization and scope.
 *
 * The REST contract used here (BeEF 0.5.x):
 *  - POST /api/admin/login            {username,password}          -> {success, token}
 *  - GET  /api/hooks?token=…                                       -> {hooked-browsers:{online,offline}}
 *  - GET  /api/modules?token=…                                     -> { "<id>": {id,name,category,…}, … }
 *  - POST /api/modules/{session}/{id}?token=…  {options…}          -> {success, command_id}
 *  - GET  /api/modules/{session}/{id}/{cmd}?token=…                -> {success, results/data,…}
 *
 * Parsing and URL/body building are kept as pure functions so they can be unit-tested on the
 * JVM with no device and no live BeEF instance.
 */
object BeefApi {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    const val DEFAULT_HOST = "127.0.0.1"
    const val DEFAULT_PORT = 3000

    // ---- URL building -------------------------------------------------------------------------

    /** Normalised base, e.g. http://127.0.0.1:3000 (no trailing slash). */
    fun baseUrl(host: String, port: Int): String = "http://${host.trim()}:$port"

    fun loginUrl(base: String) = "${base.trimEnd('/')}/api/admin/login"
    fun hooksUrl(base: String, token: String) = "${base.trimEnd('/')}/api/hooks?token=$token"
    fun modulesUrl(base: String, token: String) = "${base.trimEnd('/')}/api/modules?token=$token"
    fun launchUrl(base: String, session: String, moduleId: String, token: String) =
        "${base.trimEnd('/')}/api/modules/$session/$moduleId?token=$token"
    fun resultUrl(base: String, session: String, moduleId: String, commandId: String, token: String) =
        "${base.trimEnd('/')}/api/modules/$session/$moduleId/$commandId?token=$token"

    // ---- Request bodies -----------------------------------------------------------------------

    fun loginBody(username: String, password: String): String =
        json.encodeToString(JsonObject.serializer(), buildObj(mapOf("username" to username, "password" to password)))

    /** Body for POST launch: a flat JSON object of module option name -> string value. */
    fun launchBody(options: Map<String, String>): String =
        json.encodeToString(JsonObject.serializer(), buildObj(options))

    private fun buildObj(m: Map<String, String>): JsonObject =
        JsonObject(m.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })

    // ---- Response parsing ---------------------------------------------------------------------

    class BeefException(message: String) : Exception(message)

    /** Extract the REST token from an /api/admin/login response, or throw on failure. */
    fun parseLoginToken(body: String): String {
        val obj = parseObject(body)
        val success = obj["success"]?.jsonPrimitive?.booleanOrNull
        val token = obj["token"]?.jsonPrimitive?.contentOrNull
        if (success == false || token.isNullOrBlank()) {
            throw BeefException("BeEF login failed — check the username and password.")
        }
        return token
    }

    fun parseHooks(body: String): List<HookedBrowser> {
        val root = parseObject(body)
        val hb = root["hooked-browsers"]?.jsonObject ?: return emptyList()
        val result = ArrayList<HookedBrowser>()
        hb["online"]?.jsonObject?.let { result += it.values.mapNotNull { v -> hookFrom(v.jsonObject, online = true) } }
        hb["offline"]?.jsonObject?.let { result += it.values.mapNotNull { v -> hookFrom(v.jsonObject, online = false) } }
        return result
    }

    private fun hookFrom(o: JsonObject, online: Boolean): HookedBrowser? {
        val session = o.str("session") ?: return null
        return HookedBrowser(
            session = session,
            online = online,
            ip = o.str("ip").orEmpty(),
            browserName = o.str("name").orEmpty(),
            browserVersion = o.str("version").orEmpty(),
            os = o.str("os").orEmpty(),
            osVersion = o.str("os_version").orEmpty(),
            domain = o.str("domain").orEmpty(),
            pageUri = o.str("page_uri").orEmpty(),
        )
    }

    fun parseModules(body: String): List<BeefModule> {
        val root = parseObject(body)
        // BeEF returns either a flat map keyed by id, or {"modules": {...}}
        val map = (root["modules"]?.jsonObject ?: root)
        return map.values.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            BeefModule(
                id = id,
                name = o.str("name").orEmpty().ifBlank { id },
                category = o.str("category").orEmpty(),
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** From a launch response, return the command_id to poll the result with. */
    fun parseLaunch(body: String): String {
        val o = parseObject(body)
        if (o["success"]?.jsonPrimitive?.booleanOrNull == false) throw BeefException("BeEF rejected the module launch.")
        val cmd = o["command_id"]?.jsonPrimitive?.let { it.intOrNull?.toString() ?: it.contentOrNull }
        return cmd ?: throw BeefException("No command_id in BeEF launch response.")
    }

    /** Best-effort human-readable module result. */
    fun parseModuleResult(body: String): String {
        val o = runCatching { parseObject(body) }.getOrNull() ?: return body
        o["results"]?.let { return it.toString() }
        o["data"]?.jsonPrimitive?.contentOrNull?.let { return it }
        return o.toString()
    }

    private fun parseObject(body: String): JsonObject =
        runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw BeefException("Unexpected response from BeEF (not JSON).") }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

/** A browser currently (or previously) hooked by the BeEF hook.js. */
data class HookedBrowser(
    val session: String,
    val online: Boolean,
    val ip: String,
    val browserName: String,
    val browserVersion: String,
    val os: String,
    val osVersion: String,
    val domain: String,
    val pageUri: String,
) {
    val browserLabel: String get() = listOf(browserName, browserVersion).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unknown browser" }
    val osLabel: String get() = listOf(os, osVersion).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unknown OS" }
    /** The host this browser is hooked on, used for scope checks (domain preferred, else IP). */
    val scopeHost: String get() = domain.ifBlank { ip }
}

/** A BeEF command module. */
data class BeefModule(val id: String, val name: String, val category: String)
