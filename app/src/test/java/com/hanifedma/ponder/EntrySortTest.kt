package com.hanifedma.ponder

import com.hanifedma.ponder.core.EntrySort
import com.hanifedma.ponder.core.SortOrder
import com.hanifedma.ponder.data.Entry
import com.hanifedma.ponder.data.Spaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntrySortTest {

    private val space = Spaces.PONDER

    private fun entry(
        id: String,
        text: String = "text $id",
        source: String = "",
        tag: String = "interesting",
        createdAt: Long = 0L,
    ) = Entry(id, text, source, tag, createdAt)

    // ------------------------------------------------------------------ sort

    @Test
    fun `newest first orders by descending date`() {
        val entries = listOf(
            entry("old", createdAt = 100),
            entry("new", createdAt = 300),
            entry("mid", createdAt = 200),
        )
        val sorted = entries.sortedWith(EntrySort.comparator(SortOrder.NEWEST, space))
        assertEquals(listOf("new", "mid", "old"), sorted.map { it.id })
    }

    @Test
    fun `oldest first orders by ascending date`() {
        val entries = listOf(
            entry("old", createdAt = 100),
            entry("new", createdAt = 300),
            entry("mid", createdAt = 200),
        )
        val sorted = entries.sortedWith(EntrySort.comparator(SortOrder.OLDEST, space))
        assertEquals(listOf("old", "mid", "new"), sorted.map { it.id })
    }

    @Test
    fun `by tag groups in the order the space declares, newest first inside`() {
        val entries = listOf(
            entry("i1", tag = "interesting", createdAt = 100),
            entry("v1", tag = "very important", createdAt = 100),
            entry("e1", tag = "extraterrestrial", createdAt = 100),
            entry("v2", tag = "very important", createdAt = 500),
        )
        val sorted = entries.sortedWith(EntrySort.comparator(SortOrder.BY_TAG, space))

        // Space order is extraterrestrial, try-to-read, very, pretty, interesting.
        assertEquals(listOf("e1", "v2", "v1", "i1"), sorted.map { it.id })
    }

    @Test
    fun `unknown tags sort last`() {
        val entries = listOf(
            entry("mystery", tag = "not a real tag", createdAt = 900),
            entry("known", tag = "extraterrestrial", createdAt = 100),
        )
        val sorted = entries.sortedWith(EntrySort.comparator(SortOrder.BY_TAG, space))
        assertEquals(listOf("known", "mystery"), sorted.map { it.id })
    }

    @Test
    fun `health space ranks its own tags`() {
        val health = Spaces.HEALTH
        val entries = listOf(
            entry("c", tag = "interesting"),
            entry("a", tag = "pretty sure"),
            entry("b", tag = "not really"),
        )
        val sorted = entries.sortedWith(EntrySort.comparator(SortOrder.BY_TAG, health))
        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
    }

    // ---------------------------------------------------------------- search

    @Test
    fun `blank search returns everything untouched`() {
        val entries = listOf(entry("a"), entry("b"))
        assertEquals(entries, EntrySort.search(entries, "   "))
    }

    @Test
    fun `search is case insensitive and covers the source`() {
        val entries = listOf(
            entry("a", text = "Something about the SEA"),
            entry("b", text = "Unrelated", source = "Rachel Carson"),
            entry("c", text = "Nothing relevant"),
        )
        assertEquals(listOf("a"), EntrySort.search(entries, "sea").map { it.id })
        assertEquals(listOf("b"), EntrySort.search(entries, "carson").map { it.id })
    }

    @Test
    fun `search that matches nothing returns empty`() {
        val entries = listOf(entry("a", text = "hello"))
        assertTrue(EntrySort.search(entries, "zzz").isEmpty())
    }

    @Test
    fun `visible applies the search before the sort`() {
        val entries = listOf(
            entry("keep1", text = "water the plants", createdAt = 100),
            entry("drop", text = "call the dentist", createdAt = 200),
            entry("keep2", text = "water bill due", createdAt = 300),
        )
        val visible = EntrySort.visible(entries, "water", SortOrder.NEWEST, space)
        assertEquals(listOf("keep2", "keep1"), visible.map { it.id })
    }
}
