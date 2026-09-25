package com.maipu.callsync.data

import android.content.Context
import android.net.Uri

class CallRecordingReadinessStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    internal fun observe(
        recording: CallRecording,
        nowMillis: Long = System.currentTimeMillis(),
    ): FileStabilityDecision {
        val key = keyFor(recording.contentUri)
        val previous = if (preferences.contains("$key.size")) {
            RecordingObservation(
                sizeBytes = preferences.getLong("$key.size", 0L),
                dateModifiedSeconds = if (preferences.getBoolean("$key.has_modified", false)) {
                    preferences.getLong("$key.modified", 0L)
                } else {
                    null
                },
                firstObservedAtMillis = preferences.getLong("$key.observed_at", nowMillis),
            )
        } else {
            null
        }

        val decision = RecordingFileStability.assess(
            previous = previous,
            sizeBytes = recording.sizeBytes,
            dateModifiedSeconds = recording.dateModifiedSeconds,
            nowMillis = nowMillis,
            stableWindowMillis = STABLE_WINDOW_MILLIS,
        )

        when (decision) {
            is FileStabilityDecision.Wait -> {
                val observation = decision.observation
                if (observation == null) {
                    remove(key)
                } else {
                    preferences.edit()
                        .putLong("$key.size", observation.sizeBytes)
                        .putBoolean("$key.has_modified", observation.dateModifiedSeconds != null)
                        .putLong("$key.modified", observation.dateModifiedSeconds ?: 0L)
                        .putLong("$key.observed_at", observation.firstObservedAtMillis)
                        .commit()
                }
            }

            FileStabilityDecision.Ready -> Unit
        }
        return decision
    }

    fun isCoolingDown(
        recording: CallRecording,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val key = keyFor(recording.contentUri)
        return preferences.getString("$key.failed_source", null) == recording.syncSourceKey() &&
            preferences.getLong("$key.failed_until", 0L) > nowMillis
    }

    fun coolDownPermanentFailure(
        recording: CallRecording,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val key = keyFor(recording.contentUri)
        preferences.edit()
            .putString("$key.failed_source", recording.syncSourceKey())
            .putLong("$key.failed_until", nowMillis + PERMANENT_FAILURE_COOLDOWN_MILLIS)
            .commit()
    }

    fun forget(recording: CallRecording) {
        remove(keyFor(recording.contentUri))
    }

    private fun remove(key: String) {
        preferences.edit()
            .remove("$key.size")
            .remove("$key.has_modified")
            .remove("$key.modified")
            .remove("$key.observed_at")
            .remove("$key.failed_source")
            .remove("$key.failed_until")
            .commit()
    }

    private fun keyFor(uri: Uri): String = "recording:${uri}"

    private companion object {
        const val PREFERENCES_NAME = "recording_readiness"
        const val STABLE_WINDOW_MILLIS = 120_000L
        const val PERMANENT_FAILURE_COOLDOWN_MILLIS = 60 * 60_000L
    }
}