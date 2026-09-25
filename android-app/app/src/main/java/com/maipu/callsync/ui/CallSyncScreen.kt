package com.maipu.callsync.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.maipu.callsync.data.CallRecording
import com.maipu.callsync.data.SyncRecordState
import com.maipu.callsync.data.SyncStatus
import com.maipu.callsync.data.syncSourceKey
import com.maipu.callsync.domain.isCallRecordingPath
import com.maipu.callsync.monitoring.DeviceProfileStore
import com.maipu.callsync.monitoring.MonitoringBindingStatus
import kotlinx.coroutines.delay

@Composable
fun CallSyncScreen(
    state: CallSyncUiState,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onPlay: (CallRecording) -> Unit,
    onSync: (CallRecording) -> Unit,
    onSyncPending: () -> Unit,
) {
    val context = LocalContext.current
    val deviceProfileStore = remember(context) { DeviceProfileStore(context) }
    var sellerName by rememberSaveable { mutableStateOf(deviceProfileStore.getName().orEmpty()) }
    var corporateLine by rememberSaveable { mutableStateOf(deviceProfileStore.getLine().orEmpty()) }
    var bindingStatus by rememberSaveable { mutableStateOf(deviceProfileStore.getBindingStatus()) }
    var profileError by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var lineInput by rememberSaveable { mutableStateOf("") }
    var currentTab by rememberSaveable { mutableStateOf(0) }
    val refreshProfile = {
        sellerName = deviceProfileStore.getName().orEmpty()
        corporateLine = deviceProfileStore.getLine().orEmpty()
        bindingStatus = deviceProfileStore.getBindingStatus()
    }
    val enrollmentValues = remember(bindingStatus, deviceProfileStore) {
        if (bindingStatus == MonitoringBindingStatus.PENDING_ADMIN) {
            runCatching {
                deviceProfileStore.getDeviceId() to deviceProfileStore.getPublicMonitoringTokenHash()
            }
        } else {
            null
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshProfile()
    }
    LaunchedEffect(currentTab) {
        if (currentTab == 1) {
            repeat(30) {
                refreshProfile()
                delay(1_000)
            }
        }
    }

    val calls = state.recordings.filter { isCallRecordingPath(it.relativePath) }
    val otherRecordings = state.recordings.filterNot { isCallRecordingPath(it.relativePath) }
    val callStates = calls.map { state.syncStatuses[it.syncSourceKey()] ?: SyncRecordState() }
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
            if (sellerName.isBlank() || corporateLine.isBlank()) {
                SellerSetup(
                    name = nameInput,
                    line = lineInput,
                    onNameChange = { nameInput = it },
                    onLineChange = { lineInput = it },
                    onSave = {
                        val cleanedName = nameInput.trim()
                        val cleanedLine = lineInput.trim()
                        if (cleanedName.isNotEmpty() && cleanedLine.isNotEmpty()) {
                            runCatching { deviceProfileStore.save(cleanedName, cleanedLine) }
                                .onSuccess {
                                    sellerName = cleanedName
                                    corporateLine = cleanedLine
                                    bindingStatus = deviceProfileStore.getBindingStatus()
                                    profileError = null
                                }
                                .onFailure { error ->
                                    profileError = error.message ?: "No se pudo guardar el perfil."
                                }
                        }
                    },
                    error = profileError,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { DashboardHeader(sellerName, corporateLine) }
                    if (bindingStatus == MonitoringBindingStatus.PENDING_ADMIN) {
                        enrollmentValues?.fold(
                            onSuccess = { (deviceId, tokenHash) ->
                                item {
                                    EnrollmentCard(
                                        sellerName = sellerName,
                                        corporateLine = corporateLine,
                                        deviceId = deviceId,
                                        tokenHash = tokenHash,
                                    )
                                }
                            },
                            onFailure = { error ->
                                item {
                                    ErrorNotice(
                                        error.message ?: "No se pudo preparar la información de alta del dispositivo.",
                                    )
                                }
                            }
                        )
                    } else if (bindingStatus == MonitoringBindingStatus.BOUND) {
                        item {
                            BoundIdentityCard(
                                sellerName = sellerName,
                                corporateLine = corporateLine,
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NavigationTab("Inicio", currentTab == 0, Modifier.weight(1f)) { currentTab = 0 }
                            NavigationTab("Reporte", currentTab == 1, Modifier.weight(1f)) { currentTab = 1 }
                        }
                    }
                    if (currentTab == 0) {
                        item {
                            StatusHero(
                                snapshot = snapshot,
                                permissionGranted = state.permissionGranted,
                                isSyncing = state.isSyncing,
                                onRequestPermission = onRequestPermission,
                            )
                        }
                        item { MetricsCard(snapshot.counts) }
                        item { LastSyncCard(snapshot) }
                        item {
                            Button(
                                onClick = onSyncPending,
                                enabled = state.permissionGranted && state.hasScanned &&
                                    !state.isSyncing && manualPendingCount > 0,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PilotColors.accent),
                            ) {
                                Text(if (state.isSyncing) "Sincronizando..." else "Sincronizar ahora")
                            }
                        }
                        state.errorMessage?.let { message -> item { ErrorNotice(message) } }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Últimas llamadas",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
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
                                    if (state.isLoading) "Comprobando las grabaciones del dispositivo..."
                                    else "No se pudieron cargar los datos. Tocá Actualizar para reintentar.",
                                )
                            }
                        } else {
                            if (calls.isEmpty()) {
                                item {
                                    QuietMessage("Todavía no se detectaron llamadas grabadas para el respaldo automático.")
                                }
                            } else {
                                items(visibleCalls, key = { it.contentUri.toString() }) { recording ->
                                    RecordingListItem(
                                        recording = recording,
                                        syncState = state.syncStatuses[recording.syncSourceKey()] ?: SyncRecordState(),
                                        playbackState = state.playbackState,
                                        isSyncing = state.isSyncing,
                                        isCurrentSync = state.syncingSourceKey == recording.syncSourceKey(),
                                        isAutomaticCall = true,
                                        onPlay = { onPlay(recording) },
                                        onSync = { onSync(recording) },
                                    )
                                }
                                if (calls.size > 3) {
                                    item {
                                        TextButton(onClick = { showAllCalls = !showAllCalls }) {
                                            Text(if (showAllCalls) "Ver menos" else "Ver todas las llamadas (${calls.size})")
                                        }
                                    }
                                }
                            }
                            if (otherRecordings.isNotEmpty()) {
                                item {
                                    TextButton(onClick = { showOtherRecordings = !showOtherRecordings }) {
                                        Text(
                                            if (showOtherRecordings) "Ocultar otras grabaciones"
                                            else "Otras grabaciones (${otherRecordings.size})",
                                        )
                                    }
                                }
                                if (showOtherRecordings) {
                                    items(otherRecordings, key = { "other:${it.contentUri}" }) { recording ->
                                        RecordingListItem(
                                            recording = recording,
                                            syncState = state.syncStatuses[recording.syncSourceKey()] ?: SyncRecordState(),
                                            playbackState = state.playbackState,
                                            isSyncing = state.isSyncing,
                                            isCurrentSync = state.syncingSourceKey == recording.syncSourceKey(),
                                            isAutomaticCall = false,
                                            onPlay = { onPlay(recording) },
                                            onSync = { onSync(recording) },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            ReportScreen(
                                recordings = calls,
                                syncStatuses = state.syncStatuses,
                                hasLoaded = state.hasScanned,
                                isLoading = state.isLoading,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerSetup(
    name: String,
    line: String,
    onNameChange: (String) -> Unit,
    onLineChange: (String) -> Unit,
    onSave: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DashboardHeader("", "")
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Configurá tu perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Estos datos identifican este teléfono durante el piloto. Se guardan en el dispositivo y no se pueden editar desde la aplicación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PilotColors.muted,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre y apellido") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = line,
                    onValueChange = onLineChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Línea corporativa") },
                    singleLine = true,
                )
                Button(
                    onClick = onSave,
                    enabled = name.isNotBlank() && line.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PilotColors.accent),
                ) {
                    Text("Guardar perfil")
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = PilotColors.error)
                }
                Text(
                    "Al reinstalar la aplicación puede generarse una nueva identidad y ser necesario volver a vincular el teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PilotColors.muted,
                )
            }
        }
    }
}

@Composable
private fun NavigationTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PilotColors.accent),
        ) { Text(label) }
    } else {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 46.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, PilotColors.border),
        ) { Text(label, color = PilotColors.muted) }
    }
}

@Composable
private fun EnrollmentCard(
    sellerName: String,
    corporateLine: String,
    deviceId: String,
    tokenHash: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Alta administrativa pendiente",
                style = MaterialTheme.typography.titleMedium,
                color = PilotColors.pending,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Compartí estos datos con el administrador para vincular este teléfono. El código mostrado es solo la huella pública; la credencial secreta nunca se muestra.",
                style = MaterialTheme.typography.bodySmall,
                color = PilotColors.muted,
            )
            EnrollmentIdentityLine("Vendedor", sellerName)
            EnrollmentIdentityLine("Línea corporativa", corporateLine)
            CopyableEnrollmentValue("DEVICE_ID", deviceId)
            CopyableEnrollmentValue("TOKEN_HASH · SHA-256", tokenHash)
        }
    }
}

@Composable
private fun EnrollmentIdentityLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PilotColors.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CopyableEnrollmentValue(label: String, value: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PilotColors.muted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = PilotColors.ink,
                fontWeight = FontWeight.Medium,
            )
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                    Toast.makeText(context, "$label copiado", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("Copiar")
            }
        }
    }
}

@Composable
private fun BoundIdentityCard(sellerName: String, corporateLine: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PilotColors.successSurface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Dispositivo vinculado", color = PilotColors.success, fontWeight = FontWeight.Bold)
            Text(
                "$sellerName · Línea $corporateLine",
                color = PilotColors.ink,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Identidad asignada centralmente · Solo lectura",
                color = PilotColors.muted,
                style = MaterialTheme.typography.labelSmall,
            )
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
        Text(message, modifier = Modifier.padding(16.dp), color = PilotColors.error)
    }
}

@Composable
private fun QuietMessage(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Text(message, modifier = Modifier.padding(18.dp), color = PilotColors.muted)
    }
}