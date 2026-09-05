package com.hanifedma.ponder

import com.hanifedma.ponder.data.Thought
import com.hanifedma.ponder.data.ThoughtPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule the notification lives by: dismissing it always brings up a
 * *different* thought, for as long as there is another one to bring up.
 */
class ThoughtPickTest {

    private fun thought(id: String, space: String = "ponder") =
        Thought(spaceKey = space, id = id, text = "text $id", source = "", tag = "interesting")

    @Test
    fun `empty pool yields nothing`() {
        assertNull(ThoughtPool.pickFrom(emptyList(), null))
        assertNull(ThoughtPool.pickFrom(emptyList(), "ponder/a"))
    }

    @Test
    fun `a single entry is shown again rather than nothing`() {
        val only = thought("a")
        assertEquals(only, ThoughtPool.pickFrom(listOf(only), only.key))
    }

    @Test
    fun `the entry on screen is never the one picked next`() {
        val pool = listOf(thought("a"), thought("b"), thought("c"))
        repeat(200) {
            val next = ThoughtPool.pickFrom(pool, "ponder/b")
            assertNotEquals("ponder/b", next?.key)
        }
    }

    @Test
    fun `a pool of two always alternates`() {
        val pool = listOf(thought("a"), thought("b"))
        repeat(50) {
            assertEquals("ponder/b", ThoughtPool.pickFrom(pool, "ponder/a")?.key)
            assertEquals("ponder/a", ThoughtPool.pickFrom(pool, "ponder/b")?.key)
        }
    }

    @Test
    fun `an id remembered from a deleted entry does not stop the draw`() {
        val pool = listOf(thought("a"), thought("b"))
        val next = ThoughtPool.pickFrom(pool, "ponder/gone")
        assertTrue(next?.key in setOf("ponder/a", "ponder/b"))
    }

    @Test
    fun `the same id in two spaces is two different thoughts`() {
        val pool = listOf(thought("a", "ponder"), thought("a", "health"))
        // Excluding the Ponder one must leave the Healthy Tips one pickable.
        repeat(50) {
            assertEquals("health/a", ThoughtPool.pickFrom(pool, "ponder/a")?.key)
        }
    }
}
