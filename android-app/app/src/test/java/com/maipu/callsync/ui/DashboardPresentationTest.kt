package com.maipu.callsync.ui

import com.maipu.callsync.data.SyncRecordState
import com.maipu.callsync.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardPresentationTest {
    @Test
    fun `separates successful pending and failed recordings without double counting`() {
        val result = summarizeDashboard(
            callStates = listOf(
                SyncRecordState(SyncStatus.SYNCED, updatedAtMillis = 1_700_000_000_000L),
                SyncRecordState(SyncStatus.PENDING),
                SyncRecordState(SyncStatus.UPLOADING),
                SyncRecordState(SyncStatus.AUDIO_UPLOADED),
                SyncRecordState(SyncStatus.FAILED, lastError = "Subida fallida"),
                SyncRecordState(SyncStatus.AUDIO_UPLOADED, lastError = "Registro fallido"),
            ),
            permissionGranted = true,
            hasScanned = true,
            hasQueryError = false,
            hasScreenError = false,
        )

        assertEquals(DashboardStatus.ATTENTION, result.status)
        assertEquals(DashboardCounts(total = 6, synced = 1, pending = 3, errors = 2), result.counts)
        assertEquals(
            result.counts?.total,
            result.counts?.let { it.synced + it.pending + it.errors },
        )
        assertEquals("16,7%", dashboardPercentage(result.counts?.synced, result.counts?.total))
        assertEquals(1_700_000_000_000L, result.lastSuccessfulSyncAtMillis)
    }

    @Test
    fun `does not display zero counts before the first scan`() {
        val result = summarizeDashboard(
            callStates = emptyList(),
            permissionGranted = true,
            hasScanned = false,
            hasQueryError = false,
            hasScreenError = false,
        )

        assertEquals(DashboardStatus.CHECKING, result.status)
        assertNull(result.counts)
        assertNull(result.lastSuccessfulSyncAtMillis)
        assertNull(dashboardPercentage(result.counts?.synced, result.counts?.total))
    }

    @Test
    fun `missing permission and failed query need attention without fabricated counts`() {
        for ((permission, queryError) in listOf(false to false, true to true)) {
            val result = summarizeDashboard(
                callStates = emptyList(),
                permissionGranted = permission,
                hasScanned = true,
                hasQueryError = queryError,
                hasScreenError = false,
            )
            assertEquals(DashboardStatus.ATTENTION, result.status)
            assertNull(result.counts)
            assertNull(dashboardPercentage(result.counts?.synced, result.counts?.total))
        }
    }

    @Test
    fun `clean completed scan with no outstanding calls is active`() {
        val result = summarizeDashboard(
            callStates = emptyList(),
            permissionGranted = true,
            hasScanned = true,
            hasQueryError = false,
            hasScreenError = false,
        )

        assertEquals(DashboardStatus.ACTIVE, result.status)
        assertEquals(DashboardCounts(0, 0, 0, 0), result.counts)
        assertNull(dashboardPercentage(result.counts?.synced, result.counts?.total))
    }
}