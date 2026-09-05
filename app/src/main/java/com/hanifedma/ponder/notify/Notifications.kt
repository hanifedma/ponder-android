package com.hanifedma.ponder.notify

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hanifedma.ponder.data.Entry
import com.hanifedma.ponder.data.Prefs
import com.hanifedma.ponder.data.Thought
import com.hanifedma.ponder.data.ThoughtPool
import com.hanifedma.ponder.widget.PonderWidgets
import java.util.concurrent.Executors

/**
 * Everything the "a thought lives in your notification shade" feature decides,
 * in one place, so the app, the boot receiver, the dismissal receiver and the
 * keep-alive service all behave identically.
 *
 * Two rules shape the design:
 *
 * 1. **The notification must never depend on the service.** A posted
 *    notification survives the process being killed, and the swipe that dismisses
 *    it starts the process back up through a manifest-registered receiver. So the
 *    feature keeps working even when the foreground service is off, blocked, or
 *    killed by an aggressive OEM.
 * 2. **The thought only changes when it is dismissed.** Anything else —
 *    a reboot, an app update, unlocking the phone — restores the same one.
 *
 * Work that touches the pool file runs on [io], a single background thread. That
 * also serialises it, so a dismissal arriving while the app is writing new
 * entries can never interleave.
 */
object Notifications {

    private const val TAG = "PonderNotifications"

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ponder-notifications").apply { isDaemon = true }
    }

    // ------------------------------------------------------- entry points

    /**
     * Brings notifications in line with the current settings: create the
     * channels, show or clear the thought, start or stop the keep-alive service.
     *
     * [allowServiceStart] is false wherever Android would refuse a background
     * foreground-service start (Android 12+) — attempting it there just logs a
     * failure, so callers that are not in the foreground and not answering
     * BOOT_COMPLETED simply skip it.
     */
    fun apply(context: Context, allowServiceStart: Boolean, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        ThoughtNotifier.ensureChannels(app)
        syncService(app, allowServiceStart)
        submit(app, done) { ensureBlocking(it) }
    }

    /** The notification was swiped away: show the next random thought. */
    fun next(context: Context, done: (() -> Unit)? = null) {
        submit(context.applicationContext, done) { nextBlocking(it) }
    }

    /** Post a thought if none is showing; otherwise leave the current one alone. */
    fun ensure(context: Context, done: (() -> Unit)? = null) {
        submit(context.applicationContext, done) { ensureBlocking(it) }
    }

    /**
     * Redraws the thought that is already showing — after a language switch, or
     * after a setting changed which space it should be drawn from.
     */
    fun refresh(context: Context, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        ThoughtNotifier.ensureChannels(app)
        submit(app, done) { refreshBlocking(it) }
    }

    /**
     * Forgets every space's entries. Called when the account changes, so one
     * person's notification can never be drawn from another's entries.
     */
    fun resetPool(context: Context, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        submit(app, done) {
            ThoughtPool(it).clear()
            Prefs(it).currentThoughtKey = null
            ThoughtNotifier.cancel(it)
            PonderWidgets.onPoolChanged(it)
        }
    }

    /**
     * Called by the app whenever a space's entries load, so the pool the
     * background code draws from stays current without it ever needing the
     * network or an account.
     *
     * The pool feeds the home-screen widget as well as the shade, and this is
     * its only writer — so the widget is told from here rather than from the
     * caller, which would otherwise have to guess when the write had landed.
     */
    fun updatePool(context: Context, spaceKey: String, entries: List<Entry>) {
        val app = context.applicationContext
        submit(app, null) {
            ThoughtPool(it).replace(spaceKey, entries)
            // A first entry should light the notification up straight away, and a
            // deleted one should be replaced rather than left dangling.
            ensureBlocking(it)
            PonderWidgets.onPoolChanged(it)
        }
    }

    // ------------------------------------------------------------- the work

    private fun nextBlocking(context: Context) {
        val prefs = Prefs(context)
        if (!enabled(context, prefs)) {
            clear(context, prefs)
            return
        }
        val pool = ThoughtPool(context)
        val thought = pool.pick(prefs.notifySpaceKey, prefs.currentThoughtKey)
        if (thought == null) {
            clear(context, prefs)
            return
        }
        prefs.currentThoughtKey = thought.key
        ThoughtNotifier.post(context, thought)
    }

    private fun ensureBlocking(context: Context) {
        val prefs = Prefs(context)
        if (!enabled(context, prefs)) {
            clear(context, prefs)
            return
        }
        if (ThoughtNotifier.isShowing(context)) return

        val pool = ThoughtPool(context)
        // A reboot or an app update is not a dismissal, so put back exactly what
        // was there; only draw a new one if that entry has since been deleted.
        val thought = currentWithinFilter(pool, prefs)
            ?: pool.pick(prefs.notifySpaceKey, null)
        if (thought == null) {
            clear(context, prefs)
            return
        }
        prefs.currentThoughtKey = thought.key
        ThoughtNotifier.post(context, thought)
    }

    private fun refreshBlocking(context: Context) {
        val prefs = Prefs(context)
        if (!enabled(context, prefs)) {
            clear(context, prefs)
            return
        }
        val pool = ThoughtPool(context)
        val thought = currentWithinFilter(pool, prefs) ?: pool.pick(prefs.notifySpaceKey, null)
        if (thought == null) {
            clear(context, prefs)
            return
        }
        prefs.currentThoughtKey = thought.key
        ThoughtNotifier.post(context, thought)
    }

    /**
     * The thought last shown, but only if it is still one the settings would
     * pick: an entry deleted since, or one from a space the "pick from" setting
     * no longer includes, has to give way to a fresh draw.
     */
    private fun currentWithinFilter(pool: ThoughtPool, prefs: Prefs): Thought? {
        val key = prefs.currentThoughtKey ?: return null
        return pool.candidates(prefs.notifySpaceKey).firstOrNull { it.key == key }
    }

    private fun clear(context: Context, prefs: Prefs) {
        ThoughtNotifier.cancel(context)
        prefs.currentThoughtKey = null
    }

    /** On, permitted by the system, and with something to show. */
    private fun enabled(context: Context, prefs: Prefs): Boolean =
        prefs.notificationsEnabled && ThoughtNotifier.canPost(context)

    // ---------------------------------------------------------- the service

    /** True when the keep-alive service should currently be running. */
    fun keepAliveWanted(context: Context): Boolean {
        val prefs = Prefs(context)
        // The service exists only to serve the notification; with the
        // notification off it would be a status bar entry that does nothing.
        return prefs.notificationsEnabled && prefs.keepAlive && ThoughtNotifier.canPost(context)
    }

    /**
     * Starts or stops the keep-alive service to match the settings. Cheap and
     * synchronous — safe to call from a receiver's `onReceive`.
     */
    fun syncService(context: Context, allowServiceStart: Boolean) {
        val app = context.applicationContext
        if (keepAliveWanted(app)) {
            if (allowServiceStart) startService(app)
        } else {
            runCatching { app.stopService(Intent(app, ThoughtService::class.java)) }
                .onFailure { Log.w(TAG, "Could not stop the keep-alive service", it) }
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, ThoughtService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure {
            // Android 12+ refuses a foreground-service start from the background.
            // Nothing breaks: the notification itself is self-sustaining, and the
            // next time the app is opened the service starts normally.
            Log.w(TAG, "Could not start the keep-alive service", it)
        }
    }

    // ------------------------------------------------------------------ util

    private fun submit(context: Context, done: (() -> Unit)?, block: (Context) -> Unit) {
        io.execute {
            try {
                block(context)
            } catch (e: Throwable) {
                Log.e(TAG, "Notification work failed", e)
            } finally {
                done?.invoke()
            }
        }
    }
}
