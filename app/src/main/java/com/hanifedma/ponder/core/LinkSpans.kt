package com.hanifedma.ponder.core

/**
 * Splits entry text into plain and link segments so the UI can render real,
 * tappable links without ever interpreting the text as markup.
 *
 * Same rules as the web app: `http(s)://…` and bare `www.…` become links, and
 * trailing sentence punctuation is left outside the URL.
 */
object LinkSpans {

    sealed interface Segment {
        data class Text(val value: String) : Segment
        data class Link(val display: String, val url: String) : Segment
    }

    private val URL = Regex("(https?://[^\\s]+|www\\.[^\\s]+)", RegexOption.IGNORE_CASE)
    private val TRAILING_PUNCTUATION = Regex("[.,;:!?)\\]'\"]+$")
    private val HAS_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

    fun split(text: String?): List<Segment> {
        val str = text ?: ""
        if (str.isEmpty()) return emptyList()

        val out = ArrayList<Segment>()
        var last = 0
        for (m in URL.findAll(str)) {
            if (m.range.first > last) {
                out.add(Segment.Text(str.substring(last, m.range.first)))
            }
            val raw = m.value
            // Don't swallow the punctuation that ends the sentence into the URL.
            val display = raw.replace(TRAILING_PUNCTUATION, "")
            val trailing = raw.substring(display.length)
            if (display.isEmpty()) {
                out.add(Segment.Text(raw))
            } else {
                val url = if (HAS_SCHEME.containsMatchIn(display)) display else "https://$display"
                out.add(Segment.Link(display, url))
                if (trailing.isNotEmpty()) out.add(Segment.Text(trailing))
            }
            last = m.range.last + 1
        }
        if (last < str.length) out.add(Segment.Text(str.substring(last)))
        return out
    }
}
