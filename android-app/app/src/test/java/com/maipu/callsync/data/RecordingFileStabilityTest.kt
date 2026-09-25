package com.maipu.callsync.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingFileStabilityTest {
    @Test
    fun `waits for a second observation before considering file stable`() {
        val first = RecordingFileStability.assess(
            previous = null,
            sizeBytes = 100L,
            dateModifiedSeconds = 10L,
            nowMillis = 1_000L,
            stableWindowMillis = 120_000L,
        )

        assertTrue(first is FileStabilityDecision.Wait)
        val firstWait = first as FileStabilityDecision.Wait
        assertEquals(120_000L, firstWait.retryAfterMillis)

        val stable = RecordingFileStability.assess(
            previous = firstWait.observation,
            sizeBytes = 100L,
            dateModifiedSeconds = 10L,
            nowMillis = 121_000L,
            stableWindowMillis = 120_000L,
        )
        assertEquals(FileStabilityDecision.Ready, stable)
    }

    @Test
    fun `restarts the stability window when file size or modified time changes`() {
        val previous = RecordingObservation(
            sizeBytes = 100L,
            dateModifiedSeconds = 10L,
            firstObservedAtMillis = 1_000L,
        )

        val changed = RecordingFileStability.assess(
            previous = previous,
            sizeBytes = 200L,
            dateModifiedSeconds = 11L,
            nowMillis = 60_000L,
            stableWindowMillis = 120_000L,
        )

        assertTrue(changed is FileStabilityDecision.Wait)
        val observation = (changed as FileStabilityDecision.Wait).observation
        assertEquals(200L, observation?.sizeBytes)
        assertEquals(60_000L, observation?.firstObservedAtMillis)
    }

    @Test
    fun `does not schedule repeated checks for an empty file`() {
        val decision = RecordingFileStability.assess(
            previous = null,
            sizeBytes = 0L,
            dateModifiedSeconds = 10L,
            nowMillis = 1_000L,
            stableWindowMillis = 120_000L,
        )

        assertTrue(decision is FileStabilityDecision.Wait)
        assertEquals(null, (decision as FileStabilityDecision.Wait).retryAfterMillis)
    }
}