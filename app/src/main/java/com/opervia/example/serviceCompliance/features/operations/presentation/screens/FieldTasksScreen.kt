package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldBackdrop
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldCard
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldIcon
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldSymbol
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.components.StatusPill
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldBlue
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldTeal
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldTheme
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldTasksViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TaskFilter { TODAY, PENDING, COMPLETED }
private enum class AppSection { HOME, WORK, HISTORY, PROFILE, SYNC }

@Composable
internal fun FieldTasksScreen(
    model: FieldTasksViewModel,
    fullName: String,
    executionHistory: List<ExecutionRecord>,
    localHistoryWarning: String?,
    onOpenObligation: (Long) -> Unit,
    onLogout: () -> Unit,
    onSynchronize: (List<ExecutionRecord>) -> Unit
) {
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
    var filter by rememberSaveable { mutableStateOf(TaskFilter.TODAY) }
    var section by rememberSaveable { mutableStateOf(AppSection.HOME) }
    val locallyCompletedIds = executionHistory.filter { it.result == ExecutionResult.COMPLETED }.map { it.obligationId }.toSet()
    val completed = state.obligations.filter { it.status in completedStatuses || it.id in locallyCompletedIds }
    val pending = state.obligations.filter { it.status !in completedStatuses && it.id !in locallyCompletedIds }
    val shown = when (filter) {
        TaskFilter.TODAY -> state.obligations.filter {
            it.id !in locallyCompletedIds && (it.status == "IN_PROGRESS" || isDueToday(it.dueAt))
        }
        TaskFilter.PENDING -> pending
        TaskFilter.COMPLETED -> completed
    }

    FieldTheme {
        FieldBackdrop {
            Scaffold(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.Center),
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = Color.White.copy(alpha = .96f)) {
                        NavigationBarItem(selected = section == AppSection.HOME, onClick = { section = AppSection.HOME },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("Inicio") })
                        NavigationBarItem(selected = section == AppSection.WORK, onClick = {
                            section = AppSection.WORK
                            filter = TaskFilter.PENDING
                        },
                            icon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null) }, label = { Text("Trabajo") })
                        NavigationBarItem(selected = section == AppSection.HISTORY, onClick = { section = AppSection.HISTORY },
                            icon = { Icon(Icons.Filled.History, contentDescription = null) }, label = { Text("Historial") })
                        NavigationBarItem(selected = section == AppSection.PROFILE, onClick = { section = AppSection.PROFILE },
                            icon = { Icon(Icons.Filled.Person, contentDescription = null) }, label = { Text("Perfil") })
                    }
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (section in setOf(AppSection.HOME, AppSection.WORK, AppSection.PROFILE)) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(LocalDate.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("es-PE"))).uppercase(),
                                style = MaterialTheme.typography.labelSmall, color = FieldMuted)
                            Text(when (section) {
                                AppSection.HOME -> "Inicio"
                                AppSection.WORK -> "Trabajo"
                                AppSection.PROFILE -> "Perfil"
                                else -> ""
                            }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Buenos días, ${fullName.substringBefore(' ').ifBlank { "Operario" }}", style = MaterialTheme.typography.bodyMedium, color = FieldMuted)
                        }
                        IconButton(onClick = { section = AppSection.SYNC }) {
                            Icon(Icons.Filled.CloudSync, contentDescription = "Centro de sincronización", tint = FieldBlue)
                        }
                        IconButton(onClick = model::refresh, enabled = !state.loading) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar obligaciones", tint = FieldBlue)
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión", tint = FieldMuted)
                        }
                    }
                    localHistoryWarning?.let { InfoNote(it, warning = true) }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.cacheError?.let { InfoNote(it, warning = true) }
                    if (state.error != null) {
                        InfoNote(
                            if (state.showingStaleData) "No se pudo actualizar. Se muestra la última lista guardada en este dispositivo."
                            else state.error.orEmpty(),
                            warning = true
                        )
                        if (!state.sessionExpired) Button(onClick = model::refresh, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reintentar")
                        }
                    }
                    if (section == AppSection.HISTORY) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HistoryScreen(executionHistory, state.obligations)
                        }
                    } else if (section == AppSection.SYNC) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SyncCenterScreen(state, executionHistory, state.obligations) { onSynchronize(executionHistory) }
                        }
                    } else if (section == AppSection.PROFILE) {
                        FieldCard {
                            Text("Perfil del operario", style = MaterialTheme.typography.titleLarge)
                            Text(fullName, style = MaterialTheme.typography.titleMedium)
                            Text("Sesión autenticada", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Cerrar sesión")
                            }
                        }
                    } else {
                      Text("${state.obligations.size} actividades asignadas", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                      if (section == AppSection.HOME) {
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(filter == TaskFilter.TODAY, { filter = TaskFilter.TODAY }, label = { Text("Hoy") })
                        FilterChip(filter == TaskFilter.PENDING, { filter = TaskFilter.PENDING }, label = { Text("Pendientes ${pending.size}") })
                        FilterChip(filter == TaskFilter.COMPLETED, { filter = TaskFilter.COMPLETED }, label = { Text("Completadas ${completed.size}") })
                      }
                      }
                    }
                    if (section in setOf(AppSection.HOME, AppSection.WORK)) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!state.loading && state.loaded && shown.isEmpty() &&
                            (filter != TaskFilter.COMPLETED || completed.isEmpty()) && state.error == null) {
                            FieldCard {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FieldTeal, modifier = Modifier.size(28.dp))
                                    Column {
                                        Text(when (filter) {
                                            TaskFilter.TODAY -> "No hay actividades para hoy"
                                            TaskFilter.COMPLETED -> "Aún no hay actividades completadas"
                                            else -> "No hay actividades pendientes"
                                        }, fontWeight = FontWeight.SemiBold)
                                        Text("Las obligaciones que te asignen aparecerán aquí.", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                                    }
                                }
                            }
                        }
                        shown.forEach { obligation ->
                            ObligationCard(obligation, onClick = { onOpenObligation(obligation.id) })
                        }
                        if (!state.loaded && state.loading) {
                            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObligationCard(obligation: AssignedObligation, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Color(0xFFE7F3FF), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = FieldBlue, modifier = Modifier.padding(9.dp).size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusPill(statusLabel(obligation.status), positive = obligation.status == "IN_PROGRESS" || obligation.status in completedStatuses, warning = obligation.status == "OVERDUE")
                    Text(obligation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(obligation.siteName, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                }
            }
            if (obligation.description?.isNotBlank() == true) Text(obligation.description, style = MaterialTheme.typography.bodySmall, color = FieldMuted, maxLines = 2)
            if (obligation.dueAt.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                FieldIcon(FieldSymbol.Clock)
                Spacer(Modifier.width(6.dp))
                Text("Fecha límite · ${dateLabel(obligation.dueAt)}", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
            }
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(if (obligation.status in completedStatuses) "Ver actividad" else "Abrir actividad")
            }
        }
    }
}

private val completedStatuses = setOf("SUBMITTED", "COMPLETED")

private fun isDueToday(value: String): Boolean {
    if (value.isBlank()) return false
    val lima = ZoneId.of("America/Lima")
    val dueDate = runCatching { Instant.parse(value).atZone(lima).toLocalDate() }
        .recoverCatching { OffsetDateTime.parse(value).atZoneSameInstant(lima).toLocalDate() }
        .getOrNull()
    return dueDate == LocalDate.now(lima)
}
