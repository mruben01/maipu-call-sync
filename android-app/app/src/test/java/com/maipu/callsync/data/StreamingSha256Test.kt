package com.maipu.callsync.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingSha256Test {
    @Test
    fun computesSha256WithoutNeedingAFileCopy() {
        val hash = ByteArrayInputStream("abc".toByteArray()).sha256Hex()

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hash,
        )
    }
}