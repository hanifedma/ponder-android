package com.hanifedma.ponder

import com.hanifedma.ponder.core.Embeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Link detection must match the web app's, entry for entry. */
class EmbedsTest {

    @Test
    fun `detects a plain youtube watch link`() {
        val media = Embeds.detect("Great talk: https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(1, media.size)
        assertEquals(Embeds.Kind.YOUTUBE, media[0].kind)
        assertEquals("dQw4w9WgXcQ", media[0].id)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", media[0].thumbUrl)
    }

    @Test
    fun `detects youtu_be, shorts, embed and live forms`() {
        val ids = listOf(
            "https://youtu.be/aaaaaaaaaaa",
            "https://youtube.com/shorts/bbbbbbbbbbb",
            "https://www.youtube.com/embed/ccccccccccc",
            "https://www.youtube.com/live/ddddddddddd",
        ).flatMap { Embeds.detect(it) }.map { it.id }

        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc", "ddddddddddd"), ids)
    }

    @Test
    fun `detects a watch link with extra query parameters before v`() {
        val media = Embeds.detect("https://www.youtube.com/watch?list=PL123&v=dQw4w9WgXcQ")
        assertEquals(1, media.size)
        assertEquals("dQw4w9WgXcQ", media[0].id)
    }

    @Test
    fun `deduplicates the same video mentioned twice`() {
        val text = "https://youtu.be/dQw4w9WgXcQ and again https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(1, Embeds.detect(text).size)
    }

    @Test
    fun `detects vimeo`() {
        val media = Embeds.detect("https://vimeo.com/123456789")
        assertEquals(1, media.size)
        assertEquals(Embeds.Kind.VIMEO, media[0].kind)
        assertEquals("https://vimeo.com/123456789", media[0].openUrl)
    }

    @Test
    fun `ignores vimeo ids that are too short`() {
        assertTrue(Embeds.detect("https://vimeo.com/123").isEmpty())
    }

    @Test
    fun `normalises instagram reels to reel`() {
        val media = Embeds.detect("https://www.instagram.com/reels/AbC123/")
        assertEquals(1, media.size)
        assertEquals("https://www.instagram.com/reel/AbC123/", media[0].openUrl)
        assertEquals("Instagram reel", media[0].label)
    }

    @Test
    fun `labels instagram posts and tv correctly`() {
        assertEquals("Instagram post", Embeds.detect("instagram.com/p/XyZ/")[0].label)
        assertEquals("Instagram video", Embeds.detect("instagram.com/tv/XyZ/")[0].label)
    }

    @Test
    fun `detects direct image links including query strings`() {
        val media = Embeds.detect("Look https://cdn.example.com/a/photo.JPG?w=800 nice")
        assertEquals(1, media.size)
        assertEquals(Embeds.Kind.IMAGE, media[0].kind)
        assertEquals("https://cdn.example.com/a/photo.JPG?w=800", media[0].openUrl)
    }

    @Test
    fun `ignores non-image links`() {
        assertTrue(Embeds.detect("https://example.com/article").isEmpty())
    }

    @Test
    fun `finds several media in one entry, in order`() {
        val text = """
            https://youtu.be/dQw4w9WgXcQ
            https://vimeo.com/987654321
            https://example.com/pic.png
        """.trimIndent()
        val kinds = Embeds.detect(text).map { it.kind }
        assertEquals(
            listOf(Embeds.Kind.YOUTUBE, Embeds.Kind.VIMEO, Embeds.Kind.IMAGE),
            kinds,
        )
    }

    @Test
    fun `empty and blank text yield nothing`() {
        assertTrue(Embeds.detect(null).isEmpty())
        assertTrue(Embeds.detect("").isEmpty())
        assertTrue(Embeds.detect("just a plain thought").isEmpty())
    }
}
