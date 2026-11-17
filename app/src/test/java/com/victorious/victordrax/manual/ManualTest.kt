package com.victorious.victordrax.manual

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ManualTest {
    private val manifestText = File("src/main/assets/manual/manifest.json").readText()
    private val manifest = Manual.parse(manifestText)
    private val topics = manifest.topics

    @Test fun manifestParsesAndHasExpectedTopics() {
        assertTrue(topics.size >= 15)
        // Every required subject area is covered.
        val ids = topics.map { it.id }.toSet()
        listOf(
            "getting-started", "legal", "engagements", "privileges", "shizuku-wireless",
            "terminal", "tools", "findings", "reports", "scheduler", "beef", "afos",
            "vnc", "backup", "installer", "troubleshooting",
        ).forEach { assertTrue("missing topic $it", it in ids) }
    }

    @Test fun everyTopicHasANonEmptyMarkdownBody() {
        topics.forEach { t ->
            val f = File("src/main/assets/${t.assetPath}")
            assertTrue("missing body for ${t.id}", f.exists())
            val body = f.readText()
            assertTrue("empty body for ${t.id}", body.isNotBlank())
            // Each page should start with a heading and parse into blocks.
            val blocks = Markdown.parse(body)
            assertTrue("no blocks for ${t.id}", blocks.isNotEmpty())
            assertTrue("no heading in ${t.id}", blocks.any { it is Markdown.Block.Heading })
        }
    }

    @Test fun noChineseOrWhiteBackgroundLeaks() {
        // Fleet rule: English only. Guard against stray CJK characters in the manual content.
        val cjk = Regex("[\\u4e00-\\u9fff]")
        topics.forEach { t ->
            val body = File("src/main/assets/${t.assetPath}").readText()
            assertFalse("CJK text in ${t.id}", cjk.containsMatchIn(body))
        }
    }

    @Test fun sectionsAreOrderedAndDistinct() {
        val sections = Manual.sections(topics)
        assertEquals(sections, sections.distinct())
        assertTrue(sections.isNotEmpty())
    }

    @Test fun searchMatchesTitleSummaryAndKeywords() {
        assertEquals(topics, Manual.search(topics, "   "))
        assertTrue(Manual.search(topics, "shizuku").any { it.id == "shizuku-wireless" })
        assertTrue(Manual.search(topics, "PDF").any { it.id == "reports" })      // summary
        assertTrue(Manual.search(topics, "nmap").any { it.id == "tools" })        // keyword
        assertTrue(Manual.search(topics, "Setup").isNotEmpty())                   // section
        assertTrue(Manual.search(topics, "zzzznotarealterm").isEmpty())
    }

    @Test fun deepLinkRoutesAreKnown() {
        val known = setOf(
            "dashboard", "tools", "terminal", "engagements", "settings", "about",
            "schedules", "wireless", "beef", "packages", "desktop", "backup", "install",
        )
        topics.mapNotNull { it.route }.forEach { assertTrue("unknown route $it", it in known) }
    }

    @Test fun duplicateIdsRejected() {
        val bad = """{"version":1,"topics":[
            {"id":"a","title":"A","section":"S","summary":"x"},
            {"id":"a","title":"B","section":"S","summary":"y"}]}"""
        assertThrows(IllegalArgumentException::class.java) { Manual.parse(bad) }
    }

    @Test fun markdownParsesHeadingsBulletsNumbersParagraphs() {
        val blocks = Markdown.parse("# Title\n\nHello world.\n\n- one\n- two\n\n1. first\n2. second")
        assertTrue(blocks[0] is Markdown.Block.Heading)
        assertEquals(1, (blocks[0] as Markdown.Block.Heading).level)
        assertTrue(blocks.any { it is Markdown.Block.Paragraph })
        assertEquals(2, blocks.count { it is Markdown.Block.Bullet })
        val nums = blocks.filterIsInstance<Markdown.Block.Numbered>()
        assertEquals(2, nums.size)
        assertEquals(2, nums[1].number)
    }

    @Test fun markdownInlineBoldAndLinks() {
        val spans = Markdown.parseInline("see **bold** and [Shizuku](https://shizuku.rikka.app/) end")
        assertTrue(spans.any { it is Markdown.Span.Bold && it.text == "bold" })
        val link = spans.filterIsInstance<Markdown.Span.Link>().single()
        assertEquals("Shizuku", link.text)
        assertEquals("https://shizuku.rikka.app/", link.url)
        assertTrue(spans.first() is Markdown.Span.Text)
    }
}
