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
}
