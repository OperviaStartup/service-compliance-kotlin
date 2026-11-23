package com.opervia.example.serviceCompliance.features.operations.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldCard
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldIcon
import com.opervia.example.serviceCompliance.features.operations.presentation.components.IconTile
import com.opervia.example.serviceCompliance.features.operations.presentation.components.PrimaryAction
import com.opervia.example.serviceCompliance.features.operations.presentation.viewmodel.FieldTasksState
import com.opervia.example.serviceCompliance.features.operations.presentation.components.FieldSymbol
import com.opervia.example.serviceCompliance.features.operations.presentation.components.InfoNote
import com.opervia.example.serviceCompliance.features.operations.presentation.components.StatusPill
import com.opervia.example.serviceCompliance.features.operations.domain.model.AssignedObligation
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionRecord
import com.opervia.example.serviceCompliance.features.operations.domain.model.ExecutionResult
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldBlue
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldMuted
import com.opervia.example.serviceCompliance.features.operations.presentation.theme.FieldPaleBlue
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
    FieldTasksContent(
        state, fullName, executionHistory, localHistoryWarning, onOpenObligation, onLogout,
        onSynchronize, model::refresh
    )
}

@Composable
internal fun FieldTasksContent(
    state: FieldTasksState,
    fullName: String,
    executionHistory: List<ExecutionRecord>,
    localHistoryWarning: String?,
    onOpenObligation: (Long) -> Unit,
    onLogout: () -> Unit,
    onSynchronize: (List<ExecutionRecord>) -> Unit,
    onRefresh: () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf(TaskFilter.TODAY) }
    var section by rememberSaveable { mutableStateOf(AppSection.HOME) }
    val locallyCompletedIds = executionHistory.filter { it.result == ExecutionResult.COMPLETED }.map { it.obligationId }.toSet()
    val completed = state.obligations.filter { it.status in completedStatuses || it.id in locallyCompletedIds }
    val pending = state.obligations.filter { it.status !in completedStatuses && it.id !in locallyCompletedIds }
    val shown = when (if (section == AppSection.HOME) TaskFilter.TODAY else filter) {
        TaskFilter.TODAY -> state.obligations.filter {
            it.id !in locallyCompletedIds && (it.status == "IN_PROGRESS" || isDueToday(it.dueAt))
        }
        TaskFilter.PENDING -> pending
        TaskFilter.COMPLETED -> completed
    }

    FieldTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Scaffold(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        val itemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FieldBlue, selectedTextColor = FieldBlue,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = FieldMuted, unselectedTextColor = FieldMuted
                        )
                        NavigationBarItem(selected = section == AppSection.HOME, onClick = { section = AppSection.HOME },
                            icon = { Icon(if (section == AppSection.HOME) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null) }, label = { Text("Inicio") }, colors = itemColors)
                        NavigationBarItem(selected = section == AppSection.WORK, onClick = {
                            section = AppSection.WORK
                            filter = TaskFilter.TODAY
                        },
                            icon = { Icon(Icons.Outlined.AssignmentTurnedIn, contentDescription = null) }, label = { Text("Trabajo") }, colors = itemColors)
                        NavigationBarItem(selected = section == AppSection.HISTORY, onClick = { section = AppSection.HISTORY },
                            icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) }, label = { Text("Historial") }, colors = itemColors)
                        NavigationBarItem(selected = section == AppSection.PROFILE, onClick = { section = AppSection.PROFILE },
                            icon = { Icon(Icons.Outlined.Person, contentDescription = null) }, label = { Text("Perfil") }, colors = itemColors)
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
                                AppSection.HOME -> "Buenos días, ${fullName.substringBefore(' ').ifBlank { "Operario" }}"
                                AppSection.WORK -> "Trabajo"
                                AppSection.PROFILE -> "Perfil"
                                else -> ""
                            }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { section = AppSection.SYNC }) {
                            Icon(Icons.Filled.CloudSync, contentDescription = "Centro de sincronización", tint = FieldBlue)
                        }
                        IconButton(onClick = onRefresh, enabled = !state.loading) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar obligaciones", tint = FieldBlue)
                        }
                    }
                    localHistoryWarning?.let { InfoNote(it, warning = true) }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.cacheError?.let { InfoNote(it, warning = true) }
                    if (state.error != null && !state.showingStaleData) {
                        InfoNote(state.error.orEmpty(), warning = true)
                        if (!state.sessionExpired) Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
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
                        if (section == AppSection.WORK) {
                            Text("${state.obligations.size} actividades asignadas", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val chipColors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FieldBlue, selectedLabelColor = Color.White,
                                    containerColor = Color.White, labelColor = FieldMuted
                                )
                                FilterChip(filter == TaskFilter.TODAY, { filter = TaskFilter.TODAY }, label = { Text("Hoy") }, shape = CircleShape, colors = chipColors)
                                FilterChip(filter == TaskFilter.PENDING, { filter = TaskFilter.PENDING }, label = { Text("Pendientes ${pending.size}") }, shape = CircleShape, colors = chipColors)
                                FilterChip(filter == TaskFilter.COMPLETED, { filter = TaskFilter.COMPLETED }, label = { Text("Completadas ${completed.size}") }, shape = CircleShape, colors = chipColors)
                            }
                        }
                    }
                    if (section in setOf(AppSection.HOME, AppSection.WORK)) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.showingStaleData) {
                            SavedTasksNotice(onRetry = if (state.sessionExpired) null else onRefresh)
                        }
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
                        val ordered = shown.sortedWith(compareBy<AssignedObligation> { it.status != "IN_PROGRESS" }
                            .thenBy { it.dueAt })
                        val featured = ordered.firstOrNull {
                            it.status == "IN_PROGRESS" || (section == AppSection.HOME && it.status == "ASSIGNED")
                        }
                        featured?.let { obligation ->
                            ObligationCard(obligation, onClick = { onOpenObligation(obligation.id) })
                        }
                        val remaining = ordered.filter { it.id != featured?.id }
                        val completedIds = completed.map { it.id }.toSet()
                        val remainingPending = remaining.filter { it.id !in completedIds }
                        val remainingCompleted = remaining.filter { it.id in completedIds }
                        if (remainingPending.isNotEmpty()) {
                            Text(if (section == AppSection.HOME && featured != null) "Después" else "Pendientes",
                                style = MaterialTheme.typography.titleLarge)
                            ActivityList(remainingPending, onOpenObligation)
                        }
                        if (remainingCompleted.isNotEmpty()) {
                            Text("Completadas", style = MaterialTheme.typography.titleLarge)
                            ActivityList(remainingCompleted, onOpenObligation)
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
private fun SavedTasksNotice(onRetry: (() -> Unit)?) {
    Surface(color = FieldPaleBlue, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = FieldBlue)
            Column(Modifier.weight(1f)) {
                Text("Lista guardada", style = MaterialTheme.typography.titleSmall)
                Text("Se muestra la última actualización disponible.", style = MaterialTheme.typography.bodySmall, color = FieldMuted)
            }
            onRetry?.let { TextButton(onClick = it) { Text("Reintentar") } }
        }
    }
}

@Composable
private fun ObligationCard(obligation: AssignedObligation, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(FieldSymbol.Steps)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusPill(statusLabel(obligation.status), positive = obligation.status in completedStatuses, warning = obligation.status == "OVERDUE")
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
            PrimaryAction(
                text = if (obligation.status == "IN_PROGRESS") "Continuar" else "Ver detalle",
                onClick = onClick,
                icon = Icons.Filled.PlayArrow
            )
        }
    }
}

@Composable
private fun ActivityList(obligations: List<AssignedObligation>, onOpen: (Long) -> Unit) {
    FieldCard {
        obligations.forEachIndexed { index, obligation ->
            if (index > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(obligation.id) }.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTile(FieldSymbol.Clock)
                Column(Modifier.weight(1f)) {
                    Text(obligation.title, style = MaterialTheme.typography.titleSmall)
                    Text(obligation.siteName, style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                    if (obligation.dueAt.isNotBlank()) {
                        Text(dateLabel(obligation.dueAt), style = MaterialTheme.typography.bodySmall, color = FieldMuted)
                    }
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = FieldMuted)
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
