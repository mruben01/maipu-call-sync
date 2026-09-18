package com.maipu.callsync.domain

import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallFileNameParserTest {
    private val outputFormat = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.US,
    )

    @Test
    fun parsesObservedSamsungFilenameAsYyMmDdAndHhMmSs() {
        val parsed = CallFileNameParser.parse(
            "Grabación de llamada +543513993670_260918_090437.m4a",
        )

        val result = requireNotNull(parsed)
        assertEquals("+543513993670", result.phoneNumber)
        assertEquals("260918", result.rawDateToken)
        assertEquals("090437", result.rawTimeToken)
        assertEquals(
            "2026-09-18 09:04:37",
            outputFormat.format(result.parsedDate),
        )
    }

    @Test
    fun rejectsInvalidCalendarDateInsteadOfGuessing() {
        val parsed = CallFileNameParser.parse(
            "Grabación de llamada +543513993670_260232_090437.m4a",
        )

        assertNull(parsed)
    }

    @Test
    fun preservesUnsupportedFilenameByReturningNoParsedMetadata() {
        val parsed = CallFileNameParser.parse("grabacion-que-no-coincide.m4a")

        assertNull(parsed)
    }
}