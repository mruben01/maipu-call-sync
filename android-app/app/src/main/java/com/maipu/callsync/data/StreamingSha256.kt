package com.maipu.callsync.data

import java.io.InputStream
import java.security.MessageDigest

internal fun InputStream.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val bytesRead = read(buffer)
        if (bytesRead < 0) break
        digest.update(buffer, 0, bytesRead)
    }
    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte)
    }
}