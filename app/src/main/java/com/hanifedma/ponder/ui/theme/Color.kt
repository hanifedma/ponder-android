package com.hanifedma.ponder.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The web app's design tokens, one for one. Keeping the exact same palette means
 * a person moving between phone, tablet and browser sees one product.
 */
@Immutable
data class PonderColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val elevated: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentHover: Color,
    val accentSoft: Color,
    val accentContrast: Color,
    val danger: Color,
    val shimmer: Color,
    val isDark: Boolean,
)

/** Dark (default) — matched to hanifedma.com. */
val DarkColors = PonderColors(
    bg = Color(0xFF0F0F0F),
    surface = Color(0xFF1A1A1A),
    surface2 = Color(0xFF202021),
    surface3 = Color(0xFF282829),
    elevated = Color(0xFF232323),
    border = Color(0xFF333333),
    borderStrong = Color(0xFF454545),
    text = Color(0xFFF0F0F0),
    muted = Color(0xFFA6A6A6),
    faint = Color(0xFF6F6F6F),
    accent = Color(0xFF22C55E),
    accentHover = Color(0xFF4ADE80),
    accentSoft = Color(0x2922C55E), // rgba(34,197,94,.16)
    accentContrast = Color(0xFF052E16),
    danger = Color(0xFFF4566B),
    shimmer = Color(0x0DFFFFFF), // rgba(255,255,255,.05)
    isDark = true,
)

val LightColors = PonderColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF3F4F6),
    surface3 = Color(0xFFE8EAED),
    elevated = Color(0xFFFFFFFF),
    border = Color(0xFFE5E7EB),
    borderStrong = Color(0xFFD1D5DB),
    text = Color(0xFF1A1A1A),
    muted = Color(0xFF565B64),
    faint = Color(0xFF868E96),
    accent = Color(0xFF16A34A),
    accentHover = Color(0xFF15803D),
    accentSoft = Color(0x1A16A34A), // rgba(22,163,74,.10)
    accentContrast = Color(0xFFFFFFFF),
    danger = Color(0xFFE11D48),
    shimmer = Color(0x0B000000), // rgba(0,0,0,.045)
    isDark = false,
)

/**
 * Tag colours are shared across themes, exactly like the CSS `--tag-*`
 * variables. Unknown tags fall back to the muted footer colour.
 */
object TagColors {

    private val EXTRA = Color(0xFF14B8A6)
    private val DAILY = Color(0xFF3B82F6)
    private val VERY = Color(0xFFF43F5E)
    private val PRETTY = Color(0xFFF59E0B)
    private val INTERESTING = Color(0xFFA855F7)

    /** Null means "no dedicated colour" — render the neutral badge. */
    fun accentFor(tag: String?): Color? = when (tag) {
        "extraterrestrial" -> EXTRA
        "try to read this everyday" -> DAILY
        "very important" -> VERY
        "pretty important" -> PRETTY
        "interesting" -> INTERESTING
        // Healthy Tips tags reuse two of the same hues, as in styles.css.
        "pretty sure" -> EXTRA
        "not really" -> VERY
        else -> null
    }

    /** Badge fill: 13% of the accent (15% for amber, which reads lighter). */
    fun fillFor(tag: String?): Color? =
        accentFor(tag)?.copy(alpha = if (tag == "pretty important") 0.15f else 0.13f)

    /** Badge outline: 32% of the accent (34% for amber). */
    fun outlineFor(tag: String?): Color? =
        accentFor(tag)?.copy(alpha = if (tag == "pretty important") 0.34f else 0.32f)
}
