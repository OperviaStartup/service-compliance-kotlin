package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldCard
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldTeal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HistoryPeriod { TODAY, SEVEN_DAYS }
private enum class HistoryResult { ALL, COMPLETED, WITH_OBSERVATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    records: List<ExecutionRecord>,
    obligations: List<AssignedObligation>
) {
    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.TODAY) }
    var resultFilter by rememberSaveable { mutableStateOf(HistoryResult.ALL) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var selectedRecord by rememberSaveable { mutableStateOf<Long?>(null) }
    val lima = ZoneId.of("America/Lima")
    val today = LocalDate.now(lima)
    val filtered = records.filter { record ->
        val title = obligations.firstOrNull { it.id == record.obligationId }?.title.orEmpty()
        val date = runCatching { Instant.parse(record.executedAt).atZone(lima).toLocalDate() }.getOrNull()
        val inPeriod = date != null && when (period) {
            HistoryPeriod.TODAY -> date == today
            HistoryPeriod.SEVEN_DAYS -> !date.isBefore(today.minusDays(6)) && !date.isAfter(today)
        }
        val matchesResult = when (resultFilter) {
            HistoryResult.ALL -> true
            HistoryResult.COMPLETED -> record.result == ExecutionResult.COMPLETED
            HistoryResult.WITH_OBSERVATION -> record.result != ExecutionResult.COMPLETED
        }
        inPeriod && matchesResult && title.contains(query.trim(), ignoreCase = true)
    }.sortedByDescending { it.executedAt }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Historial", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Tus ejecuciones registradas", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Buscar actividad…") },
            shape = RoundedCornerShape(12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(period == HistoryPeriod.TODAY, { period = HistoryPeriod.TODAY }, label = { Text("Hoy") })
            FilterChip(period == HistoryPeriod.SEVEN_DAYS, { period = HistoryPeriod.SEVEN_DAYS }, label = { Text("7 días") })
            FilterChip(showFilterSheet || resultFilter != HistoryResult.ALL, { showFilterSheet = true },
                label = { Text("Filtros") }, leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) })
        }
        if (resultFilter != HistoryResult.ALL) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(resultFilter == HistoryResult.COMPLETED, { resultFilter = HistoryResult.COMPLETED }, label = { Text("Conforme") })
                FilterChip(resultFilter == HistoryResult.WITH_OBSERVATION, { resultFilter = HistoryResult.WITH_OBSERVATION }, label = { Text("Con observación") })
            }
        }
        InfoNote("Se muestran las ejecuciones confirmadas por el servidor durante esta sesión. El backend aún no expone un historial consultable.")
        if (filtered.isEmpty()) {
            FieldCard {
                Text(if (records.isEmpty()) "No hay ejecuciones disponibles en esta sesión" else "No se encontraron actividades", fontWeight = FontWeight.SemiBold)
                Text("Cambia el período o los filtros para revisar otros registros.", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
            }
        } else {
            val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("es-PE"))
            filtered.groupBy { record ->
                runCatching { Instant.parse(record.executedAt).atZone(lima).toLocalDate() }.getOrNull()
            }.forEach { (date, items) ->
                Text(date?.format(formatter)?.uppercase(Locale.forLanguageTag("es-PE")) ?: "FECHA NO DISPONIBLE",
                    style = MaterialTheme.typography.labelMedium, color = FieldMuted, fontWeight = FontWeight.Bold)
                items.forEach { record ->
                    HistoryRecordCard(
                        record = record,
                        title = obligations.firstOrNull { it.id == record.obligationId }?.title ?: "Actividad #${record.obligationId}",
                        onClick = { selectedRecord = record.id }
                    )
                }
            }
        }
    }

    val record = records.firstOrNull { it.id == selectedRecord }
    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Filtrar actividades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                HistoryResult.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = resultFilter == option, onClick = { resultFilter = option })
                        Text(when (option) {
                            HistoryResult.ALL -> "Todas"
                            HistoryResult.COMPLETED -> "Conforme"
                            HistoryResult.WITH_OBSERVATION -> "Con observación"
                        })
                    }
                }
                TextButton(onClick = {
                    resultFilter = HistoryResult.ALL
                    showFilterSheet = false
                }) { Text("Limpiar filtros") }
                TextButton(onClick = { showFilterSheet = false }) { Text("Aplicar filtros") }
            }
        }
    }
    if (record != null) {
        AlertDialog(
            onDismissRequest = { selectedRecord = null },
            title = { Text(obligations.firstOrNull { it.id == record.obligationId }?.title ?: "Detalle de ejecución") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resultado: ${record.result.label()}")
                    Text("Registrada: ${dateLabel(record.executedAt)}")
                    record.notes?.let { Text("Notas: $it") }
                    Text("El servidor no ofrece todavía flujo de correcciones del supervisor.", color = FieldMuted)
                }
            },
            confirmButton = { TextButton(onClick = { selectedRecord = null }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun HistoryRecordCard(record: ExecutionRecord, title: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = if (record.result == ExecutionResult.COMPLETED) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (record.result == ExecutionResult.COMPLETED) FieldTeal else Color(0xFFE69B12),
                modifier = Modifier.size(26.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${timeLabel(record.executedAt)} · ${record.result.label()}", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Ver detalle", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun ExecutionResult.label(): String = when (this) {
    ExecutionResult.COMPLETED -> "Conforme"
    ExecutionResult.PARTIAL -> "Parcial"
    ExecutionResult.BLOCKED -> "Con observación"
}

private fun timeLabel(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-PE"))
        .withZone(ZoneId.of("America/Lima")).format(Instant.parse(value))
}.getOrDefault("--:--")
