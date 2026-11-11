package com.nexiplay.app.data.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms WorkManager channels and background episode notification polling after device boot
 * or APK updates so alerts arrive without requiring the user to open the app first.
 */
class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val appContext = context.applicationContext
            EpisodeUpdatesNotificationManager.initChannel(appContext)
            NotificationSyncWorker.schedulePeriodic(appContext)
            NotificationSyncWorker.enqueueOneShotCatchUp(appContext)
        }
    }
}
