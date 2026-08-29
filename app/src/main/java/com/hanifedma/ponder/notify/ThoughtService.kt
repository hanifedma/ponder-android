package com.hanifedma.ponder.notify

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps Ponder alive in the background so the thought comes back after a reboot
 * and survives a swipe out of recent apps.
 *
 * It is a *safety net*, not the mechanism: the notification and its dismissal
 * loop work perfectly well with this service switched off or killed. Everything
 * here is therefore written to fail quietly — if Android refuses the foreground
 * start (it does, in the background, from Android 12 on) the service simply
 * stands down after making sure the thought is showing.
 */
class ThoughtService : Service() {

    /**
     * Unlocking the phone is a good moment to notice that something — a system
     * "clear all" that missed our delete intent, a notification the OS dropped
     * under memory pressure — left the shade empty, and to put the thought back.
     * Registered here rather than in the manifest because USER_PRESENT is not
     * delivered to manifest receivers.
     */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Notifications.ensure(applicationContext)
        }
    }

    private var unlockRegistered = false
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        ThoughtNotifier.ensureChannels(this)
        runCatching {
            ContextCompat.registerReceiver(
                this,
                unlockReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            unlockRegistered = true
        }.onFailure { Log.w(TAG, "Could not watch for unlocks", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // `intent` is null when the system restarts a START_STICKY service after
        // killing the process — the case this service exists for, so there is
        // nothing to read off it either way.
        if (!goForeground()) {
            // Android would not let us hold the foreground. Do the useful part
            // anyway, then get out of the way rather than being force-stopped.
            Notifications.ensure(applicationContext)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Notifications.keepAliveWanted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        Notifications.ensure(applicationContext)
        return START_STICKY
    }

    /** Swiping Ponder out of recents must not take the thought with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Notifications.ensure(applicationContext)
    }

    override fun onDestroy() {
        if (unlockRegistered) {
            runCatching { unregisterReceiver(unlockReceiver) }
            unlockRegistered = false
        }
        if (foreground) {
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            foreground = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun goForeground(): Boolean = runCatching {
        ServiceCompat.startForeground(
            this,
            ThoughtNotifier.ID_KEEP_ALIVE,
            ThoughtNotifier.buildKeepAlive(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        foreground = true
        true
    }.getOrElse {
        Log.w(TAG, "Could not hold the foreground; the notification carries on alone", it)
        false
    }

    private companion object {
        const val TAG = "PonderThoughtService"
    }
}
