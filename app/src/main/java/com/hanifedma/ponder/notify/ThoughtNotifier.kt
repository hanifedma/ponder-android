package com.hanifedma.ponder.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hanifedma.ponder.MainActivity
import com.hanifedma.ponder.R
import com.hanifedma.ponder.data.Prefs
import com.hanifedma.ponder.i18n.Tr

/**
 * Builds and posts the two notifications this app can show.
 *
 * The **thought** is the feature: one quote, thought or tip sitting in the
 * shade. It is deliberately silent and dismissible — swiping it away is the
 * gesture that asks for the next one, so it must never be `ongoing`, and it must
 * not auto-cancel on tap either (tapping opens the app and leaves the thought
 * where it is).
 *
 * The **keep-alive** notification is the price Android charges for a foreground
 * service. It lives on its own minimum-importance channel so it collapses to the
 * bottom of the shade with no status-bar icon and no sound.
 */
object ThoughtNotifier {

    /** Bumping a channel id is the only way to change its importance later. */
    const val CHANNEL_THOUGHTS = "ponder.thoughts.v1"
    const val CHANNEL_KEEP_ALIVE = "ponder.keepalive.v1"

    const val ID_THOUGHT = 4101
    const val ID_KEEP_ALIVE = 4102

    /** Anything longer is cut: a notification is a glance, not a document. */
    private const val MAX_CHARS = 1200

    private const val TAG = "PonderNotifier"

    // ------------------------------------------------------------- channels

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val tr = Tr(Prefs(context).lang)

        // IMPORTANCE_LOW: no sound, no heads-up, no interruption — but still
        // shown in the shade and on the lock screen, which is the whole point.
        // It also means Do Not Disturb has nothing to suppress.
        val thoughts = NotificationChannel(
            CHANNEL_THOUGHTS,
            tr("notify.channel.thoughts"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = tr("notify.channel.thoughts.desc")
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val keepAlive = NotificationChannel(
            CHANNEL_KEEP_ALIVE,
            tr("notify.channel.keepAlive"),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = tr("notify.channel.keepAlive.desc")
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        runCatching { nm.createNotificationChannels(listOf(thoughts, keepAlive)) }
            .onFailure { Log.e(TAG, "Could not create the notification channels", it) }
    }

    // -------------------------------------------------------------- posting

    /**
     * True when a notification this app posts would actually be seen: the person
     * has not turned notifications off for Ponder (which on Android 13+ includes
     * never having granted the permission), and has not blocked the channel.
     */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = nm.getNotificationChannel(CHANNEL_THOUGHTS) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** True while a thought is already sitting in the shade. */
    fun isShowing(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { nm.activeNotifications.any { it.id == ID_THOUGHT } }
            .getOrDefault(false)
    }

    /**
     * Lint asks for a POST_NOTIFICATIONS check right here; there is one, in
     * [canPost], which every caller goes through — and the call is wrapped
     * anyway, because the permission can be revoked between the check and the
     * post and a background thread must not die of it.
     */
    @SuppressLint("MissingPermission")
    fun post(context: Context, thought: Thought) {
        val notification = build(context, thought)
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_THOUGHT, notification)
        }.onFailure {
            Log.w(TAG, "Could not post the thought notification", it)
        }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_THOUGHT) }
    }

    // -------------------------------------------------------------- building

    private fun build(context: Context, thought: Thought): android.app.Notification {
        val tr = Tr(Prefs(context).lang)
        val space = ThoughtPool.spaceOf(thought.spaceKey)

        val text = thought.text.trim().let {
            if (it.length > MAX_CHARS) it.take(MAX_CHARS).trimEnd() + "…" else it
        }
        val source = thought.source.trim()
        val body = if (source.isEmpty()) text else "$text\n\n— $source"

        return NotificationCompat.Builder(context, CHANNEL_THOUGHTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(tr("tab." + space.key, space.fallbackName))
            // Collapsed: the first line of the quote. Expanded: all of it.
            .setContentText(text.replace('\n', ' '))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(tr.tag(thought.tag))
            // Readable on the lock screen rather than hidden behind "1 new".
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            // Not ongoing, so it can be swiped away — that swipe is the feature.
            .setOngoing(false)
            // Not auto-cancel, so tapping opens the app *and* leaves the thought
            // in place; only a deliberate dismissal moves it on.
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setContentIntent(openAppIntent(context))
            .setDeleteIntent(dismissedIntent(context))
            .build()
    }

    /** The always-silent notification that lets the keep-alive service exist. */
    fun buildKeepAlive(context: Context): android.app.Notification {
        val tr = Tr(Prefs(context).lang)
        return NotificationCompat.Builder(context, CHANNEL_KEEP_ALIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(tr("notify.keepAlive.title"))
            .setContentText(tr("notify.keepAlive.text"))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setContentIntent(openAppIntent(context))
            .build()
    }

    // -------------------------------------------------------------- intents

    /**
     * Behaves like tapping the launcher icon: brings the existing task forward if
     * there is one rather than starting a second copy of the app.
     */
    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        return PendingIntent.getActivity(context, REQ_OPEN, intent, FLAGS)
    }

    /** Fired by the system when the notification is swiped away or cleared. */
    private fun dismissedIntent(context: Context): PendingIntent {
        val intent = Intent(context, ThoughtReceiver::class.java)
            .setAction(ThoughtReceiver.ACTION_DISMISSED)
        return PendingIntent.getBroadcast(context, REQ_DISMISS, intent, FLAGS)
    }

    private const val REQ_OPEN = 1
    private const val REQ_DISMISS = 2

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** `--accent` from the web app's palette. */
    private val ACCENT = Color.parseColor("#22C55E")
}
