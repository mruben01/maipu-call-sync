package com.maipu.callsync.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRecordingPathTest {
    @Test
    fun `matches Samsung call recordings folder`() {
        assertTrue(isCallRecordingPath("Recordings/Call/"))
        assertTrue(isCallRecordingPath("/Recordings/Call"))
        assertTrue(isCallRecordingPath("recordings/call/2026/"))
        assertTrue(isCallRecordingPath("Internal storage/Recordings/Call/"))
    }

    @Test
    fun `rejects other recording folders`() {
        assertFalse(isCallRecordingPath(null))
        assertFalse(isCallRecordingPath("Recordings/"))
        assertFalse(isCallRecordingPath("Recordings/Voice Recorder/"))
        assertFalse(isCallRecordingPath("MyRecordings/Call/"))
        assertFalse(isCallRecordingPath("Recordings/Other/Call/"))
    }
}