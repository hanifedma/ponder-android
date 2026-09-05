package com.hanifedma.ponder.widget

import kotlin.math.floor

/**
 * What the widget shows at a given size, decided without touching Android so it
 * can be reasoned about and tested directly.
 *
 * A home-screen widget is the one surface where the app has no idea how much
 * room it will get: the person picks it, and launchers disagree about what a
 * "cell" is. Ponder can be a 2x1 strip or half a tablet screen, in either
 * orientation, at any system font scale. So nothing here is a fixed layout —
 * every element earns its place against the space actually available.
 */
data class WidgetLayout(
    /** The section name, e.g. "❝ Ponder". First thing dropped when narrow. */
    val showSpaceLabel: Boolean,
    /** The tag, appended to the section name. Needs real width to be worth it. */
    val showTag: Boolean,
    /** "— Source". Only when there is vertical room to spare. */
    val showSource: Boolean,
    val bodyTextSp: Float,
    val bodyMaxLines: Int,
    val metaTextSp: Float,
    val paddingDp: Int,
)

object WidgetSizing {

    /**
     * Below this the widget cannot hold a header row and readable text at once,
     * so the provider declares it as the minimum resize height rather than
     * rendering something unreadable.
     */
    const val MIN_HEIGHT_DP = 70

    /** Two columns on a standard 70dp grid: 70*2 - 30. */
    const val MIN_WIDTH_DP = 110

    /**
     * The shuffle button's fixed size. It, not the label beside it, is what sets
     * the header's height — leaving it out of the budget below would promise the
     * body a row it does not have.
     */
    const val BUTTON_DP = 28

    /** Gap between the header and the body. */
    private const val HEADER_GAP_DP = 5

    /** Gap between the body and the source line. */
    private const val SOURCE_GAP_DP = 4

    /** Multiplier from text size to the line box a TextView actually occupies. */
    private const val LINE_HEIGHT_RATIO = 1.35f

    fun of(
        widthDp: Int,
        heightDp: Int,
        textLength: Int,
        hasSource: Boolean,
        fontScale: Float,
    ): WidgetLayout {
        // Guard against a launcher reporting nothing useful, which some do on
        // the very first update before the widget has been measured.
        val w = if (widthDp <= 0) MIN_WIDTH_DP else widthDp
        val h = if (heightDp <= 0) MIN_HEIGHT_DP else heightDp
        val scale = if (fontScale <= 0f) 1f else fontScale

        val padding = if (w < 140 || h < 100) 10 else 14
        val metaSp = if (w < 160 || h < 100) 10.5f else 11.5f
        val bodySp = bodyTextSp(w, h, textLength)

        // The source is the first thing to go: it is the least of the three, and
        // giving its rows to the quote is nearly always the better trade.
        val showSource = hasSource && h >= 132

        // The line count depends on everything decided above, so the layout is
        // built once with a placeholder and then told how many lines it has.
        val draft = WidgetLayout(
            showSpaceLabel = w >= 155,
            showTag = w >= 235 && h >= 105,
            showSource = showSource,
            bodyTextSp = bodySp,
            bodyMaxLines = 1,
            metaTextSp = metaSp,
            paddingDp = padding,
        )
        return draft.copy(bodyMaxLines = bodyMaxLines(h, draft, scale))
    }

    /**
     * Type scales with the card, then again with how much there is to say: a
     * four-word quote should fill its widget, and a long paragraph should shrink
     * rather than be cut off after two lines.
     */
    fun bodyTextSp(widthDp: Int, heightDp: Int, textLength: Int): Float {
        val base = when {
            heightDp < 95 -> 12.5f
            heightDp < 140 -> 14.5f
            heightDp < 210 -> 16f
            else -> 17.5f
        }
        val forLength = when {
            textLength <= 45 -> 3f
            textLength <= 110 -> 1f
            textLength <= 200 -> 0f
            textLength <= 330 -> -1.5f
            else -> -2.5f
        }
        // A narrow card fits few characters per line, so large type there buys
        // nothing but more wrapping.
        val forWidth = if (widthDp < 150) -1.5f else 0f
        return (base + forLength + forWidth).coerceIn(11f, 21f)
    }

    /**
     * How many lines fit in what is left after the chrome. Without a cap the
     * TextView is simply clipped by its parent — no ellipsis, just a sentence
     * that stops mid-word — so this is what makes a too-long quote end in "…".
     */
    fun bodyMaxLines(heightDp: Int, layout: WidgetLayout, fontScale: Float): Int {
        val labelDp = layout.metaTextSp * fontScale * LINE_HEIGHT_RATIO
        val headerDp = maxOf(labelDp, BUTTON_DP.toFloat()) + HEADER_GAP_DP
        val sourceDp = if (layout.showSource) {
            layout.metaTextSp * fontScale * LINE_HEIGHT_RATIO + SOURCE_GAP_DP
        } else {
            0f
        }
        val available = heightDp - layout.paddingDp * 2 - headerDp - sourceDp
        val lineDp = layout.bodyTextSp * fontScale * LINE_HEIGHT_RATIO
        if (lineDp <= 0f) return 1
        return floor(available / lineDp).toInt().coerceIn(1, 24)
    }
}
