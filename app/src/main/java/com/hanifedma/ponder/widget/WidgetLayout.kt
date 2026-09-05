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
    /** Side of the square shuffle button. All of it is the tap target. */
    val buttonDp: Int,
    /** Its edge-to-glyph padding, which is also what gives it that size. */
    val buttonInsetDp: Int,
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
     * The shuffle glyph itself. Must match the intrinsic size in
     * `ic_widget_shuffle.xml`, because the button is an ImageView sized by its
     * drawable plus padding — see [buttonInsetDp].
     */
    const val GLYPH_DP = 22

    /**
     * The shuffle button, near enough the 48dp Android asks for a touch target.
     * It, not the label beside it, is what sets the header's height — leaving it
     * out of the budget below would promise the body a row it does not have.
     */
    const val BUTTON_DP = 46

    /**
     * On a card too small to spend 46dp of it on chrome. Still half again the
     * area of a 28dp button, and the quote keeps the rows it would have cost.
     */
    const val BUTTON_DP_COMPACT = 36

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
        val button = buttonDp(w, h)

        // The source is the first thing to go: it is the least of the three, and
        // giving its rows to the quote is nearly always the better trade — the
        // more so now that the header spends what it does on a real tap target.
        val showSource = hasSource && h >= 150

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
            buttonDp = button,
            buttonInsetDp = buttonInsetDp(button),
        )
        return draft.copy(bodyMaxLines = bodyMaxLines(h, draft, scale))
    }

    /**
     * How big to make the shuffle button.
     *
     * It is the one control the widget has, and a 28dp target was too small to
     * hit comfortably — so it gets a proper one wherever the card can afford it,
     * and shrinks only where those dp would otherwise come straight out of the
     * quote. The threshold sits at the point where a full-size button would cost
     * a line of text at the sizes people actually use.
     */
    fun buttonDp(widthDp: Int, heightDp: Int): Int =
        if (heightDp < 140 || widthDp < 140) BUTTON_DP_COMPACT else BUTTON_DP

    /**
     * Padding from the button's edge to the glyph.
     *
     * This is how the button is sized at all: `RemoteViews` cannot change a
     * view's width before Android 12, but it can always set padding, and an
     * ImageView left to wrap its drawable grows with it. So the button is
     * [GLYPH_DP] plus this on each side, on every version.
     */
    fun buttonInsetDp(buttonDp: Int): Int = ((buttonDp - GLYPH_DP) / 2).coerceAtLeast(0)

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
        val headerDp = maxOf(labelDp, layout.buttonDp.toFloat()) + HEADER_GAP_DP
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
