package com.victorious.victordrax.mitm

import com.victorious.victordrax.db.Finding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One captured HTTP(S) flow shown in the MITM console. */
@Serializable
data class MitmFlow(
    val method: String = "GET",
    val host: String = "",
    val path: String = "/",
    val status: Int = 0,
    val contentType: String = "",
    val requestHeaders: String = "",
    val responseHeaders: String = "",
    val requestBodyPreview: String = "",
    val responseBodyPreview: String = "",
    val scheme: String = "http",
    val timestamp: Long = 0,
) {
    val url: String get() = "$scheme://$host$path"
    val statusLabel: String get() = if (status > 0) status.toString() else "—"
    val summary: String get() = "$method $host$path → $statusLabel"
}

data class FlowFilter(
    val text: String = "",
    val host: String = "",
    val method: String = "",
    val status: Int? = null,
)

object MitmFlowLogic {
    fun matches(f: MitmFlow, q: FlowFilter): Boolean {
        if (q.host.isNotBlank() && !f.host.contains(q.host.trim(), true)) return false
        if (q.method.isNotBlank() && !f.method.equals(q.method.trim(), true)) return false
        if (q.status != null && f.status != q.status) return false
        val t = q.text.trim().lowercase()
        if (t.isNotEmpty()) {
            val hay = listOf(f.method, f.host, f.path, f.contentType, f.statusLabel, f.requestHeaders, f.responseHeaders)
                .joinToString(" ").lowercase()
            if (!hay.contains(t)) return false
        }
        return true
    }

    fun apply(flows: List<MitmFlow>, q: FlowFilter): List<MitmFlow> =
        flows.filter { matches(it, q) }

    fun draftFinding(engagementId: Long, flow: MitmFlow): Finding = Finding(
        engagementId = engagementId,
        title = "Intercepted ${flow.method} ${flow.host}${flow.path} → ${flow.statusLabel}",
        severity = "Info",
        description = "HTTP(S) flow captured during an authorized MITM proxy session on the operator's own test traffic.",
        target = flow.host,
        tags = "mitm,proxy,http",
        evidence = buildString {
            append(flow.summary).append('\n')
            append("URL: ").append(flow.url).append('\n')
            if (flow.contentType.isNotBlank()) append("Content-Type: ").append(flow.contentType).append('\n')
            if (flow.requestHeaders.isNotBlank()) append("\nRequest headers:\n").append(flow.requestHeaders.trimEnd()).append('\n')
            if (flow.requestBodyPreview.isNotBlank()) append("\nRequest body (preview):\n").append(flow.requestBodyPreview.trimEnd()).append('\n')
            if (flow.responseHeaders.isNotBlank()) append("\nResponse headers:\n").append(flow.responseHeaders.trimEnd()).append('\n')
            if (flow.responseBodyPreview.isNotBlank()) append("\nResponse body (preview):\n").append(flow.responseBodyPreview.trimEnd()).append('\n')
        }.trimEnd(),
        status = "OPEN",
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun toJson(list: List<MitmFlow>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MitmFlow.serializer()), list)
    fun fromJson(s: String): List<MitmFlow> = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MitmFlow.serializer()), s)
}

/**
 * Parsers for flow dumps the operator pastes/imports after a mitmdump session.
 *
 * Supported formats (auto-detected):
 *  1. JSONL — one JSON object per line (preferred; produced by [MitmCommands.exportJsonl]).
 *  2. JSON array of flow objects.
 *  3. mitmdump flow_detail≈1 text log (`>> GET http://host/path` / `<< 200 …`).
 */
object MitmFlowParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<MitmFlow> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        // JSON array
        if (trimmed.startsWith("[")) {
            return runCatching { MitmFlowLogic.fromJson(trimmed) }.getOrElse { parseJsonl(trimmed) }
        }
        // JSONL (first non-empty line starts with '{')
        val first = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (first.startsWith("{")) return parseJsonl(trimmed)
        return parseTextLog(trimmed)
    }

    fun parseJsonl(text: String): List<MitmFlow> {
        val out = ArrayList<MitmFlow>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || !line.startsWith("{")) continue
            runCatching {
                val o = json.parseToJsonElement(line).jsonObject
                out += fromObject(o)
            }
        }
        return out
    }

    private fun fromObject(o: JsonObject): MitmFlow {
        fun str(vararg keys: String): String {
            for (k in keys) {
                val v = o[k] ?: continue
                val p = v as? JsonPrimitive ?: continue
                return p.contentOrNull ?: ""
            }
            return ""
        }
        fun int(vararg keys: String): Int {
            for (k in keys) {
                val v = o[k] ?: continue
                val p = v as? JsonPrimitive ?: continue
                p.intOrNull?.let { return it }
                p.contentOrNull?.toIntOrNull()?.let { return it }
            }
            return 0
        }
        val url = str("url")
        var host = str("host", "hostname")
        var path = str("path", "url_path")
        var scheme = str("scheme", "protocol").ifBlank { "http" }
        if (host.isBlank() && url.isNotBlank()) {
            val m = Regex("""^(https?)://([^/?#]+)([^?#]*)?""", RegexOption.IGNORE_CASE).find(url)
            if (m != null) {
                scheme = m.groupValues[1].lowercase()
                host = m.groupValues[2]
                path = m.groupValues[3].ifBlank { "/" }
            }
        }
        if (path.isBlank()) path = "/"
        return MitmFlow(
            method = str("method", "http_method").ifBlank { "GET" }.uppercase(),
            host = host,
            path = path,
            status = int("status", "status_code", "code"),
            contentType = str("content_type", "contentType", "type"),
            requestHeaders = str("request_headers", "req_headers", "requestHeaders"),
            responseHeaders = str("response_headers", "resp_headers", "responseHeaders"),
            requestBodyPreview = str("request_body", "req_body", "requestBody").take(4000),
            responseBodyPreview = str("response_body", "resp_body", "responseBody").take(4000),
            scheme = scheme.lowercase().removePrefix("http://").let { if (it == "https" || it == "http") it else "http" },
            timestamp = int("timestamp", "ts", "time").toLong(),
        )
    }

    // >> GET http://example.com/path
    // << 200 OK 123b application/json
    private val REQ = Regex("""^>{0,2}\s*(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|CONNECT|TRACE)\s+(https?://\S+)""", RegexOption.IGNORE_CASE)
    private val REQ2 = Regex("""^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+(https?://\S+)""", RegexOption.IGNORE_CASE)
    private val RESP = Regex("""^<{0,2}\s*(\d{3})\b(?:.*\b([\w.+/-]+/[\w.+/-]+))?""")

    fun parseTextLog(text: String): List<MitmFlow> {
        val out = ArrayList<MitmFlow>()
        var cur: MitmFlow? = null
        fun flush() { cur?.let { out += it }; cur = null }
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val req = REQ.find(line) ?: REQ2.find(line)
            if (req != null) {
                flush()
                val method = req.groupValues[1].uppercase()
                val url = req.groupValues[2]
                val m = Regex("""^(https?)://([^/?#]+)([^?#]*)?""", RegexOption.IGNORE_CASE).find(url)
                cur = MitmFlow(
                    method = method,
                    scheme = m?.groupValues?.get(1)?.lowercase() ?: "http",
                    host = m?.groupValues?.get(2) ?: "",
                    path = m?.groupValues?.get(3)?.ifBlank { "/" } ?: "/",
                )
                continue
            }
            val resp = RESP.find(line)
            if (resp != null && cur != null) {
                cur = cur!!.copy(
                    status = resp.groupValues[1].toIntOrNull() ?: 0,
                    contentType = resp.groupValues.getOrElse(2) { "" },
                )
            }
        }
        flush()
        return out.filter { it.host.isNotBlank() }
    }
}
