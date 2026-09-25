package com.maipu.callsync.data

enum class SyncStatus {
    PENDING,
    UPLOADING,
    AUDIO_UPLOADED,
    SYNCED,
    FAILED,
}

data class SyncRecordState(
    val status: SyncStatus = SyncStatus.PENDING,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

data class SyncAttemptOutcome(
    val state: SyncRecordState,
    val retryable: Boolean,
)

data class CallRecordingMetadata(
    val phoneNumber: String?,
    val callDatetime: String?,
    val durationSeconds: Long?,
    val fileName: String,
    val deviceId: String,
)

data class StoredSyncEntry(
    val sourceKey: String,
    val status: SyncStatus,
    val sha256: String?,
    val storagePath: String?,
    val metadata: CallRecordingMetadata,
    val attemptCount: Int,
    val lastError: String?,
)

fun CallRecording.syncSourceKey(): String =
    listOf(
        contentUri.toString(),
        sizeBytes?.toString().orEmpty(),
        dateModifiedSeconds?.toString().orEmpty(),
    ).joinToString(separator = "|")
