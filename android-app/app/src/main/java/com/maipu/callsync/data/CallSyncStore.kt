package com.maipu.callsync.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CallSyncStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                source_key TEXT PRIMARY KEY NOT NULL,
                status TEXT NOT NULL,
                sha256 TEXT,
                storage_path TEXT,
                phone_number TEXT,
                call_datetime TEXT,
                duration_seconds INTEGER,
                file_name TEXT NOT NULL,
                device_id TEXT NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) = Unit

    fun ensurePending(sourceKey: String, metadata: CallRecordingMetadata) {
        val values = ContentValues().apply {
            put("source_key", sourceKey)
            put("status", SyncStatus.PENDING.name)
            put("phone_number", metadata.phoneNumber)
            put("call_datetime", metadata.callDatetime)
            put("duration_seconds", metadata.durationSeconds)
            put("file_name", metadata.fileName)
            put("device_id", metadata.deviceId)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun get(sourceKey: String): StoredSyncEntry? =
        readableDatabase.query(
            TABLE_NAME,
            null,
            "source_key = ?",
            arrayOf(sourceKey),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null

            StoredSyncEntry(
                sourceKey = cursor.getString(cursor.getColumnIndexOrThrow("source_key")),
                status = runCatching {
                    SyncStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status")))
                }.getOrDefault(SyncStatus.FAILED),
                sha256 = cursor.getString(cursor.getColumnIndexOrThrow("sha256")),
                storagePath = cursor.getString(cursor.getColumnIndexOrThrow("storage_path")),
                metadata = CallRecordingMetadata(
                    phoneNumber = cursor.getString(
                        cursor.getColumnIndexOrThrow("phone_number"),
                    ),
                    callDatetime = cursor.getString(
                        cursor.getColumnIndexOrThrow("call_datetime"),
                    ),
                    durationSeconds = cursor.nullableLong("duration_seconds"),
                    fileName = cursor.getString(cursor.getColumnIndexOrThrow("file_name")),
                    deviceId = cursor.getString(cursor.getColumnIndexOrThrow("device_id")),
                ),
                attemptCount = cursor.getInt(cursor.getColumnIndexOrThrow("attempt_count")),
                lastError = cursor.getString(cursor.getColumnIndexOrThrow("last_error")),
                updatedAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
            )
        }

    fun getState(sourceKey: String): SyncRecordState? =
        get(sourceKey)?.let {
            SyncRecordState(
                status = it.status,
                attemptCount = it.attemptCount,
                lastError = it.lastError,
                updatedAtMillis = it.updatedAtMillis,
            )
        }

    fun getStates(sourceKeys: Collection<String>): Map<String, SyncRecordState> =
        sourceKeys.mapNotNull { sourceKey ->
            getState(sourceKey)?.let { sourceKey to it }
        }.toMap()

    fun markUploading(sourceKey: String) {
        writableDatabase.execSQL(
            """
            UPDATE $TABLE_NAME
            SET status = ?, attempt_count = attempt_count + 1,
                last_error = NULL, updated_at = ?
            WHERE source_key = ?
            """.trimIndent(),
            arrayOf(SyncStatus.UPLOADING.name, System.currentTimeMillis(), sourceKey),
        )
    }

    fun markMetadataRetry(sourceKey: String) {
        writableDatabase.execSQL(
            """
            UPDATE $TABLE_NAME
            SET attempt_count = attempt_count + 1,
                last_error = NULL, updated_at = ?
            WHERE source_key = ?
            """.trimIndent(),
            arrayOf(System.currentTimeMillis(), sourceKey),
        )
    }

    fun saveUploadTarget(sourceKey: String, sha256: String, storagePath: String) {
        writableDatabase.update(
            TABLE_NAME,
            ContentValues().apply {
                put("sha256", sha256)
                put("storage_path", storagePath)
                put("updated_at", System.currentTimeMillis())
            },
            "source_key = ?",
            arrayOf(sourceKey),
        )
    }

    fun markAudioUploaded(sourceKey: String, sha256: String, storagePath: String) {
        writableDatabase.update(
            TABLE_NAME,
            ContentValues().apply {
                put("status", SyncStatus.AUDIO_UPLOADED.name)
                put("sha256", sha256)
                put("storage_path", storagePath)
                putNull("last_error")
                put("updated_at", System.currentTimeMillis())
            },
            "source_key = ?",
            arrayOf(sourceKey),
        )
    }

    fun markSynced(sourceKey: String) {
        writableDatabase.update(
            TABLE_NAME,
            ContentValues().apply {
                put("status", SyncStatus.SYNCED.name)
                putNull("last_error")
                put("updated_at", System.currentTimeMillis())
            },
            "source_key = ?",
            arrayOf(sourceKey),
        )
    }

    fun markFailed(sourceKey: String, error: String, audioUploaded: Boolean) {
        writableDatabase.update(
            TABLE_NAME,
            ContentValues().apply {
                put(
                    "status",
                    if (audioUploaded) {
                        SyncStatus.AUDIO_UPLOADED.name
                    } else {
                        SyncStatus.FAILED.name
                    },
                )
                put("last_error", error)
                put("updated_at", System.currentTimeMillis())
            },
            "source_key = ?",
            arrayOf(sourceKey),
        )
    }

    private fun android.database.Cursor.nullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private companion object {
        const val DATABASE_NAME = "call_sync_state.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "recording_sync"
    }
}