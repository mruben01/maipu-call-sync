package com.maipu.callsync.data

import android.net.Uri
import com.maipu.callsync.domain.ParsedCallFileName

data class CallRecording(
    val id: Long,
    val displayName: String,
    val dateAddedSeconds: Long?,
    val dateModifiedSeconds: Long?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val mimeType: String?,
    val relativePath: String?,
    val contentUri: Uri,
    val parsedFileName: ParsedCallFileName?,
)

data class DiagnosticReport(
    val totalAudioCount: Int = 0,
    val recordingsCount: Int = 0,
    val callRecordingsCount: Int = 0,
    val permissionGranted: Boolean = false,
    val androidVersion: String = android.os.Build.VERSION.RELEASE ?: "desconocida",
    val manufacturer: String = android.os.Build.MANUFACTURER,
    val model: String = android.os.Build.MODEL,
    val errors: List<String> = emptyList(),
)