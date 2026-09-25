package com.maipu.callsync.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import com.maipu.callsync.data.SyncRecordState
import com.maipu.callsync.data.SyncStatus
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class MonitoringReportPayloadTest {
    @Test
    fun payloadContainsOnlyContractFieldsAndDailyStats() {
        val snapshot = MonitoringSnapshot(
            deviceModel = "Samsung SM-A042M",
            androidVersion = "14",
            appVersion = "0.1.0",
            permissionsOk = true,
            recordingFolderAccessible = true,
            lastSuccessfulSync = "2026-05-10T12:00:00Z",
            callsDetected = 3,
            callsSynced = 1,
            callsPending = 1,
            callsError = 1,
            healthStatus = MonitoringHealth.SYNC_ERROR,
            dailyStats = listOf(
                DailyMonitoringStats(
                    reportDate = "2026-05-10",
                    timezoneName = "America/Argentina/Cordoba",
                    callsDetected = 3,
                    callsSynced = 1,
                    callsPending = 1,
                    callsError = 1,
                    validDurationCount = 2,
                    validDurationMs = 91_000L,
                ),
            ),
            scanErrors = emptyList(),
            timeZoneName = "America/Argentina/Cordoba",
        )

        val json = MonitoringReportPayload.toJson("device-123", snapshot)
        val daily = json.getJSONArray("daily_stats").getJSONObject(0)

        assertEquals("device-123", json.getString("device_id"))
        assertEquals("Samsung SM-A042M", json.getString("device_model"))
        assertEquals("SYNC_ERROR", json.getString("health_status"))
        assertEquals(3, json.getInt("calls_detected"))
        assertEquals(3, json.getInt("calls_synced") +
            json.getInt("calls_pending") + json.getInt("calls_error"))
        assertEquals("2026-05-10", daily.getString("report_date"))
        assertEquals("America/Argentina/Cordoba", daily.getString("timezone_name"))
        assertEquals(2, daily.getInt("valid_duration_count"))
        assertEquals(91_000L, daily.getLong("valid_duration_ms"))
        assertFalse(json.has("monitoring_token"))
        assertFalse(json.has("token_hash"))
    }

    @Test
    fun invalidScanCountersAndFolderAccessRemainJsonNull() {
        val snapshot = MonitoringSnapshot(
            deviceModel = "device",
            androidVersion = "unknown",
            appVersion = "0.1",
            permissionsOk = false,
            recordingFolderAccessible = null,
            lastSuccessfulSync = null,
            callsDetected = null,
            callsSynced = null,
            callsPending = null,
            callsError = null,
            healthStatus = MonitoringHealth.PERMISSION_ERROR,
            dailyStats = emptyList(),
            scanErrors = emptyList(),
            timeZoneName = "UTC",
        )

        val json = MonitoringReportPayload.toJson("device-123", snapshot)

        assertTrue(json.get("recording_folder_accessible") === JSONObject.NULL)
        assertTrue(json.get("calls_detected") === JSONObject.NULL)
        assertTrue(json.get("calls_synced") === JSONObject.NULL)
        assertTrue(json.get("calls_pending") === JSONObject.NULL)
        assertTrue(json.get("calls_error") === JSONObject.NULL)
        assertTrue(json.get("last_successful_sync") === JSONObject.NULL)
    }

    @Test
    fun credentialFingerprintUsesSha256OfUtf8TokenText() {
        assertEquals(
            "2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b",
            monitoringSha256Hex("secret"),
        )
    }

    @Test
    fun countsMatchDashboardErrorAndSyncedRules() {
        val states = listOf(
            SyncRecordState(status = SyncStatus.SYNCED),
            SyncRecordState(status = SyncStatus.SYNCED, lastError = "metadata retry failed"),
            SyncRecordState(status = SyncStatus.AUDIO_UPLOADED, lastError = "metadata error"),
            SyncRecordState(status = SyncStatus.FAILED),
            SyncRecordState(status = SyncStatus.PENDING),
            null,
        )

        val counts = monitoringCounts(states)

        assertEquals(6, counts.detected)
        assertEquals(1, counts.synced)
        assertEquals(3, counts.errors)
        assertEquals(2, counts.pending)
        assertEquals(counts.detected, counts.synced + counts.pending + counts.errors)
    }

    @Test
    fun invalidScanIsWarningAndHealthyRequiresValidScan() {
        assertEquals(
            MonitoringHealth.WARNING,
            monitoringHealth(permissionGranted = true, scanValid = false, counts = null),
        )
        assertEquals(
            MonitoringHealth.PERMISSION_ERROR,
            monitoringHealth(permissionGranted = false, scanValid = false, counts = null),
        )
        assertEquals(
            MonitoringHealth.OK,
            monitoringHealth(
                permissionGranted = true,
                scanValid = true,
                counts = monitoringCounts(listOf(SyncRecordState(status = SyncStatus.SYNCED))),
            ),
        )
    }

    @Test
    fun dailyTimezoneIsReportedOnlyAsAnAvailableIanaZone() {
        val cordoba = ZoneId.of("America/Argentina/Cordoba")
        assertEquals(cordoba.id, ianaZoneIdOrNull(cordoba))
        assertNull(ianaZoneIdOrNull(ZoneOffset.ofHours(5)))
    }

    @Test
    fun folderAccessibilityRemainsUnknownWithoutVerifiedEvidence() {
        assertTrue(recordingFolderAccessibility(scanValid = true, callFolderRecordingCount = 1) == true)
        assertNull(recordingFolderAccessibility(scanValid = true, callFolderRecordingCount = 0))
        assertNull(recordingFolderAccessibility(scanValid = false, callFolderRecordingCount = 0))
    }

    @Test
    fun dailyStatsWindowCapsHistoryAt35DeviceLocalDatesIncludingToday() {
        val today = LocalDate.of(2026, 5, 10)
        val timezone = "America/Argentina/Cordoba"
        val moreThanFortyDates = (-44L..1L).map { offset ->
            val date = today.plusDays(offset)
            DailyMonitoringStats(
                reportDate = date.toString(),
                timezoneName = timezone,
                callsDetected = 1,
                callsSynced = 1,
                callsPending = 0,
                callsError = 0,
                validDurationCount = 1,
                validDurationMs = 60_000L,
            )
        }

        val recent = monitoringDailyStatsForValidScan(
            stats = moreThanFortyDates,
            scanValid = true,
            today = today,
            timezoneName = timezone,
        )

        assertEquals(46, moreThanFortyDates.size)
        assertEquals(35, recent.size)
        assertEquals(today.minusDays(34).toString(), recent.first().reportDate)
        assertEquals(today.toString(), recent.last().reportDate)
        assertTrue(recent.all { LocalDate.parse(it.reportDate) <= today })
    }

    @Test
    fun validEmptyScanEmitsZeroRowsForTodayAndYesterdayButInvalidScanDoesNot() {
        val today = LocalDate.of(2026, 5, 10)
        val timezone = "America/Argentina/Cordoba"

        val validEmptyStats = monitoringDailyStatsForValidScan(
            stats = emptyList(),
            scanValid = true,
            today = today,
            timezoneName = timezone,
        )
        val invalidStats = monitoringDailyStatsForValidScan(
            stats = emptyList(),
            scanValid = false,
            today = today,
            timezoneName = timezone,
        )

        assertEquals(listOf("2026-05-09", "2026-05-10"), validEmptyStats.map { it.reportDate })
        assertTrue(validEmptyStats.all {
            it.callsDetected == 0 &&
                it.callsSynced == 0 &&
                it.callsPending == 0 &&
                it.callsError == 0 &&
                it.validDurationCount == 0 &&
                it.validDurationMs == 0L &&
                it.timezoneName == timezone
        })
        assertTrue(invalidStats.isEmpty())
    }
}