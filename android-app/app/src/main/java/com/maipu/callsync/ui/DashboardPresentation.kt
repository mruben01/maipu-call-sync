package com.maipu.callsync.ui

import com.maipu.callsync.data.SyncRecordState
import com.maipu.callsync.data.SyncStatus

internal enum class DashboardStatus {
    CHECKING,
    ACTIVE,
    PENDING,
    ATTENTION,
}

internal data class DashboardCounts(
    val synced: Int,
    val pending: Int,
    val errors: Int,
)

internal data class DashboardSnapshot(
    val status: DashboardStatus,
    val counts: DashboardCounts?,
    val lastSuccessfulSyncAtMillis: Long?,
)

internal fun summarizeDashboard(
    callStates: List<SyncRecordState>,
    permissionGranted: Boolean,
    hasScanned: Boolean,
    hasQueryError: Boolean,
    hasScreenError: Boolean,
): DashboardSnapshot {
    if (!permissionGranted || hasQueryError) {
        return DashboardSnapshot(DashboardStatus.ATTENTION, null, null)
    }
    if (!hasScanned) {
        return DashboardSnapshot(DashboardStatus.CHECKING, null, null)
    }

    val synced = callStates.count {
        it.status == SyncStatus.SYNCED && it.lastError == null
    }
    val errors = callStates.count {
        it.status == SyncStatus.FAILED || it.lastError != null
    }
    val pending = callStates.size - synced - errors
    val lastSuccessfulSync = callStates
        .filter { it.status == SyncStatus.SYNCED && it.lastError == null }
        .mapNotNull { it.updatedAtMillis }
        .maxOrNull()

    val status = when {
        errors > 0 || hasScreenError -> DashboardStatus.ATTENTION
        pending > 0 -> DashboardStatus.PENDING
        else -> DashboardStatus.ACTIVE
    }
    return DashboardSnapshot(
        status = status,
        counts = DashboardCounts(synced, pending, errors),
        lastSuccessfulSyncAtMillis = lastSuccessfulSync,
    )
}