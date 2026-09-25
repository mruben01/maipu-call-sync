package com.maipu.callsync.data

internal data class RecordingObservation(
    val sizeBytes: Long,
    val dateModifiedSeconds: Long?,
    val firstObservedAtMillis: Long,
)

internal sealed interface FileStabilityDecision {
    data class Wait(
        val observation: RecordingObservation?,
        val retryAfterMillis: Long?,
    ) : FileStabilityDecision

    data object Ready : FileStabilityDecision
}

internal object RecordingFileStability {
    fun assess(
        previous: RecordingObservation?,
        sizeBytes: Long?,
        dateModifiedSeconds: Long?,
        nowMillis: Long,
        stableWindowMillis: Long,
    ): FileStabilityDecision {
        val size = sizeBytes?.takeIf { it > 0 }
            ?: return FileStabilityDecision.Wait(
                observation = null,
                retryAfterMillis = null,
            )

        val current = if (
            previous != null &&
            previous.sizeBytes == size &&
            previous.dateModifiedSeconds == dateModifiedSeconds &&
            previous.firstObservedAtMillis <= nowMillis
        ) {
            previous
        } else {
            RecordingObservation(
                sizeBytes = size,
                dateModifiedSeconds = dateModifiedSeconds,
                firstObservedAtMillis = nowMillis,
            )
        }

        val stableForMillis = (nowMillis - current.firstObservedAtMillis).coerceAtLeast(0L)
        if (stableForMillis >= stableWindowMillis) {
            return FileStabilityDecision.Ready
        }

        return FileStabilityDecision.Wait(
            observation = current,
            retryAfterMillis = (stableWindowMillis - stableForMillis).coerceAtLeast(1L),
        )
    }
}