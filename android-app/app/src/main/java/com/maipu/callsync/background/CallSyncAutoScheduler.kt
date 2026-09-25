package com.maipu.callsync.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CallSyncAutoScheduler {
    private const val PERIODIC_WORK_NAME = "maipu-call-sync-periodic"
    private const val ON_DEMAND_WORK_NAME = "maipu-call-sync-on-demand"
    private const val AUTO_SYNC_TAG = "maipu-call-sync"
    private const val RETRY_BACKOFF_MILLIS = 30_000L

    fun ensurePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<PeriodicSyncTriggerWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .addTag(AUTO_SYNC_TAG)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueImmediate(context: Context): Operation =
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ON_DEMAND_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            requestWithDelay(0L),
        )

    fun enqueueFollowUp(context: Context, delayMillis: Long): Operation =
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ON_DEMAND_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            requestWithDelay(delayMillis.coerceAtLeast(1_000L)),
        )

    private fun requestWithDelay(delayMillis: Long): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<CallRecordingSyncWorker>()
            .setConstraints(networkConstraints())
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .addTag(AUTO_SYNC_TAG)
            .build()

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private const val PERIODIC_INTERVAL_MINUTES = 15L
}