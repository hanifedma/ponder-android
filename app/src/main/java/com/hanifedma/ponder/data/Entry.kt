package com.hanifedma.ponder.data

/**
 * One saved quote / thought / tip.
 *
 * Mirrors the web app's document shape exactly — `text`, `source`, `tag`,
 * `createdAt` — so entries written by either client are readable by the other.
 */
data class Entry(
    val id: String,
    val text: String,
    val source: String,
    val tag: String,
    /** Epoch millis. */
    val createdAt: Long,
    /**
     * True while a locally-made write is still on its way to the server, in
     * which case [createdAt] is Firestore's local estimate of the server time.
     */
    val pending: Boolean = false,
)

/**
 * A new entry on its way into a store. [createdAt] is null for a fresh add (the
 * store stamps it) and set when restoring a deleted entry or migrating from the
 * device into an account, so the original date is preserved.
 */
data class EntryDraft(
    val text: String,
    val source: String,
    val tag: String,
    val createdAt: Long? = null,
)
