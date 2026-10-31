package com.victorious.andrax

import com.victorious.andrax.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ToolCatalogTest {
    private fun asset(): String = listOf("src/main/assets/tools.json", "app/src/main/assets/tools.json").map(::File).first { it.exists() }.readText()

    @Test fun bundledCatalogParses() {
        val c = ToolCatalog.parse(asset())
        assertTrue(c.tools.size >= 15)
        assertTrue(c.categories.map { it.id }.containsAll(listOf("info", "scan", "wireless", "web", "exploit", "password", "forensics")))
        assertTrue(c.tools.all { it.upstream.startsWith("https://") })
    }

    @Test fun ignoresUnknownFields() {
        val c = ToolCatalog.parse("""{"version":1,"extra":true,"categories":[{"id":"a","name":"A"}],
            "tools":[{"id":"x","name":"X","category":"a","description":"d","usage":"u","command":"x -h","upstream":"https://x","new":1}]}""")
        assertEquals("X", c.tools.single().name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownCategory() {
        ToolCatalog.parse("""{"version":1,"categories":[],"tools":[{"id":"x","name":"X","category":"zz","description":"d","usage":"u","command":"c","upstream":"u"}]}""")
    }

    @Test fun filterSearchCategoryAndFavorites() {
        val tools = ToolCatalog.parse(asset()).tools
        assertEquals(listOf("nmap"), ToolCatalog.filter(tools, "NMAP", null, emptySet(), false).map { it.id })
        assertTrue(ToolCatalog.filter(tools, "", "web", emptySet(), false).all { it.category == "web" })
        assertEquals(listOf("john"), ToolCatalog.filter(tools, "", null, setOf("john"), true).map { it.id })
        assertEquals("hydra", ToolCatalog.filter(tools, "", "password", setOf("hydra"), false).first().id)
    }

    @Test fun allCatalogCommandsAreLaunchable() {
        val tools = ToolCatalog.parse(asset()).tools
        tools.forEach { assertTrue(it.command, com.victorious.andrax.terminal.VictorSuiteLauncher.isSafeCommand(it.command)) }
    }
}
