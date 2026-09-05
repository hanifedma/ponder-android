package com.hanifedma.ponder.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * The home-screen widget: one saved quote, thought or tip, with a shuffle
 * button for the next.
 *
 * Every entry point here does the same two things — hand the work to
 * [PonderWidgets], which owns the decisions, and hold the broadcast open with
 * `goAsync` until it finishes, since reading the pool is file I/O and a
 * receiver's `onReceive` runs on the main thread.
 */
class PonderWidget : AppWidgetProvider() {

    /** A widget was placed, the launcher restarted, or the app asked for a redraw. */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        PonderWidgets.refresh(context, appWidgetIds) { pending.finish() }
    }

    /**
     * Resized, or the phone rotated. The quote stays; only the layout around it
     * is recomputed for the new dimensions.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val pending = goAsync()
        PonderWidgets.refresh(context, intArrayOf(appWidgetId)) { pending.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SHUFFLE) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val pending = goAsync()
            PonderWidgets.shuffle(context, appWidgetId) { pending.finish() }
            return
        }
        super.onReceive(context, intent)
    }

    /** Widgets dragged off the home screen: drop what they were showing. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        PonderWidgets.forget(context, appWidgetIds)
    }

    /** The last one is gone; nothing stored about any of them still means anything. */
    override fun onDisabled(context: Context) {
        PonderWidgets.forgetAll(context)
    }

    companion object {
        /** Sent by the shuffle button, carrying the widget it belongs to. */
        const val ACTION_SHUFFLE = "com.hanifedma.ponder.action.WIDGET_SHUFFLE"
    }
}
