package com.hanifedma.ponder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Puts the thought back after the phone starts up, and after Ponder itself is
 * updated — neither of which a posted notification survives.
 *
 * Every action listened for here is a *protected* broadcast: only the system can
 * send them, which is why the receiver can safely be exported (it has to be, to
 * be given BOOT_COMPLETED at all).
 *
 * Receiving BOOT_COMPLETED is also one of the few remaining ways an app may
 * start a foreground service while in the background, so this is the one place
 * outside the app's own screens that tries to bring the keep-alive service up.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_HTC_QUICKBOOT_POWERON,
            -> Unit

            else -> return
        }
        val pending = goAsync()
        Notifications.apply(context.applicationContext, allowServiceStart = true) {
            pending.finish()
        }
    }

    companion object {
        /** Some OEMs send these instead of BOOT_COMPLETED after a fast boot. */
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
