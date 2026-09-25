package com.maipu.callsync.monitoring

import org.json.JSONArray
import org.json.JSONObject

/** Builds only the public report body. The Bearer credential is never part of JSON. */
internal object MonitoringReportPayload {
    fun toJson(deviceId: String, snapshot: MonitoringSnapshot): JSONObject {
        val daily = JSONArray()
        snapshot.dailyStats.forEach { stat ->
            daily.put(
                JSONObject()
                    .put("report_date", stat.reportDate)
                    .put("timezone_name", stat.timezoneName)
                    .put("calls_detected", stat.callsDetected)
                    .put("calls_synced", stat.callsSynced)
                    .put("calls_pending", stat.callsPending)
                    .put("calls_error", stat.callsError)
                    .put("valid_duration_count", stat.validDurationCount)
                    .put("valid_duration_ms", stat.validDurationMs),
            )
        }
        return JSONObject()
            .put("device_id", deviceId)
            .put("device_model", snapshot.deviceModel)
            .put("android_version", snapshot.androidVersion)
            .put("app_version", snapshot.appVersion)
            .put("permissions_ok", snapshot.permissionsOk)
            .put(
                "recording_folder_accessible",
                snapshot.recordingFolderAccessible ?: JSONObject.NULL,
            )
            .put("last_successful_sync", snapshot.lastSuccessfulSync ?: JSONObject.NULL)
            .put("calls_detected", snapshot.callsDetected ?: JSONObject.NULL)
            .put("calls_synced", snapshot.callsSynced ?: JSONObject.NULL)
            .put("calls_pending", snapshot.callsPending ?: JSONObject.NULL)
            .put("calls_error", snapshot.callsError ?: JSONObject.NULL)
            .put("health_status", snapshot.healthStatus.name)
            .put("daily_stats", daily)
    }
}