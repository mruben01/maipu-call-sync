package com.maipu.callsync.ui

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportPresentationTest {
    @Test
    fun `today and yesterday periods use local calendar days and omit unknown dates`() {
        val todayStart = localDay(2025, Calendar.MARCH, 12)
        val calls = listOf(
            fact(todayStart + 60 * 60 * 1_000L, ReportSyncState.SYNCED, 120_000L),
            fact(todayStart - 60_000L, ReportSyncState.PENDING, 60_000L),
            fact(null, ReportSyncState.ERROR, 240_000L),
        )
        val now = todayStart + 15 * 60 * 60 * 1_000L

        val today = calculateCallStats(calls, ReportPeriod.TODAY, now)
        val yesterday = calculateCallStats(calls, ReportPeriod.YESTERDAY, now)

        assertEquals(1, today.total)
        assertEquals(1, today.synced)
        assertEquals(100, today.syncedPercent)
        assertEquals(120_000L, today.durationTotalMillis)
        assertEquals(1, yesterday.total)
        assertEquals(1, yesterday.pending)
        assertEquals(60_000L, yesterday.averageDurationMillis)
    }

    @Test
    fun `seven day totals partition all statuses and count only valid durations`() {
        val today = localDay(2025, Calendar.MARCH, 12)
        val calls = listOf(
            fact(today, ReportSyncState.SYNCED, 1_200_000L),
            fact(today - 86_400_000L, ReportSyncState.PENDING, 600_000L),
            fact(today - 6 * 86_400_000L, ReportSyncState.ERROR, null),
            fact(today - 7 * 86_400_000L, ReportSyncState.SYNCED, 10_000L),
            fact(null, ReportSyncState.ERROR, 10_000L),
        )

        val stats = calculateCallStats(
            calls,
            ReportPeriod.SEVEN_DAYS,
            today + 10 * 60 * 60 * 1_000L,
        )

        assertEquals(3, stats.total)
        assertEquals(1, stats.synced)
        assertEquals(1, stats.pending)
        assertEquals(1, stats.errors)
        assertEquals(stats.total, stats.synced + stats.pending + stats.errors)
        assertEquals(1_800_000L, stats.durationTotalMillis)
        assertEquals(900_000L, stats.averageDurationMillis)
        assertEquals(7, stats.dailyCounts.size)
        assertEquals(1, stats.dailyCounts[0])
        assertEquals(1, stats.dailyCounts[5])
        assertEquals(1, stats.dailyCounts[6])
    }

    private fun fact(date: Long?, state: ReportSyncState, duration: Long?) =
        ReportCallFact(date, state, duration)

    private fun localDay(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}