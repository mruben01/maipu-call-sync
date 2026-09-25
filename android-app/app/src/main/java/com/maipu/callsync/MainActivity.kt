package com.maipu.callsync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maipu.callsync.background.CallSyncAutoScheduler
import com.maipu.callsync.ui.CallSyncScreen
import com.maipu.callsync.ui.CallSyncViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CallSyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                viewModel.refreshPermissionState()
                if (granted) {
                    CallSyncAutoScheduler.enqueueImmediate(this@MainActivity)
                    viewModel.scan()
                }
            }

            CallSyncScreen(
                state = state,
                onRequestPermission = {
                    permissionLauncher.launch(requiredAudioPermission())
                },
                onScan = viewModel::scan,
                onPlay = viewModel::togglePlayback,
                onSync = viewModel::syncRecording,
                onSyncPending = viewModel::syncPending,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
        if (ContextCompat.checkSelfPermission(this, requiredAudioPermission()) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            CallSyncAutoScheduler.enqueueImmediate(this)
            viewModel.scan()
        }
    }

    private fun requiredAudioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}