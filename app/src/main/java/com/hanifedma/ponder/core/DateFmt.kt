package com.hanifedma.ponder.core

import com.hanifedma.ponder.i18n.Lang
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Dates are shown in the medium form for the selected interface language —
 * "Aug 2, 2026" in English, "2026. 8. 2." in Korean — matching the web app's
 * `toLocaleDateString(…, { year, month: "short", day })`.
 */
object DateFmt {

    private val cache = HashMap<Lang, DateFormat>()

    private fun localeFor(lang: Lang): Locale =
        if (lang == Lang.KO) Locale.KOREAN else Locale.ENGLISH

    // DateFormat is not thread-safe, and this is called from both composition and
    // the PDF export coroutine, so guard the shared instances.
    @Synchronized
    private fun formatterFor(lang: Lang): DateFormat =
        cache.getOrPut(lang) { DateFormat.getDateInstance(DateFormat.MEDIUM, localeFor(lang)) }

    @Synchronized
    fun format(lang: Lang, millis: Long?): String {
        if (millis == null || millis <= 0L) return if (lang == Lang.KO) "방금" else "Just now"
        return formatterFor(lang).format(Date(millis))
    }

    /** `yyyy-MM-dd`, used in the exported PDF's file name. */
    fun isoDate(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    /** Full date + time stamp printed in the PDF header. */
    fun stamp(lang: Lang, millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, localeFor(lang))
            .format(Date(millis))
}
