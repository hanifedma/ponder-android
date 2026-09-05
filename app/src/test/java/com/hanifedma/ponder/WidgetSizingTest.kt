package com.hanifedma.ponder

import com.hanifedma.ponder.widget.WidgetSizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A widget is handed whatever size the person drags it to, on whatever launcher
 * they use, at whatever font scale they have set. These are the sizes that
 * actually occur — the 70dp grid a launcher lays out on — plus the ones that
 * should not occur but do.
 */
class WidgetSizingTest {

    /** minWidth = 70n - 30, the formula every launcher's grid follows. */
    private fun cells(n: Int) = 70 * n - 30

    private fun layout(
        widthCells: Int,
        heightCells: Int,
        textLength: Int = 120,
        hasSource: Boolean = true,
        fontScale: Float = 1f,
    ) = WidgetSizing.of(
        widthDp = cells(widthCells),
        heightDp = cells(heightCells),
        textLength = textLength,
        hasSource = hasSource,
        fontScale = fontScale,
    )

    // ------------------------------------------------------- the size ladder

    @Test
    fun `every plausible cell size leaves at least one line of quote`() {
        for (w in 2..6) {
            for (h in 1..6) {
                val l = WidgetSizing.of(cells(w), cells(h), 200, true, 1f)
                assertTrue(
                    "no room for text at ${w}x$h cells",
                    l.bodyMaxLines >= 1,
                )
            }
        }
    }

    @Test
    fun `a taller widget shows more of the quote`() {
        val short = layout(4, 1).bodyMaxLines
        val medium = layout(4, 2).bodyMaxLines
        val tall = layout(4, 4).bodyMaxLines
        assertTrue("$short -> $medium", medium > short)
        assertTrue("$medium -> $tall", tall > medium)
    }

    @Test
    fun `the smallest allowed widget is still usable`() {
        val l = WidgetSizing.of(
            widthDp = WidgetSizing.MIN_WIDTH_DP,
            heightDp = WidgetSizing.MIN_HEIGHT_DP,
            textLength = 200,
            hasSource = true,
            fontScale = 1f,
        )
        assertTrue(l.bodyMaxLines >= 1)
        // At this size the quote is all there is room for.
        assertFalse(l.showSource)
        assertFalse(l.showTag)
    }

    // --------------------------------------------------------- what is shown

    @Test
    fun `a narrow widget drops the label before the quote`() {
        assertFalse(layout(2, 2).showSpaceLabel)
        assertTrue(layout(4, 2).showSpaceLabel)
    }

    @Test
    fun `the tag needs real width to be worth showing`() {
        assertFalse(layout(3, 2).showTag)
        assertTrue(layout(5, 2).showTag)
    }

    @Test
    fun `the source appears only when there are rows to spare`() {
        assertFalse(layout(4, 1).showSource)
        assertTrue(layout(4, 3).showSource)
    }

    @Test
    fun `an entry with no source never reserves a row for one`() {
        assertFalse(layout(4, 4, hasSource = false).showSource)
        // And that row goes to the quote instead.
        val withSource = layout(4, 4, hasSource = true).bodyMaxLines
        val without = layout(4, 4, hasSource = false).bodyMaxLines
        assertTrue("$without should exceed $withSource", without > withSource)
    }

    // ------------------------------------------------------------ type scale

    @Test
    fun `a short quote is set larger than a long one`() {
        val short = WidgetSizing.bodyTextSp(250, 180, 20)
        val long = WidgetSizing.bodyTextSp(250, 180, 600)
        assertTrue("$short should exceed $long", short > long)
    }

    @Test
    fun `text size stays inside legible bounds at any extreme`() {
        val sizes = listOf(
            WidgetSizing.bodyTextSp(110, 70, 2000),
            WidgetSizing.bodyTextSp(110, 70, 1),
            WidgetSizing.bodyTextSp(1000, 1000, 1),
            WidgetSizing.bodyTextSp(1000, 1000, 5000),
        )
        for (sp in sizes) {
            assertTrue("$sp out of bounds", sp in 11f..21f)
        }
    }

    // ----------------------------------------------------------- font scale

    @Test
    fun `a large system font scale reduces the line count rather than clipping`() {
        val normal = layout(4, 3, fontScale = 1f).bodyMaxLines
        val large = layout(4, 3, fontScale = 1.5f).bodyMaxLines
        val huge = layout(4, 3, fontScale = 2f).bodyMaxLines
        assertTrue("$normal -> $large", large < normal)
        assertTrue("$large -> $huge", huge <= large)
        assertTrue("must never reach zero", huge >= 1)
    }

    // ---------------------------------------------------------- bad input

    @Test
    fun `a launcher reporting nothing still produces a drawable layout`() {
        val l = WidgetSizing.of(0, 0, 100, true, 1f)
        assertTrue(l.bodyMaxLines >= 1)
        assertTrue(l.bodyTextSp >= 11f)
        assertTrue(l.paddingDp > 0)
    }

    @Test
    fun `a zero font scale is treated as normal rather than dividing by nothing`() {
        val l = WidgetSizing.of(cells(4), cells(3), 100, true, 0f)
        assertEquals(layout(4, 3, textLength = 100).bodyMaxLines, l.bodyMaxLines)
    }

    @Test
    fun `an empty entry is sized like any other`() {
        val l = WidgetSizing.of(cells(4), cells(2), 0, false, 1f)
        assertTrue(l.bodyMaxLines >= 1)
    }

    // -------------------------------------------------------- the one control

    @Test
    fun `the shuffle button is a real tap target wherever there is room`() {
        // 28dp was too small to hit; nothing may quietly go back below this.
        for (w in 2..6) {
            for (h in 1..6) {
                val l = WidgetSizing.of(cells(w), cells(h), 200, true, 1f)
                assertTrue(
                    "${l.buttonDp}dp button at ${w}x$h cells",
                    l.buttonDp >= WidgetSizing.BUTTON_DP_COMPACT,
                )
            }
        }
        assertEquals(WidgetSizing.BUTTON_DP, layout(4, 3).buttonDp)
    }

    @Test
    fun `a bigger widget never gets a smaller button`() {
        var previous = 0
        for (n in 1..8) {
            val size = WidgetSizing.buttonDp(cells(n), cells(n))
            assertTrue("$size after $previous at $n cells", size >= previous)
            previous = size
        }
        assertEquals(WidgetSizing.BUTTON_DP, previous)
    }

    @Test
    fun `the inset is exactly what makes the button that size`() {
        // The button is the glyph plus this padding on each side — nothing sets
        // its width — so a mismatch here is a mis-sized button, not a rounding.
        for (side in listOf(WidgetSizing.BUTTON_DP_COMPACT, WidgetSizing.BUTTON_DP)) {
            assertEquals(side, WidgetSizing.GLYPH_DP + 2 * WidgetSizing.buttonInsetDp(side))
        }
    }

    @Test
    fun `a button smaller than its glyph asks for no padding rather than negative`() {
        assertEquals(0, WidgetSizing.buttonInsetDp(WidgetSizing.GLYPH_DP - 10))
    }

    @Test
    fun `growing the button never costs the quote its last line`() {
        for (w in 2..6) {
            for (h in 1..6) {
                for (scale in listOf(1f, 1.5f, 2f)) {
                    val l = WidgetSizing.of(cells(w), cells(h), 400, true, scale)
                    assertTrue("${w}x$h at ${scale}x", l.bodyMaxLines >= 1)
                }
            }
        }
    }

    @Test
    fun `a widget two rows tall always keeps two lines of quote`() {
        // The button and the source line both compete with the quote for the
        // same dp. Whatever they are set to, a card of this height owes the
        // quote more than a single line — launchers report every height in
        // this range, not just the ones the cell grid lands on.
        for (h in 110..400 step 5) {
            val l = WidgetSizing.of(250, h, 200, true, 1f)
            assertTrue("only ${l.bodyMaxLines} line(s) at ${h}dp", l.bodyMaxLines >= 2)
        }
    }
}
