package com.hanifedma.ponder.widget

import android.content.Context

/**
 * Which thought each placed widget is currently showing.
 *
 * Per instance, not global: two Ponder widgets on the same screen should be two
 * different quotes, and shuffling one must not disturb the other. Persisted
 * rather than held in memory because a widget outlives the app's process by a
 * long way — the launcher may ask for a redraw days after Ponder was last open.
 */
class WidgetStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("ponder_widgets", Context.MODE_PRIVATE)

    /** `Thought.key` of what widget [appWidgetId] shows, or null if untouched. */
    fun currentKey(appWidgetId: Int): String? = sp.getString(key(appWidgetId), null)

    fun setCurrentKey(appWidgetId: Int, thoughtKey: String?) {
        sp.edit().apply {
            if (thoughtKey == null) remove(key(appWidgetId)) else putString(key(appWidgetId), thoughtKey)
        }.apply()
    }

    /**
     * Called when widgets are removed from the home screen. Without this the
     * preferences file grows by one dead entry per widget ever placed, and ids
     * are recycled — so a new widget could inherit a stranger's quote.
     */
    fun forget(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        sp.edit().apply { for (id in appWidgetIds) remove(key(id)) }.apply()
    }

    /** The last widget is gone; nothing here can still be about anything. */
    fun forgetAll() {
        sp.edit().clear().apply()
    }

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"
}
