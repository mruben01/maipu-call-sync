package com.maipu.callsync.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object PilotColors {
    val ink = Color(0xFF19343C)
    val muted = Color(0xFF5D7175)
    val accent = Color(0xFF0B6E69)
    val background = Color(0xFFF3F7F6)
    val border = Color(0xFFDCE7E5)
    val success = Color(0xFF0A7366)
    val successSurface = Color(0xFFE5F3EF)
    val pending = Color(0xFF95620E)
    val pendingSurface = Color(0xFFFFF4E2)
    val error = Color(0xFFB13E46)
    val errorSurface = Color(0xFFFAEAEB)
}

@Composable
internal fun DashboardHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "MUNDO MAIPÚ  /  HERRAMIENTA INTERNA",
            style = MaterialTheme.typography.labelSmall,
            color = PilotColors.accent,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Maipú Call Sync",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PilotColors.ink,
        )
    }
}

@Composable
internal fun StatusHero(
    snapshot: DashboardSnapshot,
    permissionGranted: Boolean,
    isSyncing: Boolean,
    onRequestPermission: () -> Unit,
) {
    val (title, detail, tint, surface) = when (snapshot.status) {
        DashboardStatus.ACTIVE -> StatusAppearance(
            "Sincronización activa",
            "Sin pendientes en la última revisión.",
            PilotColors.success,
            PilotColors.successSurface,
        )
        DashboardStatus.PENDING -> StatusAppearance(
            "Pendiente",
            if (isSyncing) "Sincronizando ahora..." else "Hay llamadas esperando respaldo.",
            PilotColors.pending,
            PilotColors.pendingSurface,
        )
        DashboardStatus.ATTENTION -> StatusAppearance(
            "Requiere atención",
            if (!permissionGranted) {
                "Para respaldar llamadas, autorizá el acceso al audio."
            } else {
                "Revisá las llamadas con error más abajo."
            },
            PilotColors.error,
            PilotColors.errorSurface,
        )
        DashboardStatus.CHECKING -> StatusAppearance(
            "Comprobando estado",
            "Cargando las grabaciones de este dispositivo.",
            PilotColors.accent,
            PilotColors.successSurface,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    StatusGlyph(snapshot.status, tint)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PilotColors.ink,
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PilotColors.muted,
                    )
                }
            }
            Text(
                text = if (permissionGranted) {
                    "Tus llamadas se respaldan automáticamente."
                } else {
                    "Autorizá el acceso para activar el respaldo automático."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PilotColors.ink,
            )
            if (!permissionGranted) {
                TextButton(onClick = onRequestPermission) {
                    Text("Autorizar acceso al audio", color = PilotColors.error)
                }
            }
        }
    }
}

private data class StatusAppearance(
    val title: String,
    val detail: String,
    val tint: Color,
    val surface: Color,
)

@Composable
private fun StatusGlyph(status: DashboardStatus, tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = 2.3.dp.toPx()
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.43f,
            style = Stroke(width = stroke),
        )
        when (status) {
            DashboardStatus.ACTIVE -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.28f, size.height * 0.52f),
                    Offset(size.width * 0.44f, size.height * 0.67f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.44f, size.height * 0.67f),
                    Offset(size.width * 0.75f, size.height * 0.34f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            DashboardStatus.PENDING, DashboardStatus.CHECKING -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.29f),
                    Offset(size.width * 0.5f, size.height * 0.51f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.51f),
                    Offset(size.width * 0.67f, size.height * 0.59f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            DashboardStatus.ATTENTION -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.28f),
                    Offset(size.width * 0.5f, size.height * 0.57f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawCircle(tint, radius = stroke * 0.65f, center = Offset(
                    size.width * 0.5f,
                    size.height * 0.73f,
                ))
            }
        }
    }
}

@Composable
internal fun MetricsCard(counts: DashboardCounts?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Metric(counts?.synced, "Sincronizadas", PilotColors.success, Modifier.weight(1f))
            Metric(counts?.pending, "Pendientes", PilotColors.pending, Modifier.weight(1f))
            Metric(counts?.errors, "Con error", PilotColors.error, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(count: Int?, title: String, tint: Color, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = count?.toString() ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = PilotColors.muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
internal fun InformationCard(
    snapshot: DashboardSnapshot,
    manufacturer: String,
    model: String,
    androidVersion: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "ÚLTIMA SINCRONIZACIÓN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PilotColors.muted,
                )
                Text(
                    text = when {
                        snapshot.counts == null -> "No disponible todavía"
                        snapshot.lastSuccessfulSyncAtMillis == null ->
                            "Sin sincronizaciones registradas"
                        else -> SimpleDateFormat(
                            "dd MMM yyyy · HH:mm",
                            Locale("es", "AR"),
                        ).format(Date(snapshot.lastSuccessfulSyncAtMillis))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PilotColors.ink,
                )
                Text(
                    text = "De las llamadas detectadas en este dispositivo",
                    style = MaterialTheme.typography.bodySmall,
                    color = PilotColors.muted,
                )
            }
            HorizontalDivider(color = PilotColors.border)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "ESTE DISPOSITIVO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PilotColors.muted,
                    )
                    Text(
                        text = "$manufacturer $model",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PilotColors.ink,
                    )
                }
                Text(
                    text = "Android $androidVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = PilotColors.muted,
                )
            }
        }
    }
}