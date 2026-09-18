package com.maipu.callsync.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maipu.callsync.audio.AudioPlaybackController
import com.maipu.callsync.audio.PlaybackState
import com.maipu.callsync.data.CallRecording
import com.maipu.callsync.data.CallRecordingRepository
import com.maipu.callsync.data.DiagnosticReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CallRecordingRepository(application)
    private val playback = AudioPlaybackController(application) { state ->
        _uiState.value = _uiState.value.copy(playbackState = state)
    }
    private val _uiState = MutableStateFlow(
        CallSyncUiState(
            permissionGranted = hasAudioPermission(),
            diagnostics = DiagnosticReport(permissionGranted = hasAudioPermission()),
        ),
    )
    val uiState: StateFlow<CallSyncUiState> = _uiState.asStateFlow()

    fun refreshPermissionState() {
        val permissionGranted = hasAudioPermission()
        _uiState.value = _uiState.value.copy(
            permissionGranted = permissionGranted,
            diagnostics = _uiState.value.diagnostics.copy(
                permissionGranted = permissionGranted,
            ),
        )
    }

    fun scan() {
        val permissionGranted = hasAudioPermission()
        if (!permissionGranted) {
            refreshPermissionState()
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.findRecordings(permissionGranted)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                recordings = result.recordings,
                diagnostics = result.diagnostics,
                errorMessage = result.diagnostics.errors.firstOrNull(),
            )
        }
    }

    fun togglePlayback(recording: CallRecording) {
        playback.toggle(recording.contentUri)
    }

    override fun onCleared() {
        playback.release()
        super.onCleared()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            getApplication(),
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

data class CallSyncUiState(
    val permissionGranted: Boolean = false,
    val isLoading: Boolean = false,
    val recordings: List<CallRecording> = emptyList(),
    val diagnostics: DiagnosticReport = DiagnosticReport(),
    val errorMessage: String? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
)