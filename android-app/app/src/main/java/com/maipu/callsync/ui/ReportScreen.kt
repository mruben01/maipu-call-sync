package com.maipu.callsync.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maipu.callsync.data.CallRecording
import com.maipu.callsync.data.SyncRecordState
import com.maipu.callsync.data.SyncStatus
import com.maipu.callsync.data.syncSourceKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal enum class ReportPeriod(val label: String, val days: Int) {
    TODAY("Hoy", 1),
    YESTERDAY("Ayer", 1),
    SEVEN_DAYS("7 días", 7),
}

internal data class LocalCallStats(
    val total: Int,
    val synced: Int,
    val pending: Int,
    val errors: Int,
    val validDurationCount: Int,
    val durationTotalMillis: Long,
    val dailyCounts: List<Int>,
) {
    val syncedPercent: Int?
        get() = if (total > 0) (synced * 100.0 / total).toInt() else null

    val averageDurationMillis: Long?
        get() = if (validDurationCount > 0) durationTotalMillis / validDurationCount else null
}

internal enum class ReportSyncState {
    SYNCED,
    PENDING,
    ERROR,
}

internal data class ReportCallFact(
    val callDateMillis: Long?,
    val syncState: ReportSyncState,
    val durationMillis: Long?,
)

internal fun reportCallDate(recording: CallRecording): Long? =
    recording.parsedFileName?.parsedDate?.time

internal fun callStatsForPeriod(
    recordings: List<CallRecording>,
    statuses: Map<String, SyncRecordState>,
    period: ReportPeriod,
    nowMillis: Long = System.currentTimeMillis(),
): LocalCallStats = calculateCallStats(
    calls = recordings.map { recording ->
        val syncState = statuses[recording.syncSourceKey()] ?: SyncRecordState()
        ReportCallFact(
            callDateMillis = reportCallDate(recording),
            syncState = when {
                syncState.status == SyncStatus.SYNCED && syncState.lastError == null ->
                    ReportSyncState.SYNCED
                syncState.status == SyncStatus.FAILED || syncState.lastError != null ->
                    ReportSyncState.ERROR
                else -> ReportSyncState.PENDING
            },
            durationMillis = recording.durationMs,
        )
    },
    period = period,
    nowMillis = nowMillis,
)

internal fun calculateCallStats(
    calls: List<ReportCallFact>,
    period: ReportPeriod,
    nowMillis: Long,
): LocalCallStats {
    val today = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val firstDay = (today.clone() as Calendar).apply {
        when (period) {
            ReportPeriod.TODAY -> Unit
            ReportPeriod.YESTERDAY -> add(Calendar.DAY_OF_YEAR, -1)
            ReportPeriod.SEVEN_DAYS -> add(Calendar.DAY_OF_YEAR, -6)
        }
    }
    val afterEnd = (today.clone() as Calendar).apply {
        if (period != ReportPeriod.YESTERDAY) add(Calendar.DAY_OF_YEAR, 1)
    }
    val start = firstDay.timeInMillis
    val endExclusive = when (period) {
        ReportPeriod.YESTERDAY -> today.timeInMillis
        else -> afterEnd.timeInMillis
    }
    val inPeriod = calls.filter { call ->
        call.callDateMillis?.let { it >= start && it < endExclusive } == true
    }
    val synced = inPeriod.count { it.syncState == ReportSyncState.SYNCED }
    val errors = inPeriod.count { it.syncState == ReportSyncState.ERROR }
    val validDurations = inPeriod.mapNotNull { it.durationMillis?.takeIf { duration -> duration > 0L } }
    val daily = if (period == ReportPeriod.SEVEN_DAYS) {
        (0..6).map { offset ->
            val dayStart = (firstDay.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            inPeriod.count { call ->
                call.callDateMillis?.let {
                    it >= dayStart.timeInMillis && it < dayEnd.timeInMillis
                } == true
            }
        }
    } else {
        emptyList()
    }
    return LocalCallStats(
        total = inPeriod.size,
        synced = synced,
        pending = inPeriod.size - synced - errors,
        errors = errors,
        validDurationCount = validDurations.size,
        durationTotalMillis = validDurations.sum(),
        dailyCounts = daily,
    )
}

@Composable
internal fun ReportScreen(
    recordings: List<CallRecording>,
    syncStatuses: Map<String, SyncRecordState>,
    hasLoaded: Boolean,
    isLoading: Boolean,
) {
    var period by rememberSaveable { mutableStateOf(ReportPeriod.TODAY) }
    val stats = callStatsForPeriod(recordings, syncStatuses, period)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Reporte", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Datos de este teléfono", style = MaterialTheme.typography.bodyMedium, color = PilotColors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ReportPeriod.values().forEach { option ->
                val selected = period == option
                Surface(
                    onClick = { period = option },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) PilotColors.accent else Color.White,
                    contentColor = if (selected) Color.White else PilotColors.muted,
                    border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, PilotColors.border),
                ) {
                    Text(
                        option.label,
                        modifier = Modifier.padding(vertical = 13.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (!hasLoaded && !isLoading) {
            ReportEmptyState("Todavía no hay datos para armar el reporte.")
        } else if (!hasLoaded && isLoading) {
            ReportEmptyState("Cargando las llamadas de este teléfono…")
        } else if (stats.total == 0) {
            ReportEmptyState("No hay llamadas con fecha identificable en este período.")
        } else {
            ReportMetrics(stats)
            DonutReportCard(stats)
            if (period == ReportPeriod.SEVEN_DAYS) DailyBarsCard(stats.dailyCounts)
        }
        LatestCallsCard(recordings, syncStatuses)
    }
}

@Composable
private fun ReportMetrics(stats: LocalCallStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Resumen del período", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryValue("Llamadas", stats.total.toString())
                SummaryValue("Sincronizadas", stats.synced.toString(), PilotColors.success)
                SummaryValue("Pendientes", stats.pending.toString(), PilotColors.pending)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryValue("Con error", stats.errors.toString(), PilotColors.error)
                SummaryValue("Duración total", formatReportDuration(stats.durationTotalMillis.takeIf { stats.validDurationCount > 0 }))
                SummaryValue("Promedio", formatReportDuration(stats.averageDurationMillis))
            }
        }
    }
}

@Composable
private fun RowScope.SummaryValue(label: String, value: String, tint: Color = PilotColors.ink) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = PilotColors.muted)
    }
}

@Composable
private fun DonutReportCard(stats: LocalCallStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sincronización", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
                Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                    val stroke = 16.dp.toPx()
                    val boundsInset = stroke / 2
                    val topLeft = Offset(boundsInset, boundsInset)
                    val arcSize = androidx.compose.ui.geometry.Size(
                        size.width - stroke,
                        size.height - stroke,
                    )
                    val colors = listOf(PilotColors.success, PilotColors.pending, PilotColors.error)
                    val values = listOf(stats.synced, stats.pending, stats.errors)
                    var startAngle = -90f
                    values.forEachIndexed { index, count ->
                        if (count > 0) {
                            val sweep = 360f * count / stats.total
                            drawArc(
                                color = colors[index],
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Butt),
                            )
                            startAngle += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${stats.syncedPercent ?: 0}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("SINCRONIZADO", style = MaterialTheme.typography.labelSmall, color = PilotColors.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem("Sincronizadas", stats.synced, PilotColors.success)
                LegendItem("Pendientes", stats.pending, PilotColors.pending)
                LegendItem("Errores", stats.errors, PilotColors.error)
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = PilotColors.muted)
    }
}

@Composable
private fun DailyBarsCard(dailyCounts: List<Int>) {
    val dayLabels = rememberDayLabels()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Llamadas por día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(145.dp)) {
                val maxCount = dailyCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
                val gap = size.width / (dailyCounts.size * 2f + 1f)
                val barWidth = gap * 0.9f
                val baseY = size.height - 4.dp.toPx()
                drawLine(
                    color = PilotColors.border,
                    start = Offset(0f, baseY),
                    end = Offset(size.width, baseY),
                    strokeWidth = 1.dp.toPx(),
                )
                dailyCounts.forEachIndexed { index, count ->
                    if (count > 0) {
                        val left = gap + index * gap * 2
                        val barHeight = (size.height - 20.dp.toPx()) * count / maxCount
                        drawRoundRect(
                            color = PilotColors.accent,
                            topLeft = Offset(left, baseY - barHeight),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(3.dp.toPx())),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dayLabels.forEach { day ->
                    Text(day, style = MaterialTheme.typography.labelSmall, color = PilotColors.muted)
                }
            }
        }
    }
}

@Composable
private fun rememberDayLabels(): List<String> {
    val formatter = SimpleDateFormat("EE", Locale("es", "AR"))
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -6)
    }
    return (0..6).map {
        val label = formatter.format(today.time).replaceFirstChar { character -> character.titlecase(Locale("es", "AR")) }
        today.add(Calendar.DAY_OF_YEAR, 1)
        label
    }
}

@Composable
private fun LatestCallsCard(recordings: List<CallRecording>, statuses: Map<String, SyncRecordState>) {
    val sorted = recordings.sortedByDescending { reportCallDate(it) ?: Long.MIN_VALUE }.take(5)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Últimas llamadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (sorted.isEmpty()) {
            ReportEmptyState("Todavía no hay llamadas disponibles.")
        } else {
            sorted.forEach { recording ->
                val sync = statuses[recording.syncSourceKey()] ?: SyncRecordState()
                val status = when {
                    sync.status == SyncStatus.SYNCED && sync.lastError == null -> "Sincronizada" to PilotColors.success
                    sync.status == SyncStatus.FAILED || sync.lastError != null -> "Con error" to PilotColors.error
                    else -> "Pendiente" to PilotColors.pending
                }
                val date = reportCallDate(recording)?.let {
                    SimpleDateFormat("dd MMM · HH:mm", Locale("es", "AR")).format(Date(it))
                } ?: "Fecha/hora no disponible"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            recording.parsedFileName?.phoneNumber ?: "Número no disponible",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("$date · ${formatReportDuration(recording.durationMs)}", color = PilotColors.muted, style = MaterialTheme.typography.bodySmall)
                        Text(status.first, color = status.second, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportEmptyState(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("—", style = MaterialTheme.typography.headlineMedium, color = PilotColors.accent)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = PilotColors.muted, textAlign = TextAlign.Center)
        }
    }
}

private fun formatReportDuration(durationMillis: Long?): String {
    if (durationMillis == null || durationMillis <= 0L) return "No disponible"
    val totalSeconds = durationMillis / 1_000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}