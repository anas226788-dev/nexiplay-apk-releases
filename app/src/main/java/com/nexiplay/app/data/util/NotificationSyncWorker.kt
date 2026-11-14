package com.nexiplay.app.data.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Periodic and catch-up background worker that keeps episode, release, notice,
 * and personal account notifications firing even when NexiPlay is closed.
 */
class NotificationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            EpisodeUpdatesNotificationManager.initChannel(applicationContext)
            val ok = EpisodeUpdatesNotificationManager.syncOnceInBackground(applicationContext)
            runCatching { AppUpdateManager.prefetchIfAvailable(applicationContext) }
            if (ok || runAttemptCount >= 2) Result.success() else Result.retry()
        } catch (t: Throwable) {
            t.printStackTrace()
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "nexiplay_episode_updates_periodic_sync"
        private const val CATCHUP_WORK_NAME = "nexiplay_episode_updates_catchup_sync"

        private val scheduleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val connectedConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(connectedConstraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }

        fun schedulePeriodicAsync(context: Context, delayMs: Long = 1_200L) {
            val appContext = context.applicationContext
            scheduleScope.launch {
                if (delayMs > 0) delay(delayMs)
                schedulePeriodic(appContext)
            }
        }

        fun enqueueOneShotCatchUp(context: Context, delaySeconds: Long = 0L) {
            runCatching {
                val appContext = context.applicationContext
                val builder = OneTimeWorkRequestBuilder<NotificationSyncWorker>()
                    .setConstraints(connectedConstraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                if (delaySeconds > 0L) {
                    builder.setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                }

                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    CATCHUP_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    builder.build()
                )
            }
        }
    }
}
