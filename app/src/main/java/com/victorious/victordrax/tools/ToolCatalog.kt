package com.victorious.victordrax.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class Category(val id: String, val name: String)

@Serializable data class Tool(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val usage: String,
    /** Safe default command typed into the terminal (help output). */
    val command: String,
    val upstream: String,
    /** Documentation link shown on the tool screen; defaults to [upstream] when blank. */
    val docs: String = "",
    /** One of the Kali "Top 10" tools. */
    val topTen: Boolean = false,
    /** Has a full data-driven GUI form (see ToolSpecs). */
    val form: Boolean = false,
    /** Heavy desktop/Java GUI opened via the VNC desktop (see LaunchSpecs). */
    val launchOnly: Boolean = false,
) {
    val docsUrl: String get() = docs.ifBlank { upstream }
}

@Serializable data class Catalog(val version: Int, val categories: List<Category>, val tools: List<Tool>)

object ToolCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Catalog {
        val c = json.decodeFromString(Catalog.serializer(), text)
        val ids = c.categories.map { it.id }.toSet()
        require(c.tools.all { it.category in ids }) { "tool references unknown category" }
        require(c.tools.map { it.id }.toSet().size == c.tools.size) { "duplicate tool id" }
        return c
    }

    fun filter(tools: List<Tool>, query: String, category: String?, favorites: Set<String>, favoritesOnly: Boolean): List<Tool> {
        val q = query.trim().lowercase()
        return tools.filter { t ->
            (category == null || t.category == category) &&
                (!favoritesOnly || t.id in favorites) &&
                (q.isEmpty() || t.name.lowercase().contains(q) || t.description.lowercase().contains(q) || t.id.contains(q))
        }.sortedWith(compareByDescending<Tool> { it.id in favorites }.thenBy { it.name.lowercase() })
    }
}
