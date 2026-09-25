package com.maipu.callsync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maipu.callsync.audio.PlaybackState
import com.maipu.callsync.data.CallRecording
import com.maipu.callsync.data.SyncRecordState
import com.maipu.callsync.data.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecordingListItem(
    recording: CallRecording,
    syncState: SyncRecordState,
    playbackState: PlaybackState,
    isSyncing: Boolean,
    isCurrentSync: Boolean,
    isAutomaticCall: Boolean,
    onPlay: () -> Unit,
    onSync: () -> Unit,
) {
    var detailsExpanded by rememberSaveable(recording.contentUri.toString()) {
        mutableStateOf(false)
    }
    val playbackIsCurrent = when (playbackState) {
        is PlaybackState.Loading -> playbackState.uri == recording.contentUri
        is PlaybackState.Playing -> playbackState.uri == recording.contentUri
        is PlaybackState.Paused -> playbackState.uri == recording.contentUri
        is PlaybackState.Error -> playbackState.uri == recording.contentUri
        else -> false
    }
    val playLabel = when {
        playbackState is PlaybackState.Loading && playbackIsCurrent -> "Abriendo..."
        playbackState is PlaybackState.Playing && playbackIsCurrent -> "Pausar"
        playbackState is PlaybackState.Paused && playbackIsCurrent -> "Continuar"
        else -> "Reproducir"
    }
    val hasError = syncState.status == SyncStatus.FAILED || syncState.lastError != null
    val statusLabel = when {
        isCurrentSync -> "Sincronizando"
        hasError -> "Con error"
        syncState.status == SyncStatus.SYNCED -> "Sincronizada"
        else -> "Pendiente"
    }
    val badgeColor = when {
        hasError -> PilotColors.error
        syncState.status == SyncStatus.SYNCED -> PilotColors.success
        else -> PilotColors.pending
    }
    val badgeSurface = when {
        hasError -> PilotColors.errorSurface
        syncState.status == SyncStatus.SYNCED -> PilotColors.successSurface
        else -> PilotColors.pendingSurface
    }
    val date = recording.parsedFileName?.parsedDate
        ?: recording.dateModifiedSeconds?.let { Date(it * 1_000L) }
        ?: recording.dateAddedSeconds?.let { Date(it * 1_000L) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = recording.parsedFileName?.phoneNumber
                            ?: "Grabación de llamada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PilotColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = date?.let {
                            SimpleDateFormat(
                                "dd MMM · HH:mm",
                                Locale("es", "AR"),
                            ).format(it)
                        } ?: "Fecha no disponible",
                        style = MaterialTheme.typography.bodySmall,
                        color = PilotColors.muted,
                    )
                }
                Surface(
                    color = badgeSurface,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                    )
                }
            }
            if (!isAutomaticCall) {
                Text(
                    text = "Fuera del respaldo automático",
                    style = MaterialTheme.typography.bodySmall,
                    color = PilotColors.muted,
                )
            }
            if (hasError) {
                Text(
                    text = "No se pudo completar el respaldo de esta grabación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PilotColors.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onPlay,
                    enabled = !(playbackState is PlaybackState.Loading && playbackIsCurrent),
                ) {
                    Text(playLabel)
                }
                if (syncState.status != SyncStatus.SYNCED) {
                    TextButton(onClick = onSync, enabled = !isSyncing) {
                        Text(if (isCurrentSync) "Sincronizando..." else "Reintentar")
                    }
                }
            }
            TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                Text(if (detailsExpanded) "Ocultar detalles" else "Ver detalles")
            }
            if (playbackIsCurrent && playbackState is PlaybackState.Error) {
                Text(
                    text = playbackState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = PilotColors.error,
                )
            }
            if (detailsExpanded) {
                HorizontalDivider(color = PilotColors.border)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "Archivo: ${recording.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PilotColors.muted,
                    )
                    Text(
                        text = "Duración: ${formatDuration(recording.durationMs)} · " +
                            "Tamaño: ${formatBytes(recording.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PilotColors.muted,
                    )
                    Text(
                        text = "Ruta: ${recording.relativePath ?: "no disponible"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PilotColors.muted,
                    )
                    Text(
                        text = "Content URI: ${recording.contentUri}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PilotColors.muted,
                    )
                    Text(
                        text = "Intentos: ${syncState.attemptCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PilotColors.muted,
                    )
                    syncState.lastError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = PilotColors.error,
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs < 0) return "no disponible"
    val totalSeconds = durationMs / 1_000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "no disponible"
    if (bytes < 1_048_576L) return "%.0f KB".format(bytes / 1_024.0)
    return "%.2f MB".format(bytes / 1_048_576.0)
}