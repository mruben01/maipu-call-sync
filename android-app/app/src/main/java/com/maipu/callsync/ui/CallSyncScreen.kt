package com.maipu.callsync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maipu.callsync.audio.PlaybackState
import com.maipu.callsync.data.CallRecording
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSyncScreen(
    state: CallSyncUiState,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onPlay: (CallRecording) -> Unit,
) {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "MAIPÚ CALL SYNC",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    DeviceSummary(state)
                }
                item {
                    PermissionPanel(
                        permissionGranted = state.permissionGranted,
                        onRequestPermission = onRequestPermission,
                    )
                }
                item {
                    SearchPanel(
                        count = state.recordings.size,
                        isLoading = state.isLoading,
                        onScan = onScan,
                    )
                }
                state.errorMessage?.let { message ->
                    item { ErrorPanel(message) }
                }
                item {
                    Text(
                        text = "LISTADO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (state.recordings.isEmpty()) {
                    item {
                        EmptyState(permissionGranted = state.permissionGranted)
                    }
                } else {
                    items(
                        items = state.recordings,
                        key = { it.contentUri.toString() },
                    ) { recording ->
                        RecordingCard(
                            recording = recording,
                            playbackState = state.playbackState,
                            onPlay = { onPlay(recording) },
                        )
                    }
                }
                item {
                    DiagnosticPanel(state.diagnostics)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceSummary(state: CallSyncUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Dispositivo: ${state.diagnostics.manufacturer} " +
                state.diagnostics.model,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Android: ${state.diagnostics.androidVersion}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Estado del permiso de audio:",
                style = MaterialTheme.typography.bodyLarge,
            )
            AssistChip(
                onClick = {},
                label = {
                    Text(if (state.permissionGranted) "AUTORIZADO" else "NO AUTORIZADO")
                },
            )
        }
    }
}

@Composable
private fun PermissionPanel(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
) {
    if (!permissionGranted) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Se necesita acceso a audio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Android debe autorizar la lectura de audio " +
                        "expuesto por MediaStore.",
                )
                Button(onClick = onRequestPermission) {
                    Text("AUTORIZAR ACCESO")
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(
    count: Int,
    isLoading: Boolean,
    onScan: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "GRABACIONES ENCONTRADAS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Grabaciones encontradas: $count",
                style = MaterialTheme.typography.headlineSmall,
            )
            Button(
                onClick = onScan,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLoading) "BUSCANDO..." else "BUSCAR GRABACIONES")
            }
        }
    }
}

@Composable
private fun EmptyState(permissionGranted: Boolean) {
    Card {
        Text(
            text = if (permissionGranted) {
                "MediaStore no está exponiendo grabaciones .m4a dentro de " +
                    "Recordings/. Realice una llamada grabada desde Samsung " +
                    "Phone y vuelva a buscar."
            } else {
                "Autorice el permiso de audio para consultar MediaStore."
            },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun RecordingCard(
    recording: CallRecording,
    playbackState: PlaybackState,
    onPlay: () -> Unit,
) {
    val parsed = recording.parsedFileName
    val isCurrent = when (playbackState) {
        is PlaybackState.Loading -> playbackState.uri == recording.contentUri
        is PlaybackState.Playing -> playbackState.uri == recording.contentUri
        is PlaybackState.Paused -> playbackState.uri == recording.contentUri
        is PlaybackState.Error -> playbackState.uri == recording.contentUri
        else -> false
    }
    val buttonLabel = when {
        playbackState is PlaybackState.Loading &&
            playbackState.uri == recording.contentUri -> "ABRIENDO..."
        playbackState is PlaybackState.Playing &&
            playbackState.uri == recording.contentUri -> "PAUSAR"
        playbackState is PlaybackState.Paused &&
            playbackState.uri == recording.contentUri -> "CONTINUAR"
        else -> "REPRODUCIR"
    }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = parsed?.phoneNumber?.let(::prettyPhoneNumber)
                    ?: "Número no interpretado",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = parsed?.parsedDate?.let(::formatDateTime)
                    ?: recording.dateModifiedSeconds?.let(::formatEpochSeconds)
                    ?: recording.dateAddedSeconds?.let(::formatEpochSeconds)
                    ?: "Fecha no disponible",
            )
            Text("Duración: ${formatDuration(recording.durationMs)}")
            Text("Tamaño: ${formatBytes(recording.sizeBytes)}")
            Text(
                text = "Archivo: ${recording.displayName}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Ruta: ${recording.relativePath ?: "no expuesta por MediaStore"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Content URI: ${recording.contentUri}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (parsed == null) {
                Text(
                    text = "El nombre no coincide con el patrón Samsung YYMMDD_HHMMSS; " +
                        "se conserva el nombre original.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onPlay,
                enabled = !(
                    playbackState is PlaybackState.Loading &&
                        playbackState.uri == recording.contentUri
                    ),
                ) {
                Text(buttonLabel)
            }
            if (isCurrent && playbackState is PlaybackState.Error) {
                Text(
                    text = playbackState.message,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticPanel(report: com.maipu.callsync.data.DiagnosticReport) {
    var expanded by remember { mutableStateOf(false) }
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "DIAGNÓSTICO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "OCULTAR" else "MOSTRAR")
                }
            }
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Audios totales en MediaStore: ${report.totalAudioCount}")
                    Text("Audios dentro de Recordings: ${report.recordingsCount}")
                    Text("Audios dentro de Recordings/Call: ${report.callRecordingsCount}")
                    Text("Permiso concedido: ${if (report.permissionGranted) "sí" else "no"}")
                    Text("Android: ${report.androidVersion}")
                    Text("Fabricante: ${report.manufacturer}")
                    Text("Modelo: ${report.model}")
                    if (report.errors.isEmpty()) {
                        Text("Errores de acceso: ninguno")
                    } else {
                        Text("Errores de acceso:")
                        report.errors.forEach { error ->
                            Text("• $error", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDateTime(date: Date): String =
    SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale("es", "AR")).format(date)

private fun formatEpochSeconds(seconds: Long): String =
    formatDateTime(Date(seconds * 1000))

private fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs < 0) return "no disponible"
    val totalSeconds = durationMs / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "no disponible"
    if (bytes < 1024 * 1024) return "%.0f KB".format(bytes / 1024.0)
    return "%.2f MB".format(bytes / (1024.0 * 1024.0))
}

private fun prettyPhoneNumber(phone: String): String {
    val compact = phone.replace(" ", "")
    if (compact.startsWith("+54") && compact.length == 13) {
        return compact.substring(0, 3) + " " +
            compact.substring(3, 6) + " " +
            compact.substring(6)
    }
    return phone
}