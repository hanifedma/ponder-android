package com.hanifedma.ponder.core

import com.hanifedma.ponder.data.Entry
import com.hanifedma.ponder.data.Space

/** The three orderings offered in the toolbar, matching the web app's `<select>`. */
enum class SortOrder { NEWEST, OLDEST, BY_TAG }

/**
 * How the visible list is derived from everything in the current space: a
 * case-insensitive search across text and source, then one of three orderings.
 */
object EntrySort {

    /**
     * BY_TAG groups by the space's own tag order, newest first inside each
     * group; tags the space doesn't define sort last.
     */
    fun comparator(sort: SortOrder, space: Space): Comparator<Entry> = when (sort) {
        SortOrder.BY_TAG -> compareBy<Entry> { space.tagRank(it.tag) }
            .thenByDescending { it.createdAt }

        SortOrder.NEWEST -> compareByDescending { it.createdAt }
        SortOrder.OLDEST -> compareBy { it.createdAt }
    }

    /** Matches [term] against the entry's text and its source, as the web app does. */
    fun search(entries: List<Entry>, term: String): List<Entry> {
        val needle = term.trim().lowercase()
        if (needle.isEmpty()) return entries
        return entries.filter { "${it.text} ${it.source}".lowercase().contains(needle) }
    }

    /** Search then sort — the whole derivation in one call. */
    fun visible(entries: List<Entry>, term: String, sort: SortOrder, space: Space): List<Entry> =
        search(entries, term).sortedWith(comparator(sort, space))
}
