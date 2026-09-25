package com.maipu.callsync.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.maipu.callsync.data.CallRecording
import com.maipu.callsync.data.CallRecordingReadinessStore
import com.maipu.callsync.data.CallRecordingRepository
import com.maipu.callsync.data.CallRecordingSyncRepository
import com.maipu.callsync.data.FileStabilityDecision
import com.maipu.callsync.data.SyncStatus
import com.maipu.callsync.data.syncSourceKey
import java.util.concurrent.TimeUnit

class CallRecordingSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result = try {
        runSync()
    } catch (exception: Exception) {
        Log.e(
            TAG,
            "Falló el worker automático (${exception.javaClass.simpleName}); se reintentará.",
        )
        Result.retry()
    }

    private fun runSync(): Result {
        if (!hasAudioPermission()) {
            Log.i(TAG, "Worker omitido: falta el permiso de audio.")
            return Result.success(
                Data.Builder().putString(OUTPUT_REASON, "audio_permission_not_granted").build(),
            )
        }

        val query = CallRecordingRepository(applicationContext).findRecordings(
            permissionGranted = true,
            callFolderOnly = true,
        )
        if (query.diagnostics.errors.isNotEmpty()) {
            Log.w(TAG, "Falló la consulta de MediaStore; el worker se reintentará.")
            return Result.retry()
        }

        val syncRepository = CallRecordingSyncRepository(applicationContext)
        val readinessStore = CallRecordingReadinessStore(applicationContext)
        val states = syncRepository.statesFor(query.recordings)
        val recordings = query.recordings.sortedWith(
            compareBy<CallRecording> {
                states[it.syncSourceKey()]?.attemptCount ?: 0
            }.thenByDescending { it.dateModifiedSeconds ?: 0L },
        )
        var readyCount = 0
        var syncedCount = 0
        var permanentFailureCount = 0
        var retryableFailureCount = 0
        var deferredCount = 0
        var hasMorePending = false
        var earliestFollowUpMillis: Long? = null
        val now = System.currentTimeMillis()
        val startedAt = SystemClock.elapsedRealtime()

        recordings.forEach { recording ->
            val currentState = states[recording.syncSourceKey()]
            if (currentState?.status == SyncStatus.SYNCED) {
                readinessStore.forget(recording)
                return@forEach
            }
            if (readinessStore.isCoolingDown(recording, now)) {
                return@forEach
            }
            if (
                readyCount >= MAX_READY_PER_RUN ||
                SystemClock.elapsedRealtime() - startedAt >= MAX_RUN_MILLIS
            ) {
                hasMorePending = true
                return@forEach
            }

            when (val decision = readinessStore.observe(recording, now)) {
                is FileStabilityDecision.Wait -> {
                    deferredCount += 1
                    decision.retryAfterMillis?.let { retryAfter ->
                        earliestFollowUpMillis = minOf(
                            earliestFollowUpMillis ?: retryAfter,
                            retryAfter,
                        )
                    }
                }

                FileStabilityDecision.Ready -> {
                    readyCount += 1
                    val outcome = syncRepository.syncWithOutcome(recording)
                    if (outcome.state.status == SyncStatus.SYNCED) {
                        syncedCount += 1
                        readinessStore.forget(recording)
                    } else if (outcome.retryable) {
                        retryableFailureCount += 1
                    } else {
                        permanentFailureCount += 1
                        readinessStore.coolDownPermanentFailure(recording, now)
                    }
                }
            }
        }

        Log.i(
            TAG,
            "Escaneo automático: encontrados=${recordings.size}, listos=$readyCount, " +
                "sincronizados=$syncedCount, diferidos=$deferredCount, " +
                "fallos_reintentables=$retryableFailureCount, " +
                "fallos_permanentes=$permanentFailureCount, " +
                "mas_pendientes=$hasMorePending.",
        )

        if (retryableFailureCount > 0) return Result.retry()
        if (hasMorePending) {
            CallSyncAutoScheduler.enqueueFollowUp(
                applicationContext,
                NEXT_BATCH_DELAY_MILLIS,
            ).result.get(30, TimeUnit.SECONDS)
        } else earliestFollowUpMillis?.let {
            CallSyncAutoScheduler.enqueueFollowUp(
                applicationContext,
                it,
            ).result.get(30, TimeUnit.SECONDS)
        }

        return Result.success(
            Data.Builder()
                .putInt("recordings_found", recordings.size)
                .putInt("recordings_ready", readyCount)
                .putInt("recordings_synced", syncedCount)
                .putInt("recordings_deferred", deferredCount)
                .putInt("retryable_failures", retryableFailureCount)
                .putInt("permanent_failures", permanentFailureCount)
                .putBoolean("more_pending", hasMorePending)
                .build(),
        )
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            applicationContext,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val TAG = "MaipuCallSync"
        const val OUTPUT_REASON = "skip_reason"
        const val MAX_READY_PER_RUN = 2
        const val MAX_RUN_MILLIS = 6 * 60_000L
        const val NEXT_BATCH_DELAY_MILLIS = 1_000L
    }
}