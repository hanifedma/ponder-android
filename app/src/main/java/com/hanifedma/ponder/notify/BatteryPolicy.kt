package com.hanifedma.ponder.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Android's battery saver puts apps it considers idle into Doze, where
 * background work is deferred. Ponder does almost nothing in the background —
 * it reacts to a swipe and goes quiet again — but on a battery-restricted app
 * even that can be delayed, and the keep-alive service is likely to be killed.
 *
 * Being exempted is the person's decision to make, never the app's, so all this
 * does is report the current state and hand back the intent that asks.
 */
object BatteryPolicy {

    /** True when Android will not put Ponder to sleep to save battery. */
    fun isUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    /**
     * The system dialog that asks, in one tap, for this app to be exempted.
     * Needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which the manifest declares.
     */
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:" + context.packageName))

    /**
     * The full battery-optimisation list, used when the one-tap dialog is not
     * available — some OEM builds simply do not implement it.
     */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
