package com.hanifedma.ponder.core

/**
 * Finds playable/viewable media in an entry's text — the same YouTube, Vimeo,
 * Instagram and direct-image links the web app turns into previews.
 *
 * The web embeds an iframe; on Android the preview opens the link in the app
 * that owns it (YouTube, Instagram, browser), which is both faster and what
 * people expect from a native app.
 */
object Embeds {

    enum class Kind { YOUTUBE, VIMEO, INSTAGRAM, IMAGE, VIDEO }

    data class Media(
        val kind: Kind,
        val id: String,
        /** English noun used in "Play …" / "Open …" labels. */
        val label: String,
        /** Where the link goes if it can't be played in the app. */
        val openUrl: String,
        /** Still image to show as the preview, when one exists. */
        val thumbUrl: String? = null,
        /**
         * Page to load in the in-app player. Null means the app can't play it
         * itself and the preview falls back to opening [openUrl].
         */
        val embedUrl: String? = null,
    ) {
        val isPlayable: Boolean get() = embedUrl != null
    }

    private val YOUTUBE = Regex(
        "(?:youtube\\.com/(?:watch\\?(?:[^ ]*&)?v=|shorts/|embed/|live/)|youtu\\.be/)([A-Za-z0-9_-]{11})",
        RegexOption.IGNORE_CASE,
    )
    private val VIMEO = Regex("vimeo\\.com/(?:video/)?(\\d{6,})", RegexOption.IGNORE_CASE)
    private val INSTAGRAM = Regex(
        "instagram\\.com/(p|reel|reels|tv)/([A-Za-z0-9_-]+)",
        RegexOption.IGNORE_CASE,
    )
    private val IMAGE = Regex(
        "https?://[^\\s]+\\.(?:jpe?g|png|gif|webp|avif|bmp|svg)(?:\\?[^\\s]*)?",
        RegexOption.IGNORE_CASE,
    )

    // A link straight to a video file, which the player can show on its own.
    private val VIDEO_FILE = Regex(
        "https?://[^\\s]+\\.(?:mp4|webm|mov|m4v|ogv|mkv)(?:\\?[^\\s]*)?",
        RegexOption.IGNORE_CASE,
    )

    fun detect(text: String?): List<Media> {
        val str = text ?: return emptyList()
        if (str.isEmpty()) return emptyList()

        val out = ArrayList<Media>()
        val seen = HashSet<String>()
        fun add(m: Media) {
            if (seen.add(m.kind.name + ":" + m.id)) out.add(m)
        }

        for (m in YOUTUBE.findAll(str)) {
            val id = m.groupValues[1]
            add(
                Media(
                    kind = Kind.YOUTUBE,
                    id = id,
                    label = "YouTube video",
                    openUrl = "https://www.youtube.com/watch?v=$id",
                    thumbUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                    // playsinline keeps it in our player instead of handing the
                    // video to the system's full-screen one.
                    embedUrl = "https://www.youtube.com/embed/$id" +
                        "?autoplay=1&playsinline=1&rel=0&modestbranding=1",
                )
            )
        }
        for (m in VIMEO.findAll(str)) {
            val id = m.groupValues[1]
            add(
                Media(
                    kind = Kind.VIMEO,
                    id = id,
                    label = "Vimeo video",
                    openUrl = "https://vimeo.com/$id",
                    embedUrl = "https://player.vimeo.com/video/$id?autoplay=1&playsinline=1",
                )
            )
        }
        for (m in INSTAGRAM.findAll(str)) {
            val rawKind = m.groupValues[1].lowercase()
            val kind = if (rawKind == "reels") "reel" else rawKind
            val id = m.groupValues[2]
            val noun = when (kind) {
                "reel" -> "reel"
                "tv" -> "video"
                else -> "post"
            }
            add(
                Media(
                    kind = Kind.INSTAGRAM,
                    id = id,
                    label = "Instagram $noun",
                    openUrl = "https://www.instagram.com/$kind/$id/",
                    embedUrl = "https://www.instagram.com/$kind/$id/embed/",
                )
            )
        }
        for (m in VIDEO_FILE.findAll(str)) {
            val url = m.value
            add(
                Media(
                    kind = Kind.VIDEO,
                    id = url,
                    label = "video",
                    openUrl = url,
                    // Played by wrapping the file in a tiny HTML5 <video> page.
                    embedUrl = url,
                )
            )
        }
        for (m in IMAGE.findAll(str)) {
            val url = m.value
            add(
                Media(
                    kind = Kind.IMAGE,
                    id = url,
                    label = "image",
                    openUrl = url,
                    thumbUrl = url,
                )
            )
        }
        return out
    }
}
