package com.hanifedma.ponder

import com.hanifedma.ponder.core.Similarity
import com.hanifedma.ponder.data.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duplicate detector has to agree with the web app's, or the two clients
 * would flag different entries on the same data.
 */
class SimilarityTest {

    private fun entry(id: String, text: String, createdAt: Long = 0L) =
        Entry(id = id, text = text, source = "", tag = "interesting", createdAt = createdAt)

    private fun score(a: String, b: String) =
        Similarity.similarityScore(Similarity.normalizeText(a), Similarity.normalizeText(b))

    @Test
    fun `normalize lowercases, strips punctuation and collapses whitespace`() {
        assertEquals("hello world", Similarity.normalizeText("  Hello,   World!!  "))
        // Punctuation becomes a space rather than vanishing, so "it's" splits
        // into two words — matching the web app's replace(…, " ").
        assertEquals("it s a test", Similarity.normalizeText("It's a — test."))
    }

    @Test
    fun `normalize keeps letters and digits of any script`() {
        assertEquals("건강 팁 2026", Similarity.normalizeText("건강 팁 (2026)!"))
    }

    @Test
    fun `identical text scores 1`() {
        assertEquals(1.0, score("Be the change", "be the change!"), 0.0001)
    }

    @Test
    fun `empty text never matches`() {
        assertEquals(0.0, score("", "anything at all"), 0.0001)
        assertEquals(0.0, score("anything at all", ""), 0.0001)
    }

    @Test
    fun `containment scores 0_95 when both sides are long enough`() {
        val a = "The unexamined life is not worth living"
        val b = "The unexamined life is not worth living, said Socrates"
        assertEquals(0.95, score(a, b), 0.0001)
    }

    @Test
    fun `short strings do not use the containment shortcut`() {
        // Both under the 10-character floor, so this falls through to the
        // overlap measures rather than returning 0.95.
        assertTrue(score("a b", "a b c") < 0.95)
    }

    @Test
    fun `reordered words still count as similar`() {
        assertTrue(score("hope is a good thing", "a good thing is hope") >= Similarity.THRESHOLD)
    }

    @Test
    fun `unrelated text is not similar`() {
        assertFalse(
            score("The stars are made of hydrogen", "Remember to drink more water")
                >= Similarity.THRESHOLD
        )
    }

    @Test
    fun `similarity is symmetric`() {
        val a = "A journey of a thousand miles begins with a single step"
        val b = "A journey of a thousand miles starts with one step"
        assertEquals(score(a, b), score(b, a), 0.0001)
    }

    @Test
    fun `findSimilar returns matches sorted by score, capped at five`() {
        val entries = (1..8).map { entry("e$it", "Drink more water every single day $it") }
        val matches = Similarity.findSimilar("Drink more water every single day 1", entries)

        assertEquals(5, matches.size)
        assertEquals("e1", matches.first().entry.id) // the exact match ranks first
        val scores = matches.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun `findSimilar ignores blank input`() {
        val entries = listOf(entry("a", "something"))
        assertTrue(Similarity.findSimilar("   ", entries).isEmpty())
    }

    @Test
    fun `duplicate groups cluster transitively and are sorted largest first`() {
        val entries = listOf(
            entry("a", "The only true wisdom is in knowing you know nothing"),
            entry("b", "The only true wisdom is in knowing you know nothing."),
            entry("c", "the only TRUE wisdom is in knowing you know nothing"),
            entry("d", "Completely different note about gardening tomatoes"),
            entry("e", "An unrelated reminder to stretch each morning"),
            entry("f", "An unrelated reminder to stretch every morning"),
        )

        val groups = Similarity.findDuplicateGroups(entries)

        assertEquals(2, groups.size)
        assertEquals(listOf("a", "b", "c"), groups[0].map { it.id })
        assertEquals(listOf("e", "f"), groups[1].map { it.id })
    }

    @Test
    fun `duplicate groups need at least two entries`() {
        assertTrue(Similarity.findDuplicateGroups(listOf(entry("a", "alone"))).isEmpty())
        assertTrue(Similarity.findDuplicateGroups(emptyList()).isEmpty())
    }

    @Test
    fun `entries with no words are never grouped`() {
        val entries = listOf(entry("a", "!!!"), entry("b", "???"), entry("c", "..."))
        assertTrue(Similarity.findDuplicateGroups(entries).isEmpty())
    }

    /**
     * The group scan skips pairs whose lengths make the threshold unreachable.
     * That shortcut must never change which pairs are flagged, so check it
     * against the plain pairwise rule over a mixed corpus.
     */
    @Test
    fun `group scan agrees with pairwise scoring`() {
        val texts = listOf(
            "Be kind whenever possible, it is always possible",
            "Be kind whenever possible; it is always possible.",
            "Short",
            "Short note",
            "A much longer entry that shares almost nothing with the others here",
            "Water the plants on Tuesday",
            "Water the plants every Tuesday",
            "",
            "건강을 위해 매일 물을 마시세요",
            "건강을 위해 매일 물을 많이 마시세요",
        )
        val entries = texts.mapIndexed { i, t -> entry("e$i", t) }

        val expected = mutableSetOf<Set<String>>()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val s = score(entries[i].text, entries[j].text)
                if (s >= Similarity.THRESHOLD) {
                    expected.add(setOf(entries[i].id, entries[j].id))
                }
            }
        }

        // Every directly-similar pair must end up in the same cluster.
        val groups = Similarity.findDuplicateGroups(entries)
        val clusterOf = HashMap<String, Int>()
        groups.forEachIndexed { index, group -> group.forEach { clusterOf[it.id] = index } }

        for (pair in expected) {
            val (x, y) = pair.toList()
            assertEquals(
                "pair $pair should share a cluster",
                clusterOf[x],
                clusterOf[y],
            )
            assertTrue("pair $pair should be clustered at all", clusterOf[x] != null)
        }
    }
}
