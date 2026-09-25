package com.maipu.callsync.data

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter

class CallRecordingSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = CallSyncStore(appContext)
    private val deviceIdentityStore = DeviceIdentityStore(appContext)
    private val supabaseClient = SupabaseCallRecordingClient(appContext)

    fun stateFor(recording: CallRecording): SyncRecordState =
        store.getState(recording.syncSourceKey()) ?: SyncRecordState()

    fun statesFor(recordings: List<CallRecording>): Map<String, SyncRecordState> =
        store.getStates(recordings.map(CallRecording::syncSourceKey))

    fun sync(recording: CallRecording): SyncRecordState =
        syncWithOutcome(recording).state

    fun syncWithOutcome(recording: CallRecording): SyncAttemptOutcome =
        synchronized(SYNC_LOCK) {
            syncLocked(recording)
        }

    private fun syncLocked(recording: CallRecording): SyncAttemptOutcome {
        val sourceKey = recording.syncSourceKey()
        var audioUploaded = false
        var failureMessage: String? = null
        var retryable = false
        try {
            val existing = store.get(sourceKey)
            if (existing?.status == SyncStatus.SYNCED) {
                return SyncAttemptOutcome(
                    state = SyncRecordState(
                        status = existing.status,
                        attemptCount = existing.attemptCount,
                    ),
                    retryable = false,
                )
            }

            val metadata = existing?.metadata ?: recording.toMetadata(
                deviceId = deviceIdentityStore.getOrCreate(),
            )
            store.ensurePending(sourceKey, metadata)
            val pendingEntry = store.get(sourceKey)
                ?: throw SyncOperationException(
                    "No se pudo guardar el estado local de sincronización.",
                )
            audioUploaded = pendingEntry.status == SyncStatus.AUDIO_UPLOADED

            supabaseClient.validateConfiguration()
            if (audioUploaded) {
                store.markMetadataRetry(sourceKey)
            } else {
                store.markUploading(sourceKey)
                val sha256 = recording.contentUri.sha256()
                val storagePath = "$sha256.m4a"
                store.saveUploadTarget(sourceKey, sha256, storagePath)
                supabaseClient.uploadAudio(
                    contentUri = recording.contentUri,
                    sizeBytes = recording.sizeBytes,
                    mimeType = recording.mimeType,
                    storagePath = storagePath,
                    expectedSha256 = sha256,
                )
                store.markAudioUploaded(sourceKey, sha256, storagePath)
                audioUploaded = true
            }

            val storedEntry = store.get(sourceKey)
                ?: throw SyncOperationException("No se encontró el estado local de la grabación.")
            supabaseClient.insertMetadata(storedEntry)
            store.markSynced(sourceKey)
        } catch (exception: SyncOperationException) {
            val message = exception.message ?: GENERIC_ERROR
            failureMessage = message
            retryable = exception.retryable
            runCatching {
                store.markFailed(sourceKey, message, audioUploaded)
            }
        } catch (_: SecurityException) {
            val message =
                "Android no permitió leer la grabación. Revise el permiso de audio."
            failureMessage = message
            retryable = false
            runCatching { store.markFailed(sourceKey, message, audioUploaded) }
        } catch (_: IOException) {
            val message = "No se pudo leer o transferir el archivo. Puede reintentar."
            failureMessage = message
            retryable = true
            runCatching { store.markFailed(sourceKey, message, audioUploaded) }
        } catch (_: Exception) {
            failureMessage = GENERIC_ERROR
            retryable = true
            runCatching { store.markFailed(sourceKey, GENERIC_ERROR, audioUploaded) }
        }

        val state = runCatching { store.getState(sourceKey) }.getOrNull() ?: SyncRecordState(
            status = SyncStatus.FAILED,
            lastError = failureMessage ?: GENERIC_ERROR,
        )
        return SyncAttemptOutcome(state = state, retryable = retryable)
    }

    private fun CallRecording.toMetadata(deviceId: String): CallRecordingMetadata {
        val recordedAtMillis = parsedFileName?.parsedDate?.time
            ?: dateModifiedSeconds?.times(1_000)
            ?: dateAddedSeconds?.times(1_000)
        val callDatetime = recordedAtMillis?.let {
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it))
        }
        val durationSeconds = durationMs?.takeIf { it >= 0 }
            ?.let { (it + 500) / 1_000 }

        return CallRecordingMetadata(
            phoneNumber = parsedFileName?.phoneNumber,
            callDatetime = callDatetime,
            durationSeconds = durationSeconds,
            fileName = displayName,
            deviceId = deviceId,
        )
    }

    private fun Uri.sha256(): String {
        val stream = appContext.contentResolver.openInputStream(this)
            ?: throw SyncOperationException(
                "Android no pudo abrir la grabación desde MediaStore.",
            )
        return stream.use { it.sha256Hex() }
    }

    private companion object {
        const val GENERIC_ERROR = "No se pudo completar la sincronización. Puede reintentar."
        val SYNC_LOCK = Any()
    }
}