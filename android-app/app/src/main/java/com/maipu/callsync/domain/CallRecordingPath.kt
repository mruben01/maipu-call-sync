package com.maipu.callsync.domain

fun isCallRecordingPath(relativePath: String?): Boolean {
    val pathParts = relativePath
        ?.replace('\\', '/')
        ?.split('/')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    return pathParts.zipWithNext().any { (parent, child) ->
        parent.equals("Recordings", ignoreCase = true) &&
            child.equals("Call", ignoreCase = true)
    }
}