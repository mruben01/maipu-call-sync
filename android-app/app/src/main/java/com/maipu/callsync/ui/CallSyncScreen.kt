package com.maipu.callsync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maipu.callsync.data.CallRecording
import com.maipu.callsync.data.DiagnosticReport
import com.maipu.callsync.data.SyncRecordState
import com.maipu.callsync.data.SyncStatus
import com.maipu.callsync.data.syncSourceKey
import com.maipu.callsync.domain.isCallRecordingPath

@Composable
fun CallSyncScreen(
    state: CallSyncUiState,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onPlay: (CallRecording) -> Unit,
    onSync: (CallRecording) -> Unit,
    onSyncPending: () -> Unit,
) {
    val calls = state.recordings.filter { isCallRecordingPath(it.relativePath) }
    val otherRecordings = state.recordings.filterNot { isCallRecordingPath(it.relativePath) }
    val callStates = calls.map { recording ->
        state.syncStatuses[recording.syncSourceKey()] ?: SyncRecordState()
    }
    val snapshot = summarizeDashboard(
        callStates = callStates,
        permissionGranted = state.permissionGranted,
        hasScanned = state.hasScanned,
        hasQueryError = state.diagnostics.errors.isNotEmpty() ||
            (state.errorMessage != null && !state.hasScanned),
        hasScreenError = state.errorMessage != null,
    )
    val manualPendingCount = state.recordings.count { recording ->
        state.syncStatuses[recording.syncSourceKey()]?.status != SyncStatus.SYNCED
    }
    var showAllCalls by rememberSaveable { mutableStateOf(false) }
    var showOtherRecordings by rememberSaveable { mutableStateOf(false) }
    val visibleCalls = if (showAllCalls) calls else calls.take(3)

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PilotColors.accent,
            onPrimary = Color.White,
            background = PilotColors.background,
            onBackground = PilotColors.ink,
            surface = Color.White,
            onSurface = PilotColors.ink,
            error = PilotColors.error,
        ),
    ) {
        Scaffold(containerColor = PilotColors.background) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 24.dp,
                    end = 18.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { DashboardHeader() }
                item {
                    StatusHero(
                        snapshot = snapshot,
                        permissionGranted = state.permissionGranted,
                        isSyncing = state.isSyncing,
                        onRequestPermission = onRequestPermission,
                    )
                }
                item { MetricsCard(snapshot.counts) }
                item {
                    InformationCard(
                        snapshot = snapshot,
                        manufacturer = state.diagnostics.manufacturer,
                        model = state.diagnostics.model,
                        androidVersion = state.diagnostics.androidVersion,
                    )
                }
                item {
                    OutlinedButton(
                        onClick = onSyncPending,
                        enabled = state.permissionGranted && state.hasScanned &&
                            !state.isSyncing && manualPendingCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PilotColors.accent),
                    ) {
                        Text(if (state.isSyncing) "Sincronizando..." else "Sincronizar ahora")
                    }
                }
                state.errorMessage?.let { message ->
                    item { ErrorNotice(message) }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Tus llamadas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PilotColors.ink,
                        )
                        TextButton(
                            onClick = onScan,
                            enabled = state.permissionGranted && !state.isLoading,
                        ) {
                            Text(if (state.isLoading) "Actualizando..." else "Actualizar")
                        }
                    }
                }
                if (!state.permissionGranted) {
                    item { QuietMessage("Autorizá el acceso al audio para ver tus llamadas.") }
                } else if (!state.hasScanned) {
                    item {
                        QuietMessage(
                            if (state.isLoading) {
                                "Comprobando las grabaciones del dispositivo..."
                            } else {
                                "No se pudieron cargar los datos. Tocá Actualizar para reintentar."
                            },
                        )
                    }
                } else {
                    if (state.isLoading) {
                        item {
                            Text(
                                text = "Actualizando datos...",
                                style = MaterialTheme.typography.bodySmall,
                                color = PilotColors.muted,
                            )
                        }
                    }
                    if (calls.isEmpty()) {
                        item {
                            QuietMessage(
                                "Todavía no se detectaron llamadas grabadas para el " +
                                    "respaldo automático.",
                            )
                        }
                    } else {
                        items(visibleCalls, key = { it.contentUri.toString() }) { recording ->
                            RecordingListItem(
                                recording = recording,
                                syncState = state.syncStatuses[recording.syncSourceKey()]
                                    ?: SyncRecordState(),
                                playbackState = state.playbackState,
                                isSyncing = state.isSyncing,
                                isCurrentSync =
                                    state.syncingSourceKey == recording.syncSourceKey(),
                                isAutomaticCall = true,
                                onPlay = { onPlay(recording) },
                                onSync = { onSync(recording) },
                            )
                        }
                        if (calls.size > 3) {
                            item {
                                TextButton(onClick = { showAllCalls = !showAllCalls }) {
                                    Text(
                                        if (showAllCalls) {
                                            "Ver menos"
                                        } else {
                                            "Ver todas las llamadas (${calls.size})"
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (otherRecordings.isNotEmpty()) {
                        item {
                            TextButton(
                                onClick = { showOtherRecordings = !showOtherRecordings },
                            ) {
                                Text(
                                    if (showOtherRecordings) {
                                        "Ocultar otras grabaciones"
                                    } else {
                                        "Otras grabaciones (${otherRecordings.size})"
                                    },
                                )
                            }
                        }
                        if (showOtherRecordings) {
                            items(
                                otherRecordings,
                                key = { "other:${it.contentUri}" },
                            ) { recording ->
                                RecordingListItem(
                                    recording = recording,
                                    syncState = state.syncStatuses[recording.syncSourceKey()]
                                        ?: SyncRecordState(),
                                    playbackState = state.playbackState,
                                    isSyncing = state.isSyncing,
                                    isCurrentSync =
                                        state.syncingSourceKey == recording.syncSourceKey(),
                                    isAutomaticCall = false,
                                    onPlay = { onPlay(recording) },
                                    onSync = { onSync(recording) },
                                )
                            }
                        }
                    }
                }
                item { DiagnosticPanel(state.diagnostics) }
            }
        }
    }
}

@Composable
private fun ErrorNotice(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PilotColors.errorSurface),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = PilotColors.error,
        )
    }
}

@Composable
private fun QuietMessage(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = PilotColors.muted,
        )
    }
}

@Composable
private fun DiagnosticPanel(report: DiagnosticReport) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Ocultar soporte y diagnóstico" else "Soporte y diagnóstico")
        }
        if (expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Datos para soporte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider(color = PilotColors.border)
                    Text("Audios en MediaStore: ${report.totalAudioCount}")
                    Text("Audios en Recordings: ${report.recordingsCount}")
                    Text("Audios en Recordings/Call: ${report.callRecordingsCount}")
                    Text("Permiso de audio: ${if (report.permissionGranted) "concedido" else "pendiente"}")
                    Text("Android: ${report.androidVersion}")
                    Text("Dispositivo: ${report.manufacturer} ${report.model}")
                    report.errors.forEach { error ->
                        Text(error, color = PilotColors.error)
                    }
                }
            }
        }
    }
}