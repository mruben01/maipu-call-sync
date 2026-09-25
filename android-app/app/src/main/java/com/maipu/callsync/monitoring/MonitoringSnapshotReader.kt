package com.maipu.callsync.monitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.maipu.callsync.data.CallRecording
import com.maipu.callsync.data.CallRecordingRepository
import com.maipu.callsync.data.CallSyncStore
import com.maipu.callsync.data.SyncStatus
import com.maipu.callsync.data.syncSourceKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Read-only view of existing MediaStore and sync-state data. This reader does not
 * create or update sync records and does not influence the scanner or sync worker.
 * Daily stats exclude files whose filename date cannot be parsed. A valid empty
 * scan reports zero rows for today and yesterday; an invalid scan reports no days.
 */
class MonitoringSnapshotReader(context: Context) {
    private val appContext = context.applicationContext
    private val recordingRepository = CallRecordingRepository(appContext)
    private val syncStore = CallSyncStore(appContext)

    fun read(): MonitoringSnapshot {
        val permissionGranted = hasAudioPermission()
        val result = recordingRepository.findRecordings(
            permissionGranted = permissionGranted,
            callFolderOnly = true,
        )
        val scanValid = permissionGranted && result.diagnostics.errors.isEmpty()
        val states = if (scanValid) {
            syncStore.getStates(result.recordings.map(CallRecording::syncSourceKey))
        } else {
            emptyMap()
        }

        val totals = if (scanValid) {
            monitoringCounts(result.recordings.map { states[it.syncSourceKey()] })
        } else {
            null
        }
        val pending = totals?.pending
        val lastSuccessfulSync = states.values
            .filter { it.status == SyncStatus.SYNCED && it.lastError == null }
            .mapNotNull { it.updatedAtMillis }
            .maxOrNull()

        val systemZone = ZoneId.systemDefault()
        val ianaZone = ianaZoneIdOrNull(systemZone)?.let(ZoneId::of)
        val dailyStats = if (scanValid && ianaZone != null) {
            buildDailyStats(result.recordings, states, ianaZone)
        } else {
            emptyList()
        }
        val folderAccessible = recordingFolderAccessibility(
            scanValid = scanValid,
            callFolderRecordingCount = result.diagnostics.callRecordingsCount,
        )
        val health = monitoringHealth(permissionGranted, scanValid, totals)

        return MonitoringSnapshot(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = Build.VERSION.RELEASE ?: "desconocida",
            appVersion = appVersion(),
            permissionsOk = permissionGranted,
            recordingFolderAccessible = folderAccessible,
            lastSuccessfulSync = lastSuccessfulSync?.let(Instant::ofEpochMilli)?.toString(),
            callsDetected = totals?.detected,
            callsSynced = totals?.synced,
            callsPending = pending,
            callsError = totals?.errors,
            healthStatus = health,
            dailyStats = dailyStats,
            scanErrors = result.diagnostics.errors,
            timeZoneName = ianaZone?.id,
        )
    }

    private fun buildDailyStats(
        recordings: List<CallRecording>,
        states: Map<String, com.maipu.callsync.data.SyncRecordState>,
        zone: ZoneId,
    ): List<DailyMonitoringStats> {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val today = LocalDate.now(zone)
        val allDailyStats = recordings.mapNotNull { recording ->
            recording.parsedFileName?.let { parsed ->
                val localDate = parsed.parsedDate.toInstant().atZone(zone).toLocalDate()
                val duration = recording.durationMs?.takeIf { it > 0L }
                DailyCall(
                    reportDate = formatter.format(localDate),
                    state = states[recording.syncSourceKey()],
                    durationMs = duration,
                )
            }
        }
            .groupBy(DailyCall::reportDate)
            .map { (date, calls) ->
                val counts = monitoringCounts(calls.map(DailyCall::state))
                DailyMonitoringStats(
                    reportDate = date,
                    timezoneName = zone.id,
                    callsDetected = counts.detected,
                    callsSynced = counts.synced,
                    callsPending = counts.pending,
                    callsError = counts.errors,
                    validDurationCount = calls.count { it.durationMs != null },
                    validDurationMs = calls.mapNotNull(DailyCall::durationMs).sum(),
                )
            }
            .sortedBy(DailyMonitoringStats::reportDate)
        // Unparseable filenames are deliberately excluded rather than assigned an
        // inferred date. Zero rows therefore mean zero dated recordings were found.
        return monitoringDailyStatsForValidScan(
            stats = allDailyStats,
            scanValid = true,
            today = today,
            timezoneName = zone.id,
        )
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun appVersion(): String = try {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            ?: "desconocida"
    } catch (_: Exception) {
        "desconocida"
    }

    private data class DailyCall(
        val reportDate: String,
        val state: com.maipu.callsync.data.SyncRecordState?,
        val durationMs: Long?,
    )

}

internal data class MonitoringCounts(
    val detected: Int,
    val synced: Int,
    val pending: Int,
    val errors: Int,
)

internal fun monitoringHealth(
    permissionGranted: Boolean,
    scanValid: Boolean,
    counts: MonitoringCounts?,
): MonitoringHealth = when {
    !permissionGranted -> MonitoringHealth.PERMISSION_ERROR
    !scanValid || counts == null -> MonitoringHealth.WARNING
    (counts?.errors ?: 0) > 0 -> MonitoringHealth.SYNC_ERROR
    (counts?.pending ?: 0) > 0 -> MonitoringHealth.WARNING
    else -> MonitoringHealth.OK
}

internal fun ianaZoneIdOrNull(zone: ZoneId): String? =
    zone.id.takeIf { it in ZoneId.getAvailableZoneIds() }

internal fun monitoringDailyStatsStartDate(
    today: LocalDate,
    windowDays: Long = MONITORING_DAILY_STATS_WINDOW_DAYS,
): LocalDate {
    require(windowDays > 0) { "La ventana diaria debe tener al menos un día." }
    return today.minusDays(windowDays - 1)
}

internal fun limitMonitoringDailyStats(
    stats: List<DailyMonitoringStats>,
    today: LocalDate,
    windowDays: Long = MONITORING_DAILY_STATS_WINDOW_DAYS,
): List<DailyMonitoringStats> {
    val firstIncludedDate = monitoringDailyStatsStartDate(today, windowDays)
    return stats.filter { stat ->
        val reportDate = LocalDate.parse(stat.reportDate)
        !reportDate.isBefore(firstIncludedDate) && !reportDate.isAfter(today)
    }.sortedBy(DailyMonitoringStats::reportDate)
}

internal fun monitoringDailyStatsForValidScan(
    stats: List<DailyMonitoringStats>,
    scanValid: Boolean,
    today: LocalDate,
    timezoneName: String,
    windowDays: Long = MONITORING_DAILY_STATS_WINDOW_DAYS,
): List<DailyMonitoringStats> {
    if (!scanValid) return emptyList()
    require(windowDays >= 2) { "La ventana diaria debe incluir hoy y ayer." }

    val rowsByDate = limitMonitoringDailyStats(stats, today, windowDays)
        .associateBy(DailyMonitoringStats::reportDate)
        .toMutableMap()
    // These are upsert report rows only; omission of older dates is never a deletion.
    listOf(today.minusDays(1), today).forEach { date ->
        rowsByDate.getOrPut(date.toString()) {
            DailyMonitoringStats(
                reportDate = date.toString(),
                timezoneName = timezoneName,
                callsDetected = 0,
                callsSynced = 0,
                callsPending = 0,
                callsError = 0,
                validDurationCount = 0,
                validDurationMs = 0L,
            )
        }
    }
    return rowsByDate.values.sortedBy(DailyMonitoringStats::reportDate)
}

internal const val MONITORING_DAILY_STATS_WINDOW_DAYS = 35L

internal fun recordingFolderAccessibility(
    scanValid: Boolean,
    callFolderRecordingCount: Int,
): Boolean? = when {
    !scanValid -> null
    callFolderRecordingCount > 0 -> true
    else -> null
}

/** Keep monitoring classification identical to the dashboard's state rules. */
internal fun monitoringCounts(
    states: List<com.maipu.callsync.data.SyncRecordState?>,
): MonitoringCounts {
    val synced = states.count {
        it != null && it.status == SyncStatus.SYNCED && it.lastError == null
    }
    val errors = states.count {
        it?.status == SyncStatus.FAILED || it?.lastError != null
    }
    return MonitoringCounts(
        detected = states.size,
        synced = synced,
        pending = states.size - synced - errors,
        errors = errors,
    )
}

enum class MonitoringHealth {
    OK,
    WARNING,
    PERMISSION_ERROR,
    SYNC_ERROR,
    STORAGE_ERROR,
}

data class MonitoringSnapshot(
    val deviceModel: String,
    val androidVersion: String,
    val appVersion: String,
    val permissionsOk: Boolean,
    val recordingFolderAccessible: Boolean?,
    val lastSuccessfulSync: String?,
    val callsDetected: Int?,
    val callsSynced: Int?,
    val callsPending: Int?,
    val callsError: Int?,
    val healthStatus: MonitoringHealth,
    val dailyStats: List<DailyMonitoringStats>,
    val scanErrors: List<String>,
    val timeZoneName: String?,
)

data class DailyMonitoringStats(
    val reportDate: String,
    val timezoneName: String,
    val callsDetected: Int,
    val callsSynced: Int,
    val callsPending: Int,
    val callsError: Int,
    val validDurationCount: Int,
    val validDurationMs: Long,
)