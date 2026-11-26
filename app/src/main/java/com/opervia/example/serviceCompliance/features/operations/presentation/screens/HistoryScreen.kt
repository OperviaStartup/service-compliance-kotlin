package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.domain.model.HistoryReviewStatus
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldCard
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.HistoryPeriod
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.HistoryState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    state: HistoryState,
    onQueryChanged: (String) -> Unit,
    onPeriodChanged: (HistoryPeriod) -> Unit,
    onReviewChanged: (HistoryReviewStatus?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSessionExpired: () -> Unit
) {
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var selectedRecord by rememberSaveable { mutableStateOf<Long?>(null) }
    val entries = state.items.map { it.toHistoryEntry() }
    val lima = ZoneId.of("America/Lima")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Historial", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Tus ejecuciones registradas", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
            }
            IconButton(onClick = onRefresh, enabled = !state.loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Actualizar historial")
            }
        }
        OutlinedTextField(value = state.query, onValueChange = onQueryChanged, modifier = Modifier.fillMaxWidth(),
            singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Buscar actividad…") }, shape = RoundedCornerShape(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.period == HistoryPeriod.TODAY, { onPeriodChanged(HistoryPeriod.TODAY) }, label = { Text("Hoy") })
            FilterChip(state.period == HistoryPeriod.SEVEN_DAYS, { onPeriodChanged(HistoryPeriod.SEVEN_DAYS) }, label = { Text("7 días") })
            FilterChip(showFilterSheet || state.reviewStatus != null, { showFilterSheet = true }, label = { Text("Filtros") },
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) })
        }
        state.reviewStatus?.let { FilterChip(true, { showFilterSheet = true }, label = { Text(reviewStatusLabel(it)) }) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.showingStaleData) {
            InfoNote("No se pudo actualizar. Se muestra la última consulta confirmada en esta sesión; la evaluación puede haber cambiado.", warning = true)
        }
        state.error?.let {
            InfoNote(it, warning = true)
            TextButton(onClick = if (state.sessionExpired) onSessionExpired else onRefresh) {
                Text(if (state.sessionExpired) "Iniciar sesión nuevamente" else "Reintentar consulta")
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            if (entries.isEmpty() && !state.loading && state.error == null) {
                item {
                    FieldCard {
                        Text("No se encontraron ejecuciones", fontWeight = FontWeight.SemiBold)
                        Text("Cambia el período o los filtros para revisar otros registros.", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                    }
                }
            }
            val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("es-PE"))
            entries.groupBy { runCatching { Instant.parse(it.record.recordedAt).atZone(lima).toLocalDate() }.getOrNull() }
                .forEach { (date, group) ->
                    item(key = "date-$date") {
                        Text(date?.format(formatter)?.uppercase(Locale.forLanguageTag("es-PE")) ?: "FECHA NO DISPONIBLE",
                            style = MaterialTheme.typography.labelMedium, color = FieldMuted, fontWeight = FontWeight.Bold)
                    }
                    items(group, key = { it.record.id }) { entry -> HistoryRecordCard(entry) { selectedRecord = entry.record.id } }
                }
            if (state.hasMore && !state.showingStaleData) {
                item {
                    OutlinedButton(onClick = onLoadMore, enabled = !state.loading && !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.loadingMore) "Cargando…" else "Cargar más")
                    }
                }
            }
            state.asOf?.let { asOf -> item {
                Text("Consulta al servidor: ${dateLabel(asOf)}", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                Text("El resultado de la ejecución y la evaluación del supervisor son independientes.",
                    style = MaterialTheme.typography.bodySmall, color = FieldMuted)
            } }
        }
    }
    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }, sheetState = rememberModalBottomSheetState(), containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Filtrar actividades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().selectable(selected = state.reviewStatus == null, role = Role.RadioButton,
                    onClick = { onReviewChanged(null) }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.reviewStatus == null, onClick = null)
                    Text("Todas")
                }
                HistoryReviewStatus.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().selectable(selected = state.reviewStatus == option, role = Role.RadioButton,
                        onClick = { onReviewChanged(option) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.reviewStatus == option, onClick = null)
                        Text(reviewStatusLabel(option))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onReviewChanged(null)
                            showFilterSheet = false
                        }
                    ) { Text("Limpiar filtros") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { showFilterSheet = false }
                    ) { Text("Aplicar filtros") }
                }
            }
        }
    }
    entries.firstOrNull { it.record.id == selectedRecord }?.let { entry ->
        val record = entry.record
        AlertDialog(onDismissRequest = { selectedRecord = null }, title = { Text(record.activity) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Revisión: ${entry.reviewLabel}")
                Text("Resultado de ejecución: ${entry.declaredResultLabel}")
                Text("Registrada: ${dateLabel(record.recordedAt)}")
                Text("Centro: ${record.siteName}")
                Text("Sincronización: guardada en el servidor")
                record.notes?.takeIf(String::isNotBlank)?.let { Text("Notas: $it") }
                record.complianceResult?.let {
                    Text("Evaluación ${it.decisionVersion} · ${dateLabel(it.decidedAt)}")
                    Text(it.explanation)
                }
                Text("El resultado operativo no determina la evaluación del supervisor.", color = FieldMuted)
            }
        }, confirmButton = { TextButton(onClick = { selectedRecord = null }) { Text("Cerrar") } })
    }
}

@Composable
private fun HistoryRecordCard(entry: HistoryEntry, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = FieldMuted, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.record.activity, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${dateLabel(entry.record.recordedAt)} · ${entry.reviewLabel}", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                Text("Resultado de ejecución: ${entry.declaredResultLabel}", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Ver detalle", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
