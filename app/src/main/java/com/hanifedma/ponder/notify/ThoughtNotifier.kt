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

    /**
     * A channel's importance is fixed once it has been created, so correcting it
     * means a new id — and deleting the old one, or it lingers in the system
     * notification settings as a second, dead entry. [RETIRED_CHANNELS] is that
     * graveyard; add to it rather than reusing an id.
     */
    const val CHANNEL_THOUGHTS = "ponder.thoughts.v2"
    const val CHANNEL_KEEP_ALIVE = "ponder.keepalive.v1"

    /**
     * v1 was IMPORTANCE_LOW. Android files anything below IMPORTANCE_DEFAULT in
     * the shade's "silent" section, and several OEM skins — HyperOS and MIUI
     * among them — hide that whole section from the lock screen by default.
     * Stock Android and One UI show it, which is why this only went wrong on
     * some phones.
     */
    private val RETIRED_CHANNELS = listOf("ponder.thoughts.v1")

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

        // IMPORTANCE_DEFAULT, then silence taken away piece by piece: no sound,
        // no vibration, no light. Importance is what decides whether the lock
        // screen shows this at all, so it has to stay at DEFAULT — quietness is
        // a separate question, and the answer to it is the four setters below.
        //
        // DEFAULT still does not peek: heads-up needs IMPORTANCE_HIGH. So this
        // appears, silently, and interrupts nothing — including under Do Not
        // Disturb, which has no sound of ours to suppress.
        val thoughts = NotificationChannel(
            CHANNEL_THOUGHTS,
            tr("notify.channel.thoughts"),
            NotificationManager.IMPORTANCE_DEFAULT,
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

        // Upgrades only: on a fresh install there is nothing here to remove.
        for (id in RETIRED_CHANNELS) {
            runCatching { nm.deleteNotificationChannel(id) }
        }
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
            // Matches the channel's DEFAULT importance, which is what Android 7
            // and 8.0-below-O-devices read instead. PRIORITY_LOW would put it in
            // the same "silent" bucket the channel deliberately avoids.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Silence spelled out rather than via setSilent(), which additionally
            // drops the notification into an implicit group with GROUP_ALERT_
            // SUMMARY and no summary to alert — a rendering quirk waiting to
            // happen. On Oreo and up the channel wins anyway; these are for
            // older devices, where they are the only say we get.
            .setSound(null)
            .setVibrate(null)
            .setDefaults(0)
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
            // Deliberately the opposite of the thought above: as far out of the
            // way as a foreground service notification is allowed to be, and off
            // the lock screen entirely.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSound(null)
            .setVibrate(null)
            .setDefaults(0)
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
