package com.hanifedma.ponder.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.hanifedma.ponder.data.Prefs
import com.hanifedma.ponder.data.Thought
import com.hanifedma.ponder.data.ThoughtPool
import java.util.concurrent.Executors

/**
 * Everything the home-screen widget decides, so the provider, the app and the
 * pool all agree on what a widget should currently be showing.
 *
 * It follows the same rule as the notification: **the quote only changes when
 * something asks it to.** A redraw — a reboot, a resize, a theme switch, the app
 * saving a new entry — puts back exactly what was there. Only the shuffle
 * button, or that entry disappearing, draws a different one.
 *
 * Reading the pool is file I/O, and every caller here is either a broadcast
 * receiver or the UI thread, so the work runs on [io]. Being a single thread
 * also serialises it: two widgets refreshing at once cannot interleave.
 */
object PonderWidgets {

    private const val TAG = "PonderWidgets"

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ponder-widgets").apply { isDaemon = true }
    }

    /** The ids of every Ponder widget currently on a home screen. */
    fun ids(context: Context): IntArray = runCatching {
        val manager = AppWidgetManager.getInstance(context) ?: return IntArray(0)
        manager.getAppWidgetIds(ComponentName(context, PonderWidget::class.java))
    }.getOrElse {
        Log.w(TAG, "Could not list the placed widgets", it)
        IntArray(0)
    }

    /**
     * Cheap and synchronous — no file is touched — so the settings screen can ask
     * before deciding whether the widget section is worth showing at all.
     */
    fun anyPlaced(context: Context): Boolean = ids(context).isNotEmpty()

    /**
     * Redraw every widget, keeping the quote each one is showing. Called from
     * the UI thread whenever something it owns changes the way widgets look —
     * the theme, the language, the section they draw from — so even the id
     * lookup, cheap as it is, happens off it.
     */
    fun refreshAll(context: Context, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        submit(done) { renderAll(app, ids(app)) }
    }

    fun refresh(context: Context, appWidgetIds: IntArray, done: (() -> Unit)? = null) {
        if (appWidgetIds.isEmpty()) {
            // Nothing to do, but the caller may be a receiver holding its
            // broadcast open on this callback — so it still has to fire.
            done?.invoke()
            return
        }
        val app = context.applicationContext
        submit(done) { renderAll(app, appWidgetIds) }
    }

    /** The pool is read once here, not per widget: they all draw from the same one. */
    private fun renderAll(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context) ?: return
        val store = WidgetStore(context)
        val candidates = ThoughtPool(context).candidates(Prefs(context).widgetSpaceKey)
        for (id in appWidgetIds) {
            render(context, manager, store, candidates, id, advance = false)
        }
    }

    /** The shuffle button on one widget: draw a different quote, just for it. */
    fun shuffle(context: Context, appWidgetId: Int, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        submit(done) {
            val manager = AppWidgetManager.getInstance(app) ?: return@submit
            val candidates = ThoughtPool(app).candidates(Prefs(app).widgetSpaceKey)
            render(app, manager, WidgetStore(app), candidates, appWidgetId, advance = true)
        }
    }

    /**
     * The entries behind the widgets changed — added to, deleted from, or a
     * different account signed in. Redraw, which also replaces any quote that no
     * longer exists.
     */
    fun onPoolChanged(context: Context) {
        val app = context.applicationContext
        submit(null) { renderAll(app, ids(app)) }
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext
        submit(null) { WidgetStore(app).forget(appWidgetIds) }
    }

    fun forgetAll(context: Context) {
        val app = context.applicationContext
        submit(null) { WidgetStore(app).forgetAll() }
    }

    // ------------------------------------------------------------- the work

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        store: WidgetStore,
        candidates: List<Thought>,
        appWidgetId: Int,
        advance: Boolean,
    ) {
        val currentKey = store.currentKey(appWidgetId)
        // Resolving against the current candidates is what handles both the
        // entry being deleted and the "pick from" setting narrowing: either way
        // the remembered key stops matching and a fresh quote takes its place.
        val current = candidates.firstOrNull { it.key == currentKey }
        val thought = when {
            advance -> ThoughtPool.pickFrom(candidates, currentKey)
            current != null -> current
            else -> ThoughtPool.pickFrom(candidates, null)
        }
        store.setCurrentKey(appWidgetId, thought?.key)

        val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
        val views = WidgetViews.build(context, appWidgetId, thought, options)
        runCatching { manager.updateAppWidget(appWidgetId, views) }
            .onFailure {
                // The widget can be removed between listing the ids and drawing
                // into it; that is not worth more than a line in the log.
                Log.w(TAG, "Could not update widget $appWidgetId", it)
            }
    }

    private fun submit(done: (() -> Unit)?, block: () -> Unit) {
        io.execute {
            try {
                block()
            } catch (e: Throwable) {
                Log.e(TAG, "Widget work failed", e)
            } finally {
                done?.invoke()
            }
        }
    }
}
