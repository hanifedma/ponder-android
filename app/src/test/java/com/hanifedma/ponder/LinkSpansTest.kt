package com.hanifedma.ponder

import com.hanifedma.ponder.core.LinkSpans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkSpansTest {

    private fun links(text: String) =
        LinkSpans.split(text).filterIsInstance<LinkSpans.Segment.Link>()

    private fun rebuild(text: String) = LinkSpans.split(text).joinToString("") {
        when (it) {
            is LinkSpans.Segment.Text -> it.value
            is LinkSpans.Segment.Link -> it.display
        }
    }

    @Test
    fun `plain text produces a single text segment`() {
        val segments = LinkSpans.split("no links here")
        assertEquals(1, segments.size)
        assertTrue(segments[0] is LinkSpans.Segment.Text)
    }

    @Test
    fun `http links are detected`() {
        val found = links("see https://example.com/page for more")
        assertEquals(1, found.size)
        assertEquals("https://example.com/page", found[0].url)
    }

    @Test
    fun `bare www links get an https scheme`() {
        val found = links("visit www.example.com today")
        assertEquals(1, found.size)
        assertEquals("https://www.example.com", found[0].url)
        assertEquals("www.example.com", found[0].display)
    }

    @Test
    fun `trailing sentence punctuation stays out of the url`() {
        val segments = LinkSpans.split("Read https://example.com/a.")
        val link = segments.filterIsInstance<LinkSpans.Segment.Link>().single()
        assertEquals("https://example.com/a", link.url)
        // The full stop survives as its own text run.
        assertEquals("Read https://example.com/a.", rebuild("Read https://example.com/a."))
    }

    @Test
    fun `closing bracket and quote are trimmed too`() {
        assertEquals("https://example.com/x", links("(https://example.com/x)").single().url)
        assertEquals("https://example.com/y", links("\"https://example.com/y\"").single().url)
    }

    @Test
    fun `several links in one string are all found`() {
        val found = links("a https://one.com b www.two.com c https://three.com d")
        assertEquals(3, found.size)
    }

    @Test
    fun `splitting never loses or duplicates characters`() {
        val samples = listOf(
            "start https://a.com middle www.b.com end.",
            "https://only.com",
            "trailing text after",
            "",
            "punctuation only ...",
        )
        for (sample in samples) {
            assertEquals(sample, rebuild(sample))
        }
    }
}
