package com.maipu.callsync.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.maipu.callsync.domain.CallFileNameParser
import com.maipu.callsync.domain.isCallRecordingPath

class CallRecordingRepository(context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    fun findRecordings(
        permissionGranted: Boolean,
        callFolderOnly: Boolean = false,
    ): QueryResult {
        if (!permissionGranted) {
            return QueryResult(
                recordings = emptyList(),
                diagnostics = DiagnosticReport(permissionGranted = false),
            )
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                add(MediaStore.Audio.Media.DATA)
            }
        }

        val recordings = mutableListOf<CallRecording>()
        var totalAudioCount = 0
        var recordingsCount = 0
        var callRecordingsCount = 0
        val errors = mutableListOf<String>()

        try {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                )
                val dateAddedColumn = cursor.getColumnIndex(
                    MediaStore.Audio.Media.DATE_ADDED,
                )
                val dateModifiedColumn = cursor.getColumnIndex(
                    MediaStore.Audio.Media.DATE_MODIFIED,
                )
                val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val durationColumn = cursor.getColumnIndex(
                    MediaStore.Audio.Media.DURATION,
                )
                val mimeColumn = cursor.getColumnIndex(
                    MediaStore.Audio.Media.MIME_TYPE,
                )
                val relativePathColumn = cursor.getColumnIndex(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                )
                val legacyPathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    totalAudioCount += 1
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn).orEmpty()
                    val relativePath = relativePathColumn.takeIf { it >= 0 }
                        ?.let { cursor.getString(it) }
                        ?: legacyPathColumn.takeIf { it >= 0 }
                            ?.let { cursor.getString(it)?.substringBeforeLast('/', "") }
                    val mimeType = mimeColumn.takeIf { it >= 0 }
                        ?.let { cursor.getString(it) }

                    val pathParts = relativePath
                        ?.replace('\\', '/')
                        ?.split('/')
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        .orEmpty()
                    val inRecordings = pathParts.any {
                        it.equals("Recordings", ignoreCase = true)
                    }
                    val inCallFolder = isCallRecordingPath(relativePath)

                    if (inRecordings) recordingsCount += 1
                    if (inCallFolder) callRecordingsCount += 1

                    val supportedMime = mimeType.equals("audio/mp4", true) ||
                        mimeType.equals("audio/m4a", true) ||
                        displayName.endsWith(".m4a", ignoreCase = true)
                    if (!inRecordings || (callFolderOnly && !inCallFolder) || !supportedMime) {
                        continue
                    }

                    recordings += CallRecording(
                        id = id,
                        displayName = displayName,
                        dateAddedSeconds = dateAddedColumn.takeIf { it >= 0 }
                            ?.let { cursor.getLong(it) },
                        dateModifiedSeconds = dateModifiedColumn.takeIf { it >= 0 }
                            ?.let { cursor.getLong(it) },
                        sizeBytes = sizeColumn.takeIf { it >= 0 }
                            ?.let { cursor.getLong(it) },
                        durationMs = durationColumn.takeIf { it >= 0 }
                            ?.let { cursor.getLong(it) },
                        mimeType = mimeType,
                        relativePath = relativePath,
                        contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id,
                        ),
                        parsedFileName = CallFileNameParser.parse(displayName),
                    )
                }
            } ?: errors.add(
                "MediaStore no devolvió un cursor para MediaStore.Audio.Media.",
            )
        } catch (securityException: SecurityException) {
            errors.add(
                "Android rechazó el acceso a MediaStore: " +
                    (securityException.message ?: "SecurityException"),
            )
        } catch (exception: Exception) {
            errors.add(
                "Error consultando MediaStore: " +
                    (exception.message ?: exception.javaClass.simpleName),
            )
        }

        return QueryResult(
            recordings = recordings,
            diagnostics = DiagnosticReport(
                totalAudioCount = totalAudioCount,
                recordingsCount = recordingsCount,
                callRecordingsCount = callRecordingsCount,
                permissionGranted = true,
                errors = errors,
            ),
        )
    }
}

data class QueryResult(
    val recordings: List<CallRecording>,
    val diagnostics: DiagnosticReport,
)