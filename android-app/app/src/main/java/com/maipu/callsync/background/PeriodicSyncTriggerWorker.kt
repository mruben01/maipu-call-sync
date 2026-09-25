package com.maipu.callsync.background

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class PeriodicSyncTriggerWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result = try {
        CallSyncAutoScheduler.enqueueImmediate(applicationContext)
            .result.get(30, TimeUnit.SECONDS)
        Result.success()
    } catch (exception: Exception) {
        Log.w(
            TAG,
            "No se pudo encolar el escaneo periódico " +
                "(${exception.javaClass.simpleName}); se reintentará.",
        )
        Result.retry()
    }

    private companion object {
        const val TAG = "MaipuCallSync"
    }
}