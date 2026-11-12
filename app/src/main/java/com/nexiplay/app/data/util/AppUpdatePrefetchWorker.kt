package com.nexiplay.app.data.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Connected-network background worker that silently pre-downloads an available
 * NexiPlay APK update so users never face an interactive download popup for minor releases.
 */
class AppUpdatePrefetchWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val ready = AppUpdateManager.prefetchIfAvailable(applicationContext)
            if (ready || !AppUpdateManager.isUpdateAvailable || runAttemptCount >= 2) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PREFETCH_WORK_NAME = "nexiplay_ota_apk_prefetch"

        private val connectedConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<AppUpdatePrefetchWorker>()
                    .setConstraints(connectedConstraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    PREFETCH_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}
