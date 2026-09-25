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
import com.maipu.callsync.data.CallRecordingSyncRepository
import com.maipu.callsync.data.DiagnosticReport
import com.maipu.callsync.data.SyncRecordState
import com.maipu.callsync.data.SyncStatus
import com.maipu.callsync.data.syncSourceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CallRecordingRepository(application)
    private val syncRepository = CallRecordingSyncRepository(application)
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
        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.findRecordings(permissionGranted)
                val syncStatuses = if (result.diagnostics.errors.isEmpty()) {
                    syncRepository.statesFor(result.recordings)
                } else {
                    emptyMap()
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasScanned = result.diagnostics.errors.isEmpty(),
                    recordings = result.recordings,
                    syncStatuses = syncStatuses,
                    diagnostics = result.diagnostics,
                    errorMessage = result.diagnostics.errors.firstOrNull(),
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasScanned = false,
                    errorMessage = "No se pudo leer el estado local de las llamadas " +
                        "(${exception.javaClass.simpleName}).",
                )
            }
        }
    }

    fun syncRecording(recording: CallRecording) {
        if (!hasAudioPermission()) {
            refreshPermissionState()
            _uiState.value = _uiState.value.copy(
                errorMessage = "Autorice el acceso a audio antes de sincronizar.",
            )
            return
        }
        syncQueue(listOf(recording))
    }

    fun syncPending() {
        if (!hasAudioPermission()) {
            refreshPermissionState()
            _uiState.value = _uiState.value.copy(
                errorMessage = "Autorice el acceso a audio antes de sincronizar.",
            )
            return
        }

        val currentState = _uiState.value
        val pending = currentState.recordings.filter { recording ->
            currentState.syncStatuses[recording.syncSourceKey()]?.status != SyncStatus.SYNCED
        }
        syncQueue(pending)
    }

    fun togglePlayback(recording: CallRecording) {
        playback.toggle(recording.contentUri)
    }

    override fun onCleared() {
        playback.release()
        super.onCleared()
    }

    private fun syncQueue(recordings: List<CallRecording>) {
        if (recordings.isEmpty() || _uiState.value.isSyncing) return

        _uiState.value = _uiState.value.copy(
            isSyncing = true,
            syncingSourceKey = null,
            errorMessage = null,
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recordings.forEach { recording ->
                    val sourceKey = recording.syncSourceKey()
                    val previousState = runCatching {
                        syncRepository.stateFor(recording)
                    }.getOrDefault(SyncRecordState())
                    _uiState.value = _uiState.value.copy(
                        syncingSourceKey = sourceKey,
                        syncStatuses = _uiState.value.syncStatuses +
                            (sourceKey to previousState),
                    )
                    val completedState = runCatching {
                        syncRepository.sync(recording)
                    }.getOrElse { exception ->
                        SyncRecordState(
                            status = SyncStatus.FAILED,
                            lastError = "No se pudo guardar el estado local " +
                                "(${exception.javaClass.simpleName}).",
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        syncStatuses = _uiState.value.syncStatuses +
                            (sourceKey to completedState),
                    )
                }
            } finally {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncingSourceKey = null,
                )
            }
        }
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
    val hasScanned: Boolean = false,
    val recordings: List<CallRecording> = emptyList(),
    val syncStatuses: Map<String, SyncRecordState> = emptyMap(),
    val isSyncing: Boolean = false,
    val syncingSourceKey: String? = null,
    val diagnostics: DiagnosticReport = DiagnosticReport(),
    val errorMessage: String? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
)