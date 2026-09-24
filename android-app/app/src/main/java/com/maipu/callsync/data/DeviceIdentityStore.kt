package com.maipu.callsync.data

import android.content.Context
import java.util.UUID

class DeviceIdentityStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun getOrCreate(): String {
        preferences.getString(DEVICE_ID_KEY, null)?.let { return it }

        val deviceId = UUID.randomUUID().toString()
        val saved = preferences.edit().putString(DEVICE_ID_KEY, deviceId).commit()
        check(saved) { "No se pudo persistir el identificador de instalación." }
        return deviceId
    }

    private companion object {
        const val PREFERENCES_NAME = "call_sync_identity"
        const val DEVICE_ID_KEY = "device_id"
    }
}