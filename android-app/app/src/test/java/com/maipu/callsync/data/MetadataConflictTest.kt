package com.maipu.callsync.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataConflictTest {
    private val storagePathConflict =
        """{"code":"23505","details":null,"hint":null,"message":"duplicate key value violates unique constraint \"call_recordings_storage_path_key\""}"""

    @Test
    fun acceptsOnlyTheStoragePathConflictOnHttp409() {
        assertTrue(isExistingMetadataConflict(409, storagePathConflict))
        assertFalse(isExistingMetadataConflict(201, storagePathConflict))
        assertFalse(isExistingMetadataConflict(400, storagePathConflict))
    }

    @Test
    fun rejectsOtherConflictsAndMalformedResponses() {
        assertFalse(
            isExistingMetadataConflict(
                409,
                """{"code":"23505","message":"duplicate key value violates unique constraint \"call_recordings_pkey\""}""",
            ),
        )
        assertFalse(
            isExistingMetadataConflict(
                409,
                """{"code":"23503","message":"duplicate key value violates unique constraint \"call_recordings_storage_path_key\""}""",
            ),
        )
        assertFalse(isExistingMetadataConflict(409, """{"code":"23505","message":"other error: call_recordings_storage_path_key"}"""))
        assertFalse(isExistingMetadataConflict(409, "not json"))
        assertFalse(isExistingMetadataConflict(409, ""))
    }
}