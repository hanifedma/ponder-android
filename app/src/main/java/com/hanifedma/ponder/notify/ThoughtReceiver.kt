package com.hanifedma.ponder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Swiping the thought away (or hitting "clear all") lands here, and the next
 * random one goes up in its place.
 *
 * Being declared in the manifest is what makes the feature self-sustaining:
 * Android starts the process to deliver this, so the loop keeps turning long
 * after the app was last opened — no service, no alarms, no polling, and nothing
 * running in between.
 *
 * It carries no intent filter, so it is reachable only through the explicit
 * `PendingIntent` attached to the notification.
 */
class ThoughtReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        // Picking the next thought reads a file, which has no business happening
        // on the main thread; goAsync keeps the process alive until it is done.
        val pending = goAsync()
        Notifications.next(context.applicationContext) { pending.finish() }
    }

    companion object {
        /** Set on the thought notification's delete intent. */
        const val ACTION_DISMISSED = "com.hanifedma.ponder.action.THOUGHT_DISMISSED"
    }
}
