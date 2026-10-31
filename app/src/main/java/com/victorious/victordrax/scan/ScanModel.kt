package com.victorious.victordrax.scan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ScanRow(
    val host: String,
    val hostname: String = "",
    val port: Int = 0,
    val proto: String = "",
    val state: String = "",
    val service: String = "",
    val product: String = "",
    val version: String = "",
) {
    val versionText: String get() = listOf(product, version).filter { it.isNotBlank() }.joinToString(" ")
}

@Serializable
data class ScanTable(val format: String, val rows: List<ScanRow>) {
    fun hosts(): List<String> = rows.map { it.host }.distinct().sorted()

    fun sortedBy(column: String, descending: Boolean): List<ScanRow> {
        val cmp: Comparator<ScanRow> = when (column) {
            "port" -> compareBy { it.port }
            "service" -> compareBy { it.service.lowercase() }
            "state" -> compareBy { it.state.lowercase() }
            "version" -> compareBy { it.versionText.lowercase() }
            else -> compareBy { it.host }
        }
        return if (descending) rows.sortedWith(cmp.reversed()) else rows.sortedWith(cmp)
    }

    fun filter(query: String, host: String?, openOnly: Boolean): List<ScanRow> {
        val q = query.trim().lowercase()
        return rows.filter { r ->
            (host == null || r.host == host) &&
                (!openOnly || r.state.startsWith("open")) &&
                (q.isEmpty() || listOf(r.host, r.hostname, r.service, r.product, r.version, r.port.toString()).any { it.lowercase().contains(q) })
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun toJson(t: ScanTable): String = json.encodeToString(ScanTable.serializer(), t)
        fun fromJson(s: String): ScanTable = json.decodeFromString(ScanTable.serializer(), s)
    }
}
