package com.hanifedma.ponder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.hanifedma.ponder.MainActivity
import com.hanifedma.ponder.R
import com.hanifedma.ponder.data.Prefs
import com.hanifedma.ponder.data.Thought
import com.hanifedma.ponder.data.ThoughtPool
import com.hanifedma.ponder.i18n.Tr

/**
 * Turns one thought into the `RemoteViews` a launcher can draw.
 *
 * Colours come from the app's own palette rather than the system's, and follow
 * the in-app light/dark choice, so the widget looks like Ponder and not like a
 * generic card. The launcher runs in another process, so everything is pushed
 * across as explicit values — there is no theme to inherit.
 */
object WidgetViews {

    // The app's palette, duplicated as plain ints because ui.theme.Color is
    // Compose types the launcher process has no use for.
    private object Dark {
        const val TEXT = 0xFFF0F0F0.toInt()
        const val MUTED = 0xFFA6A6A6.toInt()
        const val FAINT = 0xFF6F6F6F.toInt()
        const val ACCENT = 0xFF22C55E.toInt()
    }

    private object Light {
        const val TEXT = 0xFF1A1A1A.toInt()
        const val MUTED = 0xFF565B64.toInt()
        const val FAINT = 0xFF868E96.toInt()
        const val ACCENT = 0xFF16A34A.toInt()
    }

    /**
     * Tag hues, shared across both themes exactly as in `ui/theme/Color.kt` and
     * the web app's `--tag-*` variables.
     */
    private fun tagColor(tag: String?): Int? = when (tag) {
        "extraterrestrial", "pretty sure" -> 0xFF14B8A6.toInt()
        "try to read this everyday" -> 0xFF3B82F6.toInt()
        "very important", "not really" -> 0xFFF43F5E.toInt()
        "pretty important" -> 0xFFF59E0B.toInt()
        "interesting" -> 0xFFA855F7.toInt()
        else -> null
    }

    /**
     * One widget, sized for the space the launcher reports.
     *
     * Android 12 hands over the exact sizes the widget will be shown at, one per
     * orientation, and can hold a different layout for each — so a phone that is
     * rotated swaps to a correctly-proportioned version with no round trip to
     * this process. Before that there is only a min/max range, and the minimum
     * is the honest choice: overshooting means clipped text.
     */
    fun build(
        context: Context,
        appWidgetId: Int,
        thought: Thought?,
        options: Bundle?,
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = reportedSizes(options)
            if (!sizes.isNullOrEmpty()) {
                return forReportedSizes(context, appWidgetId, thought, sizes)
            }
        }
        // Pre-12, and wherever a launcher reports nothing: only a range is
        // known, and the minimum is the honest end of it. Overshooting means
        // text clipped in half; undershooting only wastes a little room.
        val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        return single(context, appWidgetId, thought, width, height)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun forReportedSizes(
        context: Context,
        appWidgetId: Int,
        thought: Thought?,
        sizes: List<SizeF>,
    ): RemoteViews {
        if (sizes.size == 1) {
            val only = sizes[0]
            return single(context, appWidgetId, thought, only.width.toInt(), only.height.toInt())
        }
        // The multi-size constructor rejects maps it considers too large, and
        // the cost of guessing wrong is a widget that never draws — so fall back
        // to the smallest reported size, which is always safe to render.
        return runCatching {
            RemoteViews(
                sizes.take(MAX_SIZE_VARIANTS).associateWith { size ->
                    single(context, appWidgetId, thought, size.width.toInt(), size.height.toInt())
                }
            )
        }.getOrElse {
            val smallest = sizes.minBy { it.width * it.height }
            single(context, appWidgetId, thought, smallest.width.toInt(), smallest.height.toInt())
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun reportedSizes(options: Bundle?): List<SizeF>? {
        val raw = options?.get(AppWidgetManager.OPTION_APPWIDGET_SIZES) ?: return null
        return (raw as? List<*>)?.filterIsInstance<SizeF>()?.filter { it.width > 0 && it.height > 0 }
    }

    private fun single(
        context: Context,
        appWidgetId: Int,
        thought: Thought?,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val prefs = Prefs(context)
        val tr = Tr(prefs.lang)
        val dark = prefs.darkTheme

        val text = thought?.text?.trim().orEmpty()
        val source = thought?.source?.trim().orEmpty()
        val layout = WidgetSizing.of(
            widthDp = widthDp,
            heightDp = heightDp,
            textLength = if (thought == null) EMPTY_STATE_LENGTH else text.length,
            hasSource = source.isNotEmpty(),
            fontScale = context.resources.configuration.fontScale,
        )

        val views = RemoteViews(context.packageName, R.layout.widget_thought)

        val textColor = if (dark) Dark.TEXT else Light.TEXT
        val mutedColor = if (dark) Dark.MUTED else Light.MUTED
        val faintColor = if (dark) Dark.FAINT else Light.FAINT
        val accentColor = if (dark) Dark.ACCENT else Light.ACCENT

        views.setInt(
            R.id.widget_card,
            "setBackgroundResource",
            if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light,
        )
        views.setViewPadding(
            R.id.widget_card,
            layout.paddingDp.dp(context),
            layout.paddingDp.dp(context),
            layout.paddingDp.dp(context),
            layout.paddingDp.dp(context),
        )

        // ------------------------------------------------------------ header

        views.setTextViewTextSize(R.id.widget_meta, COMPLEX_UNIT_SP, layout.metaTextSp)
        views.setTextColor(R.id.widget_meta, mutedColor)
        views.setTextViewText(R.id.widget_meta, metaLabel(tr, thought, layout, mutedColor))

        views.setInt(
            R.id.widget_next,
            "setBackgroundResource",
            if (dark) R.drawable.widget_pill_dark else R.drawable.widget_pill_light,
        )
        views.setInt(R.id.widget_next, "setColorFilter", accentColor)
        // This is also what sizes the button: the ImageView wraps its glyph, so
        // the padding around it is the difference between a comfortable tap
        // target and a cramped one. Set after the background on purpose — a
        // drawable that carries its own padding replaces the view's.
        val glyphInset = layout.buttonInsetDp.dp(context)
        views.setViewPadding(R.id.widget_next, glyphInset, glyphInset, glyphInset, glyphInset)
        views.setContentDescription(R.id.widget_next, tr("widget.next"))

        // -------------------------------------------------------------- body

        views.setTextViewTextSize(R.id.widget_body, COMPLEX_UNIT_SP, layout.bodyTextSp)
        views.setInt(R.id.widget_body, "setMaxLines", layout.bodyMaxLines)

        if (thought == null) {
            // Nothing saved yet, or nothing in the section this widget draws
            // from. Say which, rather than showing a blank card that reads as a
            // bug — and drop the shuffle button, since there is nothing to
            // shuffle between.
            views.setTextColor(R.id.widget_body, mutedColor)
            views.setTextViewText(R.id.widget_body, tr("widget.empty"))
            views.setViewVisibility(R.id.widget_next, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_source, android.view.View.GONE)
        } else {
            views.setTextColor(R.id.widget_body, textColor)
            views.setTextViewText(R.id.widget_body, text)
            views.setViewVisibility(R.id.widget_next, android.view.View.VISIBLE)

            if (layout.showSource) {
                views.setViewVisibility(R.id.widget_source, android.view.View.VISIBLE)
                views.setTextViewTextSize(R.id.widget_source, COMPLEX_UNIT_SP, layout.metaTextSp)
                views.setTextColor(R.id.widget_source, faintColor)
                views.setTextViewText(R.id.widget_source, "— $source")
            } else {
                views.setViewVisibility(R.id.widget_source, android.view.View.GONE)
            }
        }

        // ------------------------------------------------------------ clicks

        // Tapping the card opens the app, which is what a widget is expected to
        // do; the explicit button is what draws another quote. Making the whole
        // card shuffle would be closer to the in-app Shuffle card, but it would
        // also mean no way at all to reach the app from here.
        views.setOnClickPendingIntent(R.id.widget_card, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_next, shuffle(context, appWidgetId))

        return views
    }

    /**
     * "❝ Ponder · Very important", with the tag in its own hue — the widget's
     * one-line echo of the coloured badge on an entry card.
     */
    private fun metaLabel(
        tr: Tr,
        thought: Thought?,
        layout: WidgetLayout,
        mutedColor: Int,
    ): CharSequence {
        if (thought == null) {
            return if (layout.showSpaceLabel) tr("widget.title") else ""
        }
        val out = SpannableStringBuilder()
        if (layout.showSpaceLabel) {
            val space = ThoughtPool.spaceOf(thought.spaceKey)
            out.append(tr("tab." + space.key, space.fallbackName))
        }
        if (layout.showTag) {
            val label = tr.tag(thought.tag)
            if (label.isNotEmpty()) {
                if (out.isNotEmpty()) out.append("  ·  ")
                val start = out.length
                out.append(label)
                out.setSpan(
                    ForegroundColorSpan(tagColor(thought.tag) ?: mutedColor),
                    start,
                    out.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return out
    }

    // ------------------------------------------------------------- intents

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        return PendingIntent.getActivity(context, 0, intent, FLAGS)
    }

    /**
     * One PendingIntent per widget. Both the request code and the data Uri are
     * unique because `PendingIntent` matching ignores extras entirely — without
     * the Uri every widget would share one intent and shuffling any of them
     * would shuffle whichever was registered last.
     */
    private fun shuffle(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, PonderWidget::class.java).apply {
            action = PonderWidget.ACTION_SHUFFLE
            data = Uri.parse("ponder://widget/$appWidgetId/shuffle")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(context, appWidgetId, intent, FLAGS)
    }

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** `TypedValue.COMPLEX_UNIT_SP`, without dragging the whole class in. */
    private const val COMPLEX_UNIT_SP = 2

    /** Roughly the length of the empty-state sentence, for sizing purposes. */
    private const val EMPTY_STATE_LENGTH = 60

    /** More layouts than a launcher plausibly needs, and a limit the platform enforces. */
    private const val MAX_SIZE_VARIANTS = 4

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density + 0.5f).toInt()
}
