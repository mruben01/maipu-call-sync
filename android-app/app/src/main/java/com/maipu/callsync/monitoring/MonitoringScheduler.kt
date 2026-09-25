package com.maipu.callsync.monitoring

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules monitoring independently of the existing call-sync worker. */
object MonitoringScheduler {
    private const val PERIODIC_WORK_NAME = "maipu-monitoring-heartbeat-periodic"
    private const val ON_OPEN_WORK_NAME = "maipu-monitoring-heartbeat-on-open"
    private const val PREFERENCES_NAME = "call_sync_monitoring_schedule"
    private const val LAST_ON_OPEN_ENQUEUE_KEY = "last_on_open_enqueue_millis"
    private const val HEARTBEAT_INTERVAL_HOURS = 2L
    private const val ON_OPEN_THROTTLE_MILLIS = 30 * 60 * 1000L
    private const val RETRY_BACKOFF_MILLIS = 30_000L

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<MonitoringReportWorker>(
            HEARTBEAT_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Queue a report on app open at most once per 30 minutes. The independent
     * periodic heartbeat remains scheduled regardless of this throttle.
     */
    fun enqueueIfDue(context: Context): Boolean {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        synchronized(this) {
            val lastEnqueue = preferences.getLong(LAST_ON_OPEN_ENQUEUE_KEY, 0L)
            if (now - lastEnqueue < ON_OPEN_THROTTLE_MILLIS) return false

            val request = OneTimeWorkRequestBuilder<MonitoringReportWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    RETRY_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                ON_OPEN_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            preferences.edit().putLong(LAST_ON_OPEN_ENQUEUE_KEY, now).apply()
            return true
        }
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}