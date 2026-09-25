package com.maipu.callsync

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.maipu.callsync.background.CallSyncAutoScheduler

class CallSyncApplication : Application() {
    private val observerHandler = Handler(Looper.getMainLooper())
    private val enqueueObservedChange = Runnable {
        CallSyncAutoScheduler.enqueueImmediate(this)
    }
    private var audioObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        CallSyncAutoScheduler.ensurePeriodicSync(this)

        val observer = object : ContentObserver(observerHandler) {
            override fun onChange(selfChange: Boolean) {
                scheduleObservedChange()
            }

            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                scheduleObservedChange()
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
            audioObserver = observer
        } catch (exception: SecurityException) {
            Log.w(TAG, "No se pudo observar MediaStore en este inicio.")
        }
    }

    private fun scheduleObservedChange() {
        observerHandler.removeCallbacks(enqueueObservedChange)
        observerHandler.postDelayed(enqueueObservedChange, OBSERVER_DEBOUNCE_MILLIS)
    }

    private companion object {
        const val TAG = "MaipuCallSync"
        const val OBSERVER_DEBOUNCE_MILLIS = 30_000L
    }
}