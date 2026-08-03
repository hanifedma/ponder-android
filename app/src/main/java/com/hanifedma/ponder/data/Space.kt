package com.hanifedma.ponder.data

/**
 * A "space" is its own little database: one Firestore collection under the
 * signed-in user, one on-device file, and its own set of tags.
 *
 * These values must stay identical to the web app's `SPACES` object — the
 * `collection` names are what make both clients read the same documents.
 */
data class Space(
    val key: String,
    val icon: String,
    val collection: String,
    val localKey: String,
    val tags: List<String>,
    val defaultTag: String,
    /** English fallback name, used when a translation is missing. */
    val fallbackName: String,
    val pdfTitle: String,
    val pdfFile: String,
) {
    /** Index of [tag] in this space's tag order; unknown tags sort last. */
    fun tagRank(tag: String?): Int {
        val i = tags.indexOf(tag)
        return if (i < 0) tags.size else i
    }
}

object Spaces {

    val PONDER = Space(
        key = "ponder",
        icon = "❝",
        collection = "quotes",
        localKey = "quotes_local_v1",
        tags = listOf(
            "extraterrestrial",
            "try to read this everyday",
            "very important",
            "pretty important",
            "interesting",
        ),
        defaultTag = "interesting",
        fallbackName = "Ponder",
        pdfTitle = "Ponder — Quotes & Thoughts",
        pdfFile = "ponder-backup",
    )

    val HEALTH = Space(
        key = "health",
        icon = "🌿",
        collection = "healthtips",
        localKey = "healthtips_local_v1",
        tags = listOf("pretty sure", "not really", "interesting"),
        defaultTag = "interesting",
        fallbackName = "Healthy Tips",
        pdfTitle = "Healthy Tips",
        pdfFile = "healthy-tips-backup",
    )

    /** Order shown in the top nav. */
    val order: List<Space> = listOf(PONDER, HEALTH)

    fun byKey(key: String?): Space = order.firstOrNull { it.key == key } ?: PONDER
}
