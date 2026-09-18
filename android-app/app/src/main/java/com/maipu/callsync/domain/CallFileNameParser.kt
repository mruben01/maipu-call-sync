package com.maipu.callsync.domain

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parser for the filename observed on the tested Samsung Galaxy A04e:
 *
 * Grabación de llamada +543513993670_260918_090437.m4a
 *
 * The suffix is interpreted as YYMMDD_HHMMSS. A two-digit year from 00 to 69
 * maps to 2000-2069; 70-99 maps to 1970-1999. The rule is explicit so an
 * unrecognized or ambiguous filename is never silently presented as parsed.
 */
object CallFileNameParser {
    private val samsungPattern = Regex(
        pattern = """^Grabación de llamada\s+(.+?)_(\d{6})_(\d{6})\.m4a$""",
        option = RegexOption.IGNORE_CASE,
    )

    fun parse(displayName: String): ParsedCallFileName? {
        val match = samsungPattern.matchEntire(displayName) ?: return null
        val phoneNumber = match.groupValues[1].trim()
        val dateToken = match.groupValues[2]
        val timeToken = match.groupValues[3]

        val twoDigitYear = dateToken.substring(0, 2).toInt()
        val fullYear = if (twoDigitYear <= 69) {
            2000 + twoDigitYear
        } else {
            1900 + twoDigitYear
        }
        val normalizedDate = "$fullYear${dateToken.substring(2)} $timeToken"

        val parser = SimpleDateFormat("yyyyMMdd HHmmss", Locale.US).apply {
            isLenient = false
        }
        val parsedDate = try {
            parser.parse(normalizedDate)
        } catch (_: ParseException) {
            null
        } ?: return null

        return ParsedCallFileName(
            phoneNumber = phoneNumber,
            parsedDate = parsedDate,
            rawDateToken = dateToken,
            rawTimeToken = timeToken,
        )
    }
}

data class ParsedCallFileName(
    val phoneNumber: String,
    val parsedDate: Date,
    val rawDateToken: String,
    val rawTimeToken: String,
)