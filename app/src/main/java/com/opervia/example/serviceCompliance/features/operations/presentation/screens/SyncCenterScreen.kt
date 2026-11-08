package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldCard
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldBlue
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldTeal
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldTasksState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SyncCenterScreen(
    state: FieldTasksState,
    records: List<ExecutionRecord>,
    obligations: List<AssignedObligation>,
    onSynchronize: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Centro de sincronización", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Surface(color = Color(0xFFE4F5F2), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CloudDone, contentDescription = null, tint = FieldTeal)
                Column(Modifier.weight(1f)) {
                    Text("Conectividad con el servidor", fontWeight = FontWeight.SemiBold)
                    val connectionLabel = when {
                        state.synchronizing -> "Consultando el servidor…"
                        state.syncError != null -> "No se pudo verificar"
                        state.lastSyncAt != null -> "Disponible al consultar"
                        else -> "Aún no verificada"
                    }
                    Text(connectionLabel, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                }
                val statusColor = if (state.syncError != null) Color(0xFFB44925) else FieldTeal
                Text(if (state.syncError != null) "Revisar" else if (state.lastSyncAt == null) "—" else "En línea",
                    style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
        }
        Button(onClick = onSynchronize, enabled = !state.synchronizing, modifier = Modifier.fillMaxWidth()) {
            if (state.synchronizing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else Icon(Icons.Filled.Sync, contentDescription = null)
            Text(if (state.synchronizing) "  Verificando…" else "  Sincronizar ahora")
        }
        state.syncError?.let { message ->
            Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Color(0xFFB44925))
                    Text(message, color = Color(0xFF8C251D), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        state.lastSyncAt?.let { instant ->
            Text("Última verificación: ${DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-PE")).withZone(ZoneId.of("America/Lima")).format(instant)}",
                style = MaterialTheme.typography.bodySmall, color = FieldMuted)
        }
        FieldCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = FieldBlue)
                Text("Pendientes por enviar: 0", fontWeight = FontWeight.Bold)
            }
            Text("No hay cola sin conexión en esta versión. Las ejecuciones se envían directamente al servidor.",
                style = MaterialTheme.typography.bodySmall, color = FieldMuted)
        }
        Text("Confirmados durante esta sesión (${records.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (records.isEmpty()) {
            FieldCard { Text("Aún no hay registros enviados desde esta sesión.", color = FieldMuted) }
        } else {
            records.forEach { record ->
                val title = obligations.firstOrNull { it.id == record.obligationId }?.title ?: "Actividad #${record.obligationId}"
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = FieldTeal)
                        Column(Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.Bold)
                            Text("Registro #${record.id} · ${dateLabel(record.executedAt)}", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                        }
                        Text("Confirmado", style = MaterialTheme.typography.labelSmall, color = FieldTeal)
                    }
                }
            }
        }
        InfoNote("La API no ofrece una cola local ni claves de idempotencia. La acción verifica los registros ya aceptados y actualiza las obligaciones; no reenvía operaciones.")
    }
}
