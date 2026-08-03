package com.hanifedma.ponder.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.hanifedma.ponder.core.DateFmt
import com.hanifedma.ponder.core.Embeds
import com.hanifedma.ponder.data.Entry
import com.hanifedma.ponder.data.Space
import com.hanifedma.ponder.i18n.Lang
import com.hanifedma.ponder.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Writes the current space to an A4 PDF backup — the same export the web app
 * offers, thumbnails included.
 *
 * Two things are better than the web version: long entries flow onto the next
 * page instead of running off the bottom, and text is drawn with the system font
 * stack, so Korean entries come out readable rather than blank.
 */
object PdfExporter {

    // A4 in PostScript points, the same unit the web export uses.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 42f

    private const val MAX_IMAGES_PER_ENTRY = 3
    private const val MAX_IMAGE_W = 300f
    private const val MAX_IMAGE_H = 200f

    private const val THUMB_TIMEOUT_MS = 9_000
    private const val THUMB_MAX_PX = 1200
    private const val THUMB_PARALLELISM = 4

    private const val TAG = "PonderPdf"

    suspend fun export(
        context: Context,
        uri: Uri,
        entries: List<Entry>,
        space: Space,
        lang: Lang,
        onProgress: (String) -> Unit,
    ): Result<Unit> = runCatching {
        // Preload each renderable thumbnail once: YouTube stills and direct
        // images. Vimeo and Instagram expose no reliable still, so they're
        // skipped — the same choice the web export makes.
        val urls = entries
            .flatMap { Embeds.detect(it.text) }
            .mapNotNull { thumbUrlOf(it) }
            .distinct()

        val thumbs: Map<String, Bitmap> = if (urls.isEmpty()) {
            emptyMap()
        } else {
            onProgress("pdf.fetching")
            fetchThumbs(urls)
        }

        onProgress("pdf.building")
        try {
            withContext(Dispatchers.IO) {
                val document = PdfDocument()
                try {
                    val fonts = Fonts()
                    // Page numbers need the total up front, and a finished PDF
                    // page cannot be reopened — so lay the document out once
                    // without drawing to count pages, then render for real.
                    val counter = PageCursor.counting()
                    layout(counter, entries, space, lang, thumbs, fonts)
                    counter.finish()

                    val renderer = PageCursor.drawing(document, counter.pageCount, fonts.pageNumber)
                    layout(renderer, entries, space, lang, thumbs, fonts)
                    renderer.finish()

                    val out = context.contentResolver.openOutputStream(uri)
                        ?: error("Could not open $uri for writing")
                    out.use { document.writeTo(it) }
                } finally {
                    document.close()
                }
            }
        } finally {
            thumbs.values.forEach { if (!it.isRecycled) it.recycle() }
        }
        Unit
    }.onFailure { Log.e(TAG, "PDF export failed", it) }

    // ------------------------------------------------------------ rendering

    private class Fonts {
        val maxWidth = PAGE_W - MARGIN * 2

        val title = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 20f
            color = Color.rgb(20, 20, 20)
        }
        val subtitle = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.SANS_SERIF
            textSize = 10f
            color = Color.rgb(130, 130, 130)
        }
        val body = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.SANS_SERIF
            textSize = 12f
            color = Color.rgb(25, 25, 25)
        }
        val meta = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.SANS_SERIF
            textSize = 9f
            color = Color.rgb(120, 120, 120)
        }
        val rule = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(220, 220, 220)
            strokeWidth = 0.7f
        }
        val pageNumber = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.SANS_SERIF
            textSize = 8f
            color = Color.rgb(160, 160, 160)
            textAlign = Paint.Align.RIGHT
        }
    }

    /** The document's content, described once and replayed by both passes. */
    private fun layout(
        pages: PageCursor,
        entries: List<Entry>,
        space: Space,
        lang: Lang,
        thumbs: Map<String, Bitmap>,
        fonts: Fonts,
    ) {
        pages.start()

        pages.paragraph(buildLayout(space.pdfTitle, fonts.title, fonts.maxWidth), MARGIN, 2f)

        val countWord = if (entries.size == 1) {
            Strings.t(lang, "count.one")
        } else {
            Strings.tf(lang, "count.all", mapOf("n" to entries.size))
        }
        val subtitle = Strings.t(lang, "pdf.exported") +
            " " + DateFmt.stamp(lang, System.currentTimeMillis()) +
            "  ·  " + countWord
        pages.paragraph(buildLayout(subtitle, fonts.subtitle, fonts.maxWidth), MARGIN, 14f)

        for (entry in entries) {
            pages.paragraph(
                buildLayout("“${entry.text}”", fonts.body, fonts.maxWidth),
                MARGIN,
                6f,
            )

            var shown = 0
            for (media in Embeds.detect(entry.text)) {
                if (shown >= MAX_IMAGES_PER_ENTRY) break
                val bitmap = thumbUrlOf(media)?.let { thumbs[it] } ?: continue
                if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) continue

                var w = minOf(MAX_IMAGE_W, fonts.maxWidth)
                var h = w * bitmap.height / bitmap.width
                if (h > MAX_IMAGE_H) {
                    h = MAX_IMAGE_H
                    w = h * bitmap.width / bitmap.height
                }
                pages.image(bitmap, MARGIN, w, h, 8f)
                shown++
            }

            val meta = buildList {
                if (entry.source.isNotEmpty()) add(entry.source)
                add(Strings.tagLabel(lang, entry.tag))
                add(DateFmt.format(lang, entry.createdAt))
            }.joinToString("  ·  ")
            pages.paragraph(buildLayout(meta, fonts.meta, fonts.maxWidth), MARGIN, 12f)

            pages.rule(fonts.rule, MARGIN, PAGE_W - MARGIN, 6f)
        }
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Float): StaticLayout {
        val w = width.toInt().coerceAtLeast(1)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .setIncludePad(false)
            .build()
    }

    /**
     * Walks down the page, starting a new one whenever the next piece of content
     * would cross the bottom margin. Paragraphs break line by line, so a long
     * entry continues onto the next page instead of being clipped.
     *
     * In counting mode it does the identical arithmetic while drawing nothing,
     * which is how the page total is known before the first stroke is made.
     */
    private class PageCursor private constructor(
        private val document: PdfDocument?,
        private val totalPages: Int,
        private val pageNumberPaint: TextPaint?,
    ) {
        companion object {
            fun counting() = PageCursor(null, 0, null)

            fun drawing(document: PdfDocument, totalPages: Int, pageNumberPaint: TextPaint) =
                PageCursor(document, totalPages, pageNumberPaint)
        }

        private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private var page: PdfDocument.Page? = null
        private var started = false

        var pageCount = 0
            private set

        private var y = MARGIN

        fun start() {
            if (started) return
            started = true
            openPage()
        }

        private fun openPage() {
            closePage()
            pageCount++
            y = MARGIN
            val doc = document ?: return
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageCount).create()
            page = doc.startPage(info)
        }

        private fun closePage() {
            val doc = document ?: return
            val open = page ?: return
            pageNumberPaint?.let { paint ->
                open.canvas.drawText(
                    "$pageCount / $totalPages",
                    PAGE_W - MARGIN,
                    PAGE_H - 18f,
                    paint,
                )
            }
            doc.finishPage(open)
            page = null
        }

        private fun remaining(): Float = PAGE_H - MARGIN - y

        fun paragraph(layout: StaticLayout, x: Float, gapAfter: Float) {
            var line = 0
            while (line < layout.lineCount) {
                val top = layout.getLineTop(line)
                var last = line
                // Take as many lines as fit in what is left of this page.
                while (last < layout.lineCount &&
                    layout.getLineBottom(last) - top <= remaining()
                ) {
                    last++
                }
                if (last == line) {
                    if (y > MARGIN) {
                        openPage() // retry the same line on a fresh page
                        continue
                    }
                    // A single line taller than a whole page: draw it anyway
                    // rather than loop forever.
                    last = line + 1
                }
                val bottom = layout.getLineBottom(last - 1)
                page?.canvas?.let { c ->
                    c.save()
                    c.translate(x, y - top)
                    c.clipRect(0f, top.toFloat(), layout.width.toFloat(), bottom.toFloat())
                    layout.draw(c)
                    c.restore()
                }
                y += (bottom - top).toFloat()
                line = last
            }
            y += gapAfter
        }

        fun image(bitmap: Bitmap, x: Float, w: Float, h: Float, gapAfter: Float) {
            if (h > remaining() && y > MARGIN) openPage()
            page?.canvas?.drawBitmap(bitmap, null, RectF(x, y, x + w, y + h), imagePaint)
            y += h + gapAfter
        }

        fun rule(paint: Paint, x0: Float, x1: Float, gapAfter: Float) {
            if (remaining() < 2f) return // a rule on the bottom edge adds nothing
            page?.canvas?.drawLine(x0, y, x1, y, paint)
            y += gapAfter
        }

        fun finish() {
            closePage()
        }
    }

    // ------------------------------------------------------------ thumbnails

    private fun thumbUrlOf(media: Embeds.Media): String? = when (media.kind) {
        Embeds.Kind.YOUTUBE -> media.thumbUrl
        Embeds.Kind.IMAGE -> media.openUrl
        else -> null
    }

    private suspend fun fetchThumbs(urls: List<String>): Map<String, Bitmap> = coroutineScope {
        val gate = Semaphore(THUMB_PARALLELISM)
        urls.map { url ->
            async(Dispatchers.IO) { gate.withPermit { url to loadBitmap(url) } }
        }.awaitAll().mapNotNull { (url, bitmap) -> bitmap?.let { url to it } }.toMap()
    }

    /** Best effort: a failure just means that entry has no picture in the PDF. */
    private fun loadBitmap(url: String): Bitmap? = runCatching {
        val bytes = download(url) ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= THUMB_MAX_PX || h / 2 >= THUMB_MAX_PX) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun download(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = THUMB_TIMEOUT_MS
                readTimeout = THUMB_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail fetch failed for $url", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
