package com.victorious.victordrax.manual

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * In-app how-to manual content model. The manifest (assets/manual/manifest.json) lists topics;
 * each topic's body is a Markdown file at assets/manual/<id>.md. Parsing and search are pure
 * (no Android deps) so they are unit tested.
 */
@Serializable
data class ManualTopic(
    val id: String,
    val title: String,
    val section: String,
    val summary: String,
    val keywords: List<String> = emptyList(),
    /** Optional in-app route this topic can deep-link to (null = no deep link). */
    val route: String? = null,
) {
    /** Markdown asset file name for this topic's body. */
    val assetPath: String get() = "manual/$id.md"
}

@Serializable
data class ManualManifest(val version: Int, val topics: List<ManualTopic>)

object Manual {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parse and validate the manifest. Throws on duplicate or empty ids. */
    fun parse(text: String): ManualManifest {
        val m = json.decodeFromString(ManualManifest.serializer(), text)
        require(m.topics.isNotEmpty()) { "manual manifest has no topics" }
        require(m.topics.all { it.id.isNotBlank() }) { "manual topic with blank id" }
        require(m.topics.map { it.id }.toSet().size == m.topics.size) { "duplicate manual topic id" }
        require(m.topics.all { it.title.isNotBlank() }) { "manual topic with blank title" }
        return m
    }

    /** Ordered distinct section names in first-seen order. */
    fun sections(topics: List<ManualTopic>): List<String> =
        topics.map { it.section }.distinct()

    /**
     * Case-insensitive search over title, summary, section and keywords.
     * A blank query returns every topic unchanged.
     */
    fun search(topics: List<ManualTopic>, query: String): List<ManualTopic> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return topics
        return topics.filter { t ->
            t.title.lowercase().contains(q) ||
                t.summary.lowercase().contains(q) ||
                t.section.lowercase().contains(q) ||
                t.id.contains(q) ||
                t.keywords.any { it.lowercase().contains(q) }
        }
    }
}
